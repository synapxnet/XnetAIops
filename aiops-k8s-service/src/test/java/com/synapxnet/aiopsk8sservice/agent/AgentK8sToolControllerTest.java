/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 验证真实工作负载读取边界。 Verifies native workload evidence boundaries.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.agent;

import com.synapxnet.aiopsk8sservice.service.K8sMonitoringService;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.impl.K8sWorkloadServiceImpl;
import io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.DaemonSetList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.List;
import com.synapxnet.aiopsk8sservice.service.K8sPodService;
import com.synapxnet.aiopsk8sservice.service.K8sWorkloadService;
import com.synapxnet.goai.contract.AgentContractException;
import com.synapxnet.goai.contract.OperationsReadAccess;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentK8sToolControllerTest {
    /** 经实际领域映射验证generation不会冒充修订，保留真正resourceVersion。 Exercises the native mapper to prove generation cannot masquerade as a revision while preserving the actual resourceVersion. */
    @Test
    @SuppressWarnings("unchecked")
    void keepsDaemonSetGenerationDistinctFromRevision() {
        KubernetesClient client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        K8sClientFactory factory = mock(K8sClientFactory.class);
        when(factory.getClient(1L)).thenReturn(client);
        NonNamespaceOperation<DaemonSet, DaemonSetList, Resource<DaemonSet>> namespace = mock(NonNamespaceOperation.class);
        Resource<DaemonSet> resource = mock(Resource.class);
        when(client.apps().daemonSets().inNamespace("team")).thenReturn(namespace);
        when(namespace.withName("edge")).thenReturn(resource);
        when(resource.get())
                .thenReturn(new DaemonSetBuilder().withNewMetadata().withName("edge").withNamespace("team")
                        .withResourceVersion("rv-actual").withGeneration(42L).endMetadata()
                        .withNewSpec().withNewTemplate().withNewSpec().withContainers(List.of())
                        .endSpec().endTemplate().endSpec().build());
        AgentK8sToolController.WorkloadEvidence result = new AgentK8sToolController(
                new K8sWorkloadServiceImpl(factory), mock(K8sPodService.class), mock(K8sMonitoringService.class),
                mock(OperationsReadAccess.class)).read(
                        new AgentK8sToolController.WorkloadArguments("1", "team", "DaemonSet", "edge", 15));
        assertEquals("rv-actual", result.resourceVersion());
        assertNull(result.currentRevision());
    }

    /** 旧演练名称缺少真实资源时必须报错。 Old scenario names must fail when their native resource does not exist. */
    @Test
    void doesNotReplaceMissingWorkloadWithSyntheticEvidence() {
        K8sWorkloadService workloads = mock(K8sWorkloadService.class);
        AgentK8sToolController reader = new AgentK8sToolController(workloads, mock(K8sPodService.class),
                mock(K8sMonitoringService.class), mock(OperationsReadAccess.class));
        assertEquals("RESOURCE_NOT_FOUND", assertThrows(AgentContractException.class,
                () -> reader.read(new AgentK8sToolController.WorkloadArguments(
                        "3", "recommendation-prod", "Deployment", "recommendation-inference", 15))).getCode());
    }

    /** 没有selector不能扩大Pod范围，版本保留真实值。 Missing selectors never expand Pod scope, and versions remain source values. */
    @Test
    void preservesVersionWithoutBroadeningPodScope() {
        K8sWorkloadService workloads = mock(K8sWorkloadService.class);
        K8sPodService pods = mock(K8sPodService.class);
        K8sMonitoringService metrics = mock(K8sMonitoringService.class);
        when(workloads.getDeployment(1L, "team", "app")).thenReturn(Map.of("resourceVersion", "rv-81", "replicas", 2));
        when(metrics.getWorkloadMetrics(anyLong(), anyString(), anyString(), anyLong(), anyLong(), anyString()))
                .thenReturn(Map.of());
        AgentK8sToolController.WorkloadEvidence result = new AgentK8sToolController(workloads, pods, metrics,
                mock(OperationsReadAccess.class)).read(
                        new AgentK8sToolController.WorkloadArguments("1", "team", "Deployment", "app", 15));
        assertEquals("rv-81", result.resourceVersion());
        assertNull(result.metrics().cpu());
        assertTrue(result.warnings().contains("POD_SELECTOR_UNAVAILABLE"));
        verifyNoInteractions(pods);
        verifyNoInteractions(metrics);
    }
}
