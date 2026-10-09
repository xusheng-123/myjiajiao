package com.nurture.shared.storage;

/**
 * 文件存储标准端口 — 核心代码只依赖本接口。
 * 首版使用受限本地持久卷实现；后续对接 Cloudflare R2 时新增适配器即可，业务零改动。
 */
public interface FileStorageService {

    StoredFile store(String category, String originalFilename, String contentType, byte[] content);

    byte[] load(String storageKey);

    void delete(String storageKey);

    record StoredFile(String storageKey, String originalFilename, String contentType, long sizeBytes) {
    }
}
