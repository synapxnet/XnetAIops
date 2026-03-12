package com.synapxnet.aiopsclmservice.controller;

import com.synapxnet.aiopsclmservice.client.HostInfo;
import com.synapxnet.aiopsclmservice.common.Result;
import com.synapxnet.aiopsclmservice.entity.JenkinsMaster;
import com.synapxnet.aiopsclmservice.entity.JenkinsMasterDeployConfig;
import com.synapxnet.aiopsclmservice.service.JenkinsMasterService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Jenkins Master 管理控制器
 */
@RestController
@RequestMapping("/api/clm/jenkins-masters")
public class JenkinsMasterController {

    private final JenkinsMasterService masterService;

    public JenkinsMasterController(JenkinsMasterService masterService) {
        this.masterService = masterService;
    }

    // ==================== CRUD ====================

    @GetMapping
    public Result<List<JenkinsMaster>> list() {
        return Result.success(masterService.getAllMasters());
    }

    @GetMapping("/deployed")
    public Result<List<JenkinsMaster>> listDeployed() {
        return Result.success(masterService.getDeployedMasters());
    }

    @GetMapping("/status/{status}")
    public Result<List<JenkinsMaster>> listByStatus(@PathVariable("status") String status) {
        return Result.success(masterService.getMastersByStatus(status));
    }

    @GetMapping("/{id}")
    public Result<JenkinsMaster> get(@PathVariable("id") Long id) {
        return Result.success(masterService.getMasterById(id));
    }

    @PostMapping
    public Result<JenkinsMaster> create(@RequestBody JenkinsMaster master) {
        return Result.success(masterService.createMaster(master));
    }

    @PutMapping("/{id}")
    public Result<JenkinsMaster> update(@PathVariable("id") Long id, @RequestBody JenkinsMaster master) {
        return Result.success(masterService.updateMaster(id, master));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        masterService.deleteMaster(id);
        return Result.success();
    }

    // ==================== SSH连接 ====================

    @PostMapping("/test-connection")
    public Result<Map<String, Object>> testConnection(@RequestBody JenkinsMaster master) {
        return Result.success(masterService.testConnection(master));
    }

    // ==================== HOM主机列表 ====================

    @GetMapping("/hom-hosts")
    public Result<List<HostInfo>> getHomHosts() {
        return Result.success(masterService.getHomHosts());
    }

    // ==================== 部署操作 ====================

    @PostMapping("/{id}/deploy")
    public Result<Map<String, Object>> deploy(@PathVariable("id") Long id,
            @RequestBody JenkinsMasterDeployConfig config) {
        return Result.success(masterService.deployMaster(id, config));
    }

    @PostMapping("/preview-script")
    public Result<Map<String, Object>> previewScript(
            @RequestParam(value = "osType", defaultValue = "linux") String osType,
            @RequestBody JenkinsMasterDeployConfig config) {
        String script = masterService.getDeployScript(osType, config);
        return Result.success(Map.of("script", script));
    }

    // ==================== 状态管理 ====================

    @GetMapping("/{id}/status")
    public Result<Map<String, Object>> checkStatus(@PathVariable("id") Long id) {
        return Result.success(masterService.checkMasterStatus(id));
    }

    @PostMapping("/{id}/start")
    public Result<Map<String, Object>> start(@PathVariable("id") Long id) {
        return Result.success(masterService.startJenkins(id));
    }

    @PostMapping("/{id}/stop")
    public Result<Map<String, Object>> stop(@PathVariable("id") Long id) {
        return Result.success(masterService.stopJenkins(id));
    }

    @PostMapping("/{id}/restart")
    public Result<Map<String, Object>> restart(@PathVariable("id") Long id) {
        return Result.success(masterService.restartJenkins(id));
    }

    @GetMapping("/{id}/initial-password")
    public Result<String> getInitialPassword(@PathVariable("id") Long id) {
        return Result.success(masterService.getInitialPassword(id));
    }

    // ==================== 凭证管理 ====================

    @PostMapping("/{id}/credentials")
    public Result<Map<String, Object>> configureCredentials(@PathVariable("id") Long id,
            @RequestBody JenkinsMasterDeployConfig config) {
        return Result.success(masterService.configureCredentials(id, config));
    }

    // ==================== Node管理 ====================

    @PostMapping("/{id}/nodes")
    public Result<Map<String, Object>> createNodeOnMaster(@PathVariable("id") Long id,
            @RequestParam("nodeName") String nodeName,
            @RequestParam(value = "workDir", defaultValue = "/opt/jenkins-agent") String workDir,
            @RequestParam(value = "labels", required = false) String labels) {
        return Result.success(masterService.createNodeOnMaster(id, nodeName, workDir, labels));
    }

    @GetMapping("/{id}/nodes/{nodeName}/secret")
    public Result<String> getNodeSecret(@PathVariable("id") Long id,
            @PathVariable("nodeName") String nodeName) {
        return Result.success(masterService.getNodeSecret(id, nodeName));
    }

    // ==================== 卸载操作 ====================

    @PostMapping("/{id}/uninstall")
    public Result<Map<String, Object>> uninstall(@PathVariable("id") Long id) {
        return Result.success(masterService.uninstallJenkins(id));
    }
}
