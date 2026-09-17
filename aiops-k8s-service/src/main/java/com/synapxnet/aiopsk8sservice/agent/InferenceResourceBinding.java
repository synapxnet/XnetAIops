/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15
 * Version: 1.0.0 | Security Level: INTERNAL
 * __version__: 1.0.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
 * __maintainer__: maoyo | __email__: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.agent;

import com.synapxnet.goai.contract.AgentContractException;
import java.util.List;

/** 已登记的比赛推理目标关系，不以incident猜测物理身份；Register competition inference target relationships without inferring identity from incidents. */
record InferenceResourceBinding(String serviceUid, String deploymentUid, String workloadId, String gpuPoolId, boolean initiallyStable) {
    private static final List<InferenceResourceBinding> TARGETS = List.of(
            new InferenceResourceBinding("service_rec_inference", "deploy_recommendation_prod", "3/recommendation-prod/recommendation-inference", "3/gpu-prewarmed", false),
            new InferenceResourceBinding("service_risk_inference", "deploy_risk_prod", "3/risk-prod/risk-inference", "3/gpu-prewarmed", true));

    /** 查找显式服务与部署绑定，未知或错配目标拒绝；Resolve an explicit service/deployment binding and reject unknown or mismatched targets. */
    static InferenceResourceBinding forRead(String serviceUid, String deploymentUid) {
        return TARGETS.stream().filter(item -> item.serviceUid.equals(serviceUid) && item.deploymentUid.equals(deploymentUid))
                .findFirst().orElseThrow(() -> new AgentContractException(404, "RESOURCE_NOT_REGISTERED", "Inference target is not registered."));
    }

    /** 查找迁移或领域状态的规范服务身份；Resolve the canonical service identity for domain state and migration. */
    static InferenceResourceBinding forService(String serviceUid) {
        return TARGETS.stream().filter(item -> item.serviceUid.equals(serviceUid))
                .findFirst().orElseThrow(() -> new AgentContractException(404, "RESOURCE_NOT_REGISTERED", "Inference service is not registered."));
    }

    /** 当前比赛写工具只治理已登记推荐目标；Limit current competition write tools to the registered recommendation target. */
    static InferenceResourceBinding recommendation() {
        return TARGETS.get(0);
    }

    /** 返回审批合同使用的原规范资源ID集合；Return the existing canonical resource identifiers used by approval contracts. */
    List<String> resourceIds() {
        return List.of(gpuPoolId, runtimeId(), workloadId, trafficId(), workloadId + "/autoscaling");
    }

    /** 返回运行时资源身份；Return the runtime resource identity. */
    String runtimeId() { return deploymentUid + "/runtime"; }

    /** 返回流量资源身份；Return the traffic resource identity. */
    String trafficId() { return serviceUid + "/traffic"; }
}
