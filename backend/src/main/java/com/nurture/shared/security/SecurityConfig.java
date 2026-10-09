package com.nurture.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 无状态安全配置 — 统一鉴权门户放行，业务 API 一律需有效凭证与 Scope。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    /** OpenAPI/Swagger 匿名访问开关：开发默认开放，生产置 false（契约端点转为需登录，调试 UI 不再匿名可用）。 */
    @org.springframework.beans.factory.annotation.Value("${nurture.security.apiDocsPublic:true}")
    private boolean apiDocsPublic;

    /**
     * CORS 允许源清单（Release P1-2）：逗号分隔，经环境变量 NURTURE_CORS_ALLOWED_ORIGINS 注入；
     * 禁止通配符 *。生产推荐前后端同源（Cloudflare 同域 /api 路由），此清单仅服务跨源部署形态。
     */
    @org.springframework.beans.factory.annotation.Value("${nurture.security.cors-allowed-origins:http://localhost:4200}")
    private String[] corsAllowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 统一鉴权门户、支付回调与沙箱网关放行（回调/沙箱靠网关签名验签保护，非 JWT）
                        .requestMatchers("/api/v1/identity/auth/**", "/api/v1/payment/callbacks/**",
                                "/api/v1/payment/sandbox/**").permitAll()
                        // P0-2 健康检查：仅放行 health 与探针（actuator 其余端点未暴露且不在此放行）
                        .requestMatchers("/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // OpenAPI 契约与调试 UI：开发默认开放（D6）；生产置 false 后转为需登录
                        .requestMatchers(apiDocsPublic
                                ? new String[]{"/api/v1/api-docs/**", "/api/v1/swagger-ui/**",
                                        "/api/v1/swagger-ui.html", "/swagger-ui/**", "/webjars/**"}
                                : new String[]{})
                        .permitAll()
                        // 其余一律鉴权
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许源经环境变量注入（默认本地开发端口）；同域部署（Cloudflare /api 路由）不触发 CORS
        config.setAllowedOriginPatterns(List.of(corsAllowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Confirmation-Token"));
        config.setExposedHeaders(List.of("Location", "X-Idempotent-Replay"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
