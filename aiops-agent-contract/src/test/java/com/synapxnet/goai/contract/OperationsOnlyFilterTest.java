/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 独立运行保障服务只读门禁回归。 Regression checks for the dedicated operations read-only gate.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.goai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class OperationsOnlyFilterTest {
    /** 只读端点允许进入控制器，其他路径和所有写入均被拒绝。 Allows evidence reads and denies all unrelated routes and writes. */
    @Test
    void allowsOnlyExactReadRoutes() throws Exception {
        OperationsOnlyFilter filter = new OperationsOnlyFilter(new ObjectMapper());
        for (String route : new String[]{"/api/mon/operations-workspace", "/api/svm/operations-workspace/evidence", "/api/k8s/operations-workspace"}) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("GET", route), new MockHttpServletResponse(), chain);
            assertNotNull(chain.getRequest());
        }
        for (String[] request : new String[][]{{"POST", "/api/mon/operations-workspace"}, {"DELETE", "/api/svm/services/1"}, {"GET", "/api/k8s/clusters"}, {"GET", "/api/mon/operations-workspace/other"}}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest(request[0], request[1]), response, chain);
            assertNull(chain.getRequest());
            assertTrue(response.getStatus() == 404 || response.getStatus() == 405);
        }
    }
}
