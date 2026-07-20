package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sStorageService {

    // StorageClass
    List<Map<String, Object>> listStorageClasses(Long clusterId);
    Map<String, Object> getStorageClass(Long clusterId, String name);

    // PersistentVolumeClaim
    List<Map<String, Object>> listPVCs(Long clusterId, String namespace);
    Map<String, Object> getPVC(Long clusterId, String namespace, String name);
    void createPVC(Long clusterId, String namespace, String yaml);
    void deletePVC(Long clusterId, String namespace, String name);

    // PersistentVolume
    List<Map<String, Object>> listPVs(Long clusterId);
    Map<String, Object> getPV(Long clusterId, String name);
    void deletePV(Long clusterId, String name);
}
