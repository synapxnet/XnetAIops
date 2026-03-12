package com.synapxnet.aiopsclmservice.service.impl;

import com.synapxnet.aiopsclmservice.entity.MySQLDeployConfig;
import com.synapxnet.aiopsclmservice.entity.MySQLInstance;
import com.synapxnet.aiopsclmservice.mapper.MySQLInstanceMapper;
import com.synapxnet.aiopsclmservice.service.MySQLInstanceService;
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
public class MySQLInstanceServiceImpl implements MySQLInstanceService {

    private static final Logger log = LoggerFactory.getLogger(MySQLInstanceServiceImpl.class);
    private final MySQLInstanceMapper mapper;

    public MySQLInstanceServiceImpl(MySQLInstanceMapper mapper) {
        this.mapper = mapper;
    }

    // ==================== CRUD ====================

    @Override
    public List<MySQLInstance> listAll() {
        return mapper.findAll();
    }

    @Override
    public MySQLInstance getById(Long id) {
        MySQLInstance instance = mapper.findById(id);
        if (instance == null) {
            throw new IllegalArgumentException("MySQL instance not found: " + id);
        }
        return instance;
    }

    @Override
    public List<MySQLInstance> listByClusterId(Long clusterId) {
        return mapper.findByClusterId(clusterId);
    }

    @Override
    public MySQLInstance create(MySQLInstance instance) {
        instance.setUid(UUID.randomUUID().toString());
        if (instance.getStatus() == null) {
            instance.setStatus("pending");
        }
        if (instance.getMysqlPort() == null) {
            instance.setMysqlPort(3306);
        }
        if (instance.getSshPort() == null) {
            instance.setSshPort(22);
        }
        if (instance.getRole() == null) {
            instance.setRole("standalone");
        }
        mapper.insert(instance);
        return instance;
    }

