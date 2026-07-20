package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.entity.K8sDevopsProject;
import com.synapxnet.aiopsk8sservice.mapper.K8sDevopsProjectMapper;
import com.synapxnet.aiopsk8sservice.service.K8sDevopsProjectService;
import com.synapxnet.aiopsk8sservice.util.JenkinsClient;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class K8sDevopsProjectServiceImpl implements K8sDevopsProjectService {

    private final K8sDevopsProjectMapper projectMapper;

    public K8sDevopsProjectServiceImpl(K8sDevopsProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    @Override
    public List<K8sDevopsProject> listProjects() {
        return projectMapper.findAll();
    }

    @Override
    public K8sDevopsProject getProject(Long id) {
        K8sDevopsProject project = projectMapper.findById(id);
        if (project == null) throw new RuntimeException("DevOps项目不存在: " + id);
        return project;
    }

    @Override
    public K8sDevopsProject createProject(K8sDevopsProject project) {
        if (project.getStatus() == null) project.setStatus("active");
        projectMapper.insert(project);
        return project;
    }

    @Override
    public void updateProject(K8sDevopsProject project) {
        projectMapper.update(project);
    }

    @Override
    public void deleteProject(Long id) {
        projectMapper.deleteById(id);
    }

    @Override
    public boolean testConnection(Long id) {
        K8sDevopsProject project = getProject(id);
        JenkinsClient client = new JenkinsClient(project.getJenkinsUrl(), project.getJenkinsUser(), project.getJenkinsToken());
        return client.testConnection();
    }
}
