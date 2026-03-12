package com.synapxnet.aiopsk8sservice.service;

import com.synapxnet.aiopsk8sservice.entity.K8sDevopsProject;
import java.util.List;

public interface K8sDevopsProjectService {
    List<K8sDevopsProject> listProjects();
    K8sDevopsProject getProject(Long id);
    K8sDevopsProject createProject(K8sDevopsProject project);
    void updateProject(K8sDevopsProject project);
    void deleteProject(Long id);
    boolean testConnection(Long id);
}
