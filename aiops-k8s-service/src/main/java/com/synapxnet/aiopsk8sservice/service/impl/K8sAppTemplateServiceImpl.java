package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.entity.K8sAppTemplate;
import com.synapxnet.aiopsk8sservice.entity.K8sHelmRepo;
import com.synapxnet.aiopsk8sservice.mapper.K8sAppTemplateMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sHelmRepoMapper;
import com.synapxnet.aiopsk8sservice.service.K8sAppTemplateService;
import com.synapxnet.aiopsk8sservice.service.K8sHelmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class K8sAppTemplateServiceImpl implements K8sAppTemplateService {

    private static final Logger log = LoggerFactory.getLogger(K8sAppTemplateServiceImpl.class);

    private final K8sAppTemplateMapper templateMapper;
    private final K8sHelmService helmService;
    private final K8sHelmRepoMapper helmRepoMapper;

    public K8sAppTemplateServiceImpl(K8sAppTemplateMapper templateMapper, K8sHelmService helmService, K8sHelmRepoMapper helmRepoMapper) {
        this.templateMapper = templateMapper;
        this.helmService = helmService;
        this.helmRepoMapper = helmRepoMapper;
    }

    @Override
    public List<K8sAppTemplate> listAll() {
        return templateMapper.findAll();
    }

    @Override
    public List<K8sAppTemplate> listByCategory(String category) {
        return templateMapper.findByCategory(category);
    }

    @Override
    public List<String> listCategories() {
        return templateMapper.findAllCategories();
    }

    @Override
    public List<K8sAppTemplate> listFeatured() {
        return templateMapper.findFeatured();
    }

    @Override
    public K8sAppTemplate getById(Long id) {
        return templateMapper.findById(id);
    }

    @Override
    public void installFromTemplate(Long templateId, Long clusterId, String namespace, String releaseName, String values) {
        K8sAppTemplate template = templateMapper.findById(templateId);
        if (template == null) {
            throw new RuntimeException("App template not found: " + templateId);
        }

        // Use the provided values, or fall back to template defaults
        String effectiveValues = (values != null && !values.trim().isEmpty()) ? values : template.getDefaultValues();

        // 确保 Helm repo 在数据库中存在（模板自带 repo 信息，自动创建）
        Long repoId = ensureRepoInDb(template.getHelmRepoName(), template.getHelmRepoUrl());

        // chartName 格式：OCI 仓库用纯名称，传统仓库用 repoName/chartName
        String chartName;
        if (template.getHelmRepoUrl() != null && template.getHelmRepoUrl().startsWith("oci://")) {
            chartName = template.getChartName();
        } else {
            chartName = template.getHelmRepoName() + "/" + template.getChartName();
        }

        helmService.installRelease(
                clusterId,
                namespace,
                releaseName,
                chartName,
                template.getChartVersion(),
                repoId,
                effectiveValues
        );
    }

    /**
     * 确保 Helm 仓库在数据库中存在，如果不存在则自动创建。
     * 返回 repoId。
     */
    private Long ensureRepoInDb(String repoName, String repoUrl) {
        K8sHelmRepo repo = helmRepoMapper.findByName(repoName);
        if (repo != null) {
            return repo.getId();
        }
        // 自动创建
        repo = new K8sHelmRepo();
        repo.setName(repoName);
        repo.setUrl(repoUrl);
        repo.setDescription("Auto-created from app template");
        repo.setAuthType("none");
        repo.setStatus("active");
        helmRepoMapper.insert(repo);
        log.info("Auto-created Helm repo: {} -> {}", repoName, repoUrl);
        return repo.getId();
    }
}
