package com.synapxnet.aiopsclmservice.service.impl;

import com.jcraft.jsch.*;
import com.synapxnet.aiopsclmservice.entity.JenkinsNode;
import com.synapxnet.aiopsclmservice.entity.JenkinsNodeDeployConfig;
import com.synapxnet.aiopsclmservice.exception.DuplicateEntryException;
import com.synapxnet.aiopsclmservice.exception.EntityNotFoundException;
import com.synapxnet.aiopsclmservice.mapper.JenkinsNodeMapper;
import com.synapxnet.aiopsclmservice.service.JenkinsNodeService;
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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class JenkinsNodeServiceImpl implements JenkinsNodeService {

    private static final Logger logger = LoggerFactory.getLogger(JenkinsNodeServiceImpl.class);
    private static final String AES_KEY = "XnetMLopsJenkins";
    private static final int SSH_TIMEOUT = 60000;
    private static final int DEPLOY_TIMEOUT = 1800000;

    private final JenkinsNodeMapper jenkinsNodeMapper;
    private final ApplicationContext applicationContext;

    @Autowired
    public JenkinsNodeServiceImpl(JenkinsNodeMapper jenkinsNodeMapper, ApplicationContext applicationContext) {
        this.jenkinsNodeMapper = jenkinsNodeMapper;
        this.applicationContext = applicationContext;
    }

    @Override
    @Transactional
    public JenkinsNode createNode(JenkinsNode node) {
        if (jenkinsNodeMapper.countByName(node.getName(), null) > 0) {
            throw new DuplicateEntryException("节点名称已存在: " + node.getName());
        }
        if (jenkinsNodeMapper.countByHostAndPort(node.getHost(), node.getPort(), null) > 0) {
            throw new DuplicateEntryException("该主机和端口已配置: " + node.getHost() + ":" + node.getPort());
        }

        node.setUid(UUID.randomUUID().toString());
        node.setStatus("pending");

        if (node.getPassword() != null && !node.getPassword().isEmpty()) {
            node.setEncrypted_password(encryptPassword(node.getPassword()));
        }

        jenkinsNodeMapper.insert(node);
        return jenkinsNodeMapper.findById(node.getId())
                .orElseThrow(() -> new EntityNotFoundException("创建节点失败"));
    }

    @Override
    @Transactional
    public JenkinsNode updateNode(Long id, JenkinsNode node) {
        JenkinsNode existing = jenkinsNodeMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("节点不存在: " + id));

        if (jenkinsNodeMapper.countByName(node.getName(), id) > 0) {
            throw new DuplicateEntryException("节点名称已存在: " + node.getName());
        }
        if (jenkinsNodeMapper.countByHostAndPort(node.getHost(), node.getPort(), id) > 0) {
            throw new DuplicateEntryException("该主机和端口已配置: " + node.getHost() + ":" + node.getPort());
        }

        existing.setName(node.getName());
        existing.setHost(node.getHost());
        existing.setPort(node.getPort());
        existing.setUsername(node.getUsername());
        existing.setOs_type(node.getOs_type());
        existing.setJenkins_url(node.getJenkins_url());
        existing.setAgent_name(node.getAgent_name());
        existing.setWork_dir(node.getWork_dir());
        existing.setJava_version(node.getJava_version());
        existing.setPython_version(node.getPython_version());
        existing.setAgent_version(node.getAgent_version());
        existing.setLabels(node.getLabels());
        existing.setDescription(node.getDescription());
        existing.setUpdated_by(node.getUpdated_by());

        if (node.getPassword() != null && !node.getPassword().isEmpty()) {
            existing.setEncrypted_password(encryptPassword(node.getPassword()));
        }

        jenkinsNodeMapper.update(existing);
        return jenkinsNodeMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("更新节点失败"));
    }

    @Override
    @Transactional
    public void deleteNode(Long id) {
        if (jenkinsNodeMapper.findById(id).isEmpty()) {
            throw new EntityNotFoundException("节点不存在: " + id);
        }
        jenkinsNodeMapper.deleteById(id);
    }

    @Override
    public JenkinsNode getNodeById(Long id) {
        return jenkinsNodeMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("节点不存在: " + id));
    }

    @Override
    public JenkinsNode getNodeByUid(String uid) {
        return jenkinsNodeMapper.findByUid(uid)
                .orElseThrow(() -> new EntityNotFoundException("节点不存在: " + uid));
    }

    @Override
    public List<JenkinsNode> getAllNodes() {
        return jenkinsNodeMapper.findAll();
    }

    @Override
    public List<JenkinsNode> getNodesByStatus(String status) {
        return jenkinsNodeMapper.findByStatus(status);
    }

    @Override
    public Map<String, Object> testConnection(JenkinsNode node) {
        Map<String, Object> result = new HashMap<>();
        Session session = null;

        try {
            String password = node.getPassword();
            if (password == null && node.getEncrypted_password() != null) {
                password = decryptPassword(node.getEncrypted_password());
            }

            JSch jsch = new JSch();
            session = jsch.getSession(node.getUsername(), node.getHost(), node.getPort());
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(SSH_TIMEOUT);
            session.connect();

            String osInfo = executeCommand(session, "uname -a 2>/dev/null || ver 2>nul || echo 'Unknown'");
            String hostname = executeCommand(session, "hostname");

            result.put("success", true);
            result.put("message", "连接成功");
            result.put("osInfo", osInfo.trim());
            result.put("hostname", hostname.trim());
            result.put("detectedOsType", detectOsType(osInfo));

        } catch (JSchException e) {
            logger.error("SSH连接失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        } finally {
            if (session != null && session.isConnected())
                session.disconnect();
        }

        return result;
    }

    @Override
    public Map<String, Object> deployNode(Long nodeId, JenkinsNodeDeployConfig config) {
        Map<String, Object> result = new HashMap<>();
        JenkinsNode node = getNodeById(nodeId);

        node.setJenkins_url(config.getJenkinsUrl());
        node.setAgent_name(config.getAgentName());
        node.setWork_dir(config.getWorkDir());
        node.setJava_version(config.getJavaVersion());
        node.setPython_version(config.getPythonVersion());
        node.setAgent_version(config.getAgentVersion());
        node.setLabels(config.getLabels());
        node.setStatus("deploying");
        jenkinsNodeMapper.update(node);

        result.put("success", true);
        result.put("message", "部署任务已启动");
        result.put("nodeId", nodeId);

        JenkinsNodeServiceImpl proxy = applicationContext.getBean(JenkinsNodeServiceImpl.class);
        proxy.deployNodeAsync(nodeId, config);

        return result;
    }

    @Async
    public void deployNodeAsync(Long nodeId, JenkinsNodeDeployConfig config) {
        Session session = null;
        StringBuilder deployLog = new StringBuilder();

        JenkinsNode node = jenkinsNodeMapper.findById(nodeId)
                .orElseThrow(() -> new RuntimeException("节点不存在: " + nodeId));

        try {
            String password = decryptPassword(node.getEncrypted_password());

            JSch jsch = new JSch();
            session = jsch.getSession(node.getUsername(), node.getHost(), node.getPort());
            session.setPassword(password);

            Properties sshConfig = new Properties();
            sshConfig.put("StrictHostKeyChecking", "no");
            session.setConfig(sshConfig);
            session.setTimeout(SSH_TIMEOUT);
            session.connect();

            deployLog.append("=== 开始部署 Jenkins Agent ===\n");
            deployLog.append("时间: ").append(new Date()).append("\n\n");

            String script = getDeployScript(node.getOs_type(), config);

            String scriptPath = "/tmp/jenkins_deploy_" + System.currentTimeMillis() + ".sh";
            if ("windows".equals(node.getOs_type())) {
                scriptPath = "C:\\Temp\\jenkins_deploy_" + System.currentTimeMillis() + ".ps1";
            }

            deployLog.append("上传部署脚本到: ").append(scriptPath).append("\n");
            uploadScript(session, script, scriptPath);

            String executeCmd;
            if ("windows".equals(node.getOs_type())) {
                executeCmd = "powershell -ExecutionPolicy Bypass -File " + scriptPath;
            } else {
                executeCmd = "chmod +x " + scriptPath + " && " + scriptPath;
            }

            deployLog.append("执行部署脚本...\n");
            String output = executeCommand(session, executeCmd, DEPLOY_TIMEOUT);
            deployLog.append(output);

            if (output.contains("Jenkins Agent 部署完成") || output.contains("Jenkins Agent 安装完成")
                    || output.contains("Installation completed")) {
                node.setStatus("deployed");
                deployLog.append("\n=== 部署成功 ===\n");
            } else {
                node.setStatus("failed");
                deployLog.append("\n=== 部署失败 ===\n");
            }

        } catch (Exception e) {
            logger.error("部署失败: {}", e.getMessage(), e);
            node.setStatus("failed");
            deployLog.append("\n=== 部署失败 ===\n");
            deployLog.append("错误: ").append(e.getMessage()).append("\n");
        } finally {
            if (session != null && session.isConnected())
                session.disconnect();
            node.setDeploy_log(deployLog.toString());
            jenkinsNodeMapper.updateStatus(node.getId(), node.getStatus(), deployLog.toString());
        }
    }

    @Override
    public String getDeployScript(String osType, JenkinsNodeDeployConfig config) {
        try {
            String templatePath;
            switch (osType.toLowerCase()) {
                case "macos":
                    templatePath = "scripts/jenkins-agent-macos.sh";
                    break;
                case "windows":
                    templatePath = "scripts/jenkins-agent-windows.ps1";
                    break;
                case "linux":
                default:
                    templatePath = "scripts/jenkins-agent-linux.sh";
                    break;
            }

            ClassPathResource resource = new ClassPathResource(templatePath);
            String template;
            try (InputStream inputStream = resource.getInputStream()) {
                byte[] bytes = FileCopyUtils.copyToByteArray(inputStream);
                template = new String(bytes, StandardCharsets.UTF_8);
            }

            template = template.replace("${JENKINS_URL}",
                    config.getJenkinsUrl() != null ? config.getJenkinsUrl() : "http://jenkins:8080");
            template = template.replace("${JENKINS_AGENT_NAME}", config.getAgentName() != null ? config.getAgentName()
                    : "agent-" + UUID.randomUUID().toString().substring(0, 8));
            template = template.replace("${JENKINS_WORK_DIR}",
                    config.getWorkDir() != null ? config.getWorkDir() : "/opt/jenkins-agent");
            template = template.replace("${JAVA_VERSION}",
                    config.getJavaVersion() != null ? config.getJavaVersion() : "11");
            template = template.replace("${PYTHON_VERSION}",
                    config.getPythonVersion() != null ? config.getPythonVersion() : "3.10");
            template = template.replace("${AGENT_VERSION}",
                    config.getAgentVersion() != null ? config.getAgentVersion() : "3261.v9c670a_4748a_9");
            template = template.replace("${JENKINS_SECRET}",
                    config.getJenkinsSecret() != null ? config.getJenkinsSecret() : "");
            template = template.replace("${LABELS}", config.getLabels() != null ? config.getLabels() : "");

            template = template.replace("${INSTALL_DOCKER}",
                    config.getInstallDocker() != null && config.getInstallDocker() ? "true" : "false");
            template = template.replace("${INSTALL_GIT}",
                    config.getInstallGit() != null && config.getInstallGit() ? "true" : "false");
            template = template.replace("${INSTALL_MAVEN}",
                    config.getInstallMaven() != null && config.getInstallMaven() ? "true" : "false");
            template = template.replace("${MAVEN_VERSION}",
                    config.getMavenVersion() != null ? config.getMavenVersion() : "3.9.6");
            template = template.replace("${INSTALL_NODE}",
                    config.getInstallNode() != null && config.getInstallNode() ? "true" : "false");
            template = template.replace("${NODE_VERSION}",
                    config.getNodeVersion() != null ? config.getNodeVersion() : "18");

            template = template.replace("${USE_DOMESTIC_MIRROR}",
                    config.getUseDomesticMirror() != null && config.getUseDomesticMirror() ? "true" : "false");

            return template;

        } catch (IOException e) {
            logger.error("读取部署脚本模板失败: {}", e.getMessage());
            throw new RuntimeException("读取部署脚本模板失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> checkNodeStatus(Long nodeId) {
        Map<String, Object> result = new HashMap<>();
        JenkinsNode node = getNodeById(nodeId);
        Session session = null;

        try {
            String password = decryptPassword(node.getEncrypted_password());

            JSch jsch = new JSch();
            session = jsch.getSession(node.getUsername(), node.getHost(), node.getPort());
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(SSH_TIMEOUT);
            session.connect();

            String checkCmd = "ps aux | grep -v grep | grep jenkins-agent || pgrep -f jenkins-agent || echo 'not running'";
            if ("windows".equals(node.getOs_type())) {
                checkCmd = "tasklist | findstr jenkins";
            }

            String output = executeCommand(session, checkCmd);
            boolean isRunning = !output.contains("not running") && !output.trim().isEmpty();

            result.put("success", true);
            result.put("isRunning", isRunning);
            result.put("status", isRunning ? "running" : "stopped");
            result.put("processInfo", output.trim());

            jenkinsNodeMapper.updateHeartbeat(nodeId);

        } catch (Exception e) {
            logger.error("检查节点状态失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "检查状态失败: " + e.getMessage());
            result.put("status", "offline");
        } finally {
            if (session != null && session.isConnected())
                session.disconnect();
        }

        return result;
    }

    @Override
    public Map<String, Object> stopAgent(Long nodeId) {
        Map<String, Object> result = new HashMap<>();
        JenkinsNode node = getNodeById(nodeId);
        Session session = null;

        try {
            String password = decryptPassword(node.getEncrypted_password());

            JSch jsch = new JSch();
            session = jsch.getSession(node.getUsername(), node.getHost(), node.getPort());
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(SSH_TIMEOUT);
            session.connect();

            String stopCmd;
            if ("windows".equals(node.getOs_type())) {
                stopCmd = "taskkill /F /IM java.exe /FI \"WINDOWTITLE eq Jenkins*\" 2>nul || echo 'stopped'";
            } else if ("macos".equals(node.getOs_type())) {
                stopCmd = "launchctl unload ~/Library/LaunchAgents/com.jenkins.agent.plist 2>/dev/null; pkill -f jenkins-agent || echo 'stopped'";
            } else {
                stopCmd = "sudo systemctl stop jenkins-agent 2>/dev/null || pkill -f jenkins-agent || echo 'stopped'";
            }

            String output = executeCommand(session, stopCmd);
            result.put("success", true);
            result.put("message", "Agent已停止");
            result.put("output", output.trim());

        } catch (Exception e) {
            logger.error("停止Agent失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "停止失败: " + e.getMessage());
        } finally {
            if (session != null && session.isConnected())
                session.disconnect();
        }

        return result;
    }

    @Override
    public Map<String, Object> startAgent(Long nodeId) {
        Map<String, Object> result = new HashMap<>();
        JenkinsNode node = getNodeById(nodeId);
        Session session = null;

        try {
            String password = decryptPassword(node.getEncrypted_password());

            JSch jsch = new JSch();
            session = jsch.getSession(node.getUsername(), node.getHost(), node.getPort());
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(SSH_TIMEOUT);
            session.connect();

            String startCmd;
            if ("windows".equals(node.getOs_type())) {
                startCmd = "Start-Process -FilePath \"" + node.getWork_dir()
                        + "\\start-agent.bat\" -WindowStyle Hidden";
            } else if ("macos".equals(node.getOs_type())) {
                startCmd = "launchctl load ~/Library/LaunchAgents/com.jenkins.agent.plist 2>/dev/null || nohup "
                        + node.getWork_dir() + "/start-agent.sh &";
            } else {
                startCmd = "sudo systemctl start jenkins-agent 2>/dev/null || nohup " + node.getWork_dir()
                        + "/start-agent.sh &";
            }

            String output = executeCommand(session, startCmd);
            result.put("success", true);
            result.put("message", "Agent已启动");
            result.put("output", output.trim());

        } catch (Exception e) {
            logger.error("启动Agent失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "启动失败: " + e.getMessage());
        } finally {
            if (session != null && session.isConnected())
                session.disconnect();
        }

        return result;
    }

    @Override
    public Map<String, Object> uninstallAgent(Long nodeId) {
        Map<String, Object> result = new HashMap<>();
        JenkinsNode node = getNodeById(nodeId);
        Session session = null;

        try {
            String password = decryptPassword(node.getEncrypted_password());

            JSch jsch = new JSch();
            session = jsch.getSession(node.getUsername(), node.getHost(), node.getPort());
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(SSH_TIMEOUT);
            session.connect();

            stopAgent(nodeId);

            String uninstallCmd;
            if ("windows".equals(node.getOs_type())) {
                uninstallCmd = "Remove-Item -Recurse -Force \"" + node.getWork_dir() + "\" 2>$null; echo 'uninstalled'";
            } else {
                uninstallCmd = "rm -rf " + node.getWork_dir() + " && echo 'uninstalled'";
            }

            String output = executeCommand(session, uninstallCmd);

            node.setStatus("pending");
            jenkinsNodeMapper.updateStatus(node.getId(), "pending", "Agent已卸载");

            result.put("success", true);
            result.put("message", "Agent已卸载");
            result.put("output", output.trim());

        } catch (Exception e) {
            logger.error("卸载Agent失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "卸载失败: " + e.getMessage());
        } finally {
            if (session != null && session.isConnected())
                session.disconnect();
        }

        return result;
    }

    // ==================== 辅助方法 ====================

    private String executeCommand(Session session, String command) {
        return executeCommand(session, command, SSH_TIMEOUT);
    }

    private String executeCommand(Session session, String command, int timeout) {
        ChannelExec channel = null;
        StringBuilder output = new StringBuilder();

        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

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
            logger.error("执行命令失败: {}", e.getMessage());
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
        String lower = osInfo.toLowerCase();
        if (lower.contains("darwin") || lower.contains("macos") || lower.contains("mac os"))
            return "macos";
        else if (lower.contains("windows") || lower.contains("microsoft"))
            return "windows";
        return "linux";
    }

    private String encryptPassword(String password) {
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    private String decryptPassword(String encryptedBase64) {
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("密码解密失败", e);
        }
    }
}
