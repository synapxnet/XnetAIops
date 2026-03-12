package com.synapxnet.aiopsk8sservice.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Session;
import com.synapxnet.aiopsk8sservice.entity.K8sCluster;
import com.synapxnet.aiopsk8sservice.entity.K8sHelmRelease;
import com.synapxnet.aiopsk8sservice.entity.K8sHelmRepo;
import com.synapxnet.aiopsk8sservice.mapper.K8sClusterMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sHelmConfigMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sHelmReleaseMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sHelmRepoMapper;
import com.synapxnet.aiopsk8sservice.service.K8sHelmService;
import com.synapxnet.aiopsk8sservice.util.SshExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class K8sHelmServiceImpl implements K8sHelmService {

    private static final Logger log = LoggerFactory.getLogger(K8sHelmServiceImpl.class);

    private final K8sHelmRepoMapper repoMapper;
    private final K8sHelmReleaseMapper releaseMapper;
    private final K8sClusterMapper clusterMapper;
    private final K8sHelmConfigMapper helmConfigMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Yaml yaml = new Yaml();

    private static final int HELM_TIMEOUT = 300000; // 5 minutes

    // Cache: repoId -> list of charts
    private final Map<Long, List<Map<String, Object>>> repoChartsCache = new HashMap<>();

    // Helm config cache: configKey -> {value, expireTime}
    private final Map<String, Object[]> helmConfigCache = new ConcurrentHashMap<>();
    private static final long CONFIG_CACHE_TTL = 5 * 60 * 1000L; // 5 minutes

    public K8sHelmServiceImpl(K8sHelmRepoMapper repoMapper, K8sHelmReleaseMapper releaseMapper,
                               K8sClusterMapper clusterMapper, K8sHelmConfigMapper helmConfigMapper) {
        this.repoMapper = repoMapper;
        this.releaseMapper = releaseMapper;
        this.clusterMapper = clusterMapper;
        this.helmConfigMapper = helmConfigMapper;
    }

    /**
     * 从数据库读取 Helm 全局配置值，带 5 分钟内存缓存。
     */
    private String getHelmConfig(String key) {
        Object[] cached = helmConfigCache.get(key);
        if (cached != null && System.currentTimeMillis() < (long) cached[1]) {
            return (String) cached[0];
        }
        try {
            com.synapxnet.aiopsk8sservice.entity.K8sHelmConfig config = helmConfigMapper.findByKey(key);
            String value = config != null ? config.getConfigValue() : null;
            helmConfigCache.put(key, new Object[]{value, System.currentTimeMillis() + CONFIG_CACHE_TTL});
            return value;
        } catch (Exception e) {
            log.warn("Failed to read helm config '{}': {}", key, e.getMessage());
            return null;
        }
    }

    // ====== SSH Helm Execution ======

    // Track which clusters have had helm verified/installed
    private final Set<Long> helmVerifiedClusters = ConcurrentHashMap.newKeySet();

    private static final String HELM_INSTALL_SCRIPT =
            "if command -v helm &>/dev/null; then echo 'helm already installed'; exit 0; fi; " +
            "echo 'Installing Helm...' && " +
            "curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 -o /tmp/get_helm.sh --connect-timeout 10 && " +
            "bash /tmp/get_helm.sh && rm -f /tmp/get_helm.sh && " +
            "helm version --short || " +
            // Fallback: 如果 GitHub 超时，使用国内镜像手动安装
            "{ echo 'GitHub timeout, trying mirror...' && " +
            "HELM_VER=$(curl -s https://api.github.com/repos/helm/helm/releases/latest 2>/dev/null | grep tag_name | cut -d'\"' -f4 || echo 'v3.17.3') && " +
            "ARCH=$(uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/') && " +
            "curl -fsSL https://mirrors.huaweicloud.com/helm/${HELM_VER}/helm-${HELM_VER}-linux-${ARCH}.tar.gz -o /tmp/helm.tar.gz && " +
            "tar -zxf /tmp/helm.tar.gz -C /tmp && mv /tmp/linux-${ARCH}/helm /usr/local/bin/helm && " +
            "chmod +x /usr/local/bin/helm && rm -rf /tmp/helm.tar.gz /tmp/linux-${ARCH} && " +
            "helm version --short; }";

    /**
     * Ensure helm CLI is installed on the cluster's master node.
     * Only checks once per cluster per application lifecycle.
     */
    private void ensureHelmInstalled(Session session, Long clusterId) {
        if (helmVerifiedClusters.contains(clusterId)) {
            return;
        }
        try {
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            int exitCode = SshExecutor.executeWithCallback(session, HELM_INSTALL_SCRIPT, HELM_TIMEOUT,
                    line -> { stdout.append(line).append("\n"); log.info("[helm-install] {}", line); },
                    line -> { stderr.append(line).append("\n"); log.warn("[helm-install] {}", line); });

            if (exitCode != 0) {
                throw new RuntimeException("Helm 安装失败: " + (stderr.length() > 0 ? stderr.toString().trim() : stdout.toString().trim()));
            }
            helmVerifiedClusters.add(clusterId);
            log.info("Helm verified/installed on cluster {}: {}", clusterId, stdout.toString().trim());

            // 配置 containerd 国内镜像加速（如果配置了 imageRegistry）
            String imageRegistry = getHelmConfig("imageRegistry");
            if (imageRegistry != null && !imageRegistry.trim().isEmpty()) {
                try {
                    String mirrorScript =
                            "if [ -f /etc/containerd/config.toml ] && ! grep -q '" + imageRegistry + "' /etc/containerd/config.toml 2>/dev/null; then " +
                            "  mkdir -p /etc/containerd/certs.d/docker.io && " +
                            "  cat > /etc/containerd/certs.d/docker.io/hosts.toml << 'TOML'\n" +
                            "server = \"https://docker.io\"\n" +
                            "[host.\"https://" + imageRegistry + "\"]\n" +
                            "  capabilities = [\"pull\", \"resolve\"]\n" +
                            "TOML\n" +
                            "  echo 'Docker mirror configured: " + imageRegistry + "'; " +
                            "else echo 'Docker mirror already configured or containerd not found'; fi";
                    SshExecutor.executeWithCallback(session, mirrorScript, 30000,
                            line -> log.info("[docker-mirror] {}", line),
                            line -> log.warn("[docker-mirror] {}", line));
                } catch (Exception e) {
                    log.warn("Failed to configure Docker mirror on cluster {}: {}", clusterId, e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Helm 安装检测失败: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a helm command on the cluster's master node via SSH.
     * Automatically installs helm if not present.
     */
    private String executeHelm(Long clusterId, String helmCommand) {
        K8sCluster cluster = clusterMapper.findById(clusterId);
        if (cluster == null) {
            throw new RuntimeException("Cluster not found: " + clusterId);
        }
        if (cluster.getSshHost() == null || cluster.getSshHost().isEmpty()) {
            throw new RuntimeException("集群未配置SSH连接信息，请先在集群管理中配置SSH（主机、用户名、密码/密钥）");
        }

        Session session = null;
        try {
            int port = cluster.getSshPort() != null ? cluster.getSshPort() : 22;
            session = SshExecutor.connect(cluster.getSshHost(), port,
                    cluster.getSshUser(), cluster.getSshPassword(), cluster.getSshKey());

            // Auto-install helm if needed
            ensureHelmInstalled(session, clusterId);

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            int exitCode = SshExecutor.executeWithCallback(session, helmCommand, HELM_TIMEOUT,
                    line -> stdout.append(line).append("\n"),
                    line -> stderr.append(line).append("\n"));

            String output = stdout.toString();
            String errOutput = stderr.toString();

            if (exitCode != 0) {
                String errorMsg = errOutput.isEmpty() ? output : errOutput;
                log.error("Helm command failed (exit={}) on cluster {}: {} — {}", exitCode, clusterId, helmCommand, errorMsg.trim());
                throw new RuntimeException(errorMsg.trim());
            }

            log.info("Helm command executed on cluster {}: {}", clusterId, helmCommand);
            return output;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute helm command on cluster {}: {} — {}", clusterId, helmCommand, e.getMessage());
            throw new RuntimeException("Helm命令执行失败: " + e.getMessage(), e);
        } finally {
            SshExecutor.disconnect(session);
        }
    }

    /**
     * Validate that the cluster has SSH configured. Call this early before any SSH operations.
     */
    private K8sCluster requireSshCluster(Long clusterId) {
        K8sCluster cluster = clusterMapper.findById(clusterId);
        if (cluster == null) {
            throw new RuntimeException("集群不存在: " + clusterId);
        }
        if (cluster.getSshHost() == null || cluster.getSshHost().isEmpty()) {
            throw new RuntimeException("集群 [" + cluster.getName() + "] 未配置SSH连接信息，请先在集群管理中编辑该集群，配置SSH主机、用户名和密码/密钥");
        }
        return cluster;
    }

    /**
     * Upload values YAML to remote /tmp and return the remote file path.
     */
    private String uploadValues(K8sCluster cluster, String values) {
        if (values == null || values.trim().isEmpty()) return null;

        int port = cluster.getSshPort() != null ? cluster.getSshPort() : 22;
        String remotePath = "/tmp/helm-values-" + System.currentTimeMillis() + ".yaml";

        Session session = null;
        try {
            session = SshExecutor.connect(cluster.getSshHost(), port,
                    cluster.getSshUser(), cluster.getSshPassword(), cluster.getSshKey());
            SshExecutor.uploadScript(session, values, remotePath);
            return remotePath;
        } catch (Exception e) {
            log.error("Failed to upload values file: {}", e.getMessage());
            throw new RuntimeException("上传Values文件失败: " + e.getMessage(), e);
        } finally {
            SshExecutor.disconnect(session);
        }
    }

    // ====== Repos ======

    @Override
    public List<K8sHelmRepo> listRepos() {
        return repoMapper.findAll();
    }

    @Override
    public K8sHelmRepo getRepo(Long id) {
        return repoMapper.findById(id);
    }

    @Override
    public void addRepo(K8sHelmRepo repo) {
        repo.setStatus("active");
        repoMapper.insert(repo);
    }

    @Override
    public void updateRepo(K8sHelmRepo repo) {
        repoMapper.update(repo);
    }

    @Override
    public void deleteRepo(Long id) {
        repoChartsCache.remove(id);
        repoMapper.deleteById(id);
    }

    @Override
    public List<Map<String, Object>> syncRepo(Long repoId) {
        K8sHelmRepo repo = repoMapper.findById(repoId);
        if (repo == null) throw new RuntimeException("Repo not found: " + repoId);

        // 自动修正已废弃的 Bitnami/stable URL 为国内镜像
        if (repo.getUrl() != null && (repo.getUrl().contains("charts.bitnami.com")
                || repo.getUrl().contains("kubernetes.oss-cn-hangzhou.aliyuncs.com"))) {
            log.warn("同步时检测到已废弃的仓库 URL: {}，自动替换为国内镜像: {}", repo.getUrl(), BITNAMI_CN_MIRROR);
            repo.setUrl(BITNAMI_CN_MIRROR);
            repoMapper.update(repo);
        }

        // OCI 仓库：使用内置的 Bitnami chart 列表（OCI registry 没有 index.yaml）
        if (isOciUrl(repo.getUrl())) {
            return syncOciRepo(repo);
        }

        // 传统 HTTP 仓库：下载 index.yaml
        try {
            String indexUrl = repo.getUrl().endsWith("/") ? repo.getUrl() + "index.yaml" : repo.getUrl() + "/index.yaml";
            HttpHeaders headers = new HttpHeaders();
            if ("basic".equalsIgnoreCase(repo.getAuthType()) && repo.getUsername() != null) {
                headers.setBasicAuth(repo.getUsername(), repo.getPassword());
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(indexUrl, HttpMethod.GET, entity, String.class);

            List<Map<String, Object>> charts = parseHelmIndex(response.getBody());
            repoChartsCache.put(repoId, charts);

            repo.setLastSyncedAt(LocalDateTime.now());
            repo.setStatus("active");
            repoMapper.update(repo);

            return charts;
        } catch (Exception e) {
            log.error("Failed to sync Helm repo {}: {}", repo.getName(), e.getMessage());
            repo.setStatus("error");
            repoMapper.update(repo);
            throw new RuntimeException("Failed to sync repo: " + e.getMessage(), e);
        }
    }

    /**
     * 同步 OCI 仓库。OCI registry 没有统一的 index.yaml，使用常见 Bitnami chart 的内置列表。
     */
    private List<Map<String, Object>> syncOciRepo(K8sHelmRepo repo) {
        // Bitnami OCI 仓库的常见 chart 列表
        String[][] knownCharts = {
            {"redis", "Redis", "高性能Key-Value内存数据库，支持持久化、集群和哨兵模式", "database"},
            {"mysql", "MySQL", "广泛使用的开源关系数据库管理系统", "database"},
            {"postgresql", "PostgreSQL", "功能强大的开源对象关系数据库", "database"},
            {"mongodb", "MongoDB", "面向文档的NoSQL数据库", "database"},
            {"mariadb", "MariaDB", "MySQL的社区分支，完全兼容MySQL协议", "database"},
            {"kafka", "Apache Kafka", "高吞吐量分布式消息队列系统", "messaging"},
            {"rabbitmq", "RabbitMQ", "功能丰富的开源消息代理", "messaging"},
            {"nginx", "Nginx", "高性能HTTP和反向代理服务器", "webserver"},
            {"apache", "Apache HTTP Server", "流行的开源Web服务器", "webserver"},
            {"tomcat", "Apache Tomcat", "Java Servlet和JSP容器", "webserver"},
            {"elasticsearch", "Elasticsearch", "分布式搜索和分析引擎", "search"},
            {"minio", "MinIO", "高性能对象存储，兼容Amazon S3 API", "storage"},
            {"harbor", "Harbor", "企业级Docker镜像仓库", "devops"},
            {"jenkins", "Jenkins", "开源自动化服务器", "devops"},
            {"gitea", "Gitea", "轻量级Git代码托管平台", "devops"},
            {"prometheus", "Prometheus", "开源监控和告警系统", "monitoring"},
            {"grafana", "Grafana", "开源可视化监控平台", "monitoring"},
            {"zookeeper", "ZooKeeper", "分布式协调服务", "infrastructure"},
            {"etcd", "etcd", "分布式可靠键值存储", "infrastructure"},
            {"consul", "Consul", "服务网格和配置管理工具", "infrastructure"},
            {"keycloak", "Keycloak", "开源身份和访问管理", "security"},
            {"wordpress", "WordPress", "世界上最流行的内容管理系统", "cms"},
            {"sonarqube", "SonarQube", "代码质量和安全分析平台", "devops"},
            {"nacos", "Nacos", "动态服务发现和配置管理平台", "infrastructure"},
        };

        List<Map<String, Object>> charts = new ArrayList<>();
        for (String[] chart : knownCharts) {
            Map<String, Object> c = new HashMap<>();
            c.put("name", chart[0]);
            c.put("description", chart[2]);
            c.put("version", "latest");
            c.put("appVersion", "");
            c.put("icon", "");
            c.put("home", "");
            c.put("keywords", List.of(chart[3]));
            c.put("maintainers", Collections.emptyList());
            c.put("created", "");
            c.put("versions", List.of(Map.of("version", "latest", "appVersion", "", "created", "")));
            c.put("versionCount", 1);
            c.put("category", chart[3]);
            c.put("displayName", chart[1]);
            c.put("oci", true);
            charts.add(c);
        }

        repoChartsCache.put(repo.getId(), charts);
        repo.setLastSyncedAt(LocalDateTime.now());
        repo.setStatus("active");
        repoMapper.update(repo);

        log.info("OCI 仓库 {} 已同步，共 {} 个 chart", repo.getName(), charts.size());
        return charts;
    }

    // ====== Apps (Charts) ======

    @Override
    public List<Map<String, Object>> searchApps(String keyword, Long repoId) {
        List<Map<String, Object>> allCharts = new ArrayList<>();

        if (repoId != null) {
            List<Map<String, Object>> cached = repoChartsCache.get(repoId);
            if (cached == null) {
                try { cached = syncRepo(repoId); } catch (Exception e) { cached = Collections.emptyList(); }
            }
            allCharts.addAll(cached);
        } else {
            for (K8sHelmRepo repo : repoMapper.findAll()) {
                List<Map<String, Object>> cached = repoChartsCache.get(repo.getId());
                if (cached == null) {
                    try { cached = syncRepo(repo.getId()); } catch (Exception e) { cached = Collections.emptyList(); }
                }
                for (Map<String, Object> chart : cached) {
                    Map<String, Object> copy = new HashMap<>(chart);
                    copy.put("repoId", repo.getId());
                    copy.put("repoName", repo.getName());
                    allCharts.add(copy);
                }
            }
        }

        if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword.toLowerCase();
            return allCharts.stream()
                    .filter(c -> {
                        String name = String.valueOf(c.getOrDefault("name", "")).toLowerCase();
                        String desc = String.valueOf(c.getOrDefault("description", "")).toLowerCase();
                        return name.contains(kw) || desc.contains(kw);
                    })
                    .collect(Collectors.toList());
        }
        return allCharts;
    }

    @Override
    public Map<String, Object> getAppDetail(Long repoId, String chartName) {
        List<Map<String, Object>> charts = repoChartsCache.get(repoId);
        if (charts == null) {
            try { charts = syncRepo(repoId); } catch (Exception e) { return null; }
        }
        return charts.stream()
                .filter(c -> chartName.equals(c.get("name")))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Map<String, Object> getAppVersion(Long repoId, String chartName, String version) {
        Map<String, Object> app = getAppDetail(repoId, chartName);
        if (app == null) return null;

        Map<String, Object> result = new HashMap<>(app);

        K8sHelmRepo repo = repoMapper.findById(repoId);
        if (repo != null) {
            if (isOciUrl(repo.getUrl())) {
                // OCI 仓库：使用 oci:// 引用（没有 .tgz 下载链接）
                String ociBase = repo.getUrl().endsWith("/") ? repo.getUrl().substring(0, repo.getUrl().length() - 1) : repo.getUrl();
                result.put("downloadUrl", ociBase + "/" + chartName);
                result.put("oci", true);
            } else {
                // 传统仓库：构建 .tgz 下载链接
                try {
                    String valuesUrl = repo.getUrl().endsWith("/")
                            ? repo.getUrl() + "charts/" + chartName + "-" + version + ".tgz"
                            : repo.getUrl() + "/charts/" + chartName + "-" + version + ".tgz";
                    result.put("downloadUrl", valuesUrl);
                } catch (Exception e) {
                    log.warn("Failed to get chart values: {}", e.getMessage());
                }
            }
        }

        result.put("selectedVersion", version);
        return result;
    }

    // ====== Releases ======

    @Override
    public List<K8sHelmRelease> listReleases(Long clusterId) {
        // Try to sync from cluster first, fall back to DB
        try {
            syncReleasesFromCluster(clusterId);
        } catch (Exception e) {
            log.warn("Failed to sync releases from cluster {}, using DB data: {}", clusterId, e.getMessage());
        }
        return releaseMapper.findByClusterId(clusterId);
    }

    @Override
    public K8sHelmRelease getReleaseDetail(Long clusterId, String namespace, String releaseName) {
        return releaseMapper.findByClusterAndRelease(clusterId, namespace, releaseName);
    }

    @Override
    public void installRelease(Long clusterId, String namespace, String releaseName,
                                String chartName, String chartVersion, Long repoId, String values) {
        // Validate SSH config FIRST before doing anything
        K8sCluster cluster = requireSshCluster(clusterId);

        // Clean up any previous failed/deploying record with the same key
        K8sHelmRelease existing = releaseMapper.findByClusterAndRelease(clusterId, namespace, releaseName);
        if (existing != null) {
            if ("failed".equals(existing.getStatus()) || "deploying".equals(existing.getStatus())) {
                releaseMapper.deleteById(existing.getId());
                log.info("Cleaned up previous failed release record: {}/{}", namespace, releaseName);
            } else {
                throw new RuntimeException("Release " + releaseName + " 在命名空间 " + namespace + " 中已存在（状态: " + existing.getStatus() + "），请先卸载或使用其他名称");
            }
        }

        // Record release in DB with deploying status
        K8sHelmRelease release = new K8sHelmRelease();
        release.setClusterId(clusterId);
        release.setNamespace(namespace);
        release.setReleaseName(releaseName);
        release.setChartName(chartName);
        release.setChartVersion(chartVersion);
        release.setValuesOverride(values);
        release.setStatus("deploying");
        release.setRevision(1);

        // Get app version from chart metadata if repoId provided
        Map<String, Object> app = repoId != null ? getAppDetail(repoId, chartName) : null;
        if (app != null) {
            release.setAppVersion(String.valueOf(app.getOrDefault("appVersion", "")));
        }

        releaseMapper.insert(release);

        try {
            // 解析 chart 引用（支持 OCI 和传统 repo 格式）
            String chartRef = resolveChartReference(clusterId, chartName, repoId);

            // Upload values file if provided
            String valuesPath = uploadValues(cluster, values);

            // Build helm install command
            String output = buildAndExecuteHelmInstall(clusterId, releaseName, chartRef, namespace, chartVersion, valuesPath, chartName);

            // Clean up values file
            if (valuesPath != null) {
                try { executeHelm(clusterId, "rm -f " + valuesPath); } catch (Exception ignored) {}
            }

            release.setStatus("deployed");
            release.setNotes(output.length() > 2000 ? output.substring(0, 2000) : output);
            releaseMapper.update(release);

            log.info("Helm release installed: {}/{} with chart {}", namespace, releaseName, chartName);

        } catch (Exception e) {
            release.setStatus("failed");
            release.setNotes("Install failed: " + e.getMessage());
            releaseMapper.update(release);
            throw new RuntimeException("Helm install failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void upgradeRelease(Long clusterId, String namespace, String releaseName,
                                String chartVersion, String values) {
        K8sCluster cluster = requireSshCluster(clusterId);
        K8sHelmRelease release = releaseMapper.findByClusterAndRelease(clusterId, namespace, releaseName);
        if (release == null) throw new RuntimeException("Release not found: " + releaseName);

        try {
            // 解析 chart 引用（支持 OCI 和传统 repo 格式）
            String chartRef = resolveChartReference(clusterId, release.getChartName(), null);

            // Upload values file if provided
            String valuesPath = uploadValues(cluster, values);

            String output = buildAndExecuteHelmUpgrade(clusterId, releaseName, chartRef, namespace, chartVersion, valuesPath, release.getChartName());

            // Clean up values file
            if (valuesPath != null) {
                try { executeHelm(clusterId, "rm -f " + valuesPath); } catch (Exception ignored) {}
            }

            release.setChartVersion(chartVersion);
            release.setValuesOverride(values);
            release.setRevision(release.getRevision() + 1);
            release.setStatus("deployed");
            release.setNotes(output.length() > 2000 ? output.substring(0, 2000) : output);
            releaseMapper.update(release);

            log.info("Helm release upgraded: {}/{} to version {}", namespace, releaseName, chartVersion);

        } catch (Exception e) {
            release.setStatus("failed");
            release.setNotes("Upgrade failed: " + e.getMessage());
            releaseMapper.update(release);
            throw new RuntimeException("Helm upgrade failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void uninstallRelease(Long clusterId, String namespace, String releaseName) {
        K8sHelmRelease release = releaseMapper.findByClusterAndRelease(clusterId, namespace, releaseName);
        if (release == null) throw new RuntimeException("Release not found: " + releaseName);

        try {
            String cmd = "helm uninstall " + releaseName + " -n " + namespace;
            executeHelm(clusterId, cmd);
            releaseMapper.deleteById(release.getId());
            log.info("Helm release uninstalled: {}/{}", namespace, releaseName);
        } catch (Exception e) {
            throw new RuntimeException("Helm uninstall failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void rollbackRelease(Long clusterId, String namespace, String releaseName, int revision) {
        K8sHelmRelease release = releaseMapper.findByClusterAndRelease(clusterId, namespace, releaseName);
        if (release == null) throw new RuntimeException("Release not found: " + releaseName);

        try {
            String cmd = "helm rollback " + releaseName + " " + revision + " -n " + namespace + " --wait --timeout 5m";
            String output = executeHelm(clusterId, cmd);

            release.setRevision(release.getRevision() + 1);
            release.setStatus("deployed");
            release.setNotes(output.length() > 2000 ? output.substring(0, 2000) : output);
            releaseMapper.update(release);

            log.info("Helm release rolled back: {}/{} to revision {}", namespace, releaseName, revision);

        } catch (Exception e) {
            release.setStatus("failed");
            release.setNotes("Rollback failed: " + e.getMessage());
            releaseMapper.update(release);
            throw new RuntimeException("Helm rollback failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void syncReleasesFromCluster(Long clusterId) {
        K8sCluster cluster = clusterMapper.findById(clusterId);
        if (cluster == null || cluster.getSshHost() == null || cluster.getSshHost().isEmpty()) {
            return; // No SSH configured, skip sync
        }

        try {
            String output = executeHelm(clusterId, "helm list --all-namespaces -o json");
            if (output == null || output.trim().isEmpty() || output.trim().equals("[]")) {
                return;
            }

            List<Map<String, Object>> helmReleases = objectMapper.readValue(output.trim(),
                    new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> hr : helmReleases) {
                String name = (String) hr.get("name");
                String ns = (String) hr.get("namespace");
                String status = (String) hr.get("status");
                String chart = (String) hr.get("chart");
                String appVersion = (String) hr.get("app_version");
                Object revisionObj = hr.get("revision");
                int revision = 1;
                if (revisionObj instanceof Number) {
                    revision = ((Number) revisionObj).intValue();
                } else if (revisionObj instanceof String) {
                    try { revision = Integer.parseInt((String) revisionObj); } catch (NumberFormatException ignored) {}
                }

                // Parse chart name and version from chart string (e.g., "mysql-9.4.1")
                String chartName = chart;
                String chartVersion = "";
                if (chart != null) {
                    int lastDash = chart.lastIndexOf('-');
                    if (lastDash > 0) {
                        String possibleVersion = chart.substring(lastDash + 1);
                        if (possibleVersion.matches("\\d+\\..*")) {
                            chartName = chart.substring(0, lastDash);
                            chartVersion = possibleVersion;
                        }
                    }
                }

                K8sHelmRelease existing = releaseMapper.findByClusterAndRelease(clusterId, ns, name);
                if (existing != null) {
                    // Update existing record
                    existing.setStatus(status);
                    existing.setRevision(revision);
                    existing.setAppVersion(appVersion);
                    existing.setChartVersion(chartVersion);
                    releaseMapper.update(existing);
                } else {
                    // Insert new record discovered from cluster
                    K8sHelmRelease newRelease = new K8sHelmRelease();
                    newRelease.setClusterId(clusterId);
                    newRelease.setNamespace(ns);
                    newRelease.setReleaseName(name);
                    newRelease.setChartName(chartName);
                    newRelease.setChartVersion(chartVersion);
                    newRelease.setAppVersion(appVersion);
                    newRelease.setStatus(status);
                    newRelease.setRevision(revision);
                    releaseMapper.insert(newRelease);
                }
            }
        } catch (Exception e) {
            log.error("Failed to sync releases from cluster {}: {}", clusterId, e.getMessage());
            throw new RuntimeException("同步集群Release失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 chart 引用，返回用于 helm install/upgrade 的 chart 引用字符串。
     * 支持传统 helm repo（repoName/chartName）和 OCI 格式（oci://registry/chart）。
     */
    private String resolveChartReference(Long clusterId, String chartName, Long repoId) {
        if (chartName == null || !chartName.contains("/")) {
            if (repoId != null) {
                K8sHelmRepo repo = repoMapper.findById(repoId);
                if (repo != null) {
                    // OCI 仓库：直接返回 oci://registry/chartName
                    if (isOciUrl(repo.getUrl())) {
                        String ociBase = repo.getUrl().endsWith("/") ? repo.getUrl().substring(0, repo.getUrl().length() - 1) : repo.getUrl();
                        return ociBase + "/" + chartName;
                    }
                    return resolveChartReference(clusterId, repo.getName() + "/" + chartName, repoId);
                }
            }
            // 无 repoId 且无 /：尝试使用默认仓库（upgrade 场景）
            if (chartName != null) {
                // 查找 DB 中的 bitnami repo，确保已 helm repo add
                ensureHelmRepo(clusterId, "bitnami", null);
                log.info("Chart {} 无仓库信息，使用默认 bitnami 仓库", chartName);
                return "bitnami/" + chartName;
            }
            return chartName;
        }

        // 如果 chartName 已经是 oci:// 格式，直接返回
        if (isOciUrl(chartName)) {
            return chartName;
        }

        String repoName = chartName.split("/")[0];
        String bareChart = chartName.split("/")[1];

        // 查找 repo URL，判断是否为 OCI
        String repoUrl = resolveRepoUrl(repoName, repoId);
        if (repoUrl != null && isOciUrl(repoUrl)) {
            // OCI 仓库：返回 oci://registry/chartName（不需要 helm repo add）
            String ociBase = repoUrl.endsWith("/") ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;
            log.info("使用 OCI chart 引用: {}/{}", ociBase, bareChart);
            return ociBase + "/" + bareChart;
        }

        // 传统仓库：确保 repo 已添加到集群节点
        ensureHelmRepo(clusterId, repoName, repoId);
        return chartName;
    }

    /**
     * 查找仓库 URL（不执行 helm repo add，仅查询）。
     * 优先级：DB(repoId) > DB(name) > 全局配置 > 内置仓库
     */
    private String resolveRepoUrl(String repoName, Long repoId) {
        if (repoId != null) {
            K8sHelmRepo repo = repoMapper.findById(repoId);
            if (repo != null) return repo.getUrl();
        }
        K8sHelmRepo repo = repoMapper.findByName(repoName);
        if (repo != null) return repo.getUrl();

        String defaultRepoName = getHelmConfig("defaultRepoName");
        String defaultRepoUrl = getHelmConfig("defaultRepoUrl");
        if (repoName.equals(defaultRepoName) && defaultRepoUrl != null && !defaultRepoUrl.isEmpty()) {
            return defaultRepoUrl;
        }

        Map<String, String> knownRepos = new HashMap<>();
        knownRepos.put("bitnami", BITNAMI_CN_MIRROR);
        knownRepos.put("stable", BITNAMI_CN_MIRROR);
        knownRepos.put("harbor", "https://helm.goharbor.io");
        knownRepos.put("jenkins", "https://charts.jenkins.io");
        knownRepos.put("gitlab", "https://charts.gitlab.io");
        knownRepos.put("sonarqube", "https://SonarSource.github.io/helm-chart-sonarqube");
        knownRepos.put("prometheus-community", "https://helm-charts.itboon.top/prometheus-community");
        knownRepos.put("grafana", "https://helm-charts.itboon.top/grafana");
        return knownRepos.get(repoName);
    }

    // Track which repos have been added on each cluster (avoid repeated helm repo add)
    private final Map<String, Set<String>> clusterRepoAdded = new ConcurrentHashMap<>();

    // 已废弃的 Helm stable 仓库 URL（charts 使用废弃的 K8s API，不兼容 1.22+）
    private static final String DEPRECATED_STABLE_URL = "https://kubernetes.oss-cn-hangzhou.aliyuncs.com/charts";
    private static final String BITNAMI_URL = "https://charts.bitnami.com/bitnami";
    // Bitnami 官方 OCI 仓库（传统 HTTP 仓库已废弃，2025年起仅支持 OCI）
    private static final String BITNAMI_OCI_URL = "oci://registry-1.docker.io/bitnamicharts";
    // 国内 Bitnami Helm chart 镜像（传统 HTTP 格式，支持 index.yaml）
    private static final String BITNAMI_CN_MIRROR = "https://helm-charts.itboon.top/bitnami";

    /**
     * 判断 URL 是否为 OCI 格式（oci:// 开头）。
     */
    private static boolean isOciUrl(String url) {
        return url != null && url.startsWith("oci://");
    }

    private void ensureHelmRepo(Long clusterId, String repoName, Long repoId) {
        String repoUrl = resolveRepoUrl(repoName, repoId);

        // 自动修正已废弃的旧 stable / bitnami 仓库 URL
        if (repoUrl != null && (repoUrl.contains("kubernetes.oss-cn-hangzhou.aliyuncs.com")
                || repoUrl.contains("charts.bitnami.com"))) {
            String newUrl = BITNAMI_CN_MIRROR;
            log.warn("检测到已废弃的 Helm 仓库 URL: {}，自动替换为国内镜像: {}", repoUrl, newUrl);
            repoUrl = newUrl;
            // 同步修正 DB 中的记录，并清空旧 chart 缓存
            try {
                K8sHelmRepo dbRepo = repoMapper.findByName(repoName);
                if (dbRepo != null) {
                    dbRepo.setUrl(newUrl);
                    repoMapper.update(dbRepo);
                    repoChartsCache.remove(dbRepo.getId());
                    log.info("已自动更新 DB 中仓库 [{}] 的 URL 为 {}，并清空 chart 缓存", repoName, newUrl);
                }
            } catch (Exception e) {
                log.warn("自动更新仓库 URL 失败: {}", e.getMessage());
            }
        }

        if (repoUrl == null) {
            throw new RuntimeException("未知的 Helm 仓库: " + repoName + "，请先在 Helm 仓库管理中添加该仓库");
        }

        // OCI 仓库不需要 helm repo add，直接返回
        if (isOciUrl(repoUrl)) {
            log.debug("OCI 仓库 {} 无需 helm repo add", repoUrl);
            return;
        }

        // 检查是否已在此集群上添加过（且 URL 没变）
        String clusterKey = String.valueOf(clusterId);
        Set<String> addedRepos = clusterRepoAdded.computeIfAbsent(clusterKey, k -> ConcurrentHashMap.newKeySet());
        String cacheKey = repoName + "=" + repoUrl;
        if (addedRepos.contains(cacheKey)) {
            return;
        }

        String addCmd = "helm repo add " + repoName + " " + repoUrl + " --force-update && helm repo update " + repoName;
        try {
            executeHelm(clusterId, addCmd);
            addedRepos.add(cacheKey);
            log.info("Helm repo {} ({}) added/updated on cluster {}", repoName, repoUrl, clusterId);
        } catch (Exception e) {
            log.error("Failed to add helm repo {} on cluster {}: {}", repoName, clusterId, e.getMessage());
            throw new RuntimeException("添加 Helm 仓库 [" + repoName + "] 失败: " + e.getMessage() +
                    "。请检查集群网络是否能访问 " + repoUrl, e);
        }
    }

    /**
     * 获取 OCI 镜像仓库地址。优先从 DB 配置读取 ociRegistry，未配置则使用官方地址。
     */
    private String getOciRegistryUrl() {
        String ociRegistry = getHelmConfig("ociRegistry");
        if (ociRegistry != null && !ociRegistry.trim().isEmpty()) {
            return ociRegistry.trim();
        }
        return BITNAMI_OCI_URL;
    }

    /**
     * 根据 chart 类型追加镜像加速参数（从 DB 读取 imageRegistry 配置）。
     * 为空则不追加，使用 chart 默认镜像源。
     */
    private void appendImageRegistryOverride(StringBuilder cmd, String chartName) {
        if (chartName == null) return;

        String registry = getHelmConfig("imageRegistry");
        if (registry == null || registry.trim().isEmpty()) {
            return; // 未配置全局镜像仓库，使用 chart 默认源
        }

        // Bitnami charts 统一支持 global.imageRegistry（支持 OCI 和传统格式）
        // 新版 Bitnami chart 会校验镜像来源，使用非官方镜像时需跳过校验
        if (chartName.startsWith("bitnami/") || chartName.equals("bitnami")
                || chartName.contains("bitnamicharts")) {
            cmd.append(" --set global.imageRegistry=").append(registry);
            cmd.append(" --set global.security.allowInsecureImages=true");
            return;
        }

        // 其他常见 charts 的镜像源设置
        String bareChart = chartName.contains("/") ? chartName.substring(chartName.lastIndexOf('/') + 1) : chartName;
        switch (bareChart) {
            case "jenkins":
                cmd.append(" --set controller.image.registry=").append(registry);
                break;
            case "gitlab":
                cmd.append(" --set global.image.registry=").append(registry);
                break;
            default:
                // 通用设置，大部分 chart 支持 global.imageRegistry
                cmd.append(" --set global.imageRegistry=").append(registry);
                cmd.append(" --set global.security.allowInsecureImages=true");
                break;
        }
    }

    /**
     * 构建并执行 helm install 命令。如果指定版本不存在（仓库切换导致），自动用最新版重试。
     */
    private String buildAndExecuteHelmInstall(Long clusterId, String releaseName, String chartRef,
                                               String namespace, String chartVersion, String valuesPath, String chartName) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("helm install ").append(releaseName).append(" ").append(chartRef);
        cmd.append(" -n ").append(namespace).append(" --create-namespace");
        if (chartVersion != null && !chartVersion.isEmpty() && !"latest".equals(chartVersion)) {
            cmd.append(" --version ").append(chartVersion);
        }
        if (valuesPath != null) {
            cmd.append(" -f ").append(valuesPath);
        }
        appendImageRegistryOverride(cmd, chartName);
        cmd.append(" --timeout 10m");

        try {
            return executeHelm(clusterId, cmd.toString());
        } catch (RuntimeException e) {
            if (chartVersion != null && !chartVersion.isEmpty() && e.getMessage() != null
                    && (e.getMessage().contains("no chart version found")
                        || e.getMessage().contains("Unable to locate any tags")
                        || e.getMessage().contains("not found in repository"))) {
                log.warn("Chart 版本 {} 不可用，改用最新版重试: {}", chartVersion, e.getMessage());
                StringBuilder retryCmd = new StringBuilder();
                retryCmd.append("helm install ").append(releaseName).append(" ").append(chartRef);
                retryCmd.append(" -n ").append(namespace).append(" --create-namespace");
                if (valuesPath != null) {
                    retryCmd.append(" -f ").append(valuesPath);
                }
                appendImageRegistryOverride(retryCmd, chartName);
                retryCmd.append(" --timeout 10m");
                return executeHelm(clusterId, retryCmd.toString());
            }
            throw e;
        }
    }

    /**
     * 构建并执行 helm upgrade 命令。如果指定版本不存在，自动用最新版重试。
     */
    private String buildAndExecuteHelmUpgrade(Long clusterId, String releaseName, String chartRef,
                                               String namespace, String chartVersion, String valuesPath, String chartName) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("helm upgrade ").append(releaseName).append(" ").append(chartRef);
        cmd.append(" -n ").append(namespace);
        if (chartVersion != null && !chartVersion.isEmpty() && !"latest".equals(chartVersion)) {
            cmd.append(" --version ").append(chartVersion);
        }
        if (valuesPath != null) {
            cmd.append(" -f ").append(valuesPath);
        }
        appendImageRegistryOverride(cmd, chartName);
        cmd.append(" --timeout 10m");

        try {
            return executeHelm(clusterId, cmd.toString());
        } catch (RuntimeException e) {
            if (chartVersion != null && !chartVersion.isEmpty() && e.getMessage() != null
                    && (e.getMessage().contains("no chart version found")
                        || e.getMessage().contains("Unable to locate any tags")
                        || e.getMessage().contains("not found in repository"))) {
                log.warn("Chart 版本 {} 不可用，改用最新版重试: {}", chartVersion, e.getMessage());
                StringBuilder retryCmd = new StringBuilder();
                retryCmd.append("helm upgrade ").append(releaseName).append(" ").append(chartRef);
                retryCmd.append(" -n ").append(namespace);
                if (valuesPath != null) {
                    retryCmd.append(" -f ").append(valuesPath);
                }
                appendImageRegistryOverride(retryCmd, chartName);
                retryCmd.append(" --timeout 10m");
                return executeHelm(clusterId, retryCmd.toString());
            }
            throw e;
        }
    }

    // ====== Helpers ======

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseHelmIndex(String indexContent) {
        List<Map<String, Object>> charts = new ArrayList<>();
        try {
            Map<String, Object> index = yaml.load(indexContent);
            Map<String, Object> entries = (Map<String, Object>) index.get("entries");
            if (entries == null) return charts;

            for (Map.Entry<String, Object> entry : entries.entrySet()) {
                String chartName = entry.getKey();
                List<Map<String, Object>> versions = (List<Map<String, Object>>) entry.getValue();
                if (versions == null || versions.isEmpty()) continue;

                // Use the latest version as the primary entry
                Map<String, Object> latest = versions.get(0);
                Map<String, Object> chart = new HashMap<>();
                chart.put("name", chartName);
                chart.put("description", latest.getOrDefault("description", ""));
                chart.put("version", latest.getOrDefault("version", ""));
                chart.put("appVersion", latest.getOrDefault("appVersion", ""));
                chart.put("icon", latest.getOrDefault("icon", ""));
                chart.put("home", latest.getOrDefault("home", ""));
                chart.put("keywords", latest.getOrDefault("keywords", Collections.emptyList()));
                chart.put("maintainers", latest.getOrDefault("maintainers", Collections.emptyList()));
                chart.put("created", latest.getOrDefault("created", ""));

                // Collect all versions
                List<Map<String, String>> versionList = versions.stream().map(v -> {
                    Map<String, String> ver = new HashMap<>();
                    ver.put("version", String.valueOf(v.getOrDefault("version", "")));
                    ver.put("appVersion", String.valueOf(v.getOrDefault("appVersion", "")));
                    ver.put("created", String.valueOf(v.getOrDefault("created", "")));
                    return ver;
                }).collect(Collectors.toList());
                chart.put("versions", versionList);
                chart.put("versionCount", versionList.size());

                charts.add(chart);
            }
        } catch (Exception e) {
            log.error("Failed to parse Helm index: {}", e.getMessage());
        }
        return charts;
    }
}
