package com.synapxnet.aiopsk8sservice.websocket;

import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class K8sTerminalHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(K8sTerminalHandler.class);

    private final K8sClientFactory clientFactory;
    private final Map<String, ExecWatch> execWatches = new ConcurrentHashMap<>();

    public K8sTerminalHandler(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = session.getUri().getPath();
        log.info("WebSocket terminal connection established: {}", path);

        // Parse path: /ws/terminal/{clusterId}/{namespace}/{podName}/{containerName}
        String[] parts = path.split("/");
        if (parts.length < 6) {
            session.sendMessage(new TextMessage("Invalid terminal path. Expected: /ws/terminal/{clusterId}/{namespace}/{podName}/{containerName}"));
            session.close();
            return;
        }

        try {
            Long clusterId = Long.parseLong(parts[3]);
            String namespace = parts[4];
            String podName = parts[5];
            String containerName = parts.length > 6 ? parts[6] : null;

            KubernetesClient client = clientFactory.getClient(clusterId);

            var podResource = client.pods()
                    .inNamespace(namespace)
                    .withName(podName);

            var containerResource = (containerName != null && !containerName.isEmpty())
                    ? podResource.inContainer(containerName)
                    : podResource;

            ExecWatch execWatch = containerResource
                    .redirectingInput()
                    .redirectingOutput()
                    .redirectingError()
                    .withTTY()
                    .usingListener(new ExecListener() {
                        @Override
                        public void onOpen() {
                            log.info("Exec session opened for pod {}/{}", namespace, podName);
                        }

                        @Override
                        public void onFailure(Throwable t, Response failureResponse) {
                            log.error("Exec session failed for pod {}/{}: {}", namespace, podName, t.getMessage());
                            try {
                                session.sendMessage(new TextMessage("\r\nConnection failed: " + t.getMessage() + "\r\n"));
                                session.close();
                            } catch (IOException e) {
                                log.error("Error sending failure message", e);
                            }
                        }

                        @Override
                        public void onClose(int code, String reason) {
                            log.info("Exec session closed for pod {}/{}: {} {}", namespace, podName, code, reason);
                            try {
                                session.close();
                            } catch (IOException e) {
                                log.error("Error closing WebSocket session", e);
                            }
                        }
                    })
                    .exec("sh", "-c", "if command -v bash >/dev/null 2>&1; then exec bash; else exec sh; fi");

            execWatches.put(session.getId(), execWatch);

            // Start reading output from the exec session
            new Thread(() -> {
                try {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    var inputStream = execWatch.getOutput();
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        if (session.isOpen()) {
                            String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                            session.sendMessage(new TextMessage(output));
                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    if (session.isOpen()) {
                        log.error("Error reading exec output: {}", e.getMessage());
                    }
                }
            }, "terminal-output-" + session.getId()).start();

            // Also read error stream
            new Thread(() -> {
                try {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    var errorStream = execWatch.getError();
                    while ((bytesRead = errorStream.read(buffer)) != -1) {
                        if (session.isOpen()) {
                            String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                            session.sendMessage(new TextMessage(output));
                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    if (session.isOpen()) {
                        log.debug("Error stream ended: {}", e.getMessage());
                    }
                }
            }, "terminal-error-" + session.getId()).start();

        } catch (NumberFormatException e) {
            session.sendMessage(new TextMessage("Invalid cluster ID"));
            session.close();
        } catch (Exception e) {
            log.error("Failed to establish terminal session", e);
            session.sendMessage(new TextMessage("Failed to connect: " + e.getMessage()));
            session.close();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ExecWatch execWatch = execWatches.get(session.getId());
        if (execWatch != null) {
            try {
                String payload = message.getPayload();
                // Handle resize message
                if (payload.startsWith("{\"type\":\"resize\"")) {
                    // Resize is handled by xterm.js fit addon on frontend
                    return;
                }
                OutputStream inputStream = execWatch.getInput();
                inputStream.write(payload.getBytes(StandardCharsets.UTF_8));
                inputStream.flush();
            } catch (IOException e) {
                log.error("Error writing to exec session: {}", e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket terminal connection closed: {}", session.getId());
        ExecWatch execWatch = execWatches.remove(session.getId());
        if (execWatch != null) {
            try {
                execWatch.close();
            } catch (Exception e) {
                log.warn("Error closing exec watch: {}", e.getMessage());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error: {}", exception.getMessage());
        ExecWatch execWatch = execWatches.remove(session.getId());
        if (execWatch != null) {
            try {
                execWatch.close();
            } catch (Exception e) {
                log.warn("Error closing exec watch after transport error: {}", e.getMessage());
            }
        }
    }
}
