package com.nurture.shared.mcp;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具描述符 — OpenAPI 操作到 Agent Tool 定义的协议适配元数据。
 * 仅描述「工具名、方法、路径、所需 Scope、参数 Schema 引用」，严禁在此承载业务规则。
 */
public record McpToolDescriptor(
        String toolId,
        String title,
        String description,
        String httpMethod,
        String path,
        List<String> requiredScopes,
        boolean twoPhase,
        Map<String, Object> parameterSchema
) {
}
