package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sJobService {

    // Jobs
    List<Map<String, Object>> listJobs(Long clusterId, String namespace);
    Map<String, Object> getJob(Long clusterId, String namespace, String name);
    void createJob(Long clusterId, String namespace, String yaml);
    void deleteJob(Long clusterId, String namespace, String name);
    void rerunJob(Long clusterId, String namespace, String name);

    // CronJobs
    List<Map<String, Object>> listCronJobs(Long clusterId, String namespace);
    Map<String, Object> getCronJob(Long clusterId, String namespace, String name);
    void createCronJob(Long clusterId, String namespace, String yaml);
    void updateCronJob(Long clusterId, String namespace, String name, String yaml);
    void deleteCronJob(Long clusterId, String namespace, String name);
    void triggerCronJob(Long clusterId, String namespace, String name);
}
