package com.nurture.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常 → RFC 7807 Problem Detail。
 * ADHD 友好原则同样适用于 API 语义：错误信息明确指出原因与下一步，而非仅返回状态码。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Bad Request");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /** 频率超限（短信限频 / 登录防暴力破解锁定）：429 + Retry-After，明确告知何时可重试。 */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setTitle("Too Many Requests");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(Math.max(1, e.getRetryAfterSeconds())))
                .body(problem);
    }

    /** 外部回调凭据校验失败：401 拒绝处理，不泄露校验失败的具体原因。 */
    @ExceptionHandler(UntrustedCallbackException.class)
    public ResponseEntity<ProblemDetail> handleUntrustedCallback(UntrustedCallbackException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problem.setTitle("Unauthorized");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(org.springframework.security.access.AccessDeniedException e) {
        // 业务代码抛出的 AccessDenied 携带具体原因（如"仅预约学生本人或其已确认监护家长可下单"），
        // 优先透传；仅框架缺省（无消息）时回落到 Scope 通用提示
        String detail = (e.getMessage() == null || e.getMessage().isBlank())
                ? "当前凭证缺少所需权限作用域（Scope），请确认账号角色或 Agent Token 声明"
                : e.getMessage();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
        problem.setTitle("Forbidden");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                detail.isBlank() ? "请求参数校验未通过" : detail);
        problem.setTitle("Bad Request");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请求体缺失或格式不正确");
        problem.setTitle("Bad Request");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<ProblemDetail> handleMissingPart(org.springframework.web.multipart.support.MissingServletRequestPartException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "缺少上传分片: " + e.getRequestPartName());
        problem.setTitle("Bad Request");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "与现有数据冲突（如时段被他人抢先预约），请刷新后重试");
        problem.setTitle("Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(Exception e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "资源刚被其他操作更新，请刷新后重试");
        problem.setTitle("Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnknown(Exception e) {
        log.error("未处理异常: {}", e.toString(), e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "服务暂时不可用，请稍后重试；若持续出现请联系运营");
        problem.setTitle("Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
