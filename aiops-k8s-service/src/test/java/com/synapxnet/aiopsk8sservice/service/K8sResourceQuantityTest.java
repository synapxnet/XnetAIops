/* -*- coding: utf-8 -*-
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 真实 Quantity 与资源读取契约回归。 / Real Quantity and resource read contract regression.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.aiopsk8sservice.service.impl.K8sNodeServiceImpl;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.NodeListBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetricsList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.AnyNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class K8sResourceQuantityTest {
    /** 覆盖 Kubernetes 原始 CPU 度量单位，包含线上纳核样本。 / Cover native Kubernetes CPU units including the live nanocore sample. */
    @ParameterizedTest
    @CsvSource({
        "405713144n,0.405713144",
        "125000u,0.125",
        "1500m,1.5",
        "16,16",
        "0.5,0.5",
        "0,0",
        "20,20"
    })
    void convertsNativeCpuQuantitiesToCores(String encoded, double expected) {
        assertEquals(expected, K8sResourceQuantity.cpuCores(new Quantity(encoded)), 1e-12);
    }

    /** 核对 Fabric8 将后缀存入 format 的真实行为。 / Check the actual Fabric8 behavior that stores suffixes in format. */
    @Test
    void preservesSeparatedAmountAndFormat() {
        Quantity nanocores = new Quantity("405713144n");
        assertEquals("405713144", nanocores.getAmount());
        assertEquals("n", nanocores.getFormat());
        assertEquals(0.405713144, K8sResourceQuantity.cpuCores(new Quantity("405713144", "n")), 1e-12);
        assertEquals("1.5", K8sResourceQuantity.cpuCoresText(new Quantity("1500", "m")));
    }

    /** 内存和存储应保留二进制、十进制及字节本身的单位。 / Preserve binary, decimal and plain-byte memory/storage units. */
    @ParameterizedTest
    @CsvSource({
        "1Ki,1024",
        "128Mi,134217728",
        "64Gi,68719476736",
        "1Ti,1099511627776",
        "1G,1000000000",
        "1048576,1048576",
        "0,0"
    })
    void convertsMemoryQuantitiesToBytes(String encoded, long expected) {
        assertEquals(expected, K8sResourceQuantity.bytes(new Quantity(encoded)));
    }

    /** 缺少可选容量沿用零值；非法度量必须暴露为读取失败。 / Keep zero for absent optional capacity but reject malformed measurements. */
    @Test
    void rejectsMalformedAndNegativeQuantities() {
        assertEquals(0, K8sResourceQuantity.cpuCores(null));
        assertEquals(0, K8sResourceQuantity.bytes(null));
        assertThrows(RuntimeException.class, () -> K8sResourceQuantity.cpuCores(new Quantity("invalid", "n")));
        assertThrows(IllegalArgumentException.class, () -> K8sResourceQuantity.cpuCores(new Quantity("-1", "m")));
        assertThrows(ArithmeticException.class, () -> K8sResourceQuantity.bytes(new Quantity("999999999999999999999", "Gi")));
    }

    /** 真实指标 JSON 经生产服务聚合后必须是核数和字节。 / Aggregate real-shaped metrics JSON through the production service into cores and bytes. */
    @Test
    void aggregatesMetricsWithNativeQuantityUnits() throws Exception {
        KubernetesClient client = clusterClient("405713144n");
        K8sClientFactory factory = factory(client);
        Map<String, Object> metrics = new K8sMetricsService(factory).getClusterMetrics(3L);

        assertEquals(16.0, metrics.get("cpuCapacity"));
        assertEquals(0.405713144, (Double) metrics.get("cpuUsed"), 1e-12);
        assertEquals(64L * 1024 * 1024 * 1024, metrics.get("memoryCapacity"));
        assertEquals(128L * 1024 * 1024, metrics.get("memoryUsed"));
        assertEquals(300L * 1024 * 1024 * 1024, metrics.get("storageCapacity"));
        assertEquals(110, metrics.get("podCapacity"));
    }

    /** 排名使用相同换算，并由真实用量与容量计算百分比。 / Compute rankings from the same normalized usage and capacity. */
    @Test
    void computesObservedNanocoreRankingAsTwoPointFiveFourPercent() throws Exception {
        K8sMetricsService service = new K8sMetricsService(factory(clusterClient("405713144n")));
        Map<String, Object> rank = service.getNodeRanking(3L, "cpu", 5).get(0);
        assertEquals(0.405713144, (Double) rank.get("cpuUsed"), 1e-12);
        assertEquals(2.54, rank.get("cpuPercent"));
        assertEquals(0.20, rank.get("memoryPercent"));
    }

    /** 不通过截断到 100% 隐藏观测结果。 / Do not hide measurements by clamping them to one hundred percent. */
    @Test
    void retainsAboveCapacityMeasurements() throws Exception {
        K8sMetricsService service = new K8sMetricsService(factory(clusterClient("20")));
        assertEquals(125.0, service.getNodeRanking(3L, "cpu", 5).get(0).get("cpuPercent"));
    }

    /** 节点属性保留字段类型，并提供无歧义的内存字节字段。 / Preserve node field types and add unambiguous memory-byte fields. */
    @Test
    void exposesCoresAndExplicitBytesInNodeAttributes() throws Exception {
        KubernetesClient client = clusterClient("405713144n");
        Node node = clusterNode();
        node.getStatus().getCapacity().put("cpu", new Quantity("1500m"));
        when(client.nodes().list()).thenReturn(new NodeListBuilder().withItems(node).build());
        Map<String, Object> result = new K8sNodeServiceImpl(factory(client)).listNodes(3L).get(0);
        assertEquals("1.5", result.get("cpuCapacity"));
        assertEquals("1.25", result.get("cpuAllocatable"));
        assertEquals("64", result.get("memoryCapacity"));
        assertEquals(64L * 1024 * 1024 * 1024, result.get("memoryCapacityBytes"));
        assertEquals(60L * 1024 * 1024 * 1024, result.get("memoryAllocatableBytes"));
    }

    /** 创建生产字段形状的节点，不连接真实集群。 / Create a node with production field shapes without connecting to a cluster. */
    private Node clusterNode() {
        return new NodeBuilder()
            .withNewMetadata().withName("live-node").endMetadata()
            .withNewSpec().endSpec()
            .withNewStatus()
                .addToCapacity("cpu", new Quantity("16"))
                .addToCapacity("memory", new Quantity("64Gi"))
                .addToCapacity("pods", new Quantity("110"))
                .addToCapacity("ephemeral-storage", new Quantity("300Gi"))
                .addToAllocatable("cpu", new Quantity("1250m"))
                .addToAllocatable("memory", new Quantity("60Gi"))
            .endStatus().build();
    }

    /** 从原始 metrics-server JSON 反序列化真实 Quantity，再隔离网络调用。 / Deserialize real Quantities from metrics-server JSON and isolate network calls. */
    @SuppressWarnings("unchecked")
    private KubernetesClient clusterClient(String cpu) throws Exception {
        NodeMetrics metrics = new ObjectMapper().readValue(
            "{\"metadata\":{\"name\":\"live-node\"},\"usage\":{\"cpu\":\"" + cpu + "\",\"memory\":\"128Mi\"}}",
            NodeMetrics.class);
        NodeMetricsList metricList = new NodeMetricsList();
        metricList.setItems(List.of(metrics));
        KubernetesClient client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        when(client.nodes().list()).thenReturn(new NodeListBuilder().withItems(clusterNode()).build());
        when(client.top().nodes().metrics()).thenReturn(metricList);
        AnyNamespaceOperation<Pod, PodList, PodResource> pods = mock(AnyNamespaceOperation.class, RETURNS_SELF);
        when(client.pods().inAnyNamespace()).thenReturn(pods);
        when(pods.withField("spec.nodeName", "live-node")).thenReturn(pods);
        when(pods.list()).thenReturn(new PodListBuilder().build());
        return client;
    }

    /** 注入隔离客户端，测试真实服务逻辑。 / Inject the isolated client while testing real service logic. */
    private K8sClientFactory factory(KubernetesClient client) {
        K8sClientFactory factory = mock(K8sClientFactory.class);
        when(factory.getClient(3L)).thenReturn(client);
        return factory;
    }
}
