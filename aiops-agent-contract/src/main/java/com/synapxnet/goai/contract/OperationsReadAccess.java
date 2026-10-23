/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 网页身份与资源授权读取边界。 Browser identity and resource authorization for read-only evidence.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.goai.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class OperationsReadAccess {
    private final List<ScopeGrant> grants;
    private final URI accessUri;
    private final HttpClient client;
    private ZoneId databaseTimeZone;

    /** 读取显式配置并限制HTTP连接；不自动发现或开放资源。 Loads explicit grants and bounded HTTP transport without discovering resources. */
    public OperationsReadAccess(ObjectMapper mapper, String accessUrl, String grantJson) {
        this(mapper, accessUrl, grantJson,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    /** 显式配置数据库时区，空配置保留未知时间。 Configures the database timezone explicitly; absent configuration preserves unknown timestamps. */
    public OperationsReadAccess(ObjectMapper mapper, String accessUrl, String grantJson, String databaseTimeZone) {
        this(mapper, accessUrl, grantJson);
        this.databaseTimeZone = databaseTimeZone == null || databaseTimeZone.isBlank() ? null : ZoneId.of(databaseTimeZone);
    }

    /** 仅在来源时区明确时转换数据库时间。 Converts database timestamps only when the source timezone is explicitly configured. */
    public Instant databaseInstant(LocalDateTime value) {
        return value == null || databaseTimeZone == null ? null : value.atZone(databaseTimeZone).toInstant();
    }

    /** 注入客户端供本地授权测试，拒绝含凭据或片段的地址。 Injects transport for local tests and rejects credentials or fragments in URLs. */
    OperationsReadAccess(ObjectMapper mapper, String accessUrl, String grantJson, HttpClient client) {
        this.client = client;
        this.accessUri = accessUrl == null || accessUrl.isBlank() ? null : URI.create(accessUrl);
        if (accessUri != null && (!Set.of("http", "https").contains(accessUri.getScheme())
                || accessUri.getHost() == null || accessUri.getUserInfo() != null
                || accessUri.getFragment() != null || accessUri.getQuery() != null)) {
            throw new IllegalArgumentException("Invalid organization access URL");
        }
        try {
            if (grantJson == null || grantJson.length() > 262_144) {
                throw new IllegalArgumentException("Invalid resource grant size");
            }
            this.grants = List.copyOf(mapper.readValue(grantJson, new TypeReference<List<ScopeGrant>>() { }));
            validateGrants();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid operations resource grants", exception);
        }
    }

    /** 验证Web JWT与组织成员关系后仅返回已配置资源。 Verifies the Web JWT and organization membership before returning configured resources. */
    public AuthorizedScope authorizeWeb(HttpServletRequest request, String kind) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 8192) {
            throw error(401, "UNAUTHENTICATED", "请先登录后读取运行证据。");
        }
        String tenant = header(request, "X-Tenant-Uid");
        String department = header(request, "X-Dept-Uid");
        String team = header(request, "X-Team-Uid");
        if (accessUri == null || grants.isEmpty()) {
            throw error(503, "OPERATIONS_SCOPE_NOT_CONFIGURED", "运行证据尚未配置身份校验与资源授权映射。");
        }
        HttpRequest verification = HttpRequest.newBuilder(accessUri).timeout(Duration.ofSeconds(5))
                .header("Authorization", authorization).header("X-Tenant-Uid", tenant)
                .header("X-Dept-Uid", department).header("X-Team-Uid", team).GET().build();
        try {
            int status = client.send(verification, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 401 || status == 403) {
                throw error(403, "PERMISSION_DENIED", "当前身份无权读取所选组织的运行证据。");
            }
            if (status != 204) {
                throw error(503, "IDENTITY_SERVICE_UNAVAILABLE", "组织身份校验暂不可用。");
            }
        } catch (AgentContractException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw error(503, "IDENTITY_SERVICE_UNAVAILABLE", "组织身份校验已中断。");
        } catch (Exception exception) {
            throw error(503, "IDENTITY_SERVICE_UNAVAILABLE", "组织身份校验暂不可用。");
        }
        for (ScopeGrant grant : grants) {
            if (grant.tenantUid().equals(tenant) && grant.deptUid().equals(department) && grant.teamUid().equals(team)) {
                // 只按已配置资源类型过滤。 Filter only the explicitly configured resource kind.
                return new AuthorizedScope(grant.workspaceId(), grant.resources().stream()
                        .filter(resource -> resource.kind().equals(kind)).toList());
            }
        }
        throw error(403, "PERMISSION_DENIED", "当前组织尚未授权运行证据资源。");
    }

    /** 在已验证workspace中精确匹配Agent目标；不接受通配符。 Matches the Agent target exactly within its verified workspace without wildcards. */
    public void requireAgentResource(String workspaceId, String kind, String key) {
        if (grants.isEmpty()) {
            throw error(503, "OPERATIONS_SCOPE_NOT_CONFIGURED", "运行证据尚未配置资源授权映射。");
        }
        for (ScopeGrant grant : grants) {
            if (!grant.workspaceId().equals(workspaceId)) continue;
            for (Resource resource : grant.resources()) {
                if (resource.kind().equals(kind) && resource.key().equals(key)) return;
            }
        }
        throw error(403, "PERMISSION_DENIED", "资源不在当前工作区的授权范围内。");
    }

    /** 在当前已授权目录中查找资源，避免跨范围ID探测。 Resolves a resource only inside the authorized catalog to prevent cross-scope lookup. */
    public Resource find(AuthorizedScope scope, String resourceId) {
        for (Resource resource : scope.resources()) {
            if (resource.id().equals(resourceId)) return resource;
        }
        throw error(404, "RESOURCE_NOT_FOUND", "资源不存在或未获得读取授权。");
    }

    /** 构造真实授权目录，不把空目录作为故障。 Builds the authorized native catalog while distinguishing empty results from failures. */
    public Catalog catalog(AuthorizedScope scope) {
        return new Catalog("1.0.0", UUID.randomUUID().toString(), "aiops", Instant.now(), scope.workspaceId(),
                scope.resources().isEmpty() ? "empty" : "available", "live", "native", scope.resources(),
                List.of("read"), List.of("此目录来自服务器授权配置；目标存在性与状态需逐项读取确认。", "仅提供读取；未接入全局计划与执行。"));
    }

    /** 构造只读来源记录；缺失原始时间保持unknown。 Builds read-only evidence and preserves unknown source timestamps. */
    public Evidence evidence(Resource resource, Object data, String version, Instant observedAt) {
        return new Evidence("1.0.0", UUID.randomUUID().toString(), "aiops", Instant.now(), "available", resource.executionMode(), "native", resource.id(),
                version, observedAt, "unknown", data, List.of("read"),
                List.of("平台状态记录不等于完整SLO验证；新鲜度需结合原始观测窗口。", "数据库时区未显式配置时，数据库时间保持未知；采集时间不是原始采样时间。"));
    }

    /** 限制配置规模、组织唯一性和资源定位，避免宽泛或歧义授权。 Bounds grants and requires unambiguous organizations and resource targets. */
    private void validateGrants() {
        if (grants.size() > 100) throw new IllegalArgumentException("Too many scopes");
        Set<String> identities = new HashSet<>();
        Set<String> workspaces = new HashSet<>();
        for (ScopeGrant grant : grants) {
            for (String value : List.of(grant.tenantUid(), grant.deptUid(), grant.teamUid(), grant.workspaceId())) {
                if (!validId(value)) throw new IllegalArgumentException("Invalid scope identifier");
            }
            if (!identities.add(grant.tenantUid() + "/" + grant.deptUid() + "/" + grant.teamUid())
                    || !workspaces.add(grant.workspaceId())
                    || grant.resources() == null || grant.resources().size() > 200) {
                throw new IllegalArgumentException("Ambiguous or unbounded scope");
            }
            Set<String> ids = new HashSet<>();
            for (Resource resource : grant.resources()) {
                if (!validId(resource.id()) || !ids.add(resource.id())
                        || resource.label() == null || resource.label().isBlank() || resource.label().length() > 200
                        || !Set.of("alert", "service", "workload").contains(resource.kind())
                        || !"live".equals(resource.executionMode())) {
                    throw new IllegalArgumentException("Invalid resource descriptor");
                }
                if ("workload".equals(resource.kind())) {
                    if (resource.clusterId() == null || !resource.clusterId().matches("[1-9][0-9]{0,17}")
                            || resource.namespace() == null || !resource.namespace().matches("[a-z0-9][a-z0-9-]{0,62}")
                            || resource.name() == null || !resource.name().matches("[a-z0-9][a-z0-9.-]{0,252}")
                            || !Set.of("Deployment", "StatefulSet", "DaemonSet").contains(resource.workloadKind())) {
                        throw new IllegalArgumentException("Invalid workload target");
                    }
                } else if (!validId(resource.resourceUid())) {
                    throw new IllegalArgumentException("Invalid domain resource UID");
                }
            }
        }
    }

    /** 读取有界组织标识。 Reads a bounded organization identifier from the request. */
    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (!validId(value)) throw error(403, "PERMISSION_DENIED", "请选择已授权的租户、部门和团队。");
        return value;
    }

    /** 限制标识字符且拒绝通配符。 Restricts identifier characters and excludes wildcard grants. */
    private boolean validId(String value) {
        return value != null && value.matches("[A-Za-z0-9._:-]{1,128}");
    }

    /** 创建无秘密字段的错误。 Creates a structured error containing no secret fields. */
    private AgentContractException error(int status, String code, String message) {
        return new AgentContractException(status, code, message);
    }

    public record ScopeGrant(String tenantUid, String deptUid, String teamUid, String workspaceId, List<Resource> resources) { }
    public record AuthorizedScope(String workspaceId, List<Resource> resources) { }
    public record Resource(String id, String label, String kind, String resourceUid,
                           String clusterId, String namespace, String workloadKind, String name, String executionMode) {
        /** 生成配置资源的精确授权键。 Returns the exact authorization key of this configured resource. */
        public String key() {
            return "workload".equals(kind) ? clusterId + "/" + namespace + "/" + workloadKind + "/" + name : resourceUid;
        }
    }
    public record Catalog(String schemaVersion, String requestId, String sourcePlatform, Instant capturedAt, String workspaceId,
                          String availability, String executionMode, String sourceOrigin, List<Resource> resources,
                          List<String> capabilities, List<String> limitations) { }
    public record Evidence(String schemaVersion, String requestId, String sourcePlatform, Instant capturedAt, String availability,
                           String executionMode, String sourceOrigin, String resourceId, String resourceVersion,
                           Instant observedAt, String freshness, Object data, List<String> capabilities,
                           List<String> limitations) { }
}
