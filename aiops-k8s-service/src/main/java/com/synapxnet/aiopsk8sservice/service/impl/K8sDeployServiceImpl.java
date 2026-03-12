package com.synapxnet.aiopsk8sservice.service.impl;

import com.jcraft.jsch.Session;
import com.synapxnet.aiopsk8sservice.entity.K8sCluster;
import com.synapxnet.aiopsk8sservice.entity.K8sDeployLog;
import com.synapxnet.aiopsk8sservice.entity.K8sDeployNode;
import com.synapxnet.aiopsk8sservice.entity.K8sDeployPlan;
import com.synapxnet.aiopsk8sservice.mapper.K8sDeployLogMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sDeployNodeMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sDeployPlanMapper;
import com.synapxnet.aiopsk8sservice.service.K8sClusterService;
import com.synapxnet.aiopsk8sservice.service.K8sDeployScriptGenerator;
import com.synapxnet.aiopsk8sservice.service.K8sDeployService;
import com.synapxnet.aiopsk8sservice.util.SshExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class K8sDeployServiceImpl implements K8sDeployService {

    private static final Logger log = LoggerFactory.getLogger(K8sDeployServiceImpl.class);

    private static final int SSH_CONNECT_TIMEOUT = 10000;    // 10s
    private static final int DEPLOY_CMD_TIMEOUT = 1800000;  // 30 min

    private final K8sDeployPlanMapper planMapper;
    private final K8sDeployNodeMapper nodeMapper;
    private final K8sDeployLogMapper logMapper;
    private final K8sDeployScriptGenerator scriptGenerator;
    private final K8sClusterService clusterService;
    private final ApplicationContext applicationContext;

    public K8sDeployServiceImpl(K8sDeployPlanMapper planMapper,
                                 K8sDeployNodeMapper nodeMapper,
                                 K8sDeployLogMapper logMapper,
                                 K8sDeployScriptGenerator scriptGenerator,
                                 K8sClusterService clusterService,
                                 ApplicationContext applicationContext) {
        this.planMapper = planMapper;
        this.nodeMapper = nodeMapper;
        this.logMapper = logMapper;
        this.scriptGenerator = scriptGenerator;
        this.clusterService = clusterService;
        this.applicationContext = applicationContext;
    }

    @Override
    public List<K8sDeployPlan> listPlans() {
        return planMapper.findAll();
    }

    @Override
    public Map<String, Object> getPlanDetail(Long planId) {
        K8sDeployPlan plan = planMapper.findById(planId);
        if (plan == null) return null;

        Map<String, Object> detail = new HashMap<>();
        detail.put("plan", plan);
        detail.put("nodes", nodeMapper.findByPlanId(planId));
        detail.put("logs", logMapper.findByPlanId(planId));

        List<K8sDeployNode> nodes = nodeMapper.findByPlanId(planId);
        long readyCount = nodes.stream().filter(n -> "ready".equals(n.getStatus())).count();
        long failedCount = nodes.stream().filter(n -> "failed".equals(n.getStatus())).count();
        detail.put("totalNodes", nodes.size());
        detail.put("readyNodes", readyCount);
        detail.put("failedNodes", failedCount);
        detail.put("progress", nodes.isEmpty() ? 0 : (int) (readyCount * 100 / nodes.size()));

        return detail;
    }

    @Override
    public K8sDeployPlan createPlan(K8sDeployPlan plan, List<K8sDeployNode> nodes) {
        plan.setUid(UUID.randomUUID().toString());
        plan.setStatus("pending");
        planMapper.insert(plan);

        if (nodes != null) {
            for (K8sDeployNode node : nodes) {
                node.setPlanId(plan.getId());
                node.setStatus("pending");
                if (node.getSshPort() == null) node.setSshPort(22);
                if (node.getSshUser() == null) node.setSshUser("root");
                nodeMapper.insert(node);
            }
        }

        appendLog(plan.getId(), null, "plan_created", "INFO", "部署计划已创建: " + plan.getPlanName());
        return plan;
    }

    @Override
    public void deletePlan(Long planId) {
        K8sDeployPlan plan = planMapper.findById(planId);
        if (plan != null && "running".equals(plan.getStatus())) {
            throw new RuntimeException("Cannot delete a running deployment plan");
        }
        planMapper.deleteById(planId);
    }

    @Override
    public void addNode(Long planId, K8sDeployNode node) {
        node.setPlanId(planId);
        node.setStatus("pending");
        if (node.getSshPort() == null) node.setSshPort(22);
        if (node.getSshUser() == null) node.setSshUser("root");
        nodeMapper.insert(node);
        appendLog(planId, node.getHost(), "node_added", "INFO", "节点已添加: " + node.getHost() + " (" + node.getRole() + ")");
    }

    @Override
    public void removeNode(Long nodeId) {
        K8sDeployNode node = nodeMapper.findById(nodeId);
        if (node != null) {
            nodeMapper.deleteById(nodeId);
            appendLog(node.getPlanId(), node.getHost(), "node_removed", "INFO", "节点已移除: " + node.getHost());
        }
    }

    @Override
    public boolean validateNode(Long nodeId) {
        K8sDeployNode node = nodeMapper.findById(nodeId);
        if (node == null) return false;

        Session session = null;
        try {
            appendLog(node.getPlanId(), node.getHost(), "node_validate", "INFO",
                    "开始验证节点: " + node.getHost() + ":" + node.getSshPort());

            // Real SSH authentication test
            int port = node.getSshPort() != null ? node.getSshPort() : 22;
            session = SshExecutor.connect(
                    node.getHost(), port, node.getSshUser(),
                    node.getSshPassword(), node.getSshKey(), SSH_CONNECT_TIMEOUT);

            // Check OS and hardware
            String sysInfo = SshExecutor.executeCommand(session,
                    "cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | cut -d= -f2 | tr -d '\"'; " +
                    "echo '---'; nproc; echo '---'; free -m | awk '/Mem:/{print $2}'",
                    SSH_CONNECT_TIMEOUT);

            String[] parts = sysInfo.trim().split("---");
            String osName = parts.length > 0 ? parts[0].trim() : "Unknown";
            int cpuCores = 0;
            long memoryMb = 0;
            if (parts.length > 1) {
                try { cpuCores = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) {}
            }
            if (parts.length > 2) {
                try { memoryMb = Long.parseLong(parts[2].trim()); } catch (Exception ignored) {}
            }

            StringBuilder statusMsg = new StringBuilder();
            statusMsg.append("OS: ").append(osName);
            statusMsg.append(", CPU: ").append(cpuCores).append("核");
            statusMsg.append(", 内存: ").append(memoryMb).append("MB");

            // Check minimum requirements: 2 cores, 1800MB RAM
            if (cpuCores < 2) {
                nodeMapper.updateStatus(nodeId, "failed", "CPU不足: " + cpuCores + "核 (最少2核)");
                appendLog(node.getPlanId(), node.getHost(), "node_validate", "ERROR",
                        "节点验证失败: CPU不足 (" + cpuCores + "核, 最少2核)");
                return false;
            }
            if (memoryMb < 1800) {
                nodeMapper.updateStatus(nodeId, "failed", "内存不足: " + memoryMb + "MB (最少2GB)");
                appendLog(node.getPlanId(), node.getHost(), "node_validate", "ERROR",
                        "节点验证失败: 内存不足 (" + memoryMb + "MB, 最少2GB)");
                return false;
            }

            nodeMapper.updateStatus(nodeId, "validated", statusMsg.toString());
            appendLog(node.getPlanId(), node.getHost(), "node_validate", "INFO",
                    "节点验证通过: " + statusMsg);
            return true;

        } catch (Exception e) {
            nodeMapper.updateStatus(nodeId, "failed", "连接失败: " + e.getMessage());
            appendLog(node.getPlanId(), node.getHost(), "node_validate", "ERROR",
                    "节点验证失败: " + node.getHost() + " - " + e.getMessage());
            return false;
        } finally {
            SshExecutor.disconnect(session);
        }
    }

    @Override
    @Async
    public void executePlan(Long planId) {
        K8sDeployPlan plan = planMapper.findById(planId);
        if (plan == null) throw new RuntimeException("Plan not found: " + planId);

        planMapper.updateStatus(planId, "running", null);
        appendLog(planId, null, "deploy_start", "INFO", "开始执行部署计划: " + plan.getPlanName());

        List<K8sDeployNode> nodes = nodeMapper.findByPlanId(planId);
        List<K8sDeployNode> masters = new ArrayList<>();
        List<K8sDeployNode> workers = new ArrayList<>();
        for (K8sDeployNode node : nodes) {
            if ("master".equals(node.getRole())) masters.add(node);
            else workers.add(node);
        }

        if (masters.isEmpty()) {
            planMapper.updateStatus(planId, "failed", null);
            appendLog(planId, null, "deploy_failed", "ERROR", "部署失败: 没有Master节点");
            return;
        }

        // Track SSH sessions for reuse and cleanup
        Map<Long, Session> sessions = new HashMap<>();
        String joinToken = null;
        String joinCaHash = null;
        String kubeconfig = null;

        try {
            // Connect to all nodes first
            appendLog(planId, null, "connect", "INFO", "连接所有节点...");
            for (K8sDeployNode node : nodes) {
                try {
                    int nodePort = node.getSshPort() != null ? node.getSshPort() : 22;
                    Session session = SshExecutor.connect(
                            node.getHost(), nodePort, node.getSshUser(),
                            node.getSshPassword(), node.getSshKey(), SSH_CONNECT_TIMEOUT);
                    sessions.put(node.getId(), session);
                    appendLog(planId, node.getHost(), "connect", "INFO", "SSH连接成功: " + node.getHost());
                } catch (Exception e) {
                    nodeMapper.updateStatus(node.getId(), "failed", "SSH连接失败: " + e.getMessage());
                    appendLog(planId, node.getHost(), "connect", "ERROR", "SSH连接失败: " + e.getMessage());
                    throw new RuntimeException("Failed to connect to node " + node.getHost() + ": " + e.getMessage());
                }
            }

            // Step 1: Prepare all nodes
            appendLog(planId, null, "prepare", "INFO", "步骤1/7: 准备所有节点环境...");
            for (K8sDeployNode node : nodes) {
                nodeMapper.updateStatus(node.getId(), "preparing", "正在准备节点环境");
                String script = scriptGenerator.generatePrepareNode(node.getHostname());
                String remotePath = "/tmp/k8s_prepare_" + System.currentTimeMillis() + ".sh";

                int exitCode = SshExecutor.uploadAndExecute(
                        sessions.get(node.getId()), script, remotePath, DEPLOY_CMD_TIMEOUT,
                        line -> appendLog(planId, node.getHost(), "prepare", "INFO", line),
                        line -> appendLog(planId, node.getHost(), "prepare", "WARN", line));

                if (exitCode != 0) {
                    nodeMapper.updateStatus(node.getId(), "failed", "节点准备失败, exit code: " + exitCode);
                    throw new RuntimeException("Node preparation failed on " + node.getHost() + " (exit: " + exitCode + ")");
                }
                nodeMapper.updateStatus(node.getId(), "preparing", "节点环境准备完成");
            }

            // Step 2: Install container runtime
            appendLog(planId, null, "install_cri", "INFO",
                    "步骤2/7: 安装容器运行时 (" + plan.getContainerRuntime() + ")...");
            for (K8sDeployNode node : nodes) {
                appendLog(planId, node.getHost(), "install_cri", "INFO",
                        "安装 " + plan.getContainerRuntime() + " on " + node.getHost());
                String script = scriptGenerator.generateInstallCri(plan.getContainerRuntime(), plan.getK8sVersion(), plan.getRegistryUrl());
                String remotePath = "/tmp/k8s_cri_" + System.currentTimeMillis() + ".sh";

                int exitCode = SshExecutor.uploadAndExecute(
                        sessions.get(node.getId()), script, remotePath, DEPLOY_CMD_TIMEOUT,
                        line -> appendLog(planId, node.getHost(), "install_cri", "INFO", line),
                        line -> appendLog(planId, node.getHost(), "install_cri", "WARN", line));

                if (exitCode != 0) {
                    nodeMapper.updateStatus(node.getId(), "failed", "CRI安装失败, exit code: " + exitCode);
                    throw new RuntimeException("CRI install failed on " + node.getHost() + " (exit: " + exitCode + ")");
                }
            }

            // Step 3: Install kubeadm
            appendLog(planId, null, "install_kubeadm", "INFO",
                    "步骤3/7: 安装 kubeadm/kubelet/kubectl (" + plan.getK8sVersion() + ")...");
            for (K8sDeployNode node : nodes) {
                appendLog(planId, node.getHost(), "install_kubeadm", "INFO",
                        "安装 kubeadm " + plan.getK8sVersion() + " on " + node.getHost());
                String script = scriptGenerator.generateInstallKubeadm(plan.getK8sVersion());
                String remotePath = "/tmp/k8s_kubeadm_" + System.currentTimeMillis() + ".sh";

                int exitCode = SshExecutor.uploadAndExecute(
                        sessions.get(node.getId()), script, remotePath, DEPLOY_CMD_TIMEOUT,
                        line -> appendLog(planId, node.getHost(), "install_kubeadm", "INFO", line),
                        line -> appendLog(planId, node.getHost(), "install_kubeadm", "WARN", line));

                if (exitCode != 0) {
                    nodeMapper.updateStatus(node.getId(), "failed", "kubeadm安装失败, exit code: " + exitCode);
                    throw new RuntimeException("kubeadm install failed on " + node.getHost() + " (exit: " + exitCode + ")");
                }
            }

            // Step 4: Init first master
            K8sDeployNode firstMaster = masters.get(0);
            appendLog(planId, null, "init_master", "INFO", "步骤4/7: 初始化Master节点...");
            nodeMapper.updateStatus(firstMaster.getId(), "installing", "正在初始化Master");

            String podCidr = plan.getPodCidr() != null ? plan.getPodCidr() : "10.244.0.0/16";
            String serviceCidr = plan.getServiceCidr() != null ? plan.getServiceCidr() : "10.96.0.0/12";
            String initScript = scriptGenerator.generateInitMaster(
                    plan.getK8sVersion(), podCidr, serviceCidr, firstMaster.getHost(), plan.getRegistryUrl());
            String remotePath = "/tmp/k8s_init_" + System.currentTimeMillis() + ".sh";

            StringBuilder initOutput = new StringBuilder();
            int initExit = SshExecutor.uploadAndExecute(
                    sessions.get(firstMaster.getId()), initScript, remotePath, DEPLOY_CMD_TIMEOUT,
                    line -> {
                        initOutput.append(line).append("\n");
                        appendLog(planId, firstMaster.getHost(), "init_master", "INFO", line);
                    },
                    line -> {
                        initOutput.append(line).append("\n");
                        appendLog(planId, firstMaster.getHost(), "init_master", "WARN", line);
                    });

            if (initExit != 0) {
                nodeMapper.updateStatus(firstMaster.getId(), "failed", "Master初始化失败, exit code: " + initExit);
                throw new RuntimeException("Master init failed (exit: " + initExit + ")");
            }

            // Parse join command from output
            String fullOutput = initOutput.toString();
            joinToken = parseJoinToken(fullOutput);
            joinCaHash = parseJoinCaHash(fullOutput);
            kubeconfig = parseKubeconfig(fullOutput);

            if (joinToken == null || joinCaHash == null) {
                // Try to get join command directly from master
                appendLog(planId, firstMaster.getHost(), "init_master", "INFO", "重新生成join命令...");
                String joinCmd = SshExecutor.executeCommand(
                        sessions.get(firstMaster.getId()),
                        "kubeadm token create --print-join-command", 30000);
                joinToken = parseJoinTokenFromCmd(joinCmd);
                joinCaHash = parseJoinCaHashFromCmd(joinCmd);
            }

            if (kubeconfig == null) {
                appendLog(planId, firstMaster.getHost(), "init_master", "INFO", "获取kubeconfig...");
                kubeconfig = SshExecutor.executeCommand(
                        sessions.get(firstMaster.getId()),
                        "cat /etc/kubernetes/admin.conf", 10000);
            }

            nodeMapper.updateStatus(firstMaster.getId(), "ready", "Master初始化完成");
            appendLog(planId, firstMaster.getHost(), "init_master", "INFO", "Master节点初始化成功");

            // 单节点集群（无Worker）：移除 master taint，允许所有 Pod 调度到 master
            if (workers.isEmpty()) {
                appendLog(planId, firstMaster.getHost(), "init_master", "INFO",
                        "单节点集群，移除control-plane taint以允许Pod调度...");
                try {
                    String untaintCmd = "kubectl taint nodes --all node-role.kubernetes.io/control-plane- 2>&1 || true";
                    SshExecutor.executeCommand(sessions.get(firstMaster.getId()), untaintCmd, 15000);
                    appendLog(planId, firstMaster.getHost(), "init_master", "INFO", "control-plane taint 已移除");
                } catch (Exception e) {
                    appendLog(planId, firstMaster.getHost(), "init_master", "WARN",
                            "移除taint失败: " + e.getMessage() + " (可手动执行)");
                }
            }

            // Step 5: Install CNI
            appendLog(planId, null, "install_cni", "INFO",
                    "步骤5/7: 安装网络插件 (" + plan.getNetworkPlugin() + ")...");
            String cniScript = scriptGenerator.generateInstallCni(plan.getNetworkPlugin(), podCidr, plan.getRegistryUrl());
            String cniRemotePath = "/tmp/k8s_cni_" + System.currentTimeMillis() + ".sh";

            int cniExit = SshExecutor.uploadAndExecute(
                    sessions.get(firstMaster.getId()), cniScript, cniRemotePath, DEPLOY_CMD_TIMEOUT,
                    line -> appendLog(planId, firstMaster.getHost(), "install_cni", "INFO", line),
                    line -> appendLog(planId, firstMaster.getHost(), "install_cni", "WARN", line));

            if (cniExit != 0) {
                appendLog(planId, firstMaster.getHost(), "install_cni", "WARN",
                        "CNI安装退出码非0 (" + cniExit + "), 继续部署...");
            } else {
                appendLog(planId, null, "install_cni", "INFO", plan.getNetworkPlugin() + " 安装完成");
            }

            // Step 6: Join workers
            if (!workers.isEmpty()) {
                appendLog(planId, null, "join_worker", "INFO",
                        "步骤6/7: Worker节点加入集群 (" + workers.size() + "个节点)...");

                if (joinToken == null || joinCaHash == null) {
                    throw new RuntimeException("Cannot join workers: join token or CA hash not available");
                }

                for (K8sDeployNode worker : workers) {
                    nodeMapper.updateStatus(worker.getId(), "installing", "正在加入集群");
                    String joinScript = scriptGenerator.generateJoinWorker(
                            firstMaster.getHost(), joinToken, joinCaHash);
                    String joinRemotePath = "/tmp/k8s_join_" + System.currentTimeMillis() + ".sh";

                    int joinExit = SshExecutor.uploadAndExecute(
                            sessions.get(worker.getId()), joinScript, joinRemotePath, DEPLOY_CMD_TIMEOUT,
                            line -> appendLog(planId, worker.getHost(), "join_worker", "INFO", line),
                            line -> appendLog(planId, worker.getHost(), "join_worker", "WARN", line));

                    if (joinExit != 0) {
                        nodeMapper.updateStatus(worker.getId(), "failed", "加入集群失败, exit code: " + joinExit);
                        appendLog(planId, worker.getHost(), "join_worker", "ERROR",
                                "Worker节点加入失败 (exit: " + joinExit + ")");
                        continue;
                    }
                    nodeMapper.updateStatus(worker.getId(), "ready", "已加入集群");
                    appendLog(planId, worker.getHost(), "join_worker", "INFO", "Worker节点加入成功");
                }
            } else {
                appendLog(planId, null, "join_worker", "INFO", "步骤6/7: 无Worker节点, 跳过");
            }

            // Step 7: Optional components
            appendLog(planId, null, "install_addons", "INFO", "步骤7/7: 安装可选组件...");
            if (Boolean.TRUE.equals(plan.getInstallMetricsServer())) {
                appendLog(planId, firstMaster.getHost(), "install_addons", "INFO", "安装 metrics-server...");
                try {
                    // 优先使用国内 GitHub 代理下载 metrics-server manifest
                    // 下载后替换镜像为国内源或私有仓库，添加 --kubelet-insecure-tls 参数，并添加 control-plane toleration
                    String metricsImageReplace;
                    if (plan.getRegistryUrl() != null && !plan.getRegistryUrl().isEmpty()) {
                        metricsImageReplace = "sed -i 's|registry.k8s.io/metrics-server|" + plan.getRegistryUrl() + "/google_containers|g' /tmp/metrics-server.yaml";
                    } else {
                        metricsImageReplace = "sed -i 's|registry.k8s.io/metrics-server|registry.aliyuncs.com/google_containers|g' /tmp/metrics-server.yaml";
                    }
                    String metricsCmd = "curl -fsSL --connect-timeout 10 " +
                            "'https://mirror.ghproxy.com/https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml' " +
                            "-o /tmp/metrics-server.yaml 2>/dev/null " +
                            "|| curl -fsSL --connect-timeout 30 " +
                            "'https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml' " +
                            "-o /tmp/metrics-server.yaml " +
                            "&& " + metricsImageReplace + " " +
                            "&& sed -i '/- --kubelet-use-node-status-port/a\\        - --kubelet-insecure-tls' /tmp/metrics-server.yaml " +
                            "&& kubectl apply -f /tmp/metrics-server.yaml " +
                            "&& kubectl -n kube-system patch deployment metrics-server --type='json' " +
                            "-p='[{\"op\":\"add\",\"path\":\"/spec/template/spec/tolerations\",\"value\":[" +
                            "{\"key\":\"node-role.kubernetes.io/control-plane\",\"operator\":\"Exists\",\"effect\":\"NoSchedule\"}," +
                            "{\"key\":\"node-role.kubernetes.io/master\",\"operator\":\"Exists\",\"effect\":\"NoSchedule\"}" +
                            "]}]' 2>&1";
                    SshExecutor.executeWithCallback(
                            sessions.get(firstMaster.getId()),
                            metricsCmd,
                            DEPLOY_CMD_TIMEOUT,
                            line -> appendLog(planId, firstMaster.getHost(), "install_addons", "INFO", line),
                            line -> appendLog(planId, firstMaster.getHost(), "install_addons", "WARN", line));
                    appendLog(planId, null, "install_addons", "INFO", "metrics-server 安装完成");
                } catch (Exception e) {
                    appendLog(planId, null, "install_addons", "WARN", "metrics-server 安装失败: " + e.getMessage());
                }
            }
            if (Boolean.TRUE.equals(plan.getInstallIngressNginx())) {
                appendLog(planId, firstMaster.getHost(), "install_addons", "INFO", "安装 ingress-nginx...");
                try {
                    // 优先使用国内 GitHub 代理下载 ingress-nginx manifest，并添加 control-plane toleration
                    String ingressImageReplace = "";
                    if (plan.getRegistryUrl() != null && !plan.getRegistryUrl().isEmpty()) {
                        ingressImageReplace = "&& sed -i 's|registry.k8s.io/ingress-nginx|" + plan.getRegistryUrl() + "/ingress-nginx|g' /tmp/ingress-nginx.yaml ";
                    }
                    String ingressCmd = "curl -fsSL --connect-timeout 10 " +
                            "'https://mirror.ghproxy.com/https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.0/deploy/static/provider/cloud/deploy.yaml' " +
                            "-o /tmp/ingress-nginx.yaml 2>/dev/null " +
                            "|| curl -fsSL --connect-timeout 30 " +
                            "'https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.0/deploy/static/provider/cloud/deploy.yaml' " +
                            "-o /tmp/ingress-nginx.yaml " +
                            ingressImageReplace +
                            "&& kubectl apply -f /tmp/ingress-nginx.yaml " +
                            "&& kubectl -n ingress-nginx patch deployment ingress-nginx-controller --type='json' " +
                            "-p='[{\"op\":\"add\",\"path\":\"/spec/template/spec/tolerations\",\"value\":[" +
                            "{\"key\":\"node-role.kubernetes.io/control-plane\",\"operator\":\"Exists\",\"effect\":\"NoSchedule\"}," +
                            "{\"key\":\"node-role.kubernetes.io/master\",\"operator\":\"Exists\",\"effect\":\"NoSchedule\"}" +
                            "]}]' 2>&1";
                    SshExecutor.executeWithCallback(
                            sessions.get(firstMaster.getId()),
                            ingressCmd,
                            DEPLOY_CMD_TIMEOUT,
                            line -> appendLog(planId, firstMaster.getHost(), "install_addons", "INFO", line),
                            line -> appendLog(planId, firstMaster.getHost(), "install_addons", "WARN", line));
                    appendLog(planId, null, "install_addons", "INFO", "ingress-nginx 安装完成");
                } catch (Exception e) {
                    appendLog(planId, null, "install_addons", "WARN", "ingress-nginx 安装失败: " + e.getMessage());
                }
            }

            // Step 7b: Install storage plugin
            String storagePlugin = plan.getStoragePlugin();
            if (storagePlugin != null && !"none".equals(storagePlugin)) {
                appendLog(planId, firstMaster.getHost(), "install_addons", "INFO", "安装存储插件: " + storagePlugin + "...");
                try {
                    String storageCmd = buildStorageInstallCmd(storagePlugin);
                    SshExecutor.executeWithCallback(
                            sessions.get(firstMaster.getId()),
                            storageCmd,
                            DEPLOY_CMD_TIMEOUT,
                            line -> appendLog(planId, firstMaster.getHost(), "install_addons", "INFO", line),
                            line -> appendLog(planId, firstMaster.getHost(), "install_addons", "WARN", line));
                    appendLog(planId, null, "install_addons", "INFO", storagePlugin + " 存储插件安装完成");
                } catch (Exception e) {
                    appendLog(planId, null, "install_addons", "WARN", storagePlugin + " 存储插件安装失败: " + e.getMessage());
                }
            }

            // Auto-register cluster
            Long resultClusterId = null;
            if (kubeconfig != null && !kubeconfig.trim().isEmpty()) {
                try {
                    appendLog(planId, null, "register", "INFO", "自动注册集群到平台...");
                    K8sCluster cluster = new K8sCluster();
                    cluster.setName(plan.getPlanName());
                    cluster.setDescription("通过部署计划自动创建: " + plan.getPlanName());
                    cluster.setProvider("kubeadm");
                    // 将部署计划中 master 节点的 SSH 信息同步到集群记录，避免用户重复配置
                    cluster.setSshHost(firstMaster.getHost());
                    cluster.setSshPort(firstMaster.getSshPort());
                    cluster.setSshUser(firstMaster.getSshUser());
                    cluster.setSshPassword(firstMaster.getSshPassword());
                    cluster.setSshKey(firstMaster.getSshKey());
                    K8sCluster created = clusterService.create(cluster, kubeconfig);
                    resultClusterId = created.getId();
                    appendLog(planId, null, "register", "INFO",
                            "集群已注册, ID: " + resultClusterId);
                } catch (Exception e) {
                    appendLog(planId, null, "register", "WARN",
                            "集群自动注册失败: " + e.getMessage() + " (可手动添加kubeconfig)");
                }
            }

            // Done
            planMapper.updateStatus(planId, "completed", resultClusterId);
            appendLog(planId, null, "deploy_complete", "INFO",
                    "部署完成! 集群 " + plan.getPlanName() + " 已就绪 (" +
                            masters.size() + " master, " + workers.size() + " worker)");

        } catch (Exception e) {
            log.error("Deployment failed for plan {}: {}", planId, e.getMessage(), e);
            planMapper.updateStatus(planId, "failed", null);
            appendLog(planId, null, "deploy_failed", "ERROR", "部署失败: " + e.getMessage());
        } finally {
            // Cleanup all SSH sessions
            for (Session session : sessions.values()) {
                SshExecutor.disconnect(session);
            }
        }
    }

    @Override
    public List<K8sDeployLog> getLogs(Long planId) {
        return logMapper.findByPlanId(planId);
    }

    @Override
    public List<String> getSupportedVersions() {
        return Arrays.asList("1.31.0", "1.30.4", "1.30.2", "1.29.8", "1.29.6", "1.28.12", "1.28.10", "1.27.16");
    }

    @Override
    public List<Map<String, String>> getSupportedCni() {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(Map.of("name", "calico", "label", "Calico", "description", "功能丰富的CNI，支持网络策略"));
        list.add(Map.of("name", "flannel", "label", "Flannel", "description", "简单轻量的overlay网络"));
        list.add(Map.of("name", "cilium", "label", "Cilium", "description", "基于eBPF的高性能网络"));
        return list;
    }

    @Override
    public List<Map<String, String>> getSupportedCri() {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(Map.of("name", "containerd", "label", "containerd", "description", "行业标准容器运行时"));
        list.add(Map.of("name", "cri-o", "label", "CRI-O", "description", "轻量级Kubernetes专用运行时"));
        return list;
    }

    @Override
    public List<Map<String, String>> getSupportedStorage() {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(Map.of("name", "local-path", "label", "Local Path", "description", "轻量级本地存储（适合单节点/开发测试）"));
        list.add(Map.of("name", "longhorn", "label", "Longhorn", "description", "分布式块存储，跨节点副本（推荐3节点+生产环境）"));
        list.add(Map.of("name", "nfs", "label", "NFS", "description", "NFS共享存储（需要已有NFS服务器）"));
        list.add(Map.of("name", "none", "label", "不安装", "description", "不安装存储插件，手动配置"));
        return list;
    }

    // ====== Scale-out: add worker node to completed cluster ======

    @Override
    public void scaleOutNode(Long planId, K8sDeployNode node) {
        K8sDeployPlan plan = planMapper.findById(planId);
        if (plan == null) throw new RuntimeException("Plan not found: " + planId);
        if (!"completed".equals(plan.getStatus())) {
            throw new RuntimeException("只能对已完成部署的计划进行扩容");
        }
        if (!"worker".equals(node.getRole())) {
            throw new RuntimeException("扩容只支持添加Worker节点");
        }

        // Insert node record
        node.setPlanId(planId);
        node.setStatus("pending");
        if (node.getSshPort() == null) node.setSshPort(22);
        if (node.getSshUser() == null) node.setSshUser("root");
        nodeMapper.insert(node);
        appendLog(planId, node.getHost(), "node_added", "INFO",
                "扩容节点已添加: " + node.getHost() + " (worker)");

        // Trigger async scale-out via Spring proxy
        applicationContext.getBean(K8sDeployService.class)
                .asyncScaleOut(planId, node.getId());
    }

    @Override
    @Async
    public void asyncScaleOut(Long planId, Long nodeId) {
        K8sDeployPlan plan = planMapper.findById(planId);
        K8sDeployNode newNode = nodeMapper.findById(nodeId);
        if (plan == null || newNode == null) return;

        // Find the first master node for join token generation
        List<K8sDeployNode> allNodes = nodeMapper.findByPlanId(planId);
        K8sDeployNode master = allNodes.stream()
                .filter(n -> "master".equals(n.getRole()) && "ready".equals(n.getStatus()))
                .findFirst().orElse(null);

        if (master == null) {
            nodeMapper.updateStatus(nodeId, "failed", "未找到可用的Master节点");
            appendLog(planId, newNode.getHost(), "join_worker", "ERROR", "扩容失败: 未找到可用的Master节点");
            return;
        }

        Session newNodeSession = null;
        Session masterSession = null;

        try {
            appendLog(planId, newNode.getHost(), "connect", "INFO", "扩容开始: 连接新节点...");

            // Connect to new node
            int newNodePort = newNode.getSshPort() != null ? newNode.getSshPort() : 22;
            newNodeSession = SshExecutor.connect(
                    newNode.getHost(), newNodePort, newNode.getSshUser(),
                    newNode.getSshPassword(), newNode.getSshKey(), SSH_CONNECT_TIMEOUT);
            appendLog(planId, newNode.getHost(), "connect", "INFO", "新节点SSH连接成功");

            // Connect to master
            int masterPort = master.getSshPort() != null ? master.getSshPort() : 22;
            masterSession = SshExecutor.connect(
                    master.getHost(), masterPort, master.getSshUser(),
                    master.getSshPassword(), master.getSshKey(), SSH_CONNECT_TIMEOUT);
            appendLog(planId, master.getHost(), "connect", "INFO", "Master节点SSH连接成功");

            // Step 1: Prepare node
            nodeMapper.updateStatus(nodeId, "preparing", "正在准备节点环境");
            appendLog(planId, newNode.getHost(), "prepare", "INFO", "步骤1/5: 准备节点环境...");
            String hostname = newNode.getHostname() != null ? newNode.getHostname() : newNode.getHost();
            String prepareScript = scriptGenerator.generatePrepareNode(hostname);
            String remotePath = "/tmp/k8s_prepare_" + System.currentTimeMillis() + ".sh";

            int exitCode = SshExecutor.uploadAndExecute(
                    newNodeSession, prepareScript, remotePath, DEPLOY_CMD_TIMEOUT,
                    line -> appendLog(planId, newNode.getHost(), "prepare", "INFO", line),
                    line -> appendLog(planId, newNode.getHost(), "prepare", "WARN", line));
            if (exitCode != 0) {
                throw new RuntimeException("节点环境准备失败 (exit: " + exitCode + ")");
            }

            // Step 2: Install CRI
            nodeMapper.updateStatus(nodeId, "preparing", "正在安装容器运行时");
            appendLog(planId, newNode.getHost(), "install_cri", "INFO",
                    "步骤2/5: 安装容器运行时 (" + plan.getContainerRuntime() + ")...");
            String criScript = scriptGenerator.generateInstallCri(plan.getContainerRuntime(), plan.getK8sVersion(), plan.getRegistryUrl());
            remotePath = "/tmp/k8s_cri_" + System.currentTimeMillis() + ".sh";

            exitCode = SshExecutor.uploadAndExecute(
                    newNodeSession, criScript, remotePath, DEPLOY_CMD_TIMEOUT,
                    line -> appendLog(planId, newNode.getHost(), "install_cri", "INFO", line),
                    line -> appendLog(planId, newNode.getHost(), "install_cri", "WARN", line));
            if (exitCode != 0) {
                throw new RuntimeException("CRI安装失败 (exit: " + exitCode + ")");
            }

            // Step 3: Install kubeadm
            nodeMapper.updateStatus(nodeId, "installing", "正在安装kubeadm");
            appendLog(planId, newNode.getHost(), "install_kubeadm", "INFO",
                    "步骤3/5: 安装 kubeadm " + plan.getK8sVersion() + "...");
            String kubeadmScript = scriptGenerator.generateInstallKubeadm(plan.getK8sVersion());
            remotePath = "/tmp/k8s_kubeadm_" + System.currentTimeMillis() + ".sh";

            exitCode = SshExecutor.uploadAndExecute(
                    newNodeSession, kubeadmScript, remotePath, DEPLOY_CMD_TIMEOUT,
                    line -> appendLog(planId, newNode.getHost(), "install_kubeadm", "INFO", line),
                    line -> appendLog(planId, newNode.getHost(), "install_kubeadm", "WARN", line));
            if (exitCode != 0) {
                throw new RuntimeException("kubeadm安装失败 (exit: " + exitCode + ")");
            }

            // Step 4: Get join token from master
            appendLog(planId, master.getHost(), "join_worker", "INFO",
                    "步骤4/5: 从Master节点获取join token...");
            String joinCmd = SshExecutor.executeCommand(
                    masterSession, "kubeadm token create --print-join-command", 30000);
            String joinToken = parseJoinTokenFromCmd(joinCmd);
            String joinCaHash = parseJoinCaHashFromCmd(joinCmd);

            if (joinToken == null || joinCaHash == null) {
                throw new RuntimeException("无法从Master获取join token");
            }
            appendLog(planId, master.getHost(), "join_worker", "INFO", "join token获取成功");

            // Step 4.5: Patch kubeadm-config ConfigMap if master uses NAT (public IP differs from internal IP)
            // Without this, kubeadm join reads the ConfigMap after TLS bootstrap and tries to connect
            // to the master's internal IP, which is unreachable from worker nodes on different networks
            appendLog(planId, master.getHost(), "join_worker", "INFO",
                    "检查kubeadm-config和cluster-info中的API Server地址...");
            try {
                String patchCmd = "CURRENT_ADDR=$(kubectl -n kube-system get cm kubeadm-config " +
                        "-o jsonpath='{.data.ClusterConfiguration}' 2>/dev/null | " +
                        "grep 'advertiseAddress:' | awk '{print $2}'); " +
                        "if [ -n \"$CURRENT_ADDR\" ] && [ \"$CURRENT_ADDR\" != \"" + master.getHost() + "\" ]; then " +
                        "echo \"修复advertiseAddress: $CURRENT_ADDR -> " + master.getHost() + "\"; " +
                        "kubectl -n kube-system get cm kubeadm-config -o yaml | " +
                        "sed \"s|$CURRENT_ADDR|" + master.getHost() + "|g\" | " +
                        "kubectl apply -f - 2>&1; " +
                        "echo \"修复cluster-info...\"; " +
                        "kubectl -n kube-public get cm cluster-info -o yaml | " +
                        "sed \"s|$CURRENT_ADDR|" + master.getHost() + "|g\" | " +
                        "kubectl apply -f - 2>&1; " +
                        "else echo \"advertiseAddress正确: $CURRENT_ADDR\"; fi";
                String patchResult = SshExecutor.executeCommand(masterSession, patchCmd, 30000);
                appendLog(planId, master.getHost(), "join_worker", "INFO",
                        "ConfigMap检查完成: " + patchResult.trim());
            } catch (Exception e) {
                appendLog(planId, master.getHost(), "join_worker", "WARN",
                        "ConfigMap修复失败: " + e.getMessage() + " (继续尝试加入)");
            }

            // Step 5: Join worker
            nodeMapper.updateStatus(nodeId, "installing", "正在加入集群");
            appendLog(planId, newNode.getHost(), "join_worker", "INFO",
                    "步骤5/5: Worker节点加入集群...");
            String joinScript = scriptGenerator.generateJoinWorker(master.getHost(), joinToken, joinCaHash);
            remotePath = "/tmp/k8s_join_" + System.currentTimeMillis() + ".sh";

            exitCode = SshExecutor.uploadAndExecute(
                    newNodeSession, joinScript, remotePath, DEPLOY_CMD_TIMEOUT,
                    line -> appendLog(planId, newNode.getHost(), "join_worker", "INFO", line),
                    line -> appendLog(planId, newNode.getHost(), "join_worker", "WARN", line));
            if (exitCode != 0) {
                throw new RuntimeException("加入集群失败 (exit: " + exitCode + ")");
            }

            // Success
            nodeMapper.updateStatus(nodeId, "ready", "已加入集群");
            appendLog(planId, newNode.getHost(), "join_worker", "INFO",
                    "扩容完成: 节点 " + newNode.getHost() + " 已成功加入集群");

        } catch (Exception e) {
            log.error("Scale-out failed for node {} in plan {}: {}", nodeId, planId, e.getMessage(), e);
            nodeMapper.updateStatus(nodeId, "failed", "扩容失败: " + e.getMessage());
            appendLog(planId, newNode.getHost(), "join_worker", "ERROR",
                    "扩容失败: " + e.getMessage());
        } finally {
            SshExecutor.disconnect(newNodeSession);
            SshExecutor.disconnect(masterSession);
        }
    }

    /**
     * 构建存储插件安装命令。
     */
    private String buildStorageInstallCmd(String storagePlugin) {
        switch (storagePlugin) {
            case "local-path":
                return "curl -fsSL --connect-timeout 10 " +
                        "'https://mirror.ghproxy.com/https://raw.githubusercontent.com/rancher/local-path-provisioner/v0.0.30/deploy/local-path-storage.yaml' " +
                        "-o /tmp/local-path-storage.yaml 2>/dev/null " +
                        "|| curl -fsSL --connect-timeout 30 " +
                        "'https://raw.githubusercontent.com/rancher/local-path-provisioner/v0.0.30/deploy/local-path-storage.yaml' " +
                        "-o /tmp/local-path-storage.yaml " +
                        "&& kubectl apply -f /tmp/local-path-storage.yaml " +
                        "&& kubectl patch storageclass local-path -p '{\"metadata\":{\"annotations\":{\"storageclass.kubernetes.io/is-default-class\":\"true\"}}}' 2>&1";
            case "longhorn":
                return "curl -fsSL --connect-timeout 10 " +
                        "'https://mirror.ghproxy.com/https://raw.githubusercontent.com/longhorn/longhorn/v1.7.2/deploy/longhorn.yaml' " +
                        "-o /tmp/longhorn.yaml 2>/dev/null " +
                        "|| curl -fsSL --connect-timeout 30 " +
                        "'https://raw.githubusercontent.com/longhorn/longhorn/v1.7.2/deploy/longhorn.yaml' " +
                        "-o /tmp/longhorn.yaml " +
                        "&& kubectl apply -f /tmp/longhorn.yaml " +
                        "&& echo '等待 Longhorn 就绪...' " +
                        "&& kubectl -n longhorn-system wait pod --all --for=condition=Ready --timeout=300s 2>/dev/null || true " +
                        "&& kubectl patch storageclass longhorn -p '{\"metadata\":{\"annotations\":{\"storageclass.kubernetes.io/is-default-class\":\"true\"}}}' 2>&1";
            case "nfs":
                // NFS provisioner 需要一个已有的 NFS server，使用 nfs-subdir-external-provisioner
                return "echo 'NFS 存储需要先部署 NFS 服务器，请在 Helm 应用商店中安装 nfs-subdir-external-provisioner 并配置 NFS 地址' 2>&1";
            default:
                return "echo '未知的存储插件: " + storagePlugin + "' 2>&1";
        }
    }

    // ====== Helper methods ======

    private void appendLog(Long planId, String nodeHost, String step, String level, String message) {
        K8sDeployLog deployLog = new K8sDeployLog();
        deployLog.setPlanId(planId);
        deployLog.setNodeHost(nodeHost);
        deployLog.setStep(step);
        deployLog.setLogLevel(level);
        deployLog.setMessage(message);
        logMapper.insert(deployLog);
    }

    private String parseJoinToken(String output) {
        String joinCmd = extractBetweenMarkers(output, "===JOIN_COMMAND_START===", "===JOIN_COMMAND_END===");
        if (joinCmd != null) {
            return parseJoinTokenFromCmd(joinCmd);
        }
        return null;
    }

    private String parseJoinCaHash(String output) {
        String joinCmd = extractBetweenMarkers(output, "===JOIN_COMMAND_START===", "===JOIN_COMMAND_END===");
        if (joinCmd != null) {
            return parseJoinCaHashFromCmd(joinCmd);
        }
        return null;
    }

    private String parseJoinTokenFromCmd(String joinCmd) {
        if (joinCmd == null) return null;
        Pattern p = Pattern.compile("--token\\s+(\\S+)");
        Matcher m = p.matcher(joinCmd);
        return m.find() ? m.group(1) : null;
    }

    private String parseJoinCaHashFromCmd(String joinCmd) {
        if (joinCmd == null) return null;
        Pattern p = Pattern.compile("--discovery-token-ca-cert-hash\\s+(\\S+)");
        Matcher m = p.matcher(joinCmd);
        return m.find() ? m.group(1) : null;
    }

    private String parseKubeconfig(String output) {
        return extractBetweenMarkers(output, "===KUBECONFIG_START===", "===KUBECONFIG_END===");
    }

    private String extractBetweenMarkers(String text, String startMarker, String endMarker) {
        int startIdx = text.indexOf(startMarker);
        int endIdx = text.indexOf(endMarker);
        if (startIdx >= 0 && endIdx > startIdx) {
            return text.substring(startIdx + startMarker.length(), endIdx).trim();
        }
        return null;
    }
}
