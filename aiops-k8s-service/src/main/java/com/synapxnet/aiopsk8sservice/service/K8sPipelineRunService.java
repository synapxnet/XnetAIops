package com.synapxnet.aiopsk8sservice.service;

import com.synapxnet.aiopsk8sservice.entity.K8sPipelineRun;
import java.util.List;
import java.util.Map;

public interface K8sPipelineRunService {
    K8sPipelineRun triggerRun(Long projectId, Long pipelineId, String triggerUser, Map<String, String> params);
    List<K8sPipelineRun> listRuns(Long pipelineId);
    K8sPipelineRun getRun(Long runId);
    List<Map<String, Object>> getRunStages(Long projectId, Long pipelineId, Long runId);
    String getRunLog(Long projectId, Long pipelineId, Long runId);
    String getStageLog(Long projectId, Long pipelineId, Long runId, String nodeId);
    void stopRun(Long projectId, Long pipelineId, Long runId);
    void syncRunStatus(Long projectId, Long pipelineId, Long runId);
}
