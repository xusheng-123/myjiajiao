package com.nurture.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;

/**
 * OpenAPI 3.0 契约配置 — 人类 Web 与 AI Agent 共享同一套 /api/v1/* 语义。
 * 写操作自动携带 Idempotency-Key 参数描述（PRD 验收：写接口描述幂等与错误码）。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI nurtureOpenApi() {
        String bearerScheme = "bearerJWT";
        String agentTokenScheme = "agentApiToken";
        return new OpenAPI()
                .info(new Info()
                        .title("润物细无声 · Nurture API")
                        .description("""
                                线上学术精修与课后答疑平台 — RESTful 契约（/api/v1/*）。

                                ## 双主权客户端模型
                                - 人类 Web：Bearer JWT（登录门户签发）
                                - AI Agent：独立 Service Account API Token（`Authorization: Bearer nur_at_...`），
                                  严禁模拟人类浏览器 Cookie 会话

                                ## 幂等性
                                所有写操作（POST/PUT/PATCH/DELETE）必须在报头携带 `Idempotency-Key: <UUIDv4>`。

                                ## 两阶段执行
                                退款、取消课时等不可逆动作强制 Preview → Execute 两阶段：
                                Preview 返回短效 `confirmationToken`，Execute 携带 `X-Confirmation-Token` 终态确认。""")
                        .version("v1.0.0"))
                .components(new Components()
                        .addSecuritySchemes(bearerScheme, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes(agentTokenScheme, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")))
                .addSecurityItem(new SecurityRequirement().addList(bearerScheme));
    }

    /** 写操作自动补 Idempotency-Key / X-Confirmation-Token 头参数描述。 */
    @Bean
    public OperationCustomizer writeOperationHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            if (!isWriteOperation(handlerMethod)) {
                return operation;
            }
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name("Idempotency-Key")
                    .required(true)
                    .description("UUIDv4；相同键+相同载荷的重试将重放首次响应，防止重复扣费/重复预约")
                    .schema(new io.swagger.v3.oas.models.media.StringSchema()
                            .example("3f2b8c1e-7d4a-4c2b-9e5f-1a2b3c4d5e6f")));
            if (operation.getOperationId() != null
                    && (operation.getOperationId().contains("cancelExecute")
                    || operation.getOperationId().contains("arbitrationExecute"))) {
                operation.addParametersItem(new Parameter()
                        .in("header")
                        .name("X-Confirmation-Token")
                        .required(false)
                        .description("两阶段执行：Preview 返回的短效确认凭证（当前契约经由请求体 confirmationToken 字段传递）")
                        .schema(new io.swagger.v3.oas.models.media.StringSchema()));
            }
            return operation;
        };
    }

    private boolean isWriteOperation(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethod().getAnnotations())
                .anyMatch(a -> a.annotationType().getSimpleName().matches("(Post|Put|Patch|Delete)Mapping"));
    }
}
