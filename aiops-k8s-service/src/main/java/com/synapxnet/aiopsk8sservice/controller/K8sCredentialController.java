package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.entity.K8sCredential;
import com.synapxnet.aiopsk8sservice.service.K8sCredentialService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/k8s/devops/projects/{projectId}/credentials")
public class K8sCredentialController {

    private final K8sCredentialService credentialService;

    public K8sCredentialController(K8sCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping
    public Result<List<K8sCredential>> list(@PathVariable Long projectId) {
        return Result.success(credentialService.listCredentials(projectId));
    }

    @GetMapping("/{id}")
    public Result<K8sCredential> get(@PathVariable Long projectId, @PathVariable Long id) {
        K8sCredential cred = credentialService.getCredential(id);
        // Mask sensitive fields
        if (cred.getPassword() != null) cred.setPassword("******");
        if (cred.getToken() != null) cred.setToken("******");
        if (cred.getPrivateKey() != null) cred.setPrivateKey("******");
        if (cred.getPassphrase() != null) cred.setPassphrase("******");
        return Result.success(cred);
    }

    @PostMapping
    public Result<K8sCredential> create(@PathVariable Long projectId, @RequestBody K8sCredential credential) {
        credential.setProjectId(projectId);
        return Result.success(credentialService.createCredential(credential));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long projectId, @PathVariable Long id,
                                @RequestBody K8sCredential credential) {
        credential.setId(id);
        credential.setProjectId(projectId);
        credentialService.updateCredential(credential);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        credentialService.deleteCredential(id);
        return Result.success();
    }
}
