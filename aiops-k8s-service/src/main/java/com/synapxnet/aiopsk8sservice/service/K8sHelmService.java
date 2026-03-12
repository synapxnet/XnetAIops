package com.synapxnet.aiopsk8sservice.service;

import com.synapxnet.aiopsk8sservice.entity.K8sHelmRelease;
import com.synapxnet.aiopsk8sservice.entity.K8sHelmRepo;

import java.util.List;
import java.util.Map;

public interface K8sHelmService {
    // Repos
    List<K8sHelmRepo> listRepos();
    K8sHelmRepo getRepo(Long id);
    void addRepo(K8sHelmRepo repo);
    void updateRepo(K8sHelmRepo repo);
    void deleteRepo(Long id);
    List<Map<String, Object>> syncRepo(Long repoId);

    // Apps (Charts)
    List<Map<String, Object>> searchApps(String keyword, Long repoId);
    Map<String, Object> getAppDetail(Long repoId, String chartName);
    Map<String, Object> getAppVersion(Long repoId, String chartName, String version);

    // Releases
    List<K8sHelmRelease> listReleases(Long clusterId);
    K8sHelmRelease getReleaseDetail(Long clusterId, String namespace, String releaseName);
    void installRelease(Long clusterId, String namespace, String releaseName, String chartName,
                        String chartVersion, Long repoId, String values);
    void upgradeRelease(Long clusterId, String namespace, String releaseName,
                        String chartVersion, String values);
    void uninstallRelease(Long clusterId, String namespace, String releaseName);
    void rollbackRelease(Long clusterId, String namespace, String releaseName, int revision);

    // Sync
    void syncReleasesFromCluster(Long clusterId);
}
