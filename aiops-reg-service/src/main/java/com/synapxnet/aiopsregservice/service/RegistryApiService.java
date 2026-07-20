package com.synapxnet.aiopsregservice.service;

import com.synapxnet.aiopsregservice.entity.Registry;

import java.util.List;
import java.util.Map;

public interface RegistryApiService {

    /** Probe whether the registry endpoint is reachable */
    boolean probe(Registry registry);

    /** List projects (Harbor) / groups (GitLab) / catalog (Distribution) */
    List<Map<String, Object>> listProjects(Registry registry);

    /** Create a project/group */
    Map<String, Object> createProject(Registry registry, String projectName, boolean isPublic);

    /** Delete a project/group */
    void deleteProject(Registry registry, Object projectId);

    /** List repositories under a project */
    List<Map<String, Object>> listRepositories(Registry registry, String projectName);

    /** List tags for a repository */
    List<Map<String, Object>> listTags(Registry registry, String repoName);

    /** Delete a tag */
    void deleteTag(Registry registry, String repoName, String tag);

    /** Get manifest detail for a tag */
    Map<String, Object> getManifest(Registry registry, String repoName, String reference);

    /** List users (Harbor/GitLab only) */
    List<Map<String, Object>> listUsers(Registry registry);

    /** Create user */
    Map<String, Object> createUser(Registry registry, Map<String, Object> userInfo);

    /** Update user */
    void updateUser(Registry registry, Object userId, Map<String, Object> userInfo);

    /** Delete user */
    void deleteUser(Registry registry, Object userId);

    // ==================== Artifacts (enhanced) ====================

    /** List artifacts with full detail (tags, size, digest, architecture) */
    List<Map<String, Object>> listArtifacts(Registry registry, String projectName, String repoName);

    /** Get single artifact detail */
    Map<String, Object> getArtifactDetail(Registry registry, String repoName, String reference);

    // ==================== Registry Endpoints (Harbor Replication) ====================

    /** List external registry endpoints configured in Harbor */
    List<Map<String, Object>> listEndpoints(Registry registry);

    /** Create an external registry endpoint in Harbor */
    Map<String, Object> createEndpoint(Registry registry, Map<String, Object> endpointData);

    /** Update an external registry endpoint */
    void updateEndpoint(Registry registry, Object endpointId, Map<String, Object> endpointData);

    /** Delete an external registry endpoint */
    void deleteEndpoint(Registry registry, Object endpointId);

    /** Ping/test an external registry endpoint */
    Map<String, Object> pingEndpoint(Registry registry, Map<String, Object> endpointData);

    // ==================== Replication ====================

    /** List replication policies */
    List<Map<String, Object>> listReplicationPolicies(Registry registry);

    /** List replication policies filtered by name */
    List<Map<String, Object>> listReplicationPoliciesByName(Registry registry, String name);

    /** Create a replication policy */
    Map<String, Object> createReplicationPolicy(Registry registry, Map<String, Object> policyData);

    /** Get replication policy detail */
    Map<String, Object> getReplicationPolicy(Registry registry, Object policyId);

    /** Update a replication policy */
    void updateReplicationPolicy(Registry registry, Object policyId, Map<String, Object> policyData);

    /** Delete a replication policy */
    void deleteReplicationPolicy(Registry registry, Object policyId);

    /** Trigger a replication execution */
    Map<String, Object> triggerReplication(Registry registry, Map<String, Object> executionData);

    /** List replication executions */
    List<Map<String, Object>> listReplicationExecutions(Registry registry, Long policyId);

    /** Get replication execution tasks */
    List<Map<String, Object>> getReplicationTasks(Registry registry, Object executionId);
}
