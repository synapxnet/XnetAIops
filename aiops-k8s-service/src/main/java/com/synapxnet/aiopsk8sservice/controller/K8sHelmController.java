package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.entity.K8sHelmRelease;
import com.synapxnet.aiopsk8sservice.entity.K8sHelmRepo;
import com.synapxnet.aiopsk8sservice.service.K8sHelmService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/helm")
public class K8sHelmController {

    private final K8sHelmService helmService;

    public K8sHelmController(K8sHelmService helmService) {
        this.helmService = helmService;
    }

    // ====== Repos ======
    @GetMapping("/repos")
    public Result<List<K8sHelmRepo>> listRepos() {
        return Result.success(helmService.listRepos());
    }

    @GetMapping("/repos/{id}")
    public Result<K8sHelmRepo> getRepo(@PathVariable Long id) {
        return Result.success(helmService.getRepo(id));
    }

    @PostMapping("/repos")
    public Result<Void> addRepo(@RequestBody K8sHelmRepo repo) {
        helmService.addRepo(repo);
        return Result.success();
    }

    @PutMapping("/repos/{id}")
    public Result<Void> updateRepo(@PathVariable Long id, @RequestBody K8sHelmRepo repo) {
        repo.setId(id);
        helmService.updateRepo(repo);
        return Result.success();
    }

    @DeleteMapping("/repos/{id}")
    public Result<Void> deleteRepo(@PathVariable Long id) {
        helmService.deleteRepo(id);
        return Result.success();
    }

    @PostMapping("/repos/{id}/sync")
    public Result<List<Map<String, Object>>> syncRepo(@PathVariable Long id) {
        return Result.success(helmService.syncRepo(id));
    }

    // ====== Apps (Charts) ======
    @GetMapping("/apps")
    public Result<List<Map<String, Object>>> searchApps(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long repoId) {
        return Result.success(helmService.searchApps(keyword, repoId));
    }

    @GetMapping("/apps/{chartName}")
    public Result<Map<String, Object>> getAppDetail(
            @RequestParam Long repoId,
            @PathVariable String chartName) {
        return Result.success(helmService.getAppDetail(repoId, chartName));
    }

    @GetMapping("/apps/{chartName}/versions/{version}")
    public Result<Map<String, Object>> getAppVersion(
            @RequestParam Long repoId,
            @PathVariable String chartName,
            @PathVariable String version) {
        return Result.success(helmService.getAppVersion(repoId, chartName, version));
    }

    // ====== Releases ======
    @GetMapping("/releases")
    public Result<List<K8sHelmRelease>> listReleases(@PathVariable Long clusterId) {
        return Result.success(helmService.listReleases(clusterId));
    }

    @GetMapping("/releases/{releaseName}")
    public Result<K8sHelmRelease> getReleaseDetail(
            @PathVariable Long clusterId,
            @PathVariable String releaseName,
            @RequestParam String namespace) {
        return Result.success(helmService.getReleaseDetail(clusterId, namespace, releaseName));
    }

    @PostMapping("/releases")
    public Result<Void> installRelease(@PathVariable Long clusterId, @RequestBody Map<String, Object> body) {
        helmService.installRelease(
                clusterId,
                (String) body.get("namespace"),
                (String) body.get("releaseName"),
                (String) body.get("chartName"),
                (String) body.get("chartVersion"),
                body.get("repoId") != null ? Long.valueOf(body.get("repoId").toString()) : null,
                (String) body.get("values")
        );
        return Result.success();
    }

    @PutMapping("/releases/{releaseName}")
    public Result<Void> upgradeRelease(
            @PathVariable Long clusterId,
            @PathVariable String releaseName,
            @RequestBody Map<String, String> body) {
        helmService.upgradeRelease(clusterId, body.get("namespace"), releaseName,
                body.get("chartVersion"), body.get("values"));
        return Result.success();
    }

    @DeleteMapping("/releases/{releaseName}")
    public Result<Void> uninstallRelease(
            @PathVariable Long clusterId,
            @PathVariable String releaseName,
            @RequestParam String namespace) {
        helmService.uninstallRelease(clusterId, namespace, releaseName);
        return Result.success();
    }

    @PostMapping("/releases/{releaseName}/rollback")
    public Result<Void> rollbackRelease(
            @PathVariable Long clusterId,
            @PathVariable String releaseName,
            @RequestBody Map<String, Object> body) {
        helmService.rollbackRelease(clusterId,
                (String) body.get("namespace"),
                releaseName,
                (Integer) body.get("revision"));
        return Result.success();
    }

    @PostMapping("/releases/sync")
    public Result<Void> syncReleases(@PathVariable Long clusterId) {
        helmService.syncReleasesFromCluster(clusterId);
        return Result.success();
    }
}
