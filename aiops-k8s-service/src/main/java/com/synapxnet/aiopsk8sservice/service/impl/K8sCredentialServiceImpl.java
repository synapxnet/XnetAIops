package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.entity.K8sCredential;
import com.synapxnet.aiopsk8sservice.mapper.K8sCredentialMapper;
import com.synapxnet.aiopsk8sservice.service.K8sCredentialService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class K8sCredentialServiceImpl implements K8sCredentialService {

    private final K8sCredentialMapper credentialMapper;

    public K8sCredentialServiceImpl(K8sCredentialMapper credentialMapper) {
        this.credentialMapper = credentialMapper;
    }

    @Override
    public List<K8sCredential> listCredentials(Long projectId) {
        return credentialMapper.findByProjectId(projectId);
    }

    @Override
    public K8sCredential getCredential(Long id) {
        K8sCredential cred = credentialMapper.findById(id);
        if (cred == null) throw new RuntimeException("凭证不存在: " + id);
        return cred;
    }

    @Override
    public K8sCredential createCredential(K8sCredential credential) {
        credentialMapper.insert(credential);
        return credential;
    }

    @Override
    public void updateCredential(K8sCredential credential) {
        credentialMapper.update(credential);
    }

    @Override
    public void deleteCredential(Long id) {
        credentialMapper.deleteById(id);
    }
}
