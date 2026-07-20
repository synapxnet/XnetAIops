package com.synapxnet.aiopsk8sservice.service;

import com.synapxnet.aiopsk8sservice.entity.K8sAppTemplate;

import java.util.List;

public interface K8sAppTemplateService {

    List<K8sAppTemplate> listAll();

    List<K8sAppTemplate> listByCategory(String category);

    List<String> listCategories();

    List<K8sAppTemplate> listFeatured();

    K8sAppTemplate getById(Long id);

    void installFromTemplate(Long templateId, Long clusterId, String namespace, String releaseName, String values);
}
