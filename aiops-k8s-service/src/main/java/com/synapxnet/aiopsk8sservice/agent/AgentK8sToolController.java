package com.synapxnet.aiopsk8sservice.agent;

import com.synapxnet.aiopsk8sservice.service.K8sMonitoringService;
import com.synapxnet.aiopsk8sservice.service.K8sPodService;
import com.synapxnet.aiopsk8sservice.service.K8sWorkloadService;
import com.synapxnet.goai.contract.AgentContract;
import com.synapxnet.goai.contract.AgentContractException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 聚合 Kubernetes Workload、Pod、Event 和真实监控数据并返回脱敏证据。
 */
@RestController
public class AgentK8sToolController {

    private static final String TOOL_NAME = "aiops.k8s.workload.get";
    private static final Set<String> SUPPORTED_KINDS = Set.of("Deployment", "StatefulSet", "DaemonSet");
    private static final Set<String> SAFE_ANNOTATIONS = Set.of(
            "deployment.kubernetes.io/revision", "app.kubernetes.io/version",
            "prometheus.io/scrape", "prometheus.io/port");
    private final K8sWorkloadService workloadService;
    private final K8sPodService podService;
    private final K8sMonitoringService monitoringService;

    /**
     * 创建 Kubernetes 证据工具 Controller。
     *
     * @param workloadService Workload 领域服务
     * @param podService Pod 与 Event 领域服务
     * @param monitoringService Prometheus 监控领域服务
     */
    public AgentK8sToolController(
            K8sWorkloadService workloadService,
            K8sPodService podService,
            K8sMonitoringService monitoringService) {
        this.workloadService = workloadService;
        this.podService = podService;
        this.monitoringService = monitoringService;
    }

    /**
     * 获取指定 Workload 的结构化、脱敏、可验证证据。
     *
     * @param body 强类型工具请求
     * @param servletRequest 当前 HTTP 请求
     * @return Kubernetes Workload 证据
     */
    @PostMapping("/api/agent/v1/tools/aiops.k8s.workload.get:invoke")
    public AgentContract.ToolResponse<WorkloadEvidence> invoke(
            @RequestBody AgentContract.ToolRequest<WorkloadArguments> body,
            HttpServletRequest servletRequest) {
        long startedNanos = System.nanoTime();
        AgentContract.RequestContext context = AgentContract.requireContext(servletRequest, TOOL_NAME, body);
        WorkloadArguments arguments = requireArguments(body.arguments());
        try {
            WorkloadEvidence evidence = collect(arguments);
            return AgentContract.success(
                    evidence, context, "XnetAIops/k8s", evidence.resourceVersion(), startedNanos);
        } catch (AgentContractException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (exception.getClass().getSimpleName().contains("NotFound")) {
                throw new AgentContractException(404, "RESOURCE_NOT_FOUND", "Kubernetes Workload 不存在");
            }
            throw new AgentContractException(
                    503, "UPSTREAM_UNAVAILABLE", "Kubernetes 集群暂时不可用", true, Map.of());
        }
    }

