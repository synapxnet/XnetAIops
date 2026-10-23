/*
Copyright (C) 2026 Synapxnet. All rights reserved.
This file is Synapxnet Proprietary and Confidential. It is strictly
forbidden to copy, distribute, or use without explicit authorization.
Author: maoyo | Department: 研发部 | Date: 2026-09-13
Version: 1.0.0 | Security Level: INTERNAL
__version__: 1.0.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
__maintainer__: maoyo | __email__: synapxnet@gmail.com
*/

package com.synapxnet.aiopsk8sservice.websocket;

import com.synapxnet.goai.contract.AgentContractException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** 在任何Pod执行前验证Origin及单次票据。Verify Origin and a single-use ticket before any pod execution. */
@Component
public class TerminalHandshakeInterceptor implements HandshakeInterceptor {
    private final TerminalAccessService access;
    /** 注入独立的终端授权边界。Inject the independent terminal authorization boundary. */
    public TerminalHandshakeInterceptor(TerminalAccessService access) { this.access = access; }
    /** 从子协议读取票据，严格拒绝缺失、重复和未知协议。Read tickets from subprotocols and reject missing, duplicate or unknown protocols. */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> attributes) {
        try {
            String header = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
            List<String> protocols = header == null ? List.of() : Arrays.stream(header.split(",")).map(String::trim).toList();
            if (protocols.size() != 2 || !protocols.contains("synapxnet-terminal")) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED); return false;
            }
            String credential = protocols.stream().filter(value -> value.startsWith("ticket.")).findFirst().orElse("");
            var grant = access.consume(credential.startsWith("ticket.") ? credential.substring(7) : null,
                    request.getHeaders().getOrigin(), request.getURI().getPath());
            attributes.put(TerminalAccessService.ATTRIBUTE, grant);
            return true;
        } catch (AgentContractException exception) {
            response.setStatusCode(HttpStatus.valueOf(exception.getHttpStatus()));
            return false;
        }
    }
    /** 握手后不记录可能包含凭据的原始请求。Do not log raw requests that might contain credentials after handshake. */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) { }
}
