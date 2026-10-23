/*
Copyright (C) 2026 Synapxnet. All rights reserved.
This file is Synapxnet Proprietary and Confidential. It is strictly
forbidden to copy, distribute, or use without explicit authorization.
Author: maoyo | Department: 研发部 | Date: 2026-09-13
Version: 1.0.0 | Security Level: INTERNAL
__version__: 1.0.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
__maintainer__: maoyo | __email__: synapxnet@gmail.com
*/

package com.synapxnet.aiopsk8sservice.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.goai.contract.AgentContractException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 将网页登录与精确终端执行授权转换为一次性握手票据。Converts web authentication and exact execution grants into single-use handshake tickets. */
@Service
public class TerminalAccessService {
    public static final String ATTRIBUTE = TerminalAccessService.class.getName() + ".grant";
    private static final Logger log = LoggerFactory.getLogger(TerminalAccessService.class);
    private final ObjectMapper mapper;
    private final URI organizationUrl;
    private final URI identityUrl;
    private final Set<String> origins;
    private final List<ExecutionGrant> grants;
    private final HttpClient client;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, AuthorizedTerminal> tickets = new ConcurrentHashMap<>();

    /** 读取独立执行授权及可信身份服务，空配置默认拒绝。Read independent execution grants and trusted identity services; empty configuration denies access. */
    @Autowired
    public TerminalAccessService(ObjectMapper mapper,
            @Value("${openxnet.terminal.organization-access-url:}") String organizationUrl,
            @Value("${openxnet.terminal.identity-url:}") String identityUrl,
            @Value("${openxnet.terminal.allowed-origins:}") String origins,
            @Value("${openxnet.terminal.execution-grants:[]}") String grants) {
        this(mapper, organizationUrl, identityUrl, origins, grants,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), Clock.systemUTC());
    }

    /** 注入有界传输和时钟便于独立验证，不连接真实集群。Inject bounded transport and a clock for isolated verification without a real cluster. */
    TerminalAccessService(ObjectMapper mapper, String organizationUrl, String identityUrl, String origins,
            String grants, HttpClient client, Clock clock) {
        this.mapper = mapper;
        this.organizationUrl = validServiceUri(organizationUrl);
        this.identityUrl = validServiceUri(identityUrl);
        this.origins = origins == null || origins.isBlank() ? Set.of() : Set.copyOf(Arrays.stream(origins.split(",")).map(String::trim).toList());
        for (String origin : this.origins) if (!validOrigin(origin)) throw new IllegalArgumentException("Invalid terminal Origin");
        this.client = client;
        this.clock = clock;
        try {
            if (grants == null || grants.length() > 262144) throw new IllegalArgumentException("Terminal grant size");
            this.grants = List.copyOf(mapper.readValue(grants, new TypeReference<List<ExecutionGrant>>() { }));
            if (this.grants.size() > 500) throw new IllegalArgumentException("Too many terminal grants");
            for (ExecutionGrant grant : this.grants) {
                if (!"terminal:exec".equals(grant.action()) || !validId(grant.userId()) || !validId(grant.workspaceId())
                        || !validId(grant.tenantUid()) || !validId(grant.deptUid()) || !validId(grant.teamUid())) throw new IllegalArgumentException("Invalid terminal grant");
                grant.target().validate();
            }
        } catch (Exception exception) { throw new IllegalArgumentException("Invalid terminal execution grants", exception); }
    }

    /** 校验身份、组织、Origin和精确目标后签发30秒单次票据。Issue a 30-second single-use ticket after identity, organization, Origin and exact target checks. */
    public Ticket issue(HttpServletRequest request, Target target) {
        requireOrigin(request.getHeader("Origin"));
        target.validate();
        String authorization = request.getHeader("Authorization");
        String tenant = request.getHeader("X-Tenant-Uid");
        String department = request.getHeader("X-Dept-Uid");
        String team = request.getHeader("X-Team-Uid");
        String userId = verifyIdentity(authorization, tenant, department, team);
        ExecutionGrant grant = grants.stream().filter(entry -> entry.userId().equals(userId)
                && entry.tenantUid().equals(tenant) && entry.deptUid().equals(department) && entry.teamUid().equals(team)
                && entry.target().equals(target)).findFirst().orElseThrow(() -> error(403, "TERMINAL_PERMISSION_DENIED", "当前用户和项目未获得此容器的终端执行权限。"));
        byte[] entropy = new byte[32]; random.nextBytes(entropy);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        Instant expiresAt = clock.instant().plusSeconds(30);
        AuthorizedTerminal authorized = new AuthorizedTerminal(UUID.randomUUID().toString(), userId, grant.workspaceId(), tenant, department, team,
                request.getHeader("Origin"), target, expiresAt, authorization);
        synchronized (tickets) {
            tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(clock.instant()));
            if (tickets.size() >= 1000) throw error(429, "TERMINAL_TICKETS_BUSY", "终端连接请求较多，请稍后重试。");
            tickets.put(ticket, authorized);
        }
        log.info("terminal ticket issued audit={} user={} workspace={} target={}", authorized.auditId(), userId, grant.workspaceId(), target.path());
        return new Ticket(ticket, expiresAt, "/api/k8s" + target.path(), grant.workspaceId());
    }

    /** 原子消费票据并再次校验退出和授权状态，票据不进入URL或日志。Atomically consume tickets and recheck identity; tickets never enter URLs or logs. */
    public AuthorizedTerminal consume(String ticket, String origin, String path) {
        requireOrigin(origin);
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) throw error(401, "TERMINAL_TICKET_INVALID", "终端连接票据无效或已失效。");
        AuthorizedTerminal authorized = tickets.remove(ticket);
        if (authorized == null || !authorized.expiresAt().isAfter(clock.instant()) || !authorized.origin().equals(origin)
                || !authorized.target().path().equals(path)) throw error(403, "TERMINAL_TICKET_INVALID", "终端连接票据无效或与目标不符。");
        String userId = verifyIdentity(authorized.authorization(), authorized.tenantUid(), authorized.deptUid(), authorized.teamUid());
        if (!userId.equals(authorized.userId())) throw error(403, "TERMINAL_PERMISSION_DENIED", "终端身份已改变，请重新登录。");
        log.info("terminal ticket consumed audit={} user={} workspace={} target={}", authorized.auditId(), userId, authorized.workspaceId(), path);
        return authorized.withoutAuthorization();
    }

    /** 返回精确Origin列表，禁止通配符和未配置连接。Return exact allowed Origins, excluding wildcards and unconfigured access. */
    public String[] allowedOrigins() { return origins.toArray(String[]::new); }

    /** Origin必须明确配置且精确匹配。Require an explicitly configured, exact matching Origin. */
    private void requireOrigin(String origin) {
        if (origins.isEmpty() || grants.isEmpty() || organizationUrl == null || identityUrl == null) throw error(503, "TERMINAL_NOT_CONFIGURED", "终端尚未配置身份校验、来源和容器执行授权。");
        if (origin == null || !origins.contains(origin)) throw error(403, "TERMINAL_ORIGIN_DENIED", "当前页面来源未获得终端连接许可。");
    }

    /** 通过USR验证签名、退出黑名单、有效账号及组织成员关系；不信任用户自报ID。Verify signatures, logout revocation, active accounts and memberships through USR instead of trusting claimed IDs. */
    private String verifyIdentity(String authorization, String tenant, String department, String team) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 8192) throw error(401, "UNAUTHENTICATED", "请登录后连接终端。");
        if (!validId(tenant) || !validId(department) || !validId(team)) throw error(403, "TERMINAL_PERMISSION_DENIED", "请选择完整的已授权组织。");
        try {
            HttpRequest membership = HttpRequest.newBuilder(organizationUrl).timeout(Duration.ofSeconds(5)).header("Authorization", authorization)
                    .header("X-Tenant-Uid", tenant).header("X-Dept-Uid", department).header("X-Team-Uid", team).GET().build();
            int membershipStatus = client.send(membership, HttpResponse.BodyHandlers.discarding()).statusCode();
            requireIdentityStatus(membershipStatus, 204);
            HttpResponse<String> identity = client.send(HttpRequest.newBuilder(identityUrl).timeout(Duration.ofSeconds(5)).header("Authorization", authorization).GET().build(), HttpResponse.BodyHandlers.ofString());
            requireIdentityStatus(identity.statusCode(), 200);
            if (identity.body().length() > 65536) throw error(503, "IDENTITY_UNAVAILABLE", "终端身份校验暂不可用。");
            var payload = mapper.readTree(identity.body());
            String userId = payload.path("data").path("userId").asText();
            if (payload.path("code").asInt(-1) != 0 || !validId(userId)) throw error(403, "TERMINAL_PERMISSION_DENIED", "终端身份校验未通过。");
            return userId;
        } catch (AgentContractException exception) { throw exception; }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw error(503, "IDENTITY_UNAVAILABLE", "终端身份校验已中断。"); }
        catch (Exception exception) { throw error(503, "IDENTITY_UNAVAILABLE", "终端身份校验暂不可用。"); }
    }

    /** 身份服务异常失败关闭，不把异常响应当作授权。Fail closed on identity service failures instead of interpreting them as authorization. */
    private void requireIdentityStatus(int status, int expected) {
        if (status == 401 || status == 403) throw error(403, "TERMINAL_PERMISSION_DENIED", "当前身份无权连接所选组织的终端。");
        if (status != expected) throw error(503, "IDENTITY_UNAVAILABLE", "终端身份校验暂不可用。");
    }

    /** 配置的可信服务地址必须为无凭据的HTTP地址。Require credential-free HTTP URLs for configured trusted services. */
    private URI validServiceUri(String value) {
        if (value == null || value.isBlank()) return null;
        URI uri = URI.create(value);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException("Invalid terminal identity URL");
        return uri;
    }

    /** Origin只允许协议、主机和端口，无路径、通配符或凭据。Allow only scheme, host and port in Origins, with no path, wildcard or credentials. */
    private boolean validOrigin(String value) {
        try { URI uri = URI.create(value); return Set.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null && (uri.getPath() == null || uri.getPath().isEmpty()); }
        catch (Exception exception) { return false; }
    }

    /** 限制授权标识且拒绝通配符。Bound authorization identifiers and reject wildcards. */
    private static boolean validId(String value) { return value != null && value.matches("[A-Za-z0-9._:-]{1,128}"); }
    /** 创建公开错误，不暴露身份服务细节。Create public errors without exposing identity-service details. */
    private static AgentContractException error(int status, String code, String message) { return new AgentContractException(status, code, message); }

    public record ExecutionGrant(String userId, String tenantUid, String deptUid, String teamUid, String workspaceId, String action, Target target) { }
    public record Ticket(String ticket, Instant expiresAt, String webSocketPath, String workspaceId) { }
    public record Target(Long clusterId, String namespace, String podName, String containerName) {
        /** 精确验证Kubernetes资源目标，不接收shell或路径片段。Validate exact Kubernetes targets without accepting shell text or path fragments. */
        public void validate() {
            if (clusterId == null || clusterId < 1 || !dns(namespace, 63) || !dns(podName, 253) || !dns(containerName, 63)) throw error(400, "TERMINAL_TARGET_INVALID", "请选择有效的集群、命名空间、Pod和容器。");
        }
        /** 只允许DNS资源名称。Allow DNS resource names only. */
        private boolean dns(String value, int length) { return value != null && value.length() <= length && value.matches("[a-z0-9]([a-z0-9.-]*[a-z0-9])?"); }
        /** 生成唯一资源路径，不携带认证凭据。Build a unique resource path without credentials. */
        public String path() { return "/ws/terminal/" + clusterId + "/" + namespace + "/" + podName + "/" + containerName; }
    }
    public record AuthorizedTerminal(String auditId, String userId, String workspaceId, String tenantUid, String deptUid, String teamUid,
            String origin, Target target, Instant expiresAt, String authorization) {
        /** 握手验证后从会话属性移除JWT。Remove the JWT from session attributes after handshake verification. */
        AuthorizedTerminal withoutAuthorization() { return new AuthorizedTerminal(auditId, userId, workspaceId, tenantUid, deptUid, teamUid, origin, target, expiresAt, null); }
        /** 调试字符串不泄露认证材料。Keep authorization material out of diagnostic strings. */
        @Override public String toString() { return "AuthorizedTerminal[auditId=" + auditId + ",target=" + target.path() + "]"; }
    }
}