    /**
     * 校验集群、命名空间、类型、名称和观测窗口。
     *
     * @param arguments Workload 查询参数
     * @return 已校验参数
     */
    private WorkloadArguments requireArguments(WorkloadArguments arguments) {
        if (arguments == null || arguments.clusterId() == null || arguments.clusterId().isBlank()
                || arguments.namespace() == null || arguments.namespace().isBlank()
                || arguments.kind() == null || arguments.name() == null || arguments.name().isBlank()) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "clusterId、namespace、kind 和 name 均不能为空");
        }
        if (!SUPPORTED_KINDS.contains(arguments.kind())) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "kind 仅支持 Deployment、StatefulSet、DaemonSet");
        }
        try {
            Long.parseLong(arguments.clusterId());
        } catch (NumberFormatException exception) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "clusterId 必须是有效数字字符串");
        }
        int window = arguments.windowMinutes() == null ? 15 : arguments.windowMinutes();
        if (window < 1 || window > 1440) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "windowMinutes 必须在 1 到 1440 之间");
        }
        return new WorkloadArguments(
                arguments.clusterId(), arguments.namespace(), arguments.kind(), arguments.name(), window);
    }

    /**
     * 通过既有领域 Service 聚合 Workload、Pod、Event 和监控证据。
     *
     * @param arguments 已校验参数
     * @return Workload 证据
     */
    private WorkloadEvidence collect(WorkloadArguments arguments) {
        Long clusterId = Long.parseLong(arguments.clusterId());
        Map<String, Object> workload = loadWorkload(clusterId, arguments);
        Map<String, String> selector = stringMap(workload.get("selector"));
        List<Map<String, Object>> rawPods = podService.listPods(clusterId, arguments.namespace(), selector);
        List<PodEvidence> pods = rawPods.stream().limit(50).map(this::toPod).toList();
        List<EventEvidence> events = collectEvents(clusterId, arguments.namespace(), pods);
        List<String> warnings = new ArrayList<>();
        MetricEvidence metrics = collectMetrics(clusterId, arguments, warnings);
        if (rawPods.size() > 50) {
            warnings.add("POD_RESULT_TRUNCATED");
        }
        if (events.isEmpty()) {
            warnings.add("K8S_EVENTS_UNAVAILABLE_OR_EMPTY");
        }
        List<ConditionEvidence> conditions = objectList(workload.get("conditions")).stream()
                .map(this::toCondition).toList();
        if (conditions.isEmpty()) {
            warnings.add("WORKLOAD_CONDITIONS_UNAVAILABLE");
        }
        return new WorkloadEvidence(
                arguments.clusterId(), arguments.namespace(), arguments.kind(), arguments.name(),
                integer(workload.get("replicas")), integer(workload.get("readyReplicas")),
                availableReplicas(workload), stringList(workload.get("images")),
                safeLabels(stringMap(workload.get("labels"))),
                safeAnnotations(stringMap(workload.get("annotations"))),
                string(workload.get("currentRevision")), string(workload.get("resourceVersion")),
                conditions, pods, events, metrics, Instant.now(), List.copyOf(warnings));
    }

    /**
     * 按固定 Kind 调用现有 Workload Service。
     *
     * @param clusterId 集群数字 ID
     * @param arguments Workload 参数
     * @return 现有页面服务返回的领域快照
     */
    private Map<String, Object> loadWorkload(Long clusterId, WorkloadArguments arguments) {
        return switch (arguments.kind()) {
            case "Deployment" -> workloadService.getDeployment(clusterId, arguments.namespace(), arguments.name());
            case "StatefulSet" -> workloadService.getStatefulSet(clusterId, arguments.namespace(), arguments.name());
            case "DaemonSet" -> workloadService.getDaemonSet(clusterId, arguments.namespace(), arguments.name());
            default -> throw new AgentContractException(400, "INVALID_ARGUMENT", "不支持的 Workload 类型");
        };
    }

    /**
     * 汇总最多 50 个 Pod 的事件并按最后发生时间稳定排序。
     *
     * @param clusterId 集群 ID
     * @param namespace 命名空间
     * @param pods Pod 证据列表
     * @return 去除凭据后的事件列表
     */
    private List<EventEvidence> collectEvents(Long clusterId, String namespace, List<PodEvidence> pods) {
        List<EventEvidence> events = new ArrayList<>();
        for (PodEvidence pod : pods) {
            for (Map<String, Object> event : podService.getPodEvents(clusterId, namespace, pod.name())) {
                events.add(new EventEvidence(
                        string(event.get("type")), string(event.get("reason")),
                        truncate(string(event.get("message")), 500), integer(event.get("count")),
                        instant(event.get("firstTimestamp")), instant(event.get("lastTimestamp"))));
            }
        }
        return events.stream()
                .sorted(Comparator.comparing(EventEvidence::lastAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(100)
                .toList();
    }

    /**
     * 读取 Prometheus 工作负载指标；缺失应用指标时明确返回 warning，不生成随机数。
     *
     * @param clusterId 集群 ID
     * @param arguments Workload 参数
     * @param warnings 警告收集器
     * @return 当前窗口的真实指标摘要
     */
    private MetricEvidence collectMetrics(
            Long clusterId,
            WorkloadArguments arguments,
            List<String> warnings) {
        long end = Instant.now().getEpochSecond();
        long start = end - arguments.windowMinutes() * 60L;
        try {
            Map<String, Object> raw = monitoringService.getWorkloadMetrics(
                    clusterId, arguments.namespace(), arguments.name(), start, end, "30s");
            Double cpu = latestPoint(raw.get("cpuUsage"));
            Double memory = latestPoint(raw.get("memoryUsage"));
            if (cpu == null || memory == null) {
                warnings.add("PROMETHEUS_CONTAINER_METRICS_UNAVAILABLE");
            }
            warnings.add("APPLICATION_RATE_AND_LATENCY_METRICS_UNAVAILABLE");
            return new MetricEvidence(arguments.windowMinutes(), cpu, memory, null, null, null);
        } catch (RuntimeException exception) {
            warnings.add("PROMETHEUS_UNAVAILABLE");
            return new MetricEvidence(arguments.windowMinutes(), null, null, null, null, null);
        }
    }

    /**
     * 从 Prometheus range 序列中读取最后一个真实数值点。
     *
     * @param value range 序列对象
     * @return 最后一个数值，无法读取时返回 null
     */
    private Double latestPoint(Object value) {
        List<Map<String, Object>> series = objectList(value);
        Double latest = null;
        for (Map<String, Object> item : series) {
            Object pointsValue = item.get("values");
            if (pointsValue instanceof List<?> points && !points.isEmpty()) {
                Object point = points.get(points.size() - 1);
                if (point instanceof double[] pair && pair.length > 1) {
                    latest = pair[1];
                } else if (point instanceof List<?> pair && pair.size() > 1 && pair.get(1) instanceof Number number) {
                    latest = number.doubleValue();
                }
            }
        }
        return latest;
    }

    /**
     * 将页面 Pod 快照转换为稳定 DTO。
     *
     * @param pod 页面领域快照
     * @return Pod 证据
     */
    private PodEvidence toPod(Map<String, Object> pod) {
        int ready = integer(pod.get("readyCount"));
        int total = integer(pod.get("totalContainers"));
        return new PodEvidence(
                string(pod.get("name")), string(pod.get("status")), total > 0 && ready == total,
                integer(pod.get("restarts")), instant(pod.get("createdAt")));
    }

    /**
     * 将 Kubernetes Condition 快照转换为稳定 DTO。
     *
     * @param condition 条件对象
     * @return 条件证据
     */
    private ConditionEvidence toCondition(Map<String, Object> condition) {
        return new ConditionEvidence(
                string(condition.get("type")), string(condition.get("status")),
                string(condition.get("reason")), truncate(string(condition.get("message")), 500),
                instant(condition.get("lastTransitionTime")));
    }

    /**
     * 过滤可能包含 Secret、Token 或配置全文的 Label。
     *
     * @param labels 原始 Label
     * @return 白名单 Label
     */
    private Map<String, String> safeLabels(Map<String, String> labels) {
        Map<String, String> result = new LinkedHashMap<>();
        labels.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key = entry.getKey().toLowerCase();
            if ((key.startsWith("app") || key.startsWith("kubernetes") || key.equals("pod-template-hash"))
                    && !containsSensitiveWord(key)) {
                result.put(entry.getKey(), truncate(entry.getValue(), 256));
            }
        });
        return Map.copyOf(result);
    }

    /**
     * 仅保留经过审查的 Annotation，明确排除 last-applied-configuration。
     *
     * @param annotations 原始 Annotation
     * @return 白名单 Annotation
     */
    private Map<String, String> safeAnnotations(Map<String, String> annotations) {
        Map<String, String> result = new LinkedHashMap<>();
        annotations.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (SAFE_ANNOTATIONS.contains(entry.getKey()) && !containsSensitiveWord(entry.getKey())) {
                result.put(entry.getKey(), truncate(entry.getValue(), 256));
            }
        });
        return Map.copyOf(result);
    }

    /** 判断键名是否含常见敏感字段。 */
    private boolean containsSensitiveWord(String value) {
        String lower = value.toLowerCase();
        return lower.contains("secret") || lower.contains("token") || lower.contains("password")
                || lower.contains("credential") || lower.contains("key");
    }

    /** 截断外部文本，防止事件或标签撑大响应。 */
    private String truncate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }

    /** 安全读取字符串值。 */
    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 安全读取整数值。 */
    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /** 读取不同 Kind 的可用副本数。 */
    private int availableReplicas(Map<String, Object> workload) {
        Object value = workload.containsKey("availableReplicas")
                ? workload.get("availableReplicas") : workload.get("numberAvailable");
        return integer(value);
    }

    /** 安全读取字符串列表。 */
    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).sorted().toList();
    }

    /** 安全读取字符串 Map，供后续白名单过滤。 */
    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> values)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, item) -> {
            if (key != null && item != null) {
                result.put(String.valueOf(key), String.valueOf(item));
            }
        });
        return result;
    }

    /** 安全读取只用于适配既有领域 Service 的对象列表。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> objectList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item).toList();
    }

    /** 安全解析 Kubernetes RFC 3339 时间。 */
    private Instant instant(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 表示 Kubernetes Workload 查询参数。 */
    public record WorkloadArguments(
            String clusterId,
            String namespace,
            String kind,
            String name,
            Integer windowMinutes) {
    }

    /** 表示 Kubernetes 状态条件。 */
    public record ConditionEvidence(
            String type,
            String status,
            String reason,
            String message,
            Instant lastTransitionAt) {
    }

    /** 表示 Kubernetes Pod 摘要。 */
    public record PodEvidence(String name, String phase, boolean ready, int restartCount, Instant startedAt) {
    }

    /** 表示 Kubernetes Event 摘要。 */
    public record EventEvidence(
            String type,
            String reason,
            String message,
            int count,
            Instant firstAt,
            Instant lastAt) {
    }

    /** 表示当前观测窗口的真实指标；缺失项保持 null 并在 warnings 解释。 */
    public record MetricEvidence(
            int windowMinutes,
            Double cpu,
            Double memory,
            Double requestRate,
            Double errorRate,
            Double p95Ms) {
    }

    /** 表示可跨平台引用的 Kubernetes Workload 证据。 */
    public record WorkloadEvidence(
            String clusterId,
            String namespace,
            String kind,
            String name,
            int desiredReplicas,
            int readyReplicas,
            int availableReplicas,
            List<String> imageRefs,
            Map<String, String> labels,
            Map<String, String> annotations,
            String currentRevision,
            String resourceVersion,
            List<ConditionEvidence> conditions,
            List<PodEvidence> pods,
            List<EventEvidence> events,
            MetricEvidence metrics,
            Instant collectedAt,
            List<String> warnings) {
    }
}
