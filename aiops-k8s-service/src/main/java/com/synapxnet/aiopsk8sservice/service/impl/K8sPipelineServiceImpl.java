package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.entity.K8sDevopsProject;
import com.synapxnet.aiopsk8sservice.entity.K8sPipeline;
import com.synapxnet.aiopsk8sservice.mapper.K8sDevopsProjectMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sPipelineMapper;
import com.synapxnet.aiopsk8sservice.service.K8sPipelineService;
import com.synapxnet.aiopsk8sservice.util.JenkinsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class K8sPipelineServiceImpl implements K8sPipelineService {

    private static final Logger log = LoggerFactory.getLogger(K8sPipelineServiceImpl.class);

    private final K8sPipelineMapper pipelineMapper;
    private final K8sDevopsProjectMapper projectMapper;

    public K8sPipelineServiceImpl(K8sPipelineMapper pipelineMapper, K8sDevopsProjectMapper projectMapper) {
        this.pipelineMapper = pipelineMapper;
        this.projectMapper = projectMapper;
    }

    private JenkinsClient getJenkinsClient(Long projectId) {
        K8sDevopsProject project = projectMapper.findById(projectId);
        if (project == null) throw new RuntimeException("DevOps项目不存在: " + projectId);
        return new JenkinsClient(project.getJenkinsUrl(), project.getJenkinsUser(), project.getJenkinsToken());
    }

    @Override
    public List<K8sPipeline> listPipelines(Long projectId) {
        return pipelineMapper.findByProjectId(projectId);
    }

    @Override
    public K8sPipeline getPipeline(Long id) {
        K8sPipeline pipeline = pipelineMapper.findById(id);
        if (pipeline == null) throw new RuntimeException("流水线不存在: " + id);
        return pipeline;
    }

    @Override
    public K8sPipeline createPipeline(K8sPipeline pipeline) {
        // Generate Jenkins job name (sanitized)
        String jobName = pipeline.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        pipeline.setJenkinsJobName(jobName);
        pipeline.setJenkinsJobPath(jobName);
        if (pipeline.getStatus() == null) pipeline.setStatus("active");
        if (pipeline.getType() == null) pipeline.setType("pipeline");

        // Create in DB first
        pipelineMapper.insert(pipeline);

        // Sync to Jenkins
        try {
            JenkinsClient client = getJenkinsClient(pipeline.getProjectId());
            client.createPipelineJob(jobName, pipeline.getJenkinsfile(), pipeline.getDescription(),
                    Boolean.TRUE.equals(pipeline.getDisableConcurrent()), pipeline.getTimerTrigger());
            log.info("Jenkins job created: {}", jobName);
        } catch (Exception e) {
            log.error("Failed to create Jenkins job: {}", e.getMessage());
            pipeline.setStatus("error");
            pipelineMapper.update(pipeline);
            throw new RuntimeException("创建Jenkins Job失败: " + e.getMessage());
        }

        return pipeline;
    }

    @Override
    public void updatePipeline(K8sPipeline pipeline) {
        K8sPipeline existing = getPipeline(pipeline.getId());
        pipeline.setProjectId(existing.getProjectId());
        pipeline.setJenkinsJobName(existing.getJenkinsJobName());
        pipeline.setJenkinsJobPath(existing.getJenkinsJobPath());

        pipelineMapper.update(pipeline);

        // Sync to Jenkins
        try {
            JenkinsClient client = getJenkinsClient(pipeline.getProjectId());
            client.updatePipelineJob(existing.getJenkinsJobName(), pipeline.getJenkinsfile(),
                    pipeline.getDescription(), Boolean.TRUE.equals(pipeline.getDisableConcurrent()),
                    pipeline.getTimerTrigger());
            log.info("Jenkins job updated: {}", existing.getJenkinsJobName());
        } catch (Exception e) {
            log.warn("Failed to update Jenkins job: {}", e.getMessage());
        }
    }

    @Override
    public void deletePipeline(Long projectId, Long id) {
        K8sPipeline pipeline = getPipeline(id);

        // Delete from Jenkins
        try {
            JenkinsClient client = getJenkinsClient(projectId);
            client.deleteJob(pipeline.getJenkinsJobName());
            log.info("Jenkins job deleted: {}", pipeline.getJenkinsJobName());
        } catch (Exception e) {
            log.warn("Failed to delete Jenkins job: {}", e.getMessage());
        }

        pipelineMapper.deleteById(id);
    }

    @Override
    public String getJenkinsfile(Long id) {
        K8sPipeline pipeline = getPipeline(id);
        return pipeline.getJenkinsfile();
    }

    @Override
    public void updateJenkinsfile(Long id, String jenkinsfile) {
        K8sPipeline pipeline = getPipeline(id);
        pipeline.setJenkinsfile(jenkinsfile);
        pipelineMapper.update(pipeline);

        // Sync to Jenkins
        try {
            JenkinsClient client = getJenkinsClient(pipeline.getProjectId());
            client.updatePipelineJob(pipeline.getJenkinsJobName(), jenkinsfile,
                    pipeline.getDescription(), Boolean.TRUE.equals(pipeline.getDisableConcurrent()),
                    pipeline.getTimerTrigger());
        } catch (Exception e) {
            log.warn("Failed to sync Jenkinsfile to Jenkins: {}", e.getMessage());
        }
    }
}
