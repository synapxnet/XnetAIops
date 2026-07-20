package com.synapxnet.aiopshomservice.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class SshService {

    private static final Logger log = LoggerFactory.getLogger(SshService.class);

    public Map<String, Object> testConnection(String host, int port, String user, String password) {
        Map<String, Object> result = new HashMap<>();
        Session session = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, port);
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(10000);
            session.connect();

            result.put("success", true);
            result.put("message", "Connection successful");

            // Collect basic system info
            String osInfo = executeCommand(session, "cat /etc/os-release 2>/dev/null | head -2 || uname -a");
            String cpuInfo = executeCommand(session, "nproc");
            String memInfo = executeCommand(session, "free -g | awk '/Mem:/{print $2}'");
            String diskInfo = executeCommand(session, "df -BG / | awk 'NR==2{print $2}'");
            String arch = executeCommand(session, "uname -m");

            result.put("osInfo", osInfo.trim());
            result.put("cpuCores", cpuInfo.trim());
            result.put("totalMemGb", memInfo.trim());
            result.put("totalDiskGb", diskInfo.trim().replace("G", ""));
            result.put("cpuArch", arch.trim());

        } catch (Exception e) {
            log.error("SSH connection failed to {}:{} - {}", host, port, e.getMessage());
            result.put("success", false);
            result.put("message", "Connection failed: " + e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        return result;
    }

    private String executeCommand(Session session, String command) {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
            channel.connect();

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString();
        } catch (Exception e) {
            log.warn("Command execution failed: {}", e.getMessage());
            return "";
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }
}
