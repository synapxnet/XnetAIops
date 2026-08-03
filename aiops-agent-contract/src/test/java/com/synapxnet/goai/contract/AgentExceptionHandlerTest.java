package com.synapxnet.goai.contract;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证公共 Agent 异常处理器不会被各服务的兜底异常处理器抢占。
 */
class AgentExceptionHandlerTest {

    /** 确认契约异常处理器始终使用 Spring 最高优先级。 */
    @Test
    void usesHighestAdvicePrecedence() {
        Order order = AgentExceptionHandler.class.getAnnotation(Order.class);

        assertNotNull(order);
        assertEquals(Ordered.HIGHEST_PRECEDENCE, order.value());
    }
}
