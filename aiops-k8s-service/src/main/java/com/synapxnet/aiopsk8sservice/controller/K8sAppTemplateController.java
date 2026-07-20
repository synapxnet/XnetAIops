package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.entity.K8sAppTemplate;
import com.synapxnet.aiopsk8sservice.service.K8sAppTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/app-templates")
public class K8sAppTemplateController {

    private final K8sAppTemplateService templateService;

    public K8sAppTemplateController(K8sAppTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public Result<List<K8sAppTemplate>> list(@RequestParam(required = false) String category) {
        if (category != null && !category.isEmpty()) {
            return Result.success(templateService.listByCategory(category));
        }
        return Result.success(templateService.listAll());
    }

    @GetMapping("/categories")
    public Result<List<String>> listCategories() {
        return Result.success(templateService.listCategories());
    }

    @GetMapping("/featured")
    public Result<List<K8sAppTemplate>> listFeatured() {
        return Result.success(templateService.listFeatured());
    }

    @GetMapping("/{id}")
    public Result<K8sAppTemplate> getById(@PathVariable Long id) {
        return Result.success(templateService.getById(id));
    }

    @PostMapping("/{id}/install")
    public Result<Void> install(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long clusterId = Long.valueOf(body.get("clusterId").toString());
        String namespace = (String) body.get("namespace");
        String releaseName = (String) body.get("releaseName");
        String values = (String) body.get("values");
        templateService.installFromTemplate(id, clusterId, namespace, releaseName, values);
        return Result.success();
    }
}
