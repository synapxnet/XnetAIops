package com.synapxnet.aiopsclmservice.service.impl;

import com.jcraft.jsch.*;
import com.synapxnet.aiopsclmservice.client.HomClient;
import com.synapxnet.aiopsclmservice.client.HostInfo;
import com.synapxnet.aiopsclmservice.entity.JenkinsMaster;
import com.synapxnet.aiopsclmservice.entity.JenkinsMasterDeployConfig;
import com.synapxnet.aiopsclmservice.exception.DuplicateEntryException;
import com.synapxnet.aiopsclmservice.exception.EntityNotFoundException;
import com.synapxnet.aiopsclmservice.mapper.JenkinsMasterMapper;
import com.synapxnet.aiopsclmservice.service.JenkinsMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class JenkinsMasterServiceImpl implements JenkinsMasterService {

    private static final Logger logger = LoggerFactory.getLogger(JenkinsMasterServiceImpl.class);
    private static final String AES_KEY = "XnetMLopsJenkins";
    private static final int SSH_TIMEOUT = 60000;
    private static final int DEPLOY_TIMEOUT = 1800000;

    @Autowired
    private JenkinsMasterMapper jenkinsMasterMapper;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private HomClient homClient;

    // ==================== CRUD ====================

    @Override
    @Transactional
    public JenkinsMaster createMaster(JenkinsMaster master) {
        // If host_id is provided, fetch host info from HOM and populate SSH fields
        if (master.getHost_id() != null) {
            populateFromHomHost(master);
        }
        if (jenkinsMasterMapper.countByName(master.getName(), null) > 0) {
            throw new DuplicateEntryException("Master名称已存在: " + master.getName());
        }
        Integer jenkinsPort = master.getJenkins_port() != null ? master.getJenkins_port() : 8080;
        if (jenkinsMasterMapper.countByHostAndJenkinsPort(master.getHost(), jenkinsPort, null) > 0) {
            throw new DuplicateEntryException("该主机的Jenkins端口已被占用: " + master.getHost() + ":" + jenkinsPort);
        }
        master.setUid(UUID.randomUUID().toString());
        if (master.getPort() == null)
            master.setPort(22);
        if (master.getOs_type() == null)
            master.setOs_type("linux");
        if (master.getJenkins_port() == null)
            master.setJenkins_port(8080);
        if (master.getJenkins_home() == null)
            master.setJenkins_home("/var/jenkins_home");
        if (master.getJava_version() == null)
            master.setJava_version("17");
        if (master.getJava_opts() == null)
            master.setJava_opts("-Xmx2g -Xms1g");
        if (master.getAdmin_username() == null)
            master.setAdmin_username("admin");
        if (master.getStatus() == null)
            master.setStatus("pending");
        if (master.getRegion() == null)
            master.setRegion("guangzhou");
        if (master.getCpu_cores() == null)
            master.setCpu_cores(4);
        if (master.getRam_gb() == null)
            master.setRam_gb(8);
        if (master.getDisk_gb() == null)
            master.setDisk_gb(100);
        if (master.getPassword() != null && !master.getPassword().isEmpty()) {
            master.setEncrypted_password(encryptPassword(master.getPassword()));
        }
        if (master.getAdmin_password() != null && !master.getAdmin_password().isEmpty()) {
            master.setEncrypted_admin_password(encryptPassword(master.getAdmin_password()));
        }
        jenkinsMasterMapper.insert(master);
        logger.info("创建Jenkins Master: {}", master.getName());
        return master;
    }

    @Override
    @Transactional
    public JenkinsMaster updateMaster(Long id, JenkinsMaster master) {
        JenkinsMaster existing = getMasterById(id);
        // If host_id changed, re-populate from HOM
        if (master.getHost_id() != null && !master.getHost_id().equals(existing.getHost_id())) {
            populateFromHomHost(master);
        }
        if (!existing.getName().equals(master.getName())) {
            if (jenkinsMasterMapper.countByName(master.getName(), id) > 0) {
                throw new DuplicateEntryException("Master名称已存在: " + master.getName());
            }
        }
        Integer jenkinsPort = master.getJenkins_port() != null ? master.getJenkins_port() : existing.getJenkins_port();
        if (!existing.getHost().equals(master.getHost()) || !existing.getJenkins_port().equals(jenkinsPort)) {
            if (jenkinsMasterMapper.countByHostAndJenkinsPort(master.getHost(), jenkinsPort, id) > 0) {
                throw new DuplicateEntryException("该主机的Jenkins端口已被占用: " + master.getHost() + ":" + jenkinsPort);
            }
        }
        master.setId(id);
        master.setUid(existing.getUid());
        if (master.getPassword() != null && !master.getPassword().isEmpty()) {
            master.setEncrypted_password(encryptPassword(master.getPassword()));
        } else {
            master.setEncrypted_password(existing.getEncrypted_password());
        }
        if (master.getAdmin_password() != null && !master.getAdmin_password().isEmpty()) {
            master.setEncrypted_admin_password(encryptPassword(master.getAdmin_password()));
        } else {
            master.setEncrypted_admin_password(existing.getEncrypted_admin_password());
        }
        jenkinsMasterMapper.update(master);
        logger.info("更新Jenkins Master: {}", master.getName());
        return getMasterById(id);
    }

    @Override
    @Transactional
    public void deleteMaster(Long id) {
        JenkinsMaster master = getMasterById(id);
        jenkinsMasterMapper.deleteById(id);
        logger.info("删除Jenkins Master: {}", master.getName());
    }

    @Override
    public JenkinsMaster getMasterById(Long id) {
        return jenkinsMasterMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Master不存在: " + id));
    }

    @Override
    public JenkinsMaster getMasterByUid(String uid) {
        return jenkinsMasterMapper.findByUid(uid)
                .orElseThrow(() -> new EntityNotFoundException("Master不存在: " + uid));
    }

    @Override
    public List<JenkinsMaster> getAllMasters() {
        return jenkinsMasterMapper.findAll();
    }

    @Override
    public List<JenkinsMaster> getMastersByStatus(String status) {
        return jenkinsMasterMapper.findByStatus(status);
    }

    @Override
    public List<JenkinsMaster> getDeployedMasters() {
        return jenkinsMasterMapper.findDeployedMasters();
    }

    // ==================== HOM主机集成 ====================

    /**
     * 获取HOM主机列表，供前端选择
     */
    @Override
    public List<HostInfo> getHomHosts() {
        try {
            return homClient.getAllHosts();
        } catch (Exception e) {
            logger.warn("获取HOM主机列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从HOM主机信息填充Jenkins Master的SSH连接字段
     * 注意: HOM模块的encryptedPassword实际存储的是明文密码(HOM无加密逻辑)
     */
    private void populateFromHomHost(JenkinsMaster master) {
        HostInfo hostInfo = homClient.getHost(master.getHost_id());
        if (hostInfo == null) {
            throw new RuntimeException("HOM主机不存在: " + master.getHost_id());
        }
        logger.info("从HOM主机填充SSH信息: hostId={}, ip={}", master.getHost_id(), hostInfo.getIpAddress());
        // Populate SSH fields from HOM host
        if (master.getHost() == null || master.getHost().isEmpty()) {
            master.setHost(hostInfo.getIpAddress());
        }
        if (master.getPort() == null) {
            master.setPort(hostInfo.getSshPort() != null ? hostInfo.getSshPort() : 22);
        }
        if (master.getUsername() == null || master.getUsername().isEmpty()) {
            master.setUsername(hostInfo.getSshUser() != null ? hostInfo.getSshUser() : "root");
        }
        // HOM的encryptedPassword实际是明文密码，需要用CLM的AES加密后存储
        if ((master.getPassword() == null || master.getPassword().isEmpty())
                && master.getEncrypted_password() == null) {
            if (hostInfo.getEncryptedPassword() != null) {
                // HOM存的是明文，用CLM的AES key加密存储
                master.setEncrypted_password(encryptPassword(hostInfo.getEncryptedPassword()));
            }
        }
        // Populate OS type from HOM
        if (master.getOs_type() == null && hostInfo.getOsType() != null) {
            master.setOs_type(hostInfo.getOsType());
        }
        // Populate resource info from HOM
        if (master.getCpu_cores() == null && hostInfo.getCpuCores() != null) {
            master.setCpu_cores(hostInfo.getCpuCores());
        }
    }

    /**
     * 从HOM获取明文密码(HOM的encryptedPassword字段实际存储明文)
     */
    private String getHomHostPassword(Long hostId) {
        try {
            HostInfo hostInfo = homClient.getHost(hostId);
            if (hostInfo != null && hostInfo.getEncryptedPassword() != null) {
                return hostInfo.getEncryptedPassword();
            }
        } catch (Exception e) {
            logger.warn("获取HOM主机密码失败: hostId={}, {}", hostId, e.getMessage());
        }
        return null;
    }

    // ==================== SSH连接 ====================

    @Override
    public Map<String, Object> testConnection(JenkinsMaster master) {
        Map<String, Object> result = new HashMap<>();
        Session session = null;
        try {
            // If host_id is provided, populate SSH fields from HOM first
            if (master.getHost_id() != null) {
                HostInfo hostInfo = homClient.getHost(master.getHost_id());
                if (hostInfo != null) {
                    if (master.getHost() == null || master.getHost().isEmpty()) {
                        master.setHost(hostInfo.getIpAddress());
                    }
                    if (master.getPort() == null) {
                        master.setPort(hostInfo.getSshPort() != null ? hostInfo.getSshPort() : 22);
                    }
                    if (master.getUsername() == null || master.getUsername().isEmpty()) {
                        master.setUsername(hostInfo.getSshUser() != null ? hostInfo.getSshUser() : "root");
                    }
                }
            }

            String password = master.getPassword();
            if ((password == null || password.isEmpty()) && master.getEncrypted_password() != null) {
                password = decryptPassword(master.getEncrypted_password());
            }
            // If still no password and host_id is set, get from HOM (plain text)
            if ((password == null || password.isEmpty()) && master.getHost_id() != null) {
                password = getHomHostPassword(master.getHost_id());
            }
            JSch jsch = new JSch();
            session = jsch.getSession(master.getUsername(), master.getHost(), master.getPort());
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(SSH_TIMEOUT);
            session.connect();
            String osInfo = executeCommand(session, "uname -a 2>/dev/null || ver 2>nul || echo 'Unknown'", SSH_TIMEOUT);
            String hostname = executeCommand(session, "hostname", SSH_TIMEOUT);
            String diskInfo = executeCommand(session, "df -h / 2>/dev/null | tail -1 | awk '{print $4}'", SSH_TIMEOUT);
            String memInfo = executeCommand(session, "free -m 2>/dev/null | grep Mem | awk '{print $2}'", SSH_TIMEOUT);
            result.put("success", true);
            result.put("message", "连接成功");
            result.put("osInfo", osInfo.trim());
            result.put("hostname", hostname.trim());
            result.put("availableDisk", diskInfo.trim());
            result.put("totalMemoryMb", memInfo.trim());
            result.put("detectedOsType", detectOsType(osInfo));
        } catch (JSchException e) {
            logger.error("SSH连接失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        } catch (Exception e) {
            logger.error("测试连接异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "测试失败: " + e.getMessage());
        } finally {
            if (session != null && session.isConnected())
                session.disconnect();
        }
        return result;
    }

    // ==================== 部署 ====================

    @Override
    public Map<String, Object> deployMaster(Long masterId, JenkinsMasterDeployConfig config) {
        if (config.getAdminPassword() == null || config.getAdminPassword().isBlank()) {
            throw new IllegalArgumentException("Jenkins 管理员密码不能为空");
        }
        Map<String, Object> result = new HashMap<>();
        JenkinsMaster master = getMasterById(masterId);
        if (config.getJenkinsVersion() != null)
            master.setJenkins_version(config.getJenkinsVersion());
        if (config.getJenkinsPort() != null)
            master.setJenkins_port(config.getJenkinsPort());
        if (config.getJenkinsHome() != null)
            master.setJenkins_home(config.getJenkinsHome());
        if (config.getJavaVersion() != null)
            master.setJava_version(config.getJavaVersion());
        if (config.getJavaOpts() != null)
            master.setJava_opts(config.getJavaOpts());
        if (config.getAdminUsername() != null)
            master.setAdmin_username(config.getAdminUsername());
        if (config.getAdminPassword() != null) {
            master.setEncrypted_admin_password(encryptPassword(config.getAdminPassword()));
        }
        master.setStatus("deploying");
        jenkinsMasterMapper.update(master);
        result.put("success", true);
        result.put("message", "部署任务已启动");
        result.put("masterId", masterId);
        JenkinsMasterServiceImpl proxy = applicationContext.getBean(JenkinsMasterServiceImpl.class);
        proxy.deployMasterAsync(masterId, config);
        return result;
    }

    @Async
    public void deployMasterAsync(Long masterId, JenkinsMasterDeployConfig config) {
        Session session = null;
        StringBuilder deployLog = new StringBuilder();
        JenkinsMaster master = jenkinsMasterMapper.findById(masterId)
                .orElseThrow(() -> new RuntimeException("Master不存在: " + masterId));
        try {
            String password = getSSHPassword(master);
            JSch jsch = new JSch();
            session = jsch.getSession(master.getUsername(), master.getHost(), master.getPort());
            session.setPassword(password);
            Properties sshConfig = new Properties();
            sshConfig.put("StrictHostKeyChecking", "no");
            session.setConfig(sshConfig);
            session.setTimeout(SSH_TIMEOUT);
            session.connect();
            deployLog.append("=== 开始部署 Jenkins Master ===\n");
            deployLog.append("时间: ").append(new Date()).append("\n");
            deployLog.append("目标主机: ").append(master.getHost()).append("\n");
            deployLog.append("Jenkins版本: ").append(config.getJenkinsVersion()).append("\n\n");
            String script = getDeployScript(master.getOs_type(), config, master.getHost());
            String scriptPath = "/tmp/jenkins_master_deploy_" + System.currentTimeMillis() + ".sh";
            deployLog.append("上传部署脚本到: ").append(scriptPath).append("\n");
            uploadScript(session, script, scriptPath);
            jenkinsMasterMapper.updateStatus(masterId, "deploying", deployLog.toString());
            deployLog.append("\n执行部署脚本...\n");
            String output = executeCommand(session, "chmod +x " + scriptPath + " && " + scriptPath, DEPLOY_TIMEOUT);
            deployLog.append(output);
            if (output.contains("Jenkins Master 安装完成") || output.contains("Installation completed")) {
                master.setStatus("deployed");
                deployLog.append("\n=== 部署成功 ===\n");
                // 使用用户配置的管理员密码（init.groovy.d已自动跳过向导并创建管理员账号）
                String adminPassword = config.getAdminPassword();
                master.setInitial_password(adminPassword);
                deployLog.append("管理员用户: ").append(config.getAdminUsername() != null ? config.getAdminUsername() : "admin").append("\n");
                deployLog.append("管理员密码: ").append(adminPassword).append("\n");
            } else {
                master.setStatus("failed");
                deployLog.append("\n=== 部署失败 ===\n");
            }
        } catch (Exception e) {
            logger.error("部署失败: {}", e.getMessage(), e);
            master.setStatus("failed");
            deployLog.append("\n=== 部署失败 ===\n错误: ").append(e.getMessage()).append("\n");
        } finally {
            if (session != null && session.isConnected())
                session.disconnect();
            master.setDeploy_log(deployLog.toString());
            jenkinsMasterMapper.updateStatus(master.getId(), master.getStatus(), deployLog.toString());
            if (master.getInitial_password() != null) {
                jenkinsMasterMapper.updateInitialPassword(master.getId(), master.getInitial_password());
            }
        }
    }

    @Override
    public String getDeployScript(String osType, JenkinsMasterDeployConfig config) {
        return getDeployScript(osType, config, "");
    }

    public String getDeployScript(String osType, JenkinsMasterDeployConfig config, String hostIp) {
        try {
            String templatePath;
            switch (osType.toLowerCase()) {
                case "macos":
                    templatePath = "scripts/jenkins-master-macos.sh";
                    break;
                case "windows":
                    templatePath = "scripts/jenkins-master-windows.ps1";
                    break;
                default:
                    templatePath = "scripts/jenkins-master-linux.sh";
                    break;
            }
            ClassPathResource resource = new ClassPathResource(templatePath);
            String template;
            try (InputStream inputStream = resource.getInputStream()) {
                byte[] bytes = FileCopyUtils.copyToByteArray(inputStream);
                template = new String(bytes, StandardCharsets.UTF_8);
            }
            template = template.replace("${JENKINS_VERSION}",
                    config.getJenkinsVersion() != null ? config.getJenkinsVersion() : "2.462.3");
            template = template.replace("${JENKINS_PORT}",
                    String.valueOf(config.getJenkinsPort() != null ? config.getJenkinsPort() : 8080));
            template = template.replace("${JENKINS_HOME}",
                    config.getJenkinsHome() != null ? config.getJenkinsHome() : "/var/jenkins_home");
            template = template.replace("${JAVA_VERSION}",
                    config.getJavaVersion() != null ? config.getJavaVersion() : "17");
            template = template.replace("${JAVA_OPTS}",
                    config.getJavaOpts() != null ? config.getJavaOpts() : "-Xmx2g -Xms1g");
            template = template.replace("${ADMIN_USERNAME}",
                    config.getAdminUsername() != null ? config.getAdminUsername() : "admin");
            template = template.replace("${ADMIN_PASSWORD}",
                    config.getAdminPassword());
            template = template.replace("${ADMIN_EMAIL}",
                    config.getAdminEmail() != null ? config.getAdminEmail() : "admin@localhost");
            template = template.replace("${INSTALL_SUGGESTED_PLUGINS}",
                    config.getInstallSuggestedPlugins() == null || config.getInstallSuggestedPlugins() ? "true"
                            : "false");
            template = template.replace("${HOST_IP}", hostIp != null && !hostIp.isEmpty() ? hostIp : "");
            template = template.replace("${TIMEZONE}",
                    config.getTimezone() != null ? config.getTimezone() : "Asia/Shanghai");
            String credentialsScript = generateCredentialsGroovyScript(config);
            template = template.replace("${CREDENTIALS_GROOVY_SCRIPT}", credentialsScript);
            return template;
        } catch (Exception e) {
            throw new RuntimeException("读取部署脚本模板失败: " + e.getMessage());
        }
    }

    private String generateCredentialsGroovyScript(JenkinsMasterDeployConfig config) {
        StringBuilder script = new StringBuilder();
        boolean hasCredentials = (config.getGitCredentials() != null && !config.getGitCredentials().isEmpty()) ||
                (config.getHarborCredentials() != null && !config.getHarborCredentials().isEmpty()) ||
                (config.getSshCredentials() != null && !config.getSshCredentials().isEmpty());
        if (!hasCredentials)
            return "# 没有配置凭证";
        if (config.getGitCredentials() != null) {
            for (JenkinsMasterDeployConfig.GitCredential cred : config.getGitCredentials()) {
                if (cred.getId() != null && !cred.getId().isEmpty() && cred.getUsername() != null
                        && !cred.getUsername().isEmpty()) {
                    script.append(String.format("createUsernamePasswordCredential('%s', '%s', '%s', '%s')\n",
                            escapeGroovyString(cred.getId()),
                            escapeGroovyString(cred.getDescription() != null ? cred.getDescription() : ""),
                            escapeGroovyString(cred.getUsername()),
                            escapeGroovyString(cred.getPassword() != null ? cred.getPassword() : "")));
                }
            }
        }
        if (config.getHarborCredentials() != null) {
            for (JenkinsMasterDeployConfig.HarborCredential cred : config.getHarborCredentials()) {
                if (cred.getId() != null && !cred.getId().isEmpty() && cred.getUsername() != null
                        && !cred.getUsername().isEmpty()) {
                    String desc = (cred.getDescription() != null ? cred.getDescription() : "")
                            + (cred.getUrl() != null ? " (Harbor: " + cred.getUrl() + ")" : "");
                    script.append(String.format("createUsernamePasswordCredential('%s', '%s', '%s', '%s')\n",
                            escapeGroovyString(cred.getId()), escapeGroovyString(desc),
                            escapeGroovyString(cred.getUsername()),
                            escapeGroovyString(cred.getPassword() != null ? cred.getPassword() : "")));
                }
            }
        }
        if (config.getSshCredentials() != null) {
            for (JenkinsMasterDeployConfig.SSHCredential cred : config.getSshCredentials()) {
                if (cred.getId() != null && !cred.getId().isEmpty() && cred.getUsername() != null
                        && !cred.getUsername().isEmpty() && cred.getPrivateKey() != null
                        && !cred.getPrivateKey().isEmpty()) {
                    String privateKeyBase64 = Base64.getEncoder()
                            .encodeToString(cred.getPrivateKey().getBytes(StandardCharsets.UTF_8));
                    script.append(String.format("createSshCredential('%s', '%s', '%s', '%s', '%s')\n",
                            escapeGroovyString(cred.getId()),
                            escapeGroovyString(cred.getDescription() != null ? cred.getDescription() : ""),
                            escapeGroovyString(cred.getUsername()), privateKeyBase64,
                            escapeGroovyString(cred.getPassphrase() != null ? cred.getPassphrase() : "")));
                }
            }
        }
        return script.length() > 0 ? script.toString() : "# 没有有效的凭证配置";
    }

    // ==================== 状态管理 ====================

    @Override
    public Map<String, Object> checkMasterStatus(Long masterId) {
        Map<String, Object> result = new HashMap<>();
        JenkinsMaster master = getMasterById(masterId);
        Session session = null;
        try {
            String password = getSSHPassword(master);
            JSch jsch = new JSch();
            session = jsch.getSession(master.getUsername(), master.getHost(), master.getPort());
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(SSH_TIMEOUT);
            session.connect();
            String processCheck = executeCommand(session,
                    "pgrep -f 'jenkins.war' > /dev/null && echo 'running' || echo 'stopped'", SSH_TIMEOUT);
            boolean isRunning = processCheck.trim().equals("running");
            String portCheck = executeCommand(session, "curl -s -o /dev/null -w '%{http_code}' http://localhost:"
                    + master.getJenkins_port() + " 2>/dev/null || echo '000'", SSH_TIMEOUT);
            boolean isResponding = !portCheck.trim().equals("000");
            result.put("success", true);
            result.put("isRunning", isRunning);
            result.put("isResponding", isResponding);
            result.put("status", isRunning ? (isResponding ? "running" : "starting") : "stopped");
            jenkinsMasterMapper.updateHeartbeat(masterId);
        } catch (Exception e) {
            logger.error("检查状态失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
            result.put("status", "offline");
        } finally {
            if (session != null && session.isConnected())
                session.disconnect();
        }
        return result;
    }

    @Override
    public Map<String, Object> startJenkins(Long masterId) {
        return executeJenkinsCommand(masterId, "start");
    }

    @Override
    public Map<String, Object> stopJenkins(Long masterId) {
        return executeJenkinsCommand(masterId, "stop");
    }

    @Override
    public Map<String, Object> restartJenkins(Long masterId) {
        return executeJenkinsCommand(masterId, "restart");
    }

    private Map<String, Object> executeJenkinsCommand(Long masterId, String action) {
        Map<String, Object> result = new HashMap<>();
        JenkinsMaster master = getMasterById(masterId);
        Session session = null;
        try {
            String password = getSSHPassword(master);
            JSch jsch = new JSch();
            session = jsch.getSession(master.getUsername(), master.getHost(), master.getPort());
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(SSH_TIMEOUT);
            session.connect();
            String command = "sudo systemctl " + action + " jenkins";
            String output = executeCommand(session, command, SSH_TIMEOUT);
            result.put("success", true);
            result.put("message", "Jenkins " + action + " 命令已执行");
            result.put("output", output);
        } catch (Exception e) {
            logger.error("执行Jenkins命令失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        } finally {
            if (session != null && session.isConnected())
                session.disconnect();
        }
        return result;
    }

    @Override
    public String getInitialPassword(Long masterId) {
        JenkinsMaster master = getMasterById(masterId);
        if (master.getInitial_password() != null && !master.getInitial_password().isEmpty()) {
            return master.getInitial_password();
        }
        Session session = null;
        try {
            String password = getSSHPassword(master);
            JSch jsch = new JSch();
            session = jsch.getSession(master.getUsername(), master.getHost(), master.getPort());
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(SSH_TIMEOUT);
            session.connect();
            String initialPassword = getInitialPasswordFromServer(session, master.getJenkins_home());
            if (initialPassword != null && !initialPassword.isEmpty()) {
                jenkinsMasterMapper.updateInitialPassword(masterId, initialPassword);
            }
            return initialPassword;
        } catch (Exception e) {
            logger.error("获取初始密码失败: {}", e.getMessage());
            return null;
        } finally {
            if (session != null && session.isConnected())
                session.disconnect();
        }
    }

    private String getInitialPasswordFromServer(Session session, String jenkinsHome) {
        try {
            String passwordFile = jenkinsHome + "/secrets/initialAdminPassword";
            return executeCommand(session, "cat " + passwordFile + " 2>/dev/null", SSH_TIMEOUT).trim();
        } catch (Exception e) {
            logger.warn("获取初始密码失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 凭证管理 ====================

    @Override
    public Map<String, Object> configureCredentials(Long masterId, JenkinsMasterDeployConfig config) {
        Map<String, Object> result = new HashMap<>();
        JenkinsMaster master = getMasterById(masterId);
        try {
            String jenkinsUrl = String.format("http://%s:%d", master.getHost(), master.getJenkins_port());
            String adminUser = master.getAdmin_username();
            String encryptedPassword = master.getEncrypted_admin_password();
            if (encryptedPassword == null || encryptedPassword.isEmpty()) {
                result.put("success", false);
                result.put("message", "Jenkins Master管理员密码未配置");
                return result;
            }
            String adminPassword = decryptPassword(encryptedPassword);
            if (!testJenkinsConnection(jenkinsUrl, adminUser, adminPassword)) {
                result.put("success", false);
                result.put("message", "无法连接到Jenkins Master: " + jenkinsUrl);
                return result;
            }
            List<String> createdCredentials = new ArrayList<>();
            List<String> failedCredentials = new ArrayList<>();
            if (config.getGitCredentials() != null) {
                for (JenkinsMasterDeployConfig.GitCredential gitCred : config.getGitCredentials()) {
                    boolean success = createUsernamePasswordCredential(jenkinsUrl, adminUser, adminPassword,
                            gitCred.getId(), gitCred.getDescription(), gitCred.getUsername(), gitCred.getPassword());
                    (success ? createdCredentials : failedCredentials).add("Git: " + gitCred.getId());
                }
            }
            if (config.getHarborCredentials() != null) {
                for (JenkinsMasterDeployConfig.HarborCredential harborCred : config.getHarborCredentials()) {
                    boolean success = createUsernamePasswordCredential(jenkinsUrl, adminUser, adminPassword,
                            harborCred.getId(), harborCred.getDescription() + " (Harbor: " + harborCred.getUrl() + ")",
                            harborCred.getUsername(), harborCred.getPassword());
                    (success ? createdCredentials : failedCredentials).add("Harbor: " + harborCred.getId());
                }
            }
            if (config.getSshCredentials() != null) {
                for (JenkinsMasterDeployConfig.SSHCredential sshCred : config.getSshCredentials()) {
                    boolean success = createSshCredential(jenkinsUrl, adminUser, adminPassword, sshCred.getId(),
                            sshCred.getDescription(), sshCred.getUsername(), sshCred.getPrivateKey(),
                            sshCred.getPassphrase());
                    (success ? createdCredentials : failedCredentials).add("SSH: " + sshCred.getId());
                }
            }
            saveCredentialsConfig(masterId, config);
            if (failedCredentials.isEmpty()) {
                result.put("success", true);
                result.put("message", "成功创建 " + createdCredentials.size() + " 个凭证");
            } else {
                result.put("success", createdCredentials.size() > 0);
                result.put("message", "创建 " + createdCredentials.size() + " 个凭证，失败 " + failedCredentials.size() + " 个");
                result.put("failedCredentials", failedCredentials);
            }
            result.put("createdCredentials", createdCredentials);
        } catch (Exception e) {
            logger.error("配置凭证失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "配置凭证异常: " + e.getMessage());
        }
        return result;
    }

    private boolean createUsernamePasswordCredential(String jenkinsUrl, String user, String password,
            String credentialId, String description, String credUsername, String credPassword) {
        String groovyScript = String.format(
                "import jenkins.model.*\nimport com.cloudbees.plugins.credentials.*\nimport com.cloudbees.plugins.credentials.impl.*\nimport com.cloudbees.plugins.credentials.domains.*\n\n"
                        +
                        "def jenkins = Jenkins.instance\ndef domain = Domain.global()\ndef store = jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()\n\n"
                        +
                        "def existingCred = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(com.cloudbees.plugins.credentials.common.StandardUsernameCredentials.class, jenkins, null, null).find { it.id == '%s' }\n\n"
                        +
                        "if (existingCred != null) {\n    println 'Credential already exists: %s'\n} else {\n" +
                        "    def credential = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, '%s', '%s', '%s', '%s')\n"
                        +
                        "    store.addCredentials(domain, credential)\n    println 'Credential created successfully: %s'\n}\n",
                escapeGroovyString(credentialId), escapeGroovyString(credentialId), escapeGroovyString(credentialId),
                escapeGroovyString(description), escapeGroovyString(credUsername), escapeGroovyString(credPassword),
                escapeGroovyString(credentialId));
        return executeGroovyScript(jenkinsUrl, user, password, groovyScript);
    }

    private boolean createSshCredential(String jenkinsUrl, String user, String password, String credentialId,
            String description, String sshUsername, String privateKey, String passphrase) {
        String escapedPrivateKey = privateKey.replace("\\", "\\\\").replace("$", "\\$");
        String groovyScript = String.format(
                "import jenkins.model.*\nimport com.cloudbees.plugins.credentials.*\nimport com.cloudbees.plugins.credentials.domains.*\nimport com.cloudbees.jenkins.plugins.sshcredentials.impl.*\n\n"
                        +
                        "def jenkins = Jenkins.instance\ndef domain = Domain.global()\ndef store = jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()\n\n"
                        +
                        "def existingCred = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(com.cloudbees.plugins.credentials.common.StandardUsernameCredentials.class, jenkins, null, null).find { it.id == '%s' }\n\n"
                        +
                        "if (existingCred != null) {\n    println 'SSH Credential already exists: %s'\n} else {\n" +
                        "    def privateKeySource = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource('''%s''')\n"
                        +
                        "    def credential = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, '%s', '%s', privateKeySource, '%s', '%s')\n"
                        +
                        "    store.addCredentials(domain, credential)\n    println 'SSH Credential created successfully: %s'\n}\n",
                escapeGroovyString(credentialId), escapeGroovyString(credentialId), escapedPrivateKey,
                escapeGroovyString(credentialId), escapeGroovyString(sshUsername),
                passphrase != null ? escapeGroovyString(passphrase) : "", escapeGroovyString(description),
                escapeGroovyString(credentialId));
        return executeGroovyScript(jenkinsUrl, user, password, groovyScript);
    }

    private boolean executeGroovyScript(String jenkinsUrl, String user, String password, String groovyScript) {
        java.net.HttpURLConnection crumbConn = null;
        java.net.HttpURLConnection scriptConn = null;
        try {
            String auth = user + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            String crumbUrl = jenkinsUrl + "/crumbIssuer/api/json";
            java.net.URL url1 = new java.net.URL(crumbUrl);
            crumbConn = (java.net.HttpURLConnection) url1.openConnection();
            crumbConn.setRequestMethod("GET");
            crumbConn.setConnectTimeout(10000);
            crumbConn.setReadTimeout(10000);
            crumbConn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            String crumbFieldName = null, crumbValue = null, sessionCookie = null;
            if (crumbConn.getResponseCode() == 200) {
                String setCookie = crumbConn.getHeaderField("Set-Cookie");
                if (setCookie != null) {
                    for (String cookie : setCookie.split(";")) {
                        cookie = cookie.trim();
                        if (cookie.startsWith("JSESSIONID")) {
                            sessionCookie = cookie;
                            break;
                        }
                    }
                }
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(crumbConn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                        response.append(line);
                    String json = response.toString();
                    int crumbIndex = json.indexOf("\"crumb\":\""),
                            fieldIndex = json.indexOf("\"crumbRequestField\":\"");
                    if (crumbIndex > 0 && fieldIndex > 0) {
                        crumbValue = json.substring(crumbIndex + 9, json.indexOf("\"", crumbIndex + 9));
                        crumbFieldName = json.substring(fieldIndex + 21, json.indexOf("\"", fieldIndex + 21));
                    }
                }
            }
            crumbConn.disconnect();
            crumbConn = null;
            String groovyUrl = jenkinsUrl + "/scriptText";
            java.net.URL url2 = new java.net.URL(groovyUrl);
            scriptConn = (java.net.HttpURLConnection) url2.openConnection();
            scriptConn.setRequestMethod("POST");
            scriptConn.setDoOutput(true);
            scriptConn.setConnectTimeout(30000);
            scriptConn.setReadTimeout(30000);
            scriptConn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            scriptConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            if (sessionCookie != null)
                scriptConn.setRequestProperty("Cookie", sessionCookie);
            if (crumbFieldName != null && crumbValue != null)
                scriptConn.setRequestProperty(crumbFieldName, crumbValue);
            String postData = "script=" + java.net.URLEncoder.encode(groovyScript, "UTF-8");
            try (java.io.OutputStream os = scriptConn.getOutputStream()) {
                os.write(postData.getBytes(StandardCharsets.UTF_8));
            }
            int responseCode = scriptConn.getResponseCode();
            if (responseCode == 200) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(scriptConn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                        response.append(line).append("\n");
                    String r = response.toString();
                    logger.info("Groovy脚本执行结果: {}", r);
                    return r.contains("successfully") || r.contains("already exists");
                }
            } else {
                logger.error("Groovy脚本执行失败: HTTP {}", responseCode);
                return false;
            }
        } catch (Exception e) {
            logger.error("执行Groovy脚本失败: {}", e.getMessage(), e);
            return false;
        } finally {
            if (crumbConn != null)
                crumbConn.disconnect();
            if (scriptConn != null)
                scriptConn.disconnect();
        }
    }

    private String escapeGroovyString(String input) {
        if (input == null)
            return "";
        return input.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n").replace("\r",
                "\\r");
    }

    private void saveCredentialsConfig(Long masterId, JenkinsMasterDeployConfig config) {
        try {
            Map<String, Object> credConfig = new HashMap<>();
            if (config.getGitCredentials() != null) {
                List<Map<String, String>> gitCreds = new ArrayList<>();
                for (JenkinsMasterDeployConfig.GitCredential cred : config.getGitCredentials()) {
                    Map<String, String> m = new HashMap<>();
                    m.put("id", cred.getId());
                    m.put("description", cred.getDescription());
                    m.put("username", cred.getUsername());
                    gitCreds.add(m);
                }
                credConfig.put("gitCredentials", gitCreds);
            }
            if (config.getHarborCredentials() != null) {
                List<Map<String, String>> harborCreds = new ArrayList<>();
                for (JenkinsMasterDeployConfig.HarborCredential cred : config.getHarborCredentials()) {
                    Map<String, String> m = new HashMap<>();
                    m.put("id", cred.getId());
                    m.put("description", cred.getDescription());
                    m.put("url", cred.getUrl());
                    m.put("username", cred.getUsername());
                    harborCreds.add(m);
                }
                credConfig.put("harborCredentials", harborCreds);
            }
            if (config.getSshCredentials() != null) {
                List<Map<String, String>> sshCreds = new ArrayList<>();
                for (JenkinsMasterDeployConfig.SSHCredential cred : config.getSshCredentials()) {
                    Map<String, String> m = new HashMap<>();
                    m.put("id", cred.getId());
                    m.put("description", cred.getDescription());
                    m.put("username", cred.getUsername());
                    sshCreds.add(m);
                }
                credConfig.put("sshCredentials", sshCreds);
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String credConfigJson = mapper.writeValueAsString(credConfig);
            jenkinsMasterMapper.updateCredentialsConfig(masterId, credConfigJson);
            logger.info("凭证配置已保存到数据库, masterId: {}", masterId);
        } catch (Exception e) {
            logger.error("保存凭证配置失败: {}", e.getMessage(), e);
        }
    }

    // ==================== Node管理 ====================

    @Override
    public Map<String, Object> createNodeOnMaster(Long masterId, String nodeName, String workDir, String labels) {
        Map<String, Object> result = new HashMap<>();
        JenkinsMaster master = getMasterById(masterId);
        try {
            String jenkinsUrl = String.format("http://%s:%d", master.getHost(), master.getJenkins_port());
            String adminUser = master.getAdmin_username();
            String encryptedPassword = master.getEncrypted_admin_password();
            if (encryptedPassword == null || encryptedPassword.isEmpty()) {
                result.put("success", false);
                result.put("message", "Jenkins Master管理员密码未配置");
                result.put("nodeName", nodeName);
                return result;
            }
            String adminPassword = decryptPassword(encryptedPassword);
            if (!testJenkinsConnection(jenkinsUrl, adminUser, adminPassword)) {
                result.put("success", false);
                result.put("message", "无法连接到Jenkins Master: " + jenkinsUrl);
                result.put("nodeName", nodeName);
                return result;
            }
            String nodeJson = buildNodeConfigJson(nodeName, workDir, labels);
            boolean created = createJenkinsNode(jenkinsUrl, adminUser, adminPassword, nodeName, nodeJson);
            if (created) {
                Thread.sleep(1000);
                String secret = fetchNodeSecret(jenkinsUrl, adminUser, adminPassword, nodeName);
                result.put("success", true);
                result.put("message", secret != null && !secret.isEmpty() ? "Node创建成功" : "Node创建成功，但获取Secret失败");
                result.put("nodeName", nodeName);
                result.put("secret", secret);
                result.put("jenkinsUrl", jenkinsUrl);
            } else {
                result.put("success", false);
                result.put("message", "Node创建失败");
                result.put("nodeName", nodeName);
                result.put("jenkinsUrl", jenkinsUrl);
            }
        } catch (Exception e) {
            logger.error("创建Node失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "创建Node异常: " + e.getMessage());
            result.put("nodeName", nodeName);
        }
        return result;
    }

    private boolean testJenkinsConnection(String jenkinsUrl, String user, String password) {
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(jenkinsUrl + "/api/json");
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            String encodedAuth = Base64.getEncoder()
                    .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            logger.error("测试Jenkins连接失败: {}", e.getMessage());
            return false;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    @Override
    public String getNodeSecret(Long masterId, String nodeName) {
        JenkinsMaster master = getMasterById(masterId);
        try {
            String jenkinsUrl = String.format("http://%s:%d", master.getHost(), master.getJenkins_port());
            return fetchNodeSecret(jenkinsUrl, master.getAdmin_username(),
                    decryptPassword(master.getEncrypted_admin_password()), nodeName);
        } catch (Exception e) {
            logger.error("获取Node Secret失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private String buildNodeConfigJson(String nodeName, String workDir, String labels) {
        return "{\"name\": \"" + nodeName
                + "\",\"nodeDescription\": \"Auto-created by AIops CLM\",\"numExecutors\": 2,\"remoteFS\": \"" + workDir
                +
                "\",\"labelString\": \"" + (labels != null ? labels : "") + "\",\"mode\": \"NORMAL\"," +
                "\"retentionStrategy\": {\"stapler-class\": \"hudson.slaves.RetentionStrategy$Always\"}," +
                "\"nodeProperties\": {\"stapler-class-bag\": \"true\"}," +
                "\"launcher\": {\"stapler-class\": \"hudson.slaves.JNLPLauncher\", \"workDirSettings\": {\"disabled\": false, \"internalDir\": \"remoting\", \"failIfWorkDirIsMissing\": false}}}";
    }

    private boolean createJenkinsNode(String jenkinsUrl, String user, String password, String nodeName,
            String nodeJson) {
        if (checkNodeExists(jenkinsUrl, user, password, nodeName)) {
            logger.info("Node {} 已存在，跳过创建", nodeName);
            return true;
        }
        return createNodeViaGroovy(jenkinsUrl, user, password, nodeName, nodeJson);
    }

    private boolean createNodeViaGroovy(String jenkinsUrl, String user, String password, String nodeName,
            String nodeJson) {
        java.net.HttpURLConnection crumbConn = null, scriptConn = null;
        try {
            String encodedAuth = Base64.getEncoder()
                    .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
            crumbConn = (java.net.HttpURLConnection) new java.net.URL(jenkinsUrl + "/crumbIssuer/api/json")
                    .openConnection();
            crumbConn.setRequestMethod("GET");
            crumbConn.setConnectTimeout(10000);
            crumbConn.setReadTimeout(10000);
            crumbConn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            String crumbFieldName = null, crumbValue = null, sessionCookie = null;
            if (crumbConn.getResponseCode() == 200) {
                String setCookie = crumbConn.getHeaderField("Set-Cookie");
                if (setCookie != null) {
                    for (String cookie : setCookie.split(";")) {
                        cookie = cookie.trim();
                        if (cookie.startsWith("JSESSIONID")) {
                            sessionCookie = cookie;
                            break;
                        }
                    }
                }
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(crumbConn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                        resp.append(line);
                    String json = resp.toString();
                    int ci = json.indexOf("\"crumb\":\""), fi = json.indexOf("\"crumbRequestField\":\"");
                    if (ci > 0 && fi > 0) {
                        crumbValue = json.substring(ci + 9, json.indexOf("\"", ci + 9));
                        crumbFieldName = json.substring(fi + 21, json.indexOf("\"", fi + 21));
                    }
                }
            }
            crumbConn.disconnect();
            crumbConn = null;
            String workDir = "/opt/jenkins-agent", labels = "";
            if (nodeJson.contains("\"remoteFS\":\"")) {
                int s = nodeJson.indexOf("\"remoteFS\":\"") + 12, e = nodeJson.indexOf("\"", s);
                if (e > s)
                    workDir = nodeJson.substring(s, e);
            }
            if (nodeJson.contains("\"labelString\":\"")) {
                int s = nodeJson.indexOf("\"labelString\":\"") + 15, e = nodeJson.indexOf("\"", s);
                if (e > s)
                    labels = nodeJson.substring(s, e);
            }
            String groovyScript = String.format(
                    "import jenkins.model.*\nimport hudson.model.*\nimport hudson.slaves.*\n\ndef jenkins = Jenkins.instance\ndef nodeName = '%s'\ndef remoteFS = '%s'\ndef labels = '%s'\n\n"
                            +
                            "if (jenkins.getNode(nodeName) != null) { println 'Node already exists: ' + nodeName; return }\n\n"
                            +
                            "def launcher = new JNLPLauncher(true)\ndef node = new DumbSlave(nodeName, remoteFS, launcher)\n"
                            +
                            "node.setNumExecutors(2)\nnode.setMode(Node.Mode.NORMAL)\nnode.setLabelString(labels)\nnode.setRetentionStrategy(new RetentionStrategy.Always())\n\n"
                            +
                            "jenkins.addNode(node)\nprintln 'Node created successfully: ' + nodeName\n",
                    nodeName.replace("'", "\\'"), workDir.replace("'", "\\'"), labels.replace("'", "\\'"));
            scriptConn = (java.net.HttpURLConnection) new java.net.URL(jenkinsUrl + "/scriptText").openConnection();
            scriptConn.setRequestMethod("POST");
            scriptConn.setDoOutput(true);
            scriptConn.setConnectTimeout(30000);
            scriptConn.setReadTimeout(30000);
            scriptConn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            scriptConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            if (sessionCookie != null)
                scriptConn.setRequestProperty("Cookie", sessionCookie);
            if (crumbFieldName != null && crumbValue != null)
                scriptConn.setRequestProperty(crumbFieldName, crumbValue);
            try (java.io.OutputStream os = scriptConn.getOutputStream()) {
                os.write(("script=" + java.net.URLEncoder.encode(groovyScript, "UTF-8"))
                        .getBytes(StandardCharsets.UTF_8));
            }
            if (scriptConn.getResponseCode() == 200) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(scriptConn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                        resp.append(line).append("\n");
                    String r = resp.toString();
                    logger.info("Groovy脚本执行结果: {}", r);
                    if (r.contains("Node created successfully") || r.contains("Node already exists"))
                        return true;
                    if (r.contains("Exception") || r.contains("Error")) {
                        logger.error("Groovy脚本执行错误: {}", r);
                        return false;
                    }
                    return true;
                }
            } else {
                logger.error("Groovy创建Node失败: HTTP {}", scriptConn.getResponseCode());
                return false;
            }
        } catch (Exception e) {
            logger.error("通过Groovy创建Node失败: {}", e.getMessage(), e);
            return false;
        } finally {
            if (crumbConn != null)
                crumbConn.disconnect();
            if (scriptConn != null)
                scriptConn.disconnect();
        }
    }

    private boolean checkNodeExists(String jenkinsUrl, String user, String password, String nodeName) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(
                    jenkinsUrl + "/computer/" + java.net.URLEncoder.encode(nodeName, "UTF-8") + "/api/json")
                    .openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    private String getJenkinsCrumb(String jenkinsUrl, String user, String password) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(jenkinsUrl + "/crumbIssuer/api/json").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
            if (conn.getResponseCode() == 200) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                        resp.append(line);
                    String json = resp.toString();
                    int ci = json.indexOf("\"crumb\":\""), fi = json.indexOf("\"crumbRequestField\":\"");
                    if (ci > 0 && fi > 0)
                        return json.substring(fi + 21, json.indexOf("\"", fi + 21)) + ":"
                                + json.substring(ci + 9, json.indexOf("\"", ci + 9));
                }
            }
        } catch (Exception e) {
            logger.warn("获取Jenkins Crumb失败: {}", e.getMessage());
        } finally {
            if (conn != null)
                conn.disconnect();
        }
        return null;
    }

    private String fetchNodeSecret(String jenkinsUrl, String user, String password, String nodeName) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(
                    jenkinsUrl + "/computer/" + java.net.URLEncoder.encode(nodeName, "UTF-8") + "/slave-agent.jnlp")
                    .openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
            if (conn.getResponseCode() == 200) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                        resp.append(line);
                    String content = resp.toString();
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                            .compile("<argument>([a-f0-9]{64})</argument>").matcher(content);
                    if (matcher.find())
                        return matcher.group(1);
                }
            }
            return fetchNodeSecretViaGroovy(jenkinsUrl, user, password, nodeName);
        } catch (Exception e) {
            logger.error("获取Node Secret失败: {}", e.getMessage(), e);
            return null;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    private String fetchNodeSecretViaGroovy(String jenkinsUrl, String user, String password, String nodeName) {
        java.net.HttpURLConnection crumbConn = null, scriptConn = null;
        try {
            String encodedAuth = Base64.getEncoder()
                    .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
            crumbConn = (java.net.HttpURLConnection) new java.net.URL(jenkinsUrl + "/crumbIssuer/api/json")
                    .openConnection();
            crumbConn.setRequestMethod("GET");
            crumbConn.setConnectTimeout(10000);
            crumbConn.setReadTimeout(10000);
            crumbConn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            String crumbFieldName = null, crumbValue = null, sessionCookie = null;
            if (crumbConn.getResponseCode() == 200) {
                String setCookie = crumbConn.getHeaderField("Set-Cookie");
                if (setCookie != null) {
                    for (String cookie : setCookie.split(";")) {
                        cookie = cookie.trim();
                        if (cookie.startsWith("JSESSIONID")) {
                            sessionCookie = cookie;
                            break;
                        }
                    }
                }
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(crumbConn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                        resp.append(line);
                    String json = resp.toString();
                    int ci = json.indexOf("\"crumb\":\""), fi = json.indexOf("\"crumbRequestField\":\"");
                    if (ci > 0 && fi > 0) {
                        crumbValue = json.substring(ci + 9, json.indexOf("\"", ci + 9));
                        crumbFieldName = json.substring(fi + 21, json.indexOf("\"", fi + 21));
                    }
                }
            }
            crumbConn.disconnect();
            crumbConn = null;
            scriptConn = (java.net.HttpURLConnection) new java.net.URL(jenkinsUrl + "/scriptText").openConnection();
            scriptConn.setRequestMethod("POST");
            scriptConn.setDoOutput(true);
            scriptConn.setConnectTimeout(30000);
            scriptConn.setReadTimeout(30000);
            scriptConn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            scriptConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            if (sessionCookie != null)
                scriptConn.setRequestProperty("Cookie", sessionCookie);
            if (crumbFieldName != null && crumbValue != null)
                scriptConn.setRequestProperty(crumbFieldName, crumbValue);
            String script = "println(jenkins.model.Jenkins.instance.getComputer('" + nodeName + "')?.getJnlpMac())";
            try (java.io.OutputStream os = scriptConn.getOutputStream()) {
                os.write(("script=" + java.net.URLEncoder.encode(script, "UTF-8")).getBytes(StandardCharsets.UTF_8));
            }
            if (scriptConn.getResponseCode() == 200) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(scriptConn.getInputStream(), StandardCharsets.UTF_8))) {
                    String secret = reader.readLine();
                    if (secret != null && secret.matches("[a-f0-9]{64}"))
                        return secret.trim();
                }
            }
        } catch (Exception e) {
            logger.error("通过Groovy获取Secret失败: {}", e.getMessage());
        } finally {
            if (crumbConn != null)
                crumbConn.disconnect();
            if (scriptConn != null)
                scriptConn.disconnect();
        }
        return null;
    }

    // ==================== 卸载 ====================

    @Override
    public Map<String, Object> uninstallJenkins(Long masterId) {
        Map<String, Object> result = new HashMap<>();
        JenkinsMaster master = getMasterById(masterId);
        Session session = null;
        try {
            String password = getSSHPassword(master);
            JSch jsch = new JSch();
            session = jsch.getSession(master.getUsername(), master.getHost(), master.getPort());
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(SSH_TIMEOUT);
            session.connect();
            StringBuilder output = new StringBuilder();
            output.append(executeCommand(session, "sudo systemctl stop jenkins 2>/dev/null || true", SSH_TIMEOUT));
            output.append(executeCommand(session, "sudo systemctl disable jenkins 2>/dev/null || true", SSH_TIMEOUT));
            output.append(executeCommand(session, "sudo rm -f /etc/systemd/system/jenkins.service", SSH_TIMEOUT));
            output.append(executeCommand(session, "sudo systemctl daemon-reload", SSH_TIMEOUT));
            output.append(executeCommand(session, "sudo rm -rf /opt/jenkins", SSH_TIMEOUT));
            output.append(executeCommand(session, "sudo rm -rf " + master.getJenkins_home(), SSH_TIMEOUT));
            result.put("success", true);
            result.put("message", "Jenkins已卸载");
            result.put("output", output.toString());
            jenkinsMasterMapper.updateStatus(masterId, "pending", "Jenkins已卸载\n" + output);
        } catch (Exception e) {
            logger.error("卸载Jenkins失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        } finally {
            if (session != null && session.isConnected())
                session.disconnect();
        }
        return result;
    }

    // ==================== 辅助方法 ====================

    private String executeCommand(Session session, String command, int timeout) {
        ChannelExec channel = null;
        StringBuilder output = new StringBuilder();
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();
            channel.connect(timeout);
            byte[] buffer = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int len = in.read(buffer);
                    if (len < 0)
                        break;
                    output.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                }
                while (err.available() > 0) {
                    int len = err.read(buffer);
                    if (len < 0)
                        break;
                    output.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                }
                if (channel.isClosed()) {
                    if (in.available() > 0 || err.available() > 0)
                        continue;
                    break;
                }
                Thread.sleep(100);
            }
        } catch (Exception e) {
            output.append("Error: ").append(e.getMessage());
        } finally {
            if (channel != null && channel.isConnected())
                channel.disconnect();
        }
        return output.toString();
    }

    private void uploadScript(Session session, String content, String remotePath) throws Exception {
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();
            String unixContent = content.replace("\r\n", "\n").replace("\r", "\n");
            try (ByteArrayInputStream bis = new ByteArrayInputStream(unixContent.getBytes(StandardCharsets.UTF_8))) {
                sftp.put(bis, remotePath);
            }
        } finally {
            if (sftp != null && sftp.isConnected())
                sftp.disconnect();
        }
    }

    private String detectOsType(String osInfo) {
        String info = osInfo.toLowerCase();
        if (info.contains("darwin") || info.contains("macos"))
            return "macos";
        else if (info.contains("windows") || info.contains("microsoft"))
            return "windows";
        return "linux";
    }

    /**
     * 统一获取Master的SSH密码
     * 1. 优先使用本地加密密码解密
     * 2. 如果本地无密码但有host_id，从HOM获取→加密→存储到本地DB（一次性修复，避免后续明文传输）
     */
    private String getSSHPassword(JenkinsMaster master) {
        // 1. 本地加密密码
        if (master.getEncrypted_password() != null && !master.getEncrypted_password().isEmpty()) {
            return decryptPassword(master.getEncrypted_password());
        }
        // 2. 从HOM获取，加密后持久化到本地DB（一次性操作）
        if (master.getHost_id() != null) {
            String homPassword = getHomHostPassword(master.getHost_id());
            if (homPassword != null && !homPassword.isEmpty()) {
                String encrypted = encryptPassword(homPassword);
                // 持久化到DB，后续不再需要调用HOM
                try {
                    jenkinsMasterMapper.updateEncryptedPassword(master.getId(), encrypted);
                    master.setEncrypted_password(encrypted);
                    logger.info("已从HOM同步并加密存储SSH密码, masterId={}", master.getId());
                } catch (Exception e) {
                    logger.warn("持久化HOM密码失败, masterId={}: {}", master.getId(), e.getMessage());
                }
                return homPassword;
            }
        }
        throw new RuntimeException("无法获取SSH密码: 本地未存储密码，请编辑Master重新关联HOM主机或手动输入密码");
    }

    private String encryptPassword(String password) {
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    private String decryptPassword(String encryptedPassword) {
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedPassword)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("密码解密失败", e);
        }
    }
}
