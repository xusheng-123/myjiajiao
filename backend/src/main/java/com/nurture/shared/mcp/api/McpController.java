package com.nurture.shared.mcp.api;

import com.nurture.shared.mcp.McpToolCatalog;
import com.nurture.shared.mcp.McpToolDescriptor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * MCP 适配层入口 — 向 Agent 宿主暴露工具清单。
 * 边界硬约束：本层仅是 OpenAPI → Tool 定义的协议适配器，严禁出现任何业务规则。
 */
@RestController
@RequestMapping("/api/v1/mcp")
@Tag(name = "mcp / 适配层", description = "OpenAPI 到 Agent Tool 定义的协议适配（不含业务规则）")
public class McpController {

    private final McpToolCatalog catalog;

    public McpController(McpToolCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/tools")
    @Operation(summary = "MCP 工具清单",
            description = "供 Agent 宿主做工具发现；调用工具时走同一路径 /api/v1/*，需携带 API Token 与 Idempotency-Key")
    public Map<String, Object> tools() {
        return Map.of(
                "protocol", "mcp/1.0-adapter",
                "contract", "/api/v1/api-docs",
                "tools", catalog.all(),
                "conventions", Map.of(
                        "idempotency", "所有写操作必须携带 Idempotency-Key (UUIDv4)",
                        "twoPhase", "标记 twoPhase=true 的工具必须先 Preview 取得 confirmationToken 再 Execute",
                        "auth", "Agent 使用独立 API Token（Authorization: Bearer nur_at_...），严禁模拟人类会话"));
    }

    @GetMapping("/tools/{toolId}")
    @Operation(summary = "单个 MCP 工具定义")
    public McpToolDescriptor tool(@PathVariable String toolId) {
        return catalog.byId(toolId)
                .orElseThrow(() -> new IllegalArgumentException("工具不存在: " + toolId));
    }
}
