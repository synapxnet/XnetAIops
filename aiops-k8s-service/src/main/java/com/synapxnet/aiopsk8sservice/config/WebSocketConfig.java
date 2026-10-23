package com.synapxnet.aiopsk8sservice.config;

import com.synapxnet.aiopsk8sservice.websocket.K8sTerminalHandler;
import com.synapxnet.aiopsk8sservice.websocket.TerminalAccessService;
import com.synapxnet.aiopsk8sservice.websocket.TerminalHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "openxnet.k8s.ui-reader", havingValue = "false", matchIfMissing = true)
public class WebSocketConfig implements WebSocketConfigurer {

    private final K8sTerminalHandler terminalHandler;
    private final TerminalHandshakeInterceptor authorization;
    private final TerminalAccessService access;

    /** 为所有终端入口绑定执行授权与精确来源。Bind execution authorization and exact Origins to every terminal entry point. */
    public WebSocketConfig(K8sTerminalHandler terminalHandler, TerminalHandshakeInterceptor authorization, TerminalAccessService access) {
        this.terminalHandler = terminalHandler;
        this.authorization = authorization;
        this.access = access;
    }

    /** 拒绝无票据握手，不允许任意Origin。Reject ticketless handshakes and arbitrary Origins. */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalHandler, "/ws/terminal/**")
                .addInterceptors(authorization)
                .setAllowedOrigins(access.allowedOrigins());
    }
}
