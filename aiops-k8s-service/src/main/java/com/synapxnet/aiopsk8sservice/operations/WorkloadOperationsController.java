/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 已授权原生运行证据网页适配。 Authorized native operations evidence for the Web workspace.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.operations;

import com.synapxnet.aiopsk8sservice.agent.AgentK8sToolController;
import com.synapxnet.aiopsk8sservice.common.Result;
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
@RequestMapping("/api/k8s/operations-workspace")
public class WorkloadOperationsController {
    private final OperationsReadAccess access;
    private final AgentK8sToolController reader;

    /** 注入受限身份校验和只读领域服务。 Injects scoped identity validation and the read-only native reader. */
    public WorkloadOperationsController(OperationsReadAccess access, AgentK8sToolController reader) {
        this.access = access;
        this.reader = reader;
    }

    /** 返回已认证组织的明确资源目录。 Returns only explicit resources for the authenticated organization. */
    @GetMapping
    public Result<OperationsReadAccess.Catalog> catalog(HttpServletRequest request) {
        return Result.success(access.catalog(access.authorizeWeb(request, "workload")));
    }

    /** 验证目录归属后读取一项证据，不发起任何写动作。 Reads one authorized native evidence record without any mutation. */
    @GetMapping("/evidence")
    public Result<OperationsReadAccess.Evidence> evidence(
            @RequestParam String resourceId, HttpServletRequest request) {
        OperationsReadAccess.AuthorizedScope scope = access.authorizeWeb(request, "workload");
        OperationsReadAccess.Resource resource = access.find(scope, resourceId);
        AgentK8sToolController.WorkloadEvidence data = reader.read(new AgentK8sToolController.WorkloadArguments(resource.clusterId(), resource.namespace(), resource.workloadKind(), resource.name(), 15));
        return Result.success(access.evidence(resource, data, data.resourceVersion(), null));
    }

    /** 为网页保留原生错误包络，并在响应头提供独立请求标识。 Preserves the native Web error envelope and supplies an independent request identifier in the response header. */
    @ExceptionHandler(AgentContractException.class)
    public ResponseEntity<Result<Void>> handleReadError(AgentContractException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .header("X-Request-Id", UUID.randomUUID().toString())
                .body(Result.error(exception.getHttpStatus(), exception.getMessage()));
    }
}