    @Override
    public MySQLInstance update(MySQLInstance instance) {
        mapper.update(instance);
        return mapper.findById(instance.getId());
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    // ==================== 部署操作 ====================

    @Override
    public Map<String, Object> testConnection(MySQLInstance instance) {
        Map<String, Object> result = new HashMap<>();
        try (SSHClient ssh = createSSHClient(instance)) {
            Session session = ssh.startSession();
            Session.Command cmd = session.exec("uname -a && cat /etc/os-release | head -5 && free -m | head -2 && df -h / | tail -1");
            String output = IOUtils.readFully(cmd.getInputStream()).toString();
            cmd.join(10, TimeUnit.SECONDS);
            session.close();

            result.put("success", true);
            result.put("message", "连接成功");
            result.put("systemInfo", output.trim());
            return result;
        } catch (Exception e) {
            log.error("SSH连接测试失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> deploy(Long id, MySQLDeployConfig config) {
        MySQLInstance instance = getById(id);
        Map<String, Object> result = new HashMap<>();

        mapper.updateStatus(id, "deploying", "开始部署MySQL...\n");
        result.put("success", true);
        result.put("message", "部署任务已提交");
        result.put("instanceId", id);

        // 异步部署
        asyncDeploy(instance, config);
        return result;
    }

    @Async
    protected void asyncDeploy(MySQLInstance instance, MySQLDeployConfig config) {
        StringBuilder log = new StringBuilder();
        try (SSHClient ssh = createSSHClient(instance)) {
            String script = getDeployScript(config);

            log.append("=== 开始部署 MySQL ").append(config.getMysqlVersion()).append(" ===\n");
            log.append("目标主机: ").append(instance.getHost()).append("\n");
            log.append("MySQL端口: ").append(config.getMysqlPort()).append("\n\n");

            Session session = ssh.startSession();
            session.allocateDefaultPTY();
            Session.Command cmd = session.exec(script);
            String output = IOUtils.readFully(cmd.getInputStream()).toString();
            cmd.join(600, TimeUnit.SECONDS);

            log.append(output).append("\n");

            int exitStatus = cmd.getExitStatus() != null ? cmd.getExitStatus() : -1;
            session.close();

            if (exitStatus == 0) {
                log.append("\n=== MySQL 部署成功 ===\n");
                mapper.updateStatus(instance.getId(), "deployed", log.toString());
            } else {
                log.append("\n=== MySQL 部署失败 (exit code: ").append(exitStatus).append(") ===\n");
                mapper.updateStatus(instance.getId(), "failed", log.toString());
            }
        } catch (Exception e) {
            log.append("\n=== 部署异常: ").append(e.getMessage()).append(" ===\n");
            mapper.updateStatus(instance.getId(), "failed", log.toString());
        }
    }

    @Override
    public String getDeployScript(MySQLDeployConfig config) {
        String version = config.getMysqlVersion() != null ? config.getMysqlVersion() : "8.0";
        int port = config.getMysqlPort() != null ? config.getMysqlPort() : 3306;
        String dataDir = config.getDataDir() != null ? config.getDataDir() : "/var/lib/mysql";
        String charset = config.getCharset() != null ? config.getCharset() : "utf8mb4";
        int bufferPool = config.getInnodbBufferPoolSize() != null ? config.getInnodbBufferPoolSize() : 1024;
        int maxConn = config.getMaxConnections() != null ? config.getMaxConnections() : 500;
        String rootPwd = config.getRootPassword() != null ? config.getRootPassword() : "ChangeMe123!";
        int serverId = config.getServerId() != null ? config.getServerId() : 1;

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

        sb.append("echo \">>> 安装 MySQL ").append(version).append("...\"\n");
        sb.append("if [ \"$OS_TYPE\" = 'centos' ]; then\n");
        sb.append("  yum remove -y mariadb-libs 2>/dev/null || true\n");
        sb.append("  rpm --import https://repo.mysql.com/RPM-GPG-KEY-mysql-2023 2>/dev/null || true\n");
        if (version.startsWith("8")) {
            sb.append("  yum install -y https://dev.mysql.com/get/mysql80-community-release-el7-11.noarch.rpm 2>/dev/null || true\n");
        } else {
            sb.append("  yum install -y https://dev.mysql.com/get/mysql57-community-release-el7-11.noarch.rpm 2>/dev/null || true\n");
        }
        sb.append("  yum install -y mysql-community-server\n");
        sb.append("else\n");
        sb.append("  export DEBIAN_FRONTEND=noninteractive\n");
        sb.append("  apt-get update\n");
        sb.append("  apt-get install -y mysql-server\n");
        sb.append("fi\n\n");

        // my.cnf configuration
        sb.append("echo '>>> 配置 MySQL...'\n");
        sb.append("cat > /etc/my.cnf.d/custom.cnf 2>/dev/null || cat > /etc/mysql/conf.d/custom.cnf << 'MYCNF'\n");
        sb.append("[mysqld]\n");
        sb.append("port=").append(port).append("\n");
        sb.append("datadir=").append(dataDir).append("\n");
        sb.append("character-set-server=").append(charset).append("\n");
        sb.append("collation-server=").append(charset).append("_general_ci\n");
        sb.append("innodb_buffer_pool_size=").append(bufferPool).append("M\n");
        sb.append("max_connections=").append(maxConn).append("\n");
        sb.append("server-id=").append(serverId).append("\n");
        sb.append("log-bin=mysql-bin\n");
        sb.append("binlog_format=ROW\n");
        sb.append("default-authentication-plugin=mysql_native_password\n");
        sb.append("MYCNF\n\n");

        sb.append("echo '>>> 初始化并启动 MySQL...'\n");
        sb.append("mkdir -p ").append(dataDir).append("\n");
        sb.append("chown -R mysql:mysql ").append(dataDir).append("\n");
        sb.append("systemctl start mysqld\n");
        sb.append("systemctl enable mysqld\n\n");

        sb.append("echo '>>> 设置root密码...'\n");
        if (version.startsWith("8")) {
            sb.append("TEMP_PWD=$(grep 'temporary password' /var/log/mysqld.log 2>/dev/null | tail -1 | awk '{print $NF}' || echo '')\n");
            sb.append("if [ -n \"$TEMP_PWD\" ]; then\n");
            sb.append("  mysql --connect-expired-password -uroot -p\"$TEMP_PWD\" -e \"ALTER USER 'root'@'localhost' IDENTIFIED BY '").append(rootPwd).append("';\" 2>/dev/null || true\n");
            sb.append("fi\n");
        } else {
            sb.append("mysqladmin -u root password '").append(rootPwd).append("' 2>/dev/null || true\n");
        }

        sb.append("mysql -uroot -p'").append(rootPwd).append("' -e \"CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY '").append(rootPwd).append("';\" 2>/dev/null || true\n");
        sb.append("mysql -uroot -p'").append(rootPwd).append("' -e \"GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION; FLUSH PRIVILEGES;\" 2>/dev/null || true\n\n");

        // Replication setup
        if ("master".equals(config.getRole())) {
            sb.append("echo '>>> 配置主从复制 (Master)...'\n");
            if (config.getReplUser() != null) {
                String replPwd = config.getReplPassword() != null ? config.getReplPassword() : "ReplPass123!";
                sb.append("mysql -uroot -p'").append(rootPwd).append("' -e \"CREATE USER IF NOT EXISTS '")
                  .append(config.getReplUser()).append("'@'%' IDENTIFIED BY '").append(replPwd).append("';\" \n");
                sb.append("mysql -uroot -p'").append(rootPwd).append("' -e \"GRANT REPLICATION SLAVE ON *.* TO '")
                  .append(config.getReplUser()).append("'@'%'; FLUSH PRIVILEGES;\" \n");
            }
        } else if ("slave".equals(config.getRole()) && config.getMasterHost() != null) {
            sb.append("echo '>>> 配置主从复制 (Slave)...'\n");
            String replPwd = config.getReplPassword() != null ? config.getReplPassword() : "ReplPass123!";
            int masterPort = config.getMasterPort() != null ? config.getMasterPort() : 3306;
            sb.append("mysql -uroot -p'").append(rootPwd).append("' -e \"CHANGE MASTER TO ")
              .append("MASTER_HOST='").append(config.getMasterHost()).append("', ")
              .append("MASTER_PORT=").append(masterPort).append(", ")
              .append("MASTER_USER='").append(config.getReplUser()).append("', ")
              .append("MASTER_PASSWORD='").append(replPwd).append("';\" \n");
            sb.append("mysql -uroot -p'").append(rootPwd).append("' -e \"START SLAVE;\" \n");
        }

        sb.append("\necho '>>> MySQL 部署完成!'\n");
        sb.append("systemctl status mysqld --no-pager\n");

        return sb.toString();
    }

    // ==================== 状态管理 ====================

    @Override
    public Map<String, Object> checkStatus(Long id) {
        MySQLInstance instance = getById(id);
        Map<String, Object> result = new HashMap<>();
        try (SSHClient ssh = createSSHClient(instance)) {
            Session session = ssh.startSession();
            Session.Command cmd = session.exec("systemctl is-active mysqld 2>/dev/null || systemctl is-active mysql 2>/dev/null || echo 'inactive'");
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
    public Map<String, Object> startMySQL(Long id) {
        return execServiceCommand(id, "start");
    }

    @Override
    public Map<String, Object> stopMySQL(Long id) {
        return execServiceCommand(id, "stop");
    }

    @Override
    public Map<String, Object> restartMySQL(Long id) {
        return execServiceCommand(id, "restart");
    }

    private Map<String, Object> execServiceCommand(Long id, String action) {
        MySQLInstance instance = getById(id);
        Map<String, Object> result = new HashMap<>();
        try (SSHClient ssh = createSSHClient(instance)) {
            Session session = ssh.startSession();
            Session.Command cmd = session.exec("systemctl " + action + " mysqld 2>/dev/null || systemctl " + action + " mysql");
            cmd.join(30, TimeUnit.SECONDS);
            int exitStatus = cmd.getExitStatus() != null ? cmd.getExitStatus() : -1;
            session.close();

            result.put("success", exitStatus == 0);
            result.put("message", exitStatus == 0 ? "MySQL " + action + " 成功" : "MySQL " + action + " 失败");

            // Update status after action
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

    // ==================== 工具方法 ====================

    private SSHClient createSSHClient(MySQLInstance instance) throws IOException {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(instance.getHost(), instance.getSshPort() != null ? instance.getSshPort() : 22);
        String password = instance.getEncryptedPassword();
        ssh.authPassword(instance.getSshUser(), password != null ? password : "");
        return ssh;
    }
}
