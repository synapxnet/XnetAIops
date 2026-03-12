package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.exception.K8sResourceNotFoundException;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sStorageService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sStorageServiceImpl implements K8sStorageService {

    private final K8sClientFactory clientFactory;

    public K8sStorageServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    // ====== StorageClass ======

    @Override
    public List<Map<String, Object>> listStorageClasses(Long clusterId) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<StorageClass> storageClasses = client.storage().v1().storageClasses().list().getItems();
        return storageClasses.stream().map(sc -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", sc.getMetadata().getName());
            map.put("provisioner", sc.getProvisioner());
            map.put("reclaimPolicy", sc.getReclaimPolicy());
            map.put("volumeBindingMode", sc.getVolumeBindingMode());
            map.put("allowVolumeExpansion", sc.getAllowVolumeExpansion());
            map.put("createdAt", sc.getMetadata().getCreationTimestamp());

            // Check if default
            boolean isDefault = false;
            if (sc.getMetadata().getAnnotations() != null) {
                isDefault = "true".equals(sc.getMetadata().getAnnotations().get("storageclass.kubernetes.io/is-default-class"));
            }
            map.put("isDefault", isDefault);
            map.put("parameters", sc.getParameters());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getStorageClass(Long clusterId, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        StorageClass sc = client.storage().v1().storageClasses().withName(name).get();
        if (sc == null) {
            throw new K8sResourceNotFoundException("StorageClass not found: " + name);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("name", sc.getMetadata().getName());
        map.put("provisioner", sc.getProvisioner());
        map.put("reclaimPolicy", sc.getReclaimPolicy());
        map.put("volumeBindingMode", sc.getVolumeBindingMode());
        map.put("allowVolumeExpansion", sc.getAllowVolumeExpansion());
        map.put("parameters", sc.getParameters());
        map.put("labels", sc.getMetadata().getLabels());
        map.put("annotations", sc.getMetadata().getAnnotations());
        map.put("createdAt", sc.getMetadata().getCreationTimestamp());
        map.put("yaml", Serialization.asYaml(sc));
        return map;
    }

    // ====== PVC ======

    @Override
    public List<Map<String, Object>> listPVCs(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<PersistentVolumeClaim> pvcs;
        if (namespace == null || namespace.isEmpty()) {
            pvcs = client.persistentVolumeClaims().inAnyNamespace().list().getItems();
        } else {
            pvcs = client.persistentVolumeClaims().inNamespace(namespace).list().getItems();
        }
        return pvcs.stream().map(this::pvcToMap).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getPVC(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        PersistentVolumeClaim pvc = client.persistentVolumeClaims().inNamespace(namespace).withName(name).get();
        if (pvc == null) {
            throw new K8sResourceNotFoundException("PVC not found: " + namespace + "/" + name);
        }
        Map<String, Object> map = pvcToMap(pvc);
        map.put("labels", pvc.getMetadata().getLabels());
        map.put("annotations", pvc.getMetadata().getAnnotations());
        map.put("yaml", Serialization.asYaml(pvc));
        return map;
    }

    @Override
    public void createPVC(Long clusterId, String namespace, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        PersistentVolumeClaim pvc = Serialization.unmarshal(yaml, PersistentVolumeClaim.class);
        if (pvc.getMetadata().getNamespace() == null) {
            pvc.getMetadata().setNamespace(namespace);
        }
        client.persistentVolumeClaims().inNamespace(namespace).resource(pvc).create();
    }

    @Override
    public void deletePVC(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.persistentVolumeClaims().inNamespace(namespace).withName(name).delete();
    }

    // ====== PV ======

    @Override
    public List<Map<String, Object>> listPVs(Long clusterId) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<PersistentVolume> pvs = client.persistentVolumes().list().getItems();
        return pvs.stream().map(this::pvToMap).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getPV(Long clusterId, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        PersistentVolume pv = client.persistentVolumes().withName(name).get();
        if (pv == null) {
            throw new K8sResourceNotFoundException("PV not found: " + name);
        }
        Map<String, Object> map = pvToMap(pv);
        map.put("labels", pv.getMetadata().getLabels());
        map.put("annotations", pv.getMetadata().getAnnotations());
        map.put("yaml", Serialization.asYaml(pv));
        return map;
    }

    @Override
    public void deletePV(Long clusterId, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.persistentVolumes().withName(name).delete();
    }

    // ====== Helpers ======

    private Map<String, Object> pvcToMap(PersistentVolumeClaim pvc) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", pvc.getMetadata().getName());
        map.put("namespace", pvc.getMetadata().getNamespace());
        map.put("status", pvc.getStatus() != null ? pvc.getStatus().getPhase() : "Unknown");
        map.put("createdAt", pvc.getMetadata().getCreationTimestamp());

        if (pvc.getSpec() != null) {
            map.put("storageClassName", pvc.getSpec().getStorageClassName());
            map.put("accessModes", pvc.getSpec().getAccessModes());
            map.put("volumeName", pvc.getSpec().getVolumeName());

            if (pvc.getSpec().getResources() != null && pvc.getSpec().getResources().getRequests() != null) {
                Quantity storage = pvc.getSpec().getResources().getRequests().get("storage");
                map.put("capacity", storage != null ? storage.getAmount() + storage.getFormat() : "-");
            }
        }

        if (pvc.getStatus() != null && pvc.getStatus().getCapacity() != null) {
            Quantity actualCap = pvc.getStatus().getCapacity().get("storage");
            map.put("actualCapacity", actualCap != null ? actualCap.getAmount() + actualCap.getFormat() : "-");
        }

        return map;
    }

    private Map<String, Object> pvToMap(PersistentVolume pv) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", pv.getMetadata().getName());
        map.put("status", pv.getStatus() != null ? pv.getStatus().getPhase() : "Unknown");
        map.put("createdAt", pv.getMetadata().getCreationTimestamp());

        if (pv.getSpec() != null) {
            map.put("storageClassName", pv.getSpec().getStorageClassName());
            map.put("accessModes", pv.getSpec().getAccessModes());
            map.put("reclaimPolicy", pv.getSpec().getPersistentVolumeReclaimPolicy());
            map.put("volumeMode", pv.getSpec().getVolumeMode());

            if (pv.getSpec().getCapacity() != null) {
                Quantity cap = pv.getSpec().getCapacity().get("storage");
                map.put("capacity", cap != null ? cap.getAmount() + cap.getFormat() : "-");
            }

            if (pv.getSpec().getClaimRef() != null) {
                map.put("claimName", pv.getSpec().getClaimRef().getName());
                map.put("claimNamespace", pv.getSpec().getClaimRef().getNamespace());
            }

            // Source type
            if (pv.getSpec().getHostPath() != null) {
                map.put("source", "HostPath: " + pv.getSpec().getHostPath().getPath());
            } else if (pv.getSpec().getNfs() != null) {
                map.put("source", "NFS: " + pv.getSpec().getNfs().getServer() + ":" + pv.getSpec().getNfs().getPath());
            } else if (pv.getSpec().getCsi() != null) {
                map.put("source", "CSI: " + pv.getSpec().getCsi().getDriver());
            } else if (pv.getSpec().getLocal() != null) {
                map.put("source", "Local: " + pv.getSpec().getLocal().getPath());
            } else {
                map.put("source", "Other");
            }
        }

        return map;
    }
}
