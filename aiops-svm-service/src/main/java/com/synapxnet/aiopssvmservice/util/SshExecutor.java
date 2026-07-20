package com.synapxnet.aiopssvmservice.util;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Consumer;

public class SshExecutor {

    private static final Logger log = LoggerFactory.getLogger(SshExecutor.class);

    private static final int DEFAULT_CONNECT_TIMEOUT = 10000;
    private static final int DEFAULT_COMMAND_TIMEOUT = 1800000; // 30 minutes

    /**
     * Establish SSH connection with password or private key authentication.
     */
    public static Session connect(String host, int port, String user, String password, String privateKey, int timeoutMs) throws JSchException {
        JSch jsch = new JSch();
        if (privateKey != null && !privateKey.isEmpty()) {
            jsch.addIdentity("deploy-key", privateKey.getBytes(StandardCharsets.UTF_8), null, null);
        }

        Session session = jsch.getSession(user, host, port);
        if (privateKey == null || privateKey.isEmpty()) {
            session.setPassword(password);
        }

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.setTimeout(timeoutMs > 0 ? timeoutMs : DEFAULT_CONNECT_TIMEOUT);
        session.connect(timeoutMs > 0 ? timeoutMs : DEFAULT_CONNECT_TIMEOUT);
        return session;
    }

    public static Session connect(String host, int port, String user, String password, String privateKey) throws JSchException {
        return connect(host, port, user, password, privateKey, DEFAULT_CONNECT_TIMEOUT);
    }

    /**
     * Upload script content to remote path via SFTP.
     * Converts \r\n to \n to ensure scripts work on Linux.
     */
    public static void uploadScript(Session session, String content, String remotePath) throws JSchException, IOException {
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();
            String unixContent = content.replace("\r\n", "\n").replace("\r", "\n");
            try (ByteArrayInputStream bis = new ByteArrayInputStream(unixContent.getBytes(StandardCharsets.UTF_8))) {
                sftp.put(bis, remotePath);
            } catch (SftpException e) {
                throw new IOException("SFTP upload failed: " + e.getMessage(), e);
            }
        } finally {
            if (sftp != null && sftp.isConnected()) sftp.disconnect();
        }
    }

    /**
     * Execute a command with real-time line-by-line output streaming via callback.
     * Returns the exit code (0 = success).
     */
    public static int executeWithCallback(Session session, String command, int timeoutMs, Consumer<String> stdoutCallback, Consumer<String> stderrCallback) throws JSchException, IOException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);

        InputStream stdout = channel.getInputStream();
        InputStream stderr = channel.getErrStream();
        channel.connect(timeoutMs > 0 ? timeoutMs : DEFAULT_COMMAND_TIMEOUT);

        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stdoutCallback != null) stdoutCallback.accept(line);
                }
            } catch (IOException e) {
                log.debug("stdout stream ended: {}", e.getMessage());
            }
        }, "ssh-stdout-reader");

        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stderr, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stderrCallback != null) stderrCallback.accept(line);
                }
            } catch (IOException e) {
                log.debug("stderr stream ended: {}", e.getMessage());
            }
        }, "ssh-stderr-reader");

        stdoutThread.start();
        stderrThread.start();

        // Wait for channel to close
        long start = System.currentTimeMillis();
        int effectiveTimeout = timeoutMs > 0 ? timeoutMs : DEFAULT_COMMAND_TIMEOUT;
        while (!channel.isClosed()) {
            if (System.currentTimeMillis() - start > effectiveTimeout) {
                channel.disconnect();
                throw new IOException("Command timed out after " + effectiveTimeout + "ms");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Wait for reader threads to finish
        try {
            stdoutThread.join(5000);
            stderrThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int exitStatus = channel.getExitStatus();
        channel.disconnect();
        return exitStatus;
    }

    /**
     * Execute a command and return its full output as a string.
     */
    public static String executeCommand(Session session, String command, int timeoutMs) throws JSchException, IOException {
        StringBuilder output = new StringBuilder();
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);

        InputStream in = channel.getInputStream();
        InputStream err = channel.getErrStream();
        channel.connect(timeoutMs > 0 ? timeoutMs : DEFAULT_CONNECT_TIMEOUT);

        byte[] buffer = new byte[4096];
        long start = System.currentTimeMillis();
        int effectiveTimeout = timeoutMs > 0 ? timeoutMs : DEFAULT_COMMAND_TIMEOUT;

        while (true) {
            while (in.available() > 0) {
                int len = in.read(buffer);
                if (len < 0) break;
                output.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
            }
            while (err.available() > 0) {
                int len = err.read(buffer);
                if (len < 0) break;
                output.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
            }
            if (channel.isClosed()) {
                if (in.available() > 0 || err.available() > 0) continue;
                break;
            }
            if (System.currentTimeMillis() - start > effectiveTimeout) {
                channel.disconnect();
                throw new IOException("Command timed out after " + effectiveTimeout + "ms");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        channel.disconnect();
        return output.toString();
    }

    public static String executeCommand(Session session, String command) throws JSchException, IOException {
        return executeCommand(session, command, DEFAULT_CONNECT_TIMEOUT);
    }

    /**
     * Upload a script, make it executable, and execute it with streaming output.
     * Returns exit code (0 = success).
     */
    public static int uploadAndExecute(Session session, String scriptContent, String remotePath,
                                       int timeoutMs, Consumer<String> stdoutCallback, Consumer<String> stderrCallback)
            throws JSchException, IOException {
        uploadScript(session, scriptContent, remotePath);
        String command = "chmod +x " + remotePath + " && " + remotePath;
        return executeWithCallback(session, command, timeoutMs, stdoutCallback, stderrCallback);
    }

    /**
     * Safely disconnect a session.
     */
    public static void disconnect(Session session) {
        if (session != null && session.isConnected()) {
            try {
                session.disconnect();
            } catch (Exception e) {
                log.warn("Error disconnecting SSH session: {}", e.getMessage());
            }
        }
    }
}
