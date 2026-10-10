package com.nurture.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurture.shared.security.CallerContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency-Key 强幂等过滤器 — 所有有状态写操作（POST/PUT/PATCH/DELETE）强制校验。
 * 依据 PostgreSQL 唯一索引防重；命中且已完成则重放缓存响应（X-Idempotent-Replay: true）。
 */
@Component
@Order(60)
public class IdempotencyFilter extends OncePerRequestFilter {

    static final String HEADER = "Idempotency-Key";
    static final String REPLAY_HEADER = "X-Idempotent-Replay";

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 登录门户、支付回调与沙箱网关通知发生在鉴权主体建立之前/之外，走各自防重机制；
        // 其余有状态写操作一律强制幂等键
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/identity/auth/") || uri.startsWith("/api/v1/payment/callbacks/")
                || uri.startsWith("/api/v1/payment/sandbox/")) {
            return true;
        }
        return switch (request.getMethod()) {
            case "POST", "PUT", "PATCH", "DELETE" -> false;
            default -> true;
        };
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            respondProblem(response, 400, "缺少幂等键：写操作必须在 HTTP 报头携带 Idempotency-Key (UUIDv4)");
            return;
        }
        if (!isValidUuid(key)) {
            respondProblem(response, 400, "Idempotency-Key 必须为 UUIDv4 格式");
            return;
        }
        UUID callerId = currentCallerId();
        if (callerId == null) {
            respondProblem(response, 401, "幂等键必须与有效调用者凭证同时提交");
            return;
        }

        // multipart 请求：先解析分片并按「分片名 + 文件名 + 内容」计算真实指纹——
        // 若以空串占位，同键不同文件的请求会被静默重放；容器会缓存已解析分片，下游处理器仍可正常读取。
        // 非 multipart：缓存请求体一次读取，指纹与业务处理器共用同一份内容。
        boolean multipart = request.getContentType() != null
                && request.getContentType().toLowerCase().startsWith("multipart/");
        String fingerprint;
        CachedBodyRequest wrappedRequest = null;
        if (multipart) {
            try {
                fingerprint = multipartFingerprint(request);
            } catch (IllegalStateException e) {
                // 质量加固：超限请求在此拦截返回 413，避免异常沿过滤链落到 /error 被安全链包装成 403
                if (hasSizeLimitCause(e)) {
                    respondProblem(response, 413,
                            "文件超过大小限制：单个文件最大 20MB，单次请求（含全部材料）最大 45MB，请压缩后重试");
                    return;
                }
                throw e;
            }
        } else {
            byte[] bodyBytes = StreamUtils.copyToByteArray(request.getInputStream());
            fingerprint = sha256(new String(bodyBytes, StandardCharsets.UTF_8));
            wrappedRequest = new CachedBodyRequest(request, bodyBytes);
        }

        Optional<IdempotencyRecord> existing =
                repository.findByCallerIdAndHttpMethodAndRequestUriAndIdempotencyKey(
                        callerId, request.getMethod(), request.getRequestURI(), key);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestFingerprint().equals(fingerprint)) {
                respondProblem(response, 409, "幂等冲突：相同 Idempotency-Key 对应不同的请求载荷");
                return;
            }
            if (record.completed()) {
                response.setStatus(record.getResponseStatus());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader(REPLAY_HEADER, "true");
                response.getWriter().write(record.getResponseSnapshot());
                return;
            }
            // 同键并发首处理中：拒绝重复提交，由唯一索引兜底
            respondProblem(response, 409, "相同幂等键的请求正在处理中，请稍后查询结果");
            return;
        }

        try {
            repository.saveAndFlush(new IdempotencyRecord(callerId, request.getMethod(),
                    request.getRequestURI(), key, fingerprint));
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            respondProblem(response, 409, "相同幂等键的请求正在处理中，请稍后查询结果");
            return;
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(wrappedRequest != null ? wrappedRequest : request, responseWrapper);
        } finally {
            int status = response.getStatus();
            if (status >= 200 && status < 300) {
                repository.findByCallerIdAndHttpMethodAndRequestUriAndIdempotencyKey(
                                callerId, request.getMethod(), request.getRequestURI(), key)
                        .ifPresent(record -> {
                            record.recordResponse(status,
                                    responseWrapper.getContentAsByteArray().length > 0
                                            ? new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8)
                                            : "{}");
                            repository.save(record); // 落库快照，幂等重放依赖它
                        });
            } else {
                // 失败响应不留存快照，允许客户端修正后重试
                repository.findByCallerIdAndHttpMethodAndRequestUriAndIdempotencyKey(
                                callerId, request.getMethod(), request.getRequestURI(), key)
                        .ifPresent(repository::delete);
            }
            responseWrapper.copyBodyToResponse();
        }
    }

    private UUID currentCallerId() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context.getAuthentication() != null
                && context.getAuthentication().getPrincipal() instanceof com.nurture.shared.security.CallerPrincipal p) {
            return p.accountId();
        }
        return null;
    }

    /**
     * multipart 真实指纹：对全部分片按「分片名 + 提交文件名 + 内容」排序后逐一哈希。
     * getParts() 触发容器解析并缓存分片，下游业务处理器可重复读取（Tomcat 语义）。
     */
    private String multipartFingerprint(HttpServletRequest request) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        try {
            request.getParts().stream()
                    .sorted(java.util.Comparator.comparing(Part::getName)
                            .thenComparing(p -> p.getSubmittedFileName() == null ? "" : p.getSubmittedFileName()))
                    .forEach(part -> {
                        digest.update(part.getName().getBytes(StandardCharsets.UTF_8));
                        digest.update((part.getSubmittedFileName() == null ? "-" : part.getSubmittedFileName())
                                .getBytes(StandardCharsets.UTF_8));
                        try (java.io.InputStream in = part.getInputStream()) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = in.read(buffer)) > 0) {
                                digest.update(buffer, 0, read);
                            }
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        } catch (IOException | ServletException e) {
            throw new IllegalStateException("multipart 分片解析失败", e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * 超过 multipart 大小限制时容器抛 ServletException——cause 可能为 Spring 的
     * MaxUploadSizeExceededException，也可能为 Tomcat 原生 FileSizeLimitExceededException
     * （getParts() 直通容器解析，不经 Spring 包装；按类名识别，避免依赖容器内部 API）。
     */
    private boolean hasSizeLimitCause(Throwable e) {
        for (Throwable cur = e; cur != null; cur = cur.getCause()) {
            if (cur instanceof org.springframework.web.multipart.MaxUploadSizeExceededException) {
                return true;
            }
            if (cur.getClass().getName().endsWith("FileSizeLimitExceededException")) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String sha256(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void respondProblem(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                java.util.Map.of("title", status == 400 ? "Bad Request" : "Conflict",
                        "status", status, "detail", detail));
    }

    /** 缓存请求体以便内容重复读取（指纹校验 + 业务处理器）。 */
    static final class CachedBodyRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            java.io.ByteArrayInputStream buffer = new java.io.ByteArrayInputStream(body);
            return new jakarta.servlet.ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return buffer.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return buffer.read();
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
