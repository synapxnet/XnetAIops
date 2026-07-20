package com.synapxnet.aiopsk8sservice.config;

import com.synapxnet.aiopsk8sservice.websocket.K8sTerminalHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final K8sTerminalHandler terminalHandler;

    public WebSocketConfig(K8sTerminalHandler terminalHandler) {
        this.terminalHandler = terminalHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalHandler, "/ws/terminal/**")
                .setAllowedOriginPatterns("*");
    }
}
