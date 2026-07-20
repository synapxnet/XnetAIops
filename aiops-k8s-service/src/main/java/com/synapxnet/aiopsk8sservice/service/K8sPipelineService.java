package com.synapxnet.aiopsk8sservice.service;

import com.synapxnet.aiopsk8sservice.entity.K8sPipeline;
import java.util.List;

public interface K8sPipelineService {
    List<K8sPipeline> listPipelines(Long projectId);
    K8sPipeline getPipeline(Long id);
    K8sPipeline createPipeline(K8sPipeline pipeline);
    void updatePipeline(K8sPipeline pipeline);
    void deletePipeline(Long projectId, Long id);
    String getJenkinsfile(Long id);
    void updateJenkinsfile(Long id, String jenkinsfile);
}
