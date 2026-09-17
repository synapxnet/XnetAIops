/*
Copyright (C) 2026 Synapxnet. All rights reserved.
This file is Synapxnet Proprietary and Confidential. It is strictly
forbidden to copy, distribute, or use without explicit authorization.
Author: maoyo | Department: 研发部 | Date: 2026-09-13
Version: 1.0.0 | Security Level: INTERNAL
__version__: 1.0.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
__maintainer__: maoyo | __email__: synapxnet@gmail.com
*/

package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.websocket.TerminalAccessService;
import com.synapxnet.goai.contract.AgentContractException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 将已认证网页请求转换为不可缓存的终端连接票据。Convert authenticated web requests into non-cacheable terminal connection tickets. */
@RestController
@RequestMapping("/api/k8s/terminal")
public class TerminalTicketController {
    private final TerminalAccessService access;
    /** 注入终端授权与票据服务。Inject terminal authorization and ticket services. */
    public TerminalTicketController(TerminalAccessService access) { this.access = access; }
    /** 签发目标绑定的一次性票据，不执行任何终端命令。Issue a target-bound single-use ticket without executing terminal commands. */
    @PostMapping("/tickets")
    public ResponseEntity<Result<TerminalAccessService.Ticket>> issue(HttpServletRequest request, @RequestBody TerminalAccessService.Target target) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(access.issue(request, target)));
    }
    /** 保留实际HTTP失败状态及脱敏错误。Preserve actual HTTP failure status and redacted errors. */
    @ExceptionHandler(AgentContractException.class)
    public ResponseEntity<Result<Void>> denied(AgentContractException exception) {
        return ResponseEntity.status(exception.getHttpStatus()).cacheControl(CacheControl.noStore()).body(Result.error(exception.getHttpStatus(), exception.getMessage()));
    }
}
