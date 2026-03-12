package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sPodService {

    List<Map<String, Object>> listPods(Long clusterId, String namespace, Map<String, String> labelSelector);

    Map<String, Object> getPod(Long clusterId, String namespace, String podName);

    void deletePod(Long clusterId, String namespace, String podName);

    String getPodLogs(Long clusterId, String namespace, String podName, String container, Integer tailLines);

    List<Map<String, Object>> getPodEvents(Long clusterId, String namespace, String podName);

    List<Map<String, Object>> getPodContainers(Long clusterId, String namespace, String podName);
}
