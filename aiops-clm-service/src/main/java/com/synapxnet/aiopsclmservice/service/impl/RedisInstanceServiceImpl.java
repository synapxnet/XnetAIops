package com.synapxnet.aiopsclmservice.service.impl;

import com.synapxnet.aiopsclmservice.entity.RedisDeployConfig;
import com.synapxnet.aiopsclmservice.entity.RedisInstance;
import com.synapxnet.aiopsclmservice.mapper.RedisInstanceMapper;
import com.synapxnet.aiopsclmservice.service.RedisInstanceService;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RedisInstanceServiceImpl implements RedisInstanceService {

    private static final Logger log = LoggerFactory.getLogger(RedisInstanceServiceImpl.class);
    private final RedisInstanceMapper mapper;

    public RedisInstanceServiceImpl(RedisInstanceMapper mapper) {
        this.mapper = mapper;
    }

    // ==================== CRUD ====================

    @Override
    public List<RedisInstance> listAll() {
        return mapper.findAll();
    }

    @Override
    public RedisInstance getById(Long id) {
        RedisInstance instance = mapper.findById(id);
        if (instance == null) {
            throw new IllegalArgumentException("Redis instance not found: " + id);
        }
        return instance;
    }

    @Override
    public List<RedisInstance> listByClusterId(Long clusterId) {
        return mapper.findByClusterId(clusterId);
    }

    @Override
    public RedisInstance create(RedisInstance instance) {
        instance.setUid(UUID.randomUUID().toString());
        if (instance.getStatus() == null) {
            instance.setStatus("pending");
        }
        if (instance.getRedisPort() == null) {
            instance.setRedisPort(6379);
        }
        if (instance.getSshPort() == null) {
            instance.setSshPort(22);
        }
        if (instance.getDeployMode() == null) {
            instance.setDeployMode("standalone");
        }
        if (instance.getRole() == null) {
            instance.setRole("master");
        }
        mapper.insert(instance);
        return instance;
    }

    @Override
    public RedisInstance update(RedisInstance instance) {
        mapper.update(instance);
        return mapper.findById(instance.getId());
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    // ==================== 部署操作 ====================

    @Override
    public Map<String, Object> testConnection(RedisInstance instance) {
        Map<String, Object> result = new HashMap<>();
        try (SSHClient ssh = createSSHClient(instance)) {
            Session session = ssh.startSession();
            Session.Command cmd = session.exec("uname -a && cat /etc/os-release | head -5 && free -m | head -2");
            String output = IOUtils.readFully(cmd.getInputStream()).toString();
            cmd.join(10, TimeUnit.SECONDS);
            session.close();

            result.put("success", true);
            result.put("message", "连接成功");
            result.put("systemInfo", output.trim());
        } catch (Exception e) {
            log.error("SSH连接测试失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> deploy(Long id, RedisDeployConfig config) {
        RedisInstance instance = getById(id);
        Map<String, Object> result = new HashMap<>();

        mapper.updateStatus(id, "deploying", "开始部署Redis...\n");
        result.put("success", true);
        result.put("message", "部署任务已提交");
        result.put("instanceId", id);

        asyncDeploy(instance, config);
        return result;
    }

    @Async
    protected void asyncDeploy(RedisInstance instance, RedisDeployConfig config) {
        StringBuilder deployLog = new StringBuilder();
        try (SSHClient ssh = createSSHClient(instance)) {
            String script = getDeployScript(config);

            deployLog.append("=== 开始部署 Redis ").append(config.getRedisVersion()).append(" ===\n");
            deployLog.append("目标主机: ").append(instance.getHost()).append("\n");
            deployLog.append("Redis端口: ").append(config.getRedisPort()).append("\n\n");

            Session session = ssh.startSession();
            session.allocateDefaultPTY();
            Session.Command cmd = session.exec(script);
            String output = IOUtils.readFully(cmd.getInputStream()).toString();
            cmd.join(600, TimeUnit.SECONDS);

            deployLog.append(output).append("\n");

            int exitStatus = cmd.getExitStatus() != null ? cmd.getExitStatus() : -1;
            session.close();

            if (exitStatus == 0) {
                deployLog.append("\n=== Redis 部署成功 ===\n");
                mapper.updateStatus(instance.getId(), "deployed", deployLog.toString());
            } else {
                deployLog.append("\n=== Redis 部署失败 (exit code: ").append(exitStatus).append(") ===\n");
                mapper.updateStatus(instance.getId(), "failed", deployLog.toString());
            }
        } catch (Exception e) {
            deployLog.append("\n=== 部署异常: ").append(e.getMessage()).append(" ===\n");
            mapper.updateStatus(instance.getId(), "failed", deployLog.toString());
        }
    }

    @Override
    public String getDeployScript(RedisDeployConfig config) {
        String version = config.getRedisVersion() != null ? config.getRedisVersion() : "7.2";
        int port = config.getRedisPort() != null ? config.getRedisPort() : 6379;
        String dataDir = config.getDataDir() != null ? config.getDataDir() : "/var/lib/redis";
        int maxMem = config.getMaxMemory() != null ? config.getMaxMemory() : 1024;
        String memPolicy = config.getMaxMemoryPolicy() != null ? config.getMaxMemoryPolicy() : "noeviction";
        String persistence = config.getPersistenceMode() != null ? config.getPersistenceMode() : "rdb";
        String redisPwd = config.getRedisPassword() != null ? config.getRedisPassword() : "";
        String deployMode = config.getDeployMode() != null ? config.getDeployMode() : "standalone";

        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/bash\n");
        sb.append("set -e\n\n");

        sb.append("echo '>>> 检测操作系统...'\n");
        sb.append("if [ -f /etc/redhat-release ]; then\n");
        sb.append("  OS_TYPE='centos'\n");
        sb.append("elif [ -f /etc/debian_version ]; then\n");
        sb.append("  OS_TYPE='ubuntu'\n");
        sb.append("else\n");
        sb.append("  echo '不支持的操作系统' && exit 1\n");
        sb.append("fi\n\n");

        // Install dependencies
        sb.append("echo '>>> 安装编译依赖...'\n");
        sb.append("if [ \"$OS_TYPE\" = 'centos' ]; then\n");
        sb.append("  yum install -y gcc make wget\n");
        sb.append("else\n");
        sb.append("  apt-get update && apt-get install -y build-essential wget\n");
        sb.append("fi\n\n");

        // Download and compile Redis from source
        sb.append("echo '>>> 下载并编译 Redis ").append(version).append("...'\n");
        sb.append("cd /tmp\n");
        sb.append("wget -q https://download.redis.io/releases/redis-").append(version).append(".tar.gz || ");
        sb.append("wget -q https://github.com/redis/redis/archive/refs/tags/").append(version).append(".tar.gz -O redis-").append(version).append(".tar.gz\n");
        sb.append("tar xzf redis-").append(version).append(".tar.gz\n");
        sb.append("cd redis-").append(version).append(" 2>/dev/null || cd redis-").append(version).append("*\n");
        sb.append("make -j$(nproc) && make install PREFIX=/usr/local/redis\n\n");

        // Create user and directories
        sb.append("echo '>>> 创建用户和目录...'\n");
        sb.append("useradd -r -s /sbin/nologin redis 2>/dev/null || true\n");
        sb.append("mkdir -p ").append(dataDir).append(" /etc/redis /var/log/redis /var/run/redis\n");
        sb.append("chown -R redis:redis ").append(dataDir).append(" /var/log/redis /var/run/redis\n\n");

        // Generate redis.conf
        sb.append("echo '>>> 生成配置文件...'\n");
        sb.append("cat > /etc/redis/redis-").append(port).append(".conf << 'REDISCONF'\n");
        sb.append("bind 0.0.0.0\n");
        sb.append("port ").append(port).append("\n");
        sb.append("daemonize no\n");
        sb.append("supervised systemd\n");
        sb.append("pidfile /var/run/redis/redis-").append(port).append(".pid\n");
        sb.append("logfile /var/log/redis/redis-").append(port).append(".log\n");
        sb.append("dir ").append(dataDir).append("\n");
        sb.append("maxmemory ").append(maxMem).append("mb\n");
        sb.append("maxmemory-policy ").append(memPolicy).append("\n");

        if (!redisPwd.isEmpty()) {
            sb.append("requirepass ").append(redisPwd).append("\n");
        }

        // Persistence config
        if ("rdb".equals(persistence) || "both".equals(persistence)) {
            sb.append("save 900 1\n");
            sb.append("save 300 10\n");
            sb.append("save 60 10000\n");
            sb.append("dbfilename dump-").append(port).append(".rdb\n");
        }
        if ("aof".equals(persistence) || "both".equals(persistence)) {
            sb.append("appendonly yes\n");
            sb.append("appendfilename appendonly-").append(port).append(".aof\n");
            sb.append("appendfsync everysec\n");
        }
        if ("none".equals(persistence)) {
            sb.append("save \"\"\n");
            sb.append("appendonly no\n");
        }

        // Cluster/Sentinel config
        if ("cluster".equals(deployMode)) {
            int busPort = port + 10000;
            sb.append("cluster-enabled yes\n");
            sb.append("cluster-config-file nodes-").append(port).append(".conf\n");
            sb.append("cluster-node-timeout 15000\n");
        }

        // Slave config
        if ("slave".equals(config.getRole()) && config.getMasterHost() != null) {
            int masterPort = config.getMasterPort() != null ? config.getMasterPort() : 6379;
            sb.append("replicaof ").append(config.getMasterHost()).append(" ").append(masterPort).append("\n");
            if (config.getMasterPassword() != null && !config.getMasterPassword().isEmpty()) {
                sb.append("masterauth ").append(config.getMasterPassword()).append("\n");
            }
        }

        sb.append("REDISCONF\n\n");

        // Create systemd service
        sb.append("echo '>>> 创建systemd服务...'\n");
        sb.append("cat > /etc/systemd/system/redis-").append(port).append(".service << 'SVCEOF'\n");
        sb.append("[Unit]\n");
        sb.append("Description=Redis ").append(port).append("\n");
        sb.append("After=network.target\n\n");
        sb.append("[Service]\n");
        sb.append("Type=notify\n");
        sb.append("User=redis\n");
        sb.append("ExecStart=/usr/local/redis/bin/redis-server /etc/redis/redis-").append(port).append(".conf\n");
        sb.append("ExecStop=/usr/local/redis/bin/redis-cli -p ").append(port);
        if (!redisPwd.isEmpty()) sb.append(" -a ").append(redisPwd);
        sb.append(" shutdown\n");
        sb.append("Restart=always\n\n");
        sb.append("[Install]\n");
        sb.append("WantedBy=multi-user.target\n");
        sb.append("SVCEOF\n\n");

        sb.append("echo '>>> 启动 Redis...'\n");
        sb.append("ln -sf /usr/local/redis/bin/redis-cli /usr/local/bin/redis-cli\n");
        sb.append("ln -sf /usr/local/redis/bin/redis-server /usr/local/bin/redis-server\n");
        sb.append("systemctl daemon-reload\n");
        sb.append("systemctl start redis-").append(port).append("\n");
        sb.append("systemctl enable redis-").append(port).append("\n\n");

        sb.append("echo '>>> Redis 部署完成!'\n");
        sb.append("redis-cli -p ").append(port);
        if (!redisPwd.isEmpty()) sb.append(" -a ").append(redisPwd);
        sb.append(" ping\n");

        return sb.toString();
    }

    // ==================== 状态管理 ====================

    @Override
    public Map<String, Object> checkStatus(Long id) {
        RedisInstance instance = getById(id);
        Map<String, Object> result = new HashMap<>();
        try (SSHClient ssh = createSSHClient(instance)) {
            int port = instance.getRedisPort() != null ? instance.getRedisPort() : 6379;
            Session session = ssh.startSession();
            Session.Command cmd = session.exec("systemctl is-active redis-" + port + " 2>/dev/null || echo 'inactive'");
            String output = IOUtils.readFully(cmd.getInputStream()).toString().trim();
            cmd.join(10, TimeUnit.SECONDS);
            session.close();

            boolean isRunning = "active".equals(output);
            String newStatus = isRunning ? "running" : "stopped";
            mapper.updateStatus(id, newStatus, null);
            if (isRunning) mapper.updateHeartbeat(id);

            result.put("success", true);
            result.put("isRunning", isRunning);
            result.put("status", newStatus);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            mapper.updateStatus(id, "failed", "状态检查失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> startRedis(Long id) {
        return execServiceCommand(id, "start");
    }

    @Override
    public Map<String, Object> stopRedis(Long id) {
        return execServiceCommand(id, "stop");
    }

    @Override
    public Map<String, Object> restartRedis(Long id) {
        return execServiceCommand(id, "restart");
    }

    private Map<String, Object> execServiceCommand(Long id, String action) {
        RedisInstance instance = getById(id);
        int port = instance.getRedisPort() != null ? instance.getRedisPort() : 6379;
        Map<String, Object> result = new HashMap<>();
        try (SSHClient ssh = createSSHClient(instance)) {
            Session session = ssh.startSession();
            Session.Command cmd = session.exec("systemctl " + action + " redis-" + port);
            cmd.join(30, TimeUnit.SECONDS);
            int exitStatus = cmd.getExitStatus() != null ? cmd.getExitStatus() : -1;
            session.close();

            result.put("success", exitStatus == 0);
            result.put("message", exitStatus == 0 ? "Redis " + action + " 成功" : "Redis " + action + " 失败");

            if ("start".equals(action) && exitStatus == 0) {
                mapper.updateStatus(id, "running", null);
            } else if ("stop".equals(action) && exitStatus == 0) {
                mapper.updateStatus(id, "stopped", null);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    private SSHClient createSSHClient(RedisInstance instance) throws IOException {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(instance.getHost(), instance.getSshPort() != null ? instance.getSshPort() : 22);
        String password = instance.getEncryptedPassword();
        ssh.authPassword(instance.getSshUser(), password != null ? password : "");
        return ssh;
    }
}
