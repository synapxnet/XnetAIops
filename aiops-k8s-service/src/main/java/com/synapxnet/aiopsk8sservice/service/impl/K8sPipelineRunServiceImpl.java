package com.synapxnet.aiopsk8sservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.aiopsk8sservice.entity.K8sDevopsProject;
import com.synapxnet.aiopsk8sservice.entity.K8sPipeline;
import com.synapxnet.aiopsk8sservice.entity.K8sPipelineRun;
import com.synapxnet.aiopsk8sservice.mapper.K8sDevopsProjectMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sPipelineMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sPipelineRunMapper;
import com.synapxnet.aiopsk8sservice.service.K8sPipelineRunService;
import com.synapxnet.aiopsk8sservice.util.JenkinsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class K8sPipelineRunServiceImpl implements K8sPipelineRunService {

    private static final Logger log = LoggerFactory.getLogger(K8sPipelineRunServiceImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final K8sPipelineRunMapper runMapper;
    private final K8sPipelineMapper pipelineMapper;
    private final K8sDevopsProjectMapper projectMapper;

    public K8sPipelineRunServiceImpl(K8sPipelineRunMapper runMapper,
                                      K8sPipelineMapper pipelineMapper,
                                      K8sDevopsProjectMapper projectMapper) {
        this.runMapper = runMapper;
        this.pipelineMapper = pipelineMapper;
        this.projectMapper = projectMapper;
    }

    private JenkinsClient getJenkinsClient(Long projectId) {
        K8sDevopsProject project = projectMapper.findById(projectId);
        if (project == null) throw new RuntimeException("DevOps项目不存在: " + projectId);
        return new JenkinsClient(project.getJenkinsUrl(), project.getJenkinsUser(), project.getJenkinsToken());
    }

    @Override
    public K8sPipelineRun triggerRun(Long projectId, Long pipelineId, String triggerUser, Map<String, String> params) {
        K8sPipeline pipeline = pipelineMapper.findById(pipelineId);
        if (pipeline == null) throw new RuntimeException("流水线不存在: " + pipelineId);
        if (!Objects.equals(projectId, pipeline.getProjectId())) throw new IllegalArgumentException("流水线不属于当前项目");

        JenkinsClient client = getJenkinsClient(projectId);

        // Trigger Jenkins build
        int queueId = client.triggerBuild(pipeline.getJenkinsJobName(), params);

        // Wait briefly for build number
        int buildNumber = -1;
        for (int i = 0; i < 10; i++) {
            buildNumber = client.getQueueItemBuildNumber(queueId);
            if (buildNumber > 0) break;
            try { Thread.sleep(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }

        if (buildNumber <= 0) {
            // Use next expected build number
            Integer maxRun = runMapper.getMaxRunNumber(pipelineId);
            buildNumber = (maxRun != null ? maxRun : 0) + 1;
        }

        // Create run record
        K8sPipelineRun run = new K8sPipelineRun();
        run.setPipelineId(pipelineId);
        run.setRunNumber(buildNumber);
        run.setStatus("queued");
        run.setTriggerType("manual");
        run.setTriggerUser(triggerUser);
        if (params != null && !params.isEmpty()) {
            try { run.setParameters(objectMapper.writeValueAsString(params)); } catch (Exception ignored) {}
        }
        run.setJenkinsBuildUrl(projectMapper.findById(projectId).getJenkinsUrl() +
                "/job/" + pipeline.getJenkinsJobName() + "/" + buildNumber);
        runMapper.insert(run);

        // Update pipeline last run
        pipelineMapper.updateLastRun(pipelineId, "running", LocalDateTime.now());

        return run;
    }

    /** 重跑仅复用已结束记录的参数，不允许跨项目或跨流水线引用。 Replay completed parameters only; reject cross-project and cross-pipeline references. */
    @Override
    public K8sPipelineRun replayRun(Long projectId, Long pipelineId, Long runId, String triggerUser) {
        K8sPipeline pipeline = pipelineMapper.findById(pipelineId);
        if (pipeline == null || !Objects.equals(projectId, pipeline.getProjectId()))
            throw new IllegalArgumentException("流水线不属于当前项目");
        K8sPipelineRun prior = getRun(runId);
        if (!Objects.equals(pipelineId, prior.getPipelineId())) throw new IllegalArgumentException("运行记录不属于当前流水线");
        if (!Set.of("success", "failed", "aborted", "unstable").contains(String.valueOf(prior.getStatus()).toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("仅已结束的运行可以重放");
        Map<String, String> parameters = new LinkedHashMap<>();
        if (prior.getParameters() != null && !prior.getParameters().isBlank()) {
            try {
                var parsed = objectMapper.readTree(prior.getParameters());
                if (!parsed.isObject()) throw new IllegalArgumentException("原运行参数无效");
                var fields = parsed.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    if (!field.getValue().isTextual()) throw new IllegalArgumentException("原运行参数必须是字符串");
                    parameters.put(field.getKey(), field.getValue().asText());
                }
            } catch (java.io.IOException invalid) { throw new IllegalArgumentException("原运行参数无效"); }
        }
        return triggerRun(projectId, pipelineId, triggerUser, parameters);
    }

    @Override
    public List<K8sPipelineRun> listRuns(Long pipelineId) {
        return runMapper.findByPipelineId(pipelineId);
    }

    @Override
    public K8sPipelineRun getRun(Long runId) {
        K8sPipelineRun run = runMapper.findById(runId);
        if (run == null) throw new RuntimeException("运行记录不存在: " + runId);
        return run;
    }

    @Override
    public List<Map<String, Object>> getRunStages(Long projectId, Long pipelineId, Long runId) {
        K8sPipelineRun run = getRun(runId);
        K8sPipeline pipeline = pipelineMapper.findById(pipelineId);
        if (pipeline == null) throw new RuntimeException("流水线不存在: " + pipelineId);

        JenkinsClient client = getJenkinsClient(projectId);
        return client.getBuildStages(pipeline.getJenkinsJobName(), run.getRunNumber());
    }

    @Override
    public String getRunLog(Long projectId, Long pipelineId, Long runId) {
        K8sPipelineRun run = getRun(runId);
        K8sPipeline pipeline = pipelineMapper.findById(pipelineId);
        if (pipeline == null) throw new RuntimeException("流水线不存在: " + pipelineId);

        JenkinsClient client = getJenkinsClient(projectId);
        return client.getBuildLog(pipeline.getJenkinsJobName(), run.getRunNumber());
    }

    @Override
    public String getStageLog(Long projectId, Long pipelineId, Long runId, String nodeId) {
        K8sPipelineRun run = getRun(runId);
        K8sPipeline pipeline = pipelineMapper.findById(pipelineId);
        if (pipeline == null) throw new RuntimeException("流水线不存在: " + pipelineId);

        JenkinsClient client = getJenkinsClient(projectId);
        return client.getStageLog(pipeline.getJenkinsJobName(), run.getRunNumber(), nodeId);
    }

    @Override
    public void stopRun(Long projectId, Long pipelineId, Long runId) {
        K8sPipelineRun run = getRun(runId);
        K8sPipeline pipeline = pipelineMapper.findById(pipelineId);
        if (pipeline == null) throw new RuntimeException("流水线不存在: " + pipelineId);

        JenkinsClient client = getJenkinsClient(projectId);
        client.stopBuild(pipeline.getJenkinsJobName(), run.getRunNumber());

        run.setStatus("aborted");
        run.setEndTime(LocalDateTime.now());
        runMapper.updateStatus(run);
        pipelineMapper.updateLastRun(pipelineId, "aborted", LocalDateTime.now());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void syncRunStatus(Long projectId, Long pipelineId, Long runId) {
        K8sPipelineRun run = getRun(runId);
        K8sPipeline pipeline = pipelineMapper.findById(pipelineId);
        if (pipeline == null) return;

        try {
            JenkinsClient client = getJenkinsClient(projectId);
            Map<String, Object> buildInfo = client.getBuildInfo(pipeline.getJenkinsJobName(), run.getRunNumber());
            if (buildInfo == null) return;

            // Map Jenkins result to our status
            Boolean building = (Boolean) buildInfo.get("building");
            String result = (String) buildInfo.get("result");

            if (Boolean.TRUE.equals(building)) {
                run.setStatus("running");
            } else if ("SUCCESS".equals(result)) {
                run.setStatus("success");
            } else if ("FAILURE".equals(result)) {
                run.setStatus("failed");
            } else if ("ABORTED".equals(result)) {
                run.setStatus("aborted");
            } else {
                run.setStatus("failed");
            }

            // Timestamps
            Object timestampObj = buildInfo.get("timestamp");
            if (timestampObj instanceof Number) {
                long ts = ((Number) timestampObj).longValue();
                run.setStartTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()));
            }
            Object durationObj = buildInfo.get("duration");
            if (durationObj instanceof Number) {
                run.setDurationMs(((Number) durationObj).longValue());
                if (run.getStartTime() != null && !Boolean.TRUE.equals(building)) {
                    run.setEndTime(run.getStartTime().plusNanos(run.getDurationMs() * 1_000_000));
                }
            }

            // Get stages
            List<Map<String, Object>> stages = client.getBuildStages(pipeline.getJenkinsJobName(), run.getRunNumber());
            if (!stages.isEmpty()) {
                try { run.setStagesStatus(objectMapper.writeValueAsString(stages)); } catch (Exception ignored) {}
            }

            runMapper.updateStatus(run);
            pipelineMapper.updateLastRun(pipelineId, run.getStatus(), LocalDateTime.now());

        } catch (Exception e) {
            log.warn("Failed to sync run status: {}", e.getMessage());
        }
    }
}
