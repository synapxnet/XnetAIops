/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * Pod读取契约与安全失败回归。 Pod read contracts and safe failure regression.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-14 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 * __version__: 1.0.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
 * __maintainer__: maoyo | __email__: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.GlobalExceptionHandler;
import com.synapxnet.aiopsk8sservice.exception.ClusterConnectionException;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.impl.K8sPodServiceImpl;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class K8sPodReadContractTest {
    private static final String POD_PATH = "/api/k8s/clusters/1/namespaces/team/pods/app";
    private static final String PRIVATE_ERROR = "test-only-private-kubeconfig-marker";
    private K8sClientFactory factory;
    private PodResource resource;
    private MockMvc mvc;

    /** 在内存中连接真实Controller与Service，客户端保持Mock。 Wire the real controller and service in memory with a mocked Kubernetes client. */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        KubernetesClient client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        factory = mock(K8sClientFactory.class);
        resource = mock(PodResource.class, RETURNS_DEEP_STUBS);
        when(factory.getClient(1L)).thenReturn(client);
        NonNamespaceOperation<Pod, PodList, PodResource> namespace = mock(NonNamespaceOperation.class);
        when(client.pods().inNamespace("team")).thenReturn(namespace);
        when(namespace.withName("app")).thenReturn(resource);
        mvc = MockMvcBuilders.standaloneSetup(new K8sPodController(new K8sPodServiceImpl(factory)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    /** 选定容器的成功日志与行数参数保持原有契约。 Preserve successful log text and the selected container's tail limit. */
    @Test
    void returnsNamedContainerLogText() throws Exception {
        when(resource.inContainer("app").tailingLines(500).getLog()).thenReturn("first line\nsecond line\n");

        mvc.perform(get(POD_PATH + "/logs").param("container", "app").param("tailLines", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("first line\nsecond line\n"));

        verify(resource.inContainer("app").tailingLines(500)).getLog();
    }

    /** 正常空日志仍成功，未指定参数时保留1000行默认值。 Keep empty logs successful and retain the default 1000-line limit. */
    @Test
    void returnsEmptyLogsWithDefaultTailLimit() throws Exception {
        when(resource.tailingLines(1000).getLog()).thenReturn("");

        mvc.perform(get(POD_PATH + "/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(""));

        verify(resource.tailingLines(1000)).getLog();
        verify(resource, never()).inContainer(anyString());
    }

    /** 读取拒绝、缺失和上游故障必须使用失败包络且隐藏原始错误。 Return error envelopes for denied, missing and failed upstream reads without exposing their raw text. */
    @ParameterizedTest
    @ValueSource(ints = {403, 404, 500})
    void rejectsLogFailuresWithoutLeakingUpstreamText(int upstreamCode) throws Exception {
        when(resource.inContainer("app").tailingLines(500).getLog())
                .thenThrow(new KubernetesClientException(PRIVATE_ERROR, upstreamCode, null));

        mvc.perform(get(POD_PATH + "/logs").param("container", "app").param("tailLines", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(upstreamCode == 404 ? 404 : 500))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(content().string(not(containsString(PRIVATE_ERROR))))
                .andExpect(content().string(not(containsString("Error fetching logs:"))));
    }

    /** 创建客户端失败也只返回既有连接错误与安全文字。 Report client setup failures through the existing connection error with safe text. */
    @Test
    void sanitizesClientCreationFailure() throws Exception {
        when(factory.getClient(1L)).thenThrow(new ClusterConnectionException(PRIVATE_ERROR));

        mvc.perform(get(POD_PATH + "/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(content().string(not(containsString(PRIVATE_ERROR))));

        verifyNoInteractions(resource);
    }

    /** 主详情分别映射运行中的普通容器和已结束的初始化容器。 Map running regular containers and completed init containers independently in Pod details. */
    @Test
    void mapsRegularAndInitStatesInPodDetails() throws Exception {
        when(resource.get()).thenReturn(podWithContainerStates());

        mvc.perform(get(POD_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.containers[0].state").value("Running"))
                .andExpect(jsonPath("$.data.containers[0].ready").value(true))
                .andExpect(jsonPath("$.data.containers[0].restartCount").value(2))
                .andExpect(jsonPath("$.data.initContainers[0].state").value("Terminated"))
                .andExpect(jsonPath("$.data.initContainers[0].restartCount").value(3))
                .andExpect(jsonPath("$.data.initContainers[0].exitCode").value(0))
                .andExpect(jsonPath("$.data.initContainers[0].terminatedReason").value("Completed"));
    }

    /** 独立容器接口保留类别标识并输出初始化容器的真实状态。 Preserve container kind flags and init state in the standalone containers endpoint. */
    @Test
    void mapsRegularAndInitStatesInContainerList() throws Exception {
        when(resource.get()).thenReturn(podWithContainerStates());

        mvc.perform(get(POD_PATH + "/containers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("app"))
                .andExpect(jsonPath("$.data[0].isInit").value(false))
                .andExpect(jsonPath("$.data[0].state").value("Running"))
                .andExpect(jsonPath("$.data[1].name").value("prepare"))
                .andExpect(jsonPath("$.data[1].isInit").value(true))
                .andExpect(jsonPath("$.data[1].state").value("Terminated"))
                .andExpect(jsonPath("$.data[1].restartCount").value(3))
                .andExpect(jsonPath("$.data[1].exitCode").value(0));
    }

    /** 没有初始化状态时保持未知，不借用普通容器的状态。 Leave missing init status unknown rather than borrowing regular container status. */
    @Test
    void leavesMissingInitStatusUnknown() throws Exception {
        Pod pod = podWithContainerStates();
        pod.getStatus().setInitContainerStatuses(List.of());
        when(resource.get()).thenReturn(pod);

        mvc.perform(get(POD_PATH + "/containers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].state").value("Running"))
                .andExpect(jsonPath("$.data[1].isInit").value(true))
                .andExpect(jsonPath("$.data[1].state").doesNotExist())
                .andExpect(jsonPath("$.data[1].ready").doesNotExist())
                .andExpect(jsonPath("$.data[1].restartCount").doesNotExist());
    }

    /** 构造仅供内存测试使用的普通和初始化容器状态。 Build regular and init container states exclusively for in-memory testing. */
    private Pod podWithContainerStates() {
        return new PodBuilder().withNewMetadata().withName("app").withNamespace("team")
                .withCreationTimestamp("2026-09-14T00:00:00Z").endMetadata()
                .withNewSpec()
                .withContainers(new ContainerBuilder().withName("app").withImage("fixture.invalid/app:1").build())
                .withInitContainers(new ContainerBuilder().withName("prepare").withImage("fixture.invalid/init:1").build())
                .endSpec().withNewStatus().withPhase("Running")
                .withContainerStatuses(new ContainerStatusBuilder().withName("app").withReady(true)
                        .withRestartCount(2).withNewState().withNewRunning()
                        .withStartedAt("2026-09-14T00:01:00Z").endRunning().endState().build())
                .withInitContainerStatuses(new ContainerStatusBuilder().withName("prepare").withReady(false)
                        .withRestartCount(3).withNewState().withNewTerminated().withExitCode(0)
                        .withReason("Completed").endTerminated().endState().build())
                .endStatus().build();
    }
}
