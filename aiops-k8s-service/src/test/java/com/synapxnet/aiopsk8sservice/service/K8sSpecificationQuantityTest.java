/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 容器和配额原始单位契约。 Container and quota native-unit contracts.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.service;

import com.synapxnet.aiopsk8sservice.service.impl.*;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class K8sSpecificationQuantityTest {
    /** 核验三类真实生产容器映射，不把毫核和 Mi 单位丢失。 Checks all three production container mappers without losing millicores or Mi units. */
    @ParameterizedTest
    @ValueSource(strings = {"workload", "pod", "job"})
    @SuppressWarnings("unchecked")
    void preservesContainerUnits(String kind) throws Exception {
        K8sClientFactory factory = mock(K8sClientFactory.class);
        Object service = switch (kind) {
            case "pod" -> new K8sPodServiceImpl(factory);
            case "job" -> new K8sJobServiceImpl(factory);
            default -> new K8sWorkloadServiceImpl(factory);
        };
        Container container = new ContainerBuilder().withName("sample").withImage("sample:1")
                .withNewResources().addToRequests("cpu", new Quantity("100m"))
                .addToRequests("memory", new Quantity("70Mi"))
                .addToLimits("memory", new Quantity("170Mi")).endResources().build();
        Method method = kind.equals("pod")
                ? service.getClass().getDeclaredMethod("containerDetailMap", Container.class, PodStatus.class, boolean.class)
                : service.getClass().getDeclaredMethod("containerToMap", Container.class);
        method.setAccessible(true);
        Map<String, Object> result = (Map<String, Object>) (kind.equals("pod")
                ? method.invoke(service, container, new PodStatus(), false) : method.invoke(service, container));
        Map<String, Object> resources = (Map<String, Object>) result.get("resources");
        assertEquals(Map.of("cpu", "100m", "memory", "70Mi"), resources.get("requests"));
        assertEquals(Map.of("memory", "170Mi"), resources.get("limits"));
    }

    /** 配额读取同时保留 hard 和 used 的单位。 Preserves quantity units in both quota limits and usage. */
    @Test
    @SuppressWarnings("unchecked")
    void preservesQuotaUnits() {
        KubernetesClient client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        K8sClientFactory factory = mock(K8sClientFactory.class);
        when(factory.getClient(3L)).thenReturn(client);
        ResourceQuota quota = new ResourceQuotaBuilder().withNewMetadata().withName("quota").endMetadata()
                .withNewSpec().addToHard("requests.cpu", new Quantity("500m")).addToHard("requests.memory", new Quantity("2Gi")).endSpec()
                .withNewStatus().addToUsed("requests.cpu", new Quantity("100m")).addToUsed("requests.memory", new Quantity("70Mi")).endStatus().build();
        MixedOperation<ResourceQuota, ResourceQuotaList, Resource<ResourceQuota>> quotas = mock(MixedOperation.class);
        NonNamespaceOperation<ResourceQuota, ResourceQuotaList, Resource<ResourceQuota>> scoped = mock(NonNamespaceOperation.class);
        when(client.resourceQuotas()).thenReturn(quotas);
        when(quotas.inNamespace("test")).thenReturn(scoped);
        when(scoped.list()).thenReturn(new ResourceQuotaListBuilder().withItems(quota).build());
        Map<String, Object> result = new K8sNamespaceServiceImpl(factory).getResourceQuotas(3L, "test").get(0);
        assertEquals(Map.of("requests.cpu", "500m", "requests.memory", "2Gi"), result.get("hard"));
        assertEquals(Map.of("requests.cpu", "100m", "requests.memory", "70Mi"), result.get("used"));
    }
}
