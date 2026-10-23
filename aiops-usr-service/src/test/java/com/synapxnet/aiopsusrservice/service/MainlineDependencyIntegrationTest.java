/* Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 隔离依赖验证 / Isolated dependency validation.
 * Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsusrservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** 仅显式连接本机专用端口，不读取生产配置。 Use explicit local test ports only, never production settings. */
@EnabledIfSystemProperty(named = "mainline.integration", matches = "true")
class MainlineDependencyIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 使用真实Redis验证Lua限流、次数限制、过期和一次性消费；短信仅发往本机HTTP接收器。 Verify Redis Lua with a local-only SMS receiver. */
    @Test
    void consumesOneTimeCodesInRealRedis() throws Exception {
        var config = new RedisStandaloneConfiguration("127.0.0.1", 16379);
        config.setDatabase(15);
        var factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        factory.start();
        var redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var delivered = new AtomicReference<String>();
        server.createContext("/sms", exchange -> {
            var body = JSON.readTree(exchange.getRequestBody());
            delivered.set(body.path("code").asText());
            byte[] response = "{\"accepted\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        String phone = "139" + String.format("%08d", Math.floorMod(System.nanoTime(), 100_000_000));
        String key = "xnet:aiops:login:" + phone;
        try {
            var service = new LoginVerificationService(redis, JSON,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/sms", "local-test-only");
            service.send(phone);
            assertTrue(delivered.get().matches("[0-9]{6}"));
            assertTrue(redis.getExpire(key) > 0 && redis.getExpire(key) <= 300);
            assertThrows(ResponseStatusException.class, () -> service.send(phone));
            String wrong = delivered.get().equals("000000") ? "111111" : "000000";
            assertFalse(service.verify(phone, wrong));
            assertTrue(service.verify(phone, delivered.get()));
            assertFalse(service.verify(phone, delivered.get()));
            redis.delete(key + ":send");
            service.send(phone);
            wrong = delivered.get().equals("000000") ? "111111" : "000000";
            for (int attempt = 0; attempt < 5; attempt++) assertFalse(service.verify(phone, wrong));
            assertFalse(redis.hasKey(key));
            redis.delete(key + ":send");
            service.send(phone);
            redis.expire(key, Duration.ofMillis(5));
            Thread.sleep(20);
            assertFalse(service.verify(phone, delivered.get()));
        } finally {
            redis.delete(List.of(key, key + ":send"));
            server.stop(0);
            factory.destroy();
        }
    }

    /** 在随机隔离schema执行实际维护SQL，验证默认预览及精确归档边界。 Execute maintenance SQL in an isolated schema and verify its preview and archive boundaries. */
    @Test
    void archivesOnlyUnchangedSeedIdentityInMySql() throws Exception {
        Path settings = Path.of(System.getProperty("mainline.dependencies.file"));
        String password = JSON.readTree(Files.readString(settings)).path("mysql").path("password").asText();
        assertFalse(password.isBlank());
        String schema = "aiops_mainline_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection db = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/?useSSL=false&allowPublicKeyRetrieval=true", "root", password)) {
            execute(db, "CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
            try {
                execute(db, "USE " + schema);
                Path root = Path.of(System.getProperty("basedir")).getParent();
                String source = Files.readString(root.resolve("XnetAIops.sql"));
                for (String table : List.of("xnet_aiops_usr_user", "xnet_aiops_usr_user_role_cluster",
                        "xnet_aiops_usr_organization_membership", "xnet_aiops_usr_session", "xnet_aiops_sys_team")) {
                    var pattern = Pattern.compile("CREATE TABLE " + table + " \\([\\s\\S]*?;");
                    var match = pattern.matcher(source);
                    assertTrue(match.find(), table);
                    execute(db, match.group());
                }
                execute(db, "INSERT INTO xnet_aiops_usr_user(id,uid,username,password,phone) VALUES (1,'USR-GOAI-OPERATOR','goai_operator','local-seed-only','17870171303'),(2,'USER-REAL','real_user','real-test-only','17870171303')");
                execute(db, "INSERT INTO xnet_aiops_usr_user_role_cluster(user_id,role_id) VALUES (1,1),(2,1)");
                execute(db, "INSERT INTO xnet_aiops_sys_team(uid,dept_uid,team_name) VALUES ('TEAM-GOAI-INFRA','dept','GOAI Infrastructure 联合团队')");
                execute(db, "INSERT INTO xnet_aiops_usr_organization_membership(user_id,tenant_uid,team_uid) VALUES (1,'tenant','TEAM-GOAI-INFRA'),(2,'tenant','TEAM-GOAI-INFRA')");
                execute(db, "INSERT INTO xnet_aiops_usr_session(user_id,token,expire_at) VALUES (1,'test-only-one',DATE_ADD(NOW(),INTERVAL 1 DAY)),(2,'test-only-two',DATE_ADD(NOW(),INTERVAL 1 DAY))");
                String script = Files.readString(root.resolve("database/maintenance/archive-competition-identities.sql"));
                // 只替换样本密码指纹，不把旧凭据写入测试。 Replace only the fixture fingerprint; never embed historical credentials in tests.
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("local-seed-only".getBytes(StandardCharsets.UTF_8)));
                script = script.replaceFirst("SET @seed_password_sha256 = '[^']+';", "SET @seed_password_sha256 = '" + digest + "';");
                executeScript(db, script);
                assertEquals(2, count(db, "SELECT COUNT(*) FROM xnet_aiops_usr_session"));
                assertEquals(0, count(db, "SELECT COUNT(*) FROM archive_aiops_goai_users_20261023"));
                // 旧归档冲突应阻断所有撤销。 A conflicting prior archive must block every mutation.
                execute(db, "INSERT INTO archive_aiops_goai_users_20261023 SELECT * FROM xnet_aiops_usr_user WHERE id=1");
                execute(db, "UPDATE archive_aiops_goai_users_20261023 SET password='conflicting-test-only' WHERE id=1");
                executeScript(db, script.replace("SET @apply_goai_cleanup = 0;", "SET @apply_goai_cleanup = 1;"));
                assertEquals(2, count(db, "SELECT COUNT(*) FROM xnet_aiops_usr_session"));
                assertEquals(1, count(db, "SELECT COUNT(*) FROM xnet_aiops_usr_user WHERE id=1 AND status='active'"));
                execute(db, "DELETE FROM archive_aiops_goai_users_20261023 WHERE id=1");
                executeScript(db, script.replace("SET @apply_goai_cleanup = 0;", "SET @apply_goai_cleanup = 1;"));
                assertEquals(1, count(db, "SELECT COUNT(*) FROM xnet_aiops_usr_user WHERE id=1 AND status='disabled'"));
                assertEquals(1, count(db, "SELECT COUNT(*) FROM xnet_aiops_usr_user WHERE id=2 AND status='active'"));
                assertEquals(0, count(db, "SELECT COUNT(*) FROM xnet_aiops_usr_session WHERE user_id=1"));
                assertEquals(1, count(db, "SELECT COUNT(*) FROM xnet_aiops_usr_session WHERE user_id=2"));
                assertEquals(1, count(db, "SELECT COUNT(*) FROM archive_aiops_goai_roles_20261023"));
                assertEquals(1, count(db, "SELECT COUNT(*) FROM xnet_aiops_sys_team WHERE status=1"));
                execute(db, "UPDATE xnet_aiops_usr_user SET password='changed-test-only',status='active' WHERE id=1");
                executeScript(db, script.replace("SET @apply_goai_cleanup = 0;", "SET @apply_goai_cleanup = 1;"));
                assertEquals(1, count(db, "SELECT COUNT(*) FROM xnet_aiops_usr_user WHERE id=1 AND status='active'"));
            } finally {
                execute(db, "DROP DATABASE " + schema);
            }
        }
    }

    /** 顺序执行已审阅的单条SQL。 Execute one reviewed SQL statement. */
    private static void execute(Connection db, String sql) throws Exception {
        try (var statement = db.createStatement()) { statement.execute(sql); }
    }

    /** 去掉行注释后按分号执行维护脚本，不改动业务语句。 Run maintenance statements after stripping line comments. */
    private static void executeScript(Connection db, String script) throws Exception {
        for (String statement : script.replaceAll("(?m)^--[^\\r\\n]*", "").split(";")) {
            if (!statement.isBlank()) execute(db, statement.trim());
        }
    }

    /** 读取可断言的行数，禁止返回敏感字段。 Read counts only, never sensitive values. */
    private static int count(Connection db, String query) throws Exception {
        try (var statement = db.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
