package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sNodeService {

    List<Map<String, Object>> listNodes(Long clusterId);

    Map<String, Object> getNode(Long clusterId, String nodeName);

    List<Map<String, Object>> getNodePods(Long clusterId, String nodeName);

    void cordonNode(Long clusterId, String nodeName);

    void uncordonNode(Long clusterId, String nodeName);

    void drainNode(Long clusterId, String nodeName);

    void updateLabels(Long clusterId, String nodeName, Map<String, String> labels);

    void updateTaints(Long clusterId, String nodeName, List<Map<String, String>> taints);

    Map<String, Object> getNodeMetrics(Long clusterId, String nodeName);
}
