package com.nurture.identity.api;

import com.nurture.identity.app.IdentityApplicationService;
import com.nurture.shared.security.CallerContext;
import com.nurture.shared.security.CallerRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Agent API Token 管理 — 独立智能体身份的凭证签发与吊销（仅运营治理角色可签发）。
 */
@RestController
@RequestMapping("/api/v1/identity/api-tokens")
@Tag(name = "identity / Agent API Token", description = "AI Agent 独立 Service Account 凭证：签发（明文仅展示一次）、吊销")
@SecurityRequirement(name = "bearerJWT")
public class ApiTokenController {

    private final IdentityApplicationService identityService;

    public ApiTokenController(IdentityApplicationService identityService) {
        this.identityService = identityService;
    }

    public record IssueApiTokenRequest(@NotBlank String name,
                                       @NotEmpty List<String> scopes,
                                       long validDays) {
    }

    public record IssuedApiTokenResponse(String plaintextTokenOnce, UUID tokenId, Instant expiresAt,
                                         String warning) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "签发 Agent API Token",
            description = "明文仅在本次响应中展示一次，平台仅存不可逆加盐哈希；Scope 遵循最小权限原则")
    public IssuedApiTokenResponse issue(@RequestBody IssueApiTokenRequest request) {
        var issued = identityService.issueApiToken(CallerContext.require().accountId(),
                request.name(), request.scopes(), request.validDays());
        return new IssuedApiTokenResponse(issued.plaintextOnce(), issued.tokenId(), issued.expiresAt(),
                "请立即妥善保存 Token 明文，关闭本页后将无法再次查看；泄露时立即吊销。");
    }

    @DeleteMapping("/{tokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "即时吊销 Agent API Token")
    public void revoke(@PathVariable UUID tokenId) {
        if (CallerContext.require().role() != CallerRole.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("仅运营治理角色可吊销 Token");
        }
        identityService.revokeApiToken(tokenId);
    }
}
