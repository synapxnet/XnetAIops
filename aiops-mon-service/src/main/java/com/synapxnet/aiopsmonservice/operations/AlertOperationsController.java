/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 已授权原生运行证据网页适配。 Authorized native operations evidence for the Web workspace.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsmonservice.operations;

import com.synapxnet.aiopsmonservice.agent.AgentAlertToolController;
import com.synapxnet.aiopsmonservice.common.Result;
import com.synapxnet.goai.contract.OperationsReadAccess;
import com.synapxnet.goai.contract.AgentContractException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mon/operations-workspace")
public class AlertOperationsController {
    private final OperationsReadAccess access;
    private final AgentAlertToolController reader;

    /** 注入受限身份校验和只读领域服务。 Injects scoped identity validation and the read-only native reader. */
    public AlertOperationsController(OperationsReadAccess access, AgentAlertToolController reader) {
        this.access = access;
        this.reader = reader;
    }

    /** 返回已认证组织的明确资源目录。 Returns only explicit resources for the authenticated organization. */
    @GetMapping
    public Result<OperationsReadAccess.Catalog> catalog(HttpServletRequest request) {
        return Result.success(access.catalog(access.authorizeWeb(request, "alert")));
    }

    /** 验证目录归属后读取一项证据，不发起任何写动作。 Reads one authorized native evidence record without any mutation. */
    @GetMapping("/evidence")
    public Result<OperationsReadAccess.Evidence> evidence(
            @RequestParam String resourceId, HttpServletRequest request) {
        OperationsReadAccess.AuthorizedScope scope = access.authorizeWeb(request, "alert");
        OperationsReadAccess.Resource resource = access.find(scope, resourceId);
        AgentAlertToolController.AlertEvidence data = reader.read(resource.resourceUid());
        return Result.success(access.evidence(resource, data, data.sourceRecordVersion(), data.triggeredAt()));
    }

    /** 为网页保留原生错误包络，并在响应头提供独立请求标识。 Preserves the native Web error envelope and supplies an independent request identifier in the response header. */
    @ExceptionHandler(AgentContractException.class)
    public ResponseEntity<Result<Void>> handleReadError(AgentContractException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .header("X-Request-Id", UUID.randomUUID().toString())
                .body(Result.error(exception.getHttpStatus(), exception.getMessage()));
    }
}
