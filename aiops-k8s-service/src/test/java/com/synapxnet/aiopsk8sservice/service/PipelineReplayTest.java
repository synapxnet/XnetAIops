/* Copyright (C) 2026 Synapxnet. All rights reserved.
 * Synapxnet Proprietary and Confidential. No copying, distribution or use without authorization.
 * 重放归属和参数回归 / Replay ownership and parameter regression.
 * Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.service;
import com.synapxnet.aiopsk8sservice.entity.K8sPipeline;
import com.synapxnet.aiopsk8sservice.entity.K8sPipelineRun;
import com.synapxnet.aiopsk8sservice.mapper.K8sDevopsProjectMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sPipelineMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sPipelineRunMapper;
import com.synapxnet.aiopsk8sservice.service.impl.K8sPipelineRunServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PipelineReplayTest {
    /** 重放使用原参数，跨归属及运行中状态不能产生新执行。 Preserve parameters and reject cross-scope or running replays. */
    @Test void validatesOwnershipStateAndParameters() {
        var runs = mock(K8sPipelineRunMapper.class); var pipelines = mock(K8sPipelineMapper.class); var projects = mock(K8sDevopsProjectMapper.class);
        var service = spy(new K8sPipelineRunServiceImpl(runs, pipelines, projects));
        var pipeline = new K8sPipeline(); pipeline.setProjectId(1L);
        var prior = new K8sPipelineRun(); prior.setPipelineId(2L); prior.setStatus("failed"); prior.setParameters("{\"branch\":\"main\"}");
        when(pipelines.findById(2L)).thenReturn(pipeline); when(runs.findById(3L)).thenReturn(prior);
        var next = new K8sPipelineRun(); next.setId(4L);
        doReturn(next).when(service).triggerRun(1L, 2L, "manual-replay", Map.of("branch", "main"));
        assertSame(next, service.replayRun(1L, 2L, 3L, "manual-replay"));
        assertThrows(IllegalArgumentException.class, () -> service.replayRun(9L, 2L, 3L, "manual-replay"));
        prior.setPipelineId(9L);
        assertThrows(IllegalArgumentException.class, () -> service.replayRun(1L, 2L, 3L, "manual-replay"));
        prior.setPipelineId(2L); prior.setStatus("running");
        assertThrows(IllegalArgumentException.class, () -> service.replayRun(1L, 2L, 3L, "manual-replay"));
        prior.setStatus("failed"); prior.setParameters("{\"unsafe\":{\"nested\":true}}");
        assertThrows(IllegalArgumentException.class, () -> service.replayRun(1L, 2L, 3L, "manual-replay"));
        verify(service, times(1)).triggerRun(any(), any(), any(), any());
        verifyNoInteractions(projects);
    }
}
