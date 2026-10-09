package com.nurture.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT 签发与校验 — HS256 对称密钥，生产密钥经环境变量注入。
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_SCOPES = "scopes";

    private final SecretKey key;
    private final String issuer;
    private final long accessTtlMinutes;

    public JwtService(SecurityProperties properties) {
        SecurityProperties.Jwt jwt = properties.jwt();
        this.key = Keys.hmacShaKeyFor(jwt.secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = jwt.issuer();
        this.accessTtlMinutes = jwt.accessTokenTtlMinutes();
    }

    public String issueAccessToken(UUID accountId, CallerRole role, String displayName, Set<Scope> scopes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(accountId.toString())
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_NAME, displayName)
                .claim(CLAIM_SCOPES, scopes.stream().map(Scope::code).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlMinutes * 60)))
                .signWith(key)
                .compact();
    }

    /** 校验并解析；失败抛出 io.jsonwebtoken.JwtException 系列异常。 */
    public CallerPrincipal verify(String jwt) {
        Claims claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                .parseSignedClaims(jwt).getPayload();
        CallerRole role = CallerRole.valueOf(claims.get(CLAIM_ROLE, String.class));
        Set<Scope> scopes = ((List<String>) claims.getOrDefault(CLAIM_SCOPES, List.of())).stream()
                .map(Scope::fromCode)
                .filter(s -> s != null)
                .collect(Collectors.toUnmodifiableSet());
        return new CallerPrincipal(UUID.fromString(claims.getSubject()), role, claims.get(CLAIM_NAME, String.class), scopes);
    }

    /** 人类角色的默认作用域展开；Agent 不适用（Agent 按 Token 显式声明）。 */
    public static Set<Scope> defaultScopesFor(CallerRole role) {
        return switch (role) {
            case STUDENT -> Set.of(Scope.STUDENT_READ, Scope.STUDENT_WRITE, Scope.MATCHING_READ, Scope.SCHEDULING_READ,
                    Scope.BOOKING_READ, Scope.BOOKING_CREATE, Scope.BOOKING_CANCEL, Scope.CLASSROOM_JOIN,
                    Scope.ORDER_READ, Scope.ORDER_CREATE, Scope.REVIEW_READ, Scope.REVIEW_WRITE,
                    Scope.MESSAGING_READ, Scope.MESSAGING_WRITE);
            case PARENT -> Set.of(Scope.PARENT_READ, Scope.PARENT_WRITE, Scope.STUDENT_READ, Scope.MATCHING_READ,
                    Scope.SCHEDULING_READ, Scope.BOOKING_READ, Scope.BOOKING_CREATE, Scope.BOOKING_CANCEL,
                    Scope.ORDER_READ, Scope.ORDER_CREATE, Scope.PAYMENT_READ, Scope.REFUND_READ, Scope.REFUND_REQUEST,
                    Scope.REVIEW_READ, Scope.REVIEW_WRITE, Scope.MESSAGING_READ, Scope.MESSAGING_WRITE);
            case TEACHER -> Set.of(Scope.TEACHER_READ, Scope.TEACHER_WRITE, Scope.SCHEDULING_READ, Scope.SCHEDULING_WRITE,
                    Scope.BOOKING_READ, Scope.CLASSROOM_JOIN, Scope.ORDER_READ, Scope.REVIEW_READ, Scope.REVIEW_WRITE,
                    Scope.MESSAGING_READ, Scope.MESSAGING_WRITE);
            case ADMIN -> Set.of(Scope.ADMIN_READ, Scope.ADMIN_WRITE, Scope.AUDIT_READ, Scope.MATCHING_READ,
                    Scope.BOOKING_READ, Scope.ORDER_READ, Scope.PAYMENT_READ, Scope.REFUND_READ,
                    Scope.REVIEW_READ, Scope.MESSAGING_READ);
            case AGENT -> Set.of();
        };
    }
}
