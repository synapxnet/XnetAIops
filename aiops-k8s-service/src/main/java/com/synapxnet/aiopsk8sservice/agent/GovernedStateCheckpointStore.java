/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 完整治理状态的原子持久保存。 Atomic persistence of complete governed state.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.agent;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

final class GovernedStateCheckpointStore implements AutoCloseable {
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private final ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path directory;
    private final Path marker;
    private final Path checkpoint;
    private final String bootstrapDigest;
    private final Durability durability;
    private final FileChannel leaseChannel;
    private final FileLock lease;
    private final boolean previouslyOwned;
    private long generation;
    private String expectedFileDigest;
    private String payloadDigest;
    private boolean failed;

    /** Linux 生产使用真实目录同步，挂载不支持时失败关闭。 Uses real directory synchronization in Linux production and fails closed on unsupported mounts. */
    GovernedStateCheckpointStore(Path directory, String bootstrapDigest) throws IOException {
        this(directory, bootstrapDigest, GovernedStateCheckpointStore::syncDirectory);
    }

    /** 为离线故障测试注入目录持久化边界，不更改生产实现。 Injects the directory durability boundary for offline fault tests without changing production behavior. */
    GovernedStateCheckpointStore(Path directory, String bootstrapDigest, Durability durability) throws IOException {
        if (!directory.isAbsolute() || !directory.normalize().equals(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !directory.toRealPath().equals(directory) || !bootstrapDigest.matches("[a-f0-9]{64}")) {
            throw new IOException("INVALID_CHECKPOINT_DIRECTORY_OR_BINDING");
        }
        this.directory = directory;
        this.marker = directory.resolve("initialized.json");
        this.checkpoint = directory.resolve("checkpoint.json");
        this.bootstrapDigest = bootstrapDigest;
        this.durability = durability;
        Path lock = directory.resolve("writer.lock");
        previouslyOwned = Files.exists(lock, LinkOption.NOFOLLOW_LINKS);
        if (Files.isSymbolicLink(lock)) throw new IOException("CHECKPOINT_LOCK_SYMLINK");
        leaseChannel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        FileLock acquired = null;
        try {
            acquired = leaseChannel.tryLock();
            if (acquired == null) throw new IOException("CHECKPOINT_ALREADY_OWNED");
            restrict(lock);
        } catch (Exception error) {
            if (acquired != null) acquired.release();
            leaseChannel.close();
            throw new IOException("CHECKPOINT_LOCK_FAILED", error);
        }
        lease = acquired;
    }

    /** 已初始化标记和 checkpoint 必须同时存在，缺失时不回退旧快照。 Requires both the initialization marker and checkpoint; never falls back to a stale snapshot. */
    boolean initialized() throws IOException {
        requireHealthy();
        boolean marked = Files.exists(marker, LinkOption.NOFOLLOW_LINKS);
        boolean saved = Files.exists(checkpoint, LinkOption.NOFOLLOW_LINKS);
        if (marked != saved) throw fail("CHECKPOINT_INITIALIZATION_INCOMPLETE", null);
        if (!marked && previouslyOwned) throw fail("CHECKPOINT_MARKERS_MISSING", null);
        return marked;
    }

    /** 先锁定不可复用的初始化身份，再保存初始完整状态。 Commits a non-reusable initialization identity before saving the initial complete state. */
    void initialize(JsonNode payload) throws IOException {
        requireHealthy();
        if (initialized()) throw fail("CHECKPOINT_ALREADY_INITIALIZED", null);
        ObjectNode identity = mapper.createObjectNode();
        identity.put("schema", "openxnet.governed-checkpoint-initialized.v1");
        identity.put("bootstrapDigest", bootstrapDigest);
        try {
            writeAtomic(marker, mapper.writeValueAsBytes(identity), false);
            writeCheckpoint(payload, 1L);
        } catch (Exception error) {
            throw fail("CHECKPOINT_INITIALIZATION_FAILED", error);
        }
    }

    /** 恢复经过摘要、代数和初始身份验证的完整状态。 Restores complete state validated against its digest, generation and initialization identity. */
    JsonNode load() throws IOException {
        requireHealthy();
        if (!initialized()) throw fail("CHECKPOINT_REQUIRED", null);
        try {
            JsonNode identity = readJson(marker);
            if (identity.size() != 2 || !"openxnet.governed-checkpoint-initialized.v1".equals(identity.path("schema").asText())
                    || !bootstrapDigest.equals(identity.path("bootstrapDigest").asText())) {
                throw new IOException("CHECKPOINT_INITIALIZATION_BINDING_MISMATCH");
            }
            byte[] bytes = readBounded(checkpoint);
            JsonNode record = mapper.readTree(bytes);
            if (!record.isObject() || record.size() != 5 || !"openxnet.governed-checkpoint.v1".equals(record.path("schema").asText())
                    || !bootstrapDigest.equals(record.path("bootstrapDigest").asText())
                    || !record.path("generation").isIntegralNumber() || !record.path("generation").canConvertToLong()
                    || record.path("generation").longValue() < 1 || !record.path("payload").isObject()) {
                throw new IOException("CHECKPOINT_SCHEMA_MISMATCH");
            }
            String computed = digest(mapper.writeValueAsBytes(canonical(record.required("payload"))));
            if (!computed.equals(record.path("payloadSha256").asText())) throw new IOException("CHECKPOINT_PAYLOAD_DIGEST_MISMATCH");
            generation = record.path("generation").longValue();
            expectedFileDigest = digest(bytes);
            payloadDigest = computed;
            return record.required("payload").deepCopy();
        } catch (Exception error) {
            throw fail("CHECKPOINT_RESTORE_FAILED", error);
        }
    }

    /** 请求前检查文件未被删除、损坏或替换。 Checks that the checkpoint was not deleted, corrupted or replaced before serving a request. */
    void verifyCurrent() throws IOException {
        requireHealthy();
        try {
            if (expectedFileDigest == null || !expectedFileDigest.equals(digest(readBounded(checkpoint)))) {
                throw new IOException("CHECKPOINT_CHANGED_OUTSIDE_OWNER");
            }
            JsonNode identity = readJson(marker);
            if (identity.size() != 2 || !bootstrapDigest.equals(identity.path("bootstrapDigest").asText())
                    || !"openxnet.governed-checkpoint-initialized.v1".equals(identity.path("schema").asText())) {
                throw new IOException("CHECKPOINT_INITIALIZATION_BINDING_MISMATCH");
            }
        } catch (Exception error) {
            throw fail("CHECKPOINT_CURRENT_STATE_UNAVAILABLE", error);
        }
    }

    /** 完整状态变化时落盘后才返回，任何失败永久阻断本进程。 Persists changed complete state before returning; any failure permanently blocks this process. */
    void commit(JsonNode payload) throws IOException {
        verifyCurrent();
        try {
            if (payloadDigest.equals(digest(mapper.writeValueAsBytes(canonical(payload))))) return;
            if (generation == Long.MAX_VALUE) throw new IOException("CHECKPOINT_GENERATION_EXHAUSTED");
            writeCheckpoint(payload, generation + 1);
        } catch (Exception error) {
            throw fail("CHECKPOINT_COMMIT_FAILED", error);
        }
    }

    /** 封装完整内容和摘要，以单文件原子提交。 Wraps the full payload and digest into one atomically committed file. */
    private void writeCheckpoint(JsonNode payload, long nextGeneration) throws IOException {
        if (!payload.isObject()) throw new IOException("INVALID_CHECKPOINT_PAYLOAD");
        JsonNode canonicalPayload = canonical(payload);
        String computed = digest(mapper.writeValueAsBytes(canonicalPayload));
        ObjectNode record = mapper.createObjectNode();
        record.put("schema", "openxnet.governed-checkpoint.v1");
        record.put("generation", nextGeneration);
        record.put("bootstrapDigest", bootstrapDigest);
        record.put("payloadSha256", computed);
        record.set("payload", canonicalPayload);
        byte[] bytes = mapper.writeValueAsBytes(record);
        if (bytes.length > MAX_BYTES) throw new IOException("CHECKPOINT_TOO_LARGE");
        writeAtomic(checkpoint, bytes, nextGeneration > 1);
        expectedFileDigest = digest(bytes);
        payloadDigest = computed;
        generation = nextGeneration;
    }

    /** 同目录临时文件强制落盘后原子替换，并强制同步目录。 Forces a sibling temporary file, atomically replaces the target, then forces its directory. */
    private void writeAtomic(Path target, byte[] bytes, boolean replace) throws IOException {
        if (Files.isSymbolicLink(target) || (!replace && Files.exists(target, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("CHECKPOINT_TARGET_CONFLICT");
        }
        Path temporary = directory.resolve(".checkpoint-" + UUID.randomUUID() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                restrict(temporary);
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            durability.synchronize(directory, "before-replace");
            if (replace) Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            durability.synchronize(directory, "after-replace");
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 限制读取文件类型和大小，不跟随符号链接。 Bounds file size and type without following symbolic links. */
    private byte[] readBounded(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES) throw new IOException("INVALID_CHECKPOINT_FILE");
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size > MAX_BYTES) throw new IOException("CHECKPOINT_TOO_LARGE");
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw new IOException("CHECKPOINT_CHANGED_DURING_READ");
            }
            if (channel.size() != size || channel.read(ByteBuffer.allocate(1)) != -1) throw new IOException("CHECKPOINT_CHANGED_DURING_READ");
            return buffer.array();
        }
    }

    /** 严格读取 JSON 对象。 Reads a strict JSON object. */
    private JsonNode readJson(Path file) throws IOException {
        JsonNode value = mapper.readTree(readBounded(file));
        if (value == null || !value.isObject()) throw new IOException("INVALID_CHECKPOINT_JSON");
        return value;
    }

    /** 对象字段排序以保证跨进程稳定摘要，数组保持原顺序。 Sorts object fields for stable cross-process digests while retaining array order. */
    private JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            java.util.TreeSet<String> names = new java.util.TreeSet<>();
            value.fieldNames().forEachRemaining(names::add);
            for (String name : names) result.set(name, canonical(value.get(name)));
            return result;
        }
        if (value.isArray()) {
            var result = mapper.createArrayNode();
            value.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return value.deepCopy();
    }

