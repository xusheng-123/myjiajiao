package com.nurture.shared.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 本地持久卷存储适配器 — 首版默认实现，预留 Cloudflare R2 对接通道。
 */
@Component
public class LocalFileStorageAdapter implements FileStorageService {

    private final Path baseDir;

    public LocalFileStorageAdapter(@Value("${nurture.storage.local.base-dir:./data/files}") String baseDir) throws IOException {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        Files.createDirectories(this.baseDir);
    }

    @Override
    public StoredFile store(String category, String originalFilename, String contentType, byte[] content) {
        try {
            String safeCategory = category.replaceAll("[^a-zA-Z0-9_-]", "_");
            String extension = extensionOf(originalFilename);
            String storageKey = safeCategory + "/" + UUID.randomUUID() + extension;
            Path target = resolve(storageKey);
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(tmp, content);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(storageKey, originalFilename, contentType, content.length);
        } catch (IOException e) {
            throw new IllegalStateException("文件存储写入失败", e);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException e) {
            throw new IllegalArgumentException("文件不存在或不可读: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new IllegalStateException("文件删除失败", e);
        }
    }

    /** 防目录穿越：仅允许基目录内的相对路径。 */
    private Path resolve(String storageKey) {
        Path resolved = baseDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("非法存储键");
        }
        return resolved;
    }

    private String extensionOf(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase();
    }
}
