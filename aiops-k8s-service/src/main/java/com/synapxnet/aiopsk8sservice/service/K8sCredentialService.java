package com.synapxnet.aiopsk8sservice.service;

import com.synapxnet.aiopsk8sservice.entity.K8sCredential;
import java.util.List;

public interface K8sCredentialService {
    List<K8sCredential> listCredentials(Long projectId);
    K8sCredential getCredential(Long id);
    K8sCredential createCredential(K8sCredential credential);
    void updateCredential(K8sCredential credential);
    void deleteCredential(Long id);
}