    /** 计算持久化内容摘要。 Computes the persisted-content digest. */
    private static String digest(byte[] bytes) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception error) { throw new IOException("CHECKPOINT_DIGEST_UNAVAILABLE", error); }
    }

    /** 生产环境真实同步目录，Windows 离线测试必须显式注入替身。 Synchronizes the directory in production; Windows offline tests explicitly inject a substitute. */
    private static void syncDirectory(Path directory, String phase) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }

    /** POSIX 挂载限定属主访问，其他文件系统保留其访问控制。 Restricts owner permissions on POSIX mounts while retaining native ACLs elsewhere. */
    private static void restrict(Path file) throws IOException {
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }

    /** 故障后所有治理请求失败关闭。 Fails all governed requests closed after a persistence fault. */
    private void requireHealthy() throws IOException {
        if (failed || !lease.isValid()) throw new IOException("CHECKPOINT_STORE_POISONED");
    }

    /** 记录本进程无法继续服务的状态。 Records that this process cannot continue serving state. */
    private IOException fail(String code, Throwable cause) {
        failed = true;
        return new IOException(code, cause);
    }

    /** 正常关闭释放独占写锁。 Releases exclusive writer ownership on normal shutdown. */
    @Override
    public void close() throws IOException {
        failed = true;
        try { if (lease.isValid()) lease.release(); } finally { leaseChannel.close(); }
    }

    /** 提供文件系统持久化故障注入点。 Provides a filesystem durability fault-injection boundary. */
    @FunctionalInterface
    interface Durability {
        /** 强制目录或在离线测试中注入指定阶段故障。 Forces the directory or injects a phase-specific offline test fault. */
        void synchronize(Path directory, String phase) throws IOException;
    }
}
