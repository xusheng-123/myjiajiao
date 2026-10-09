package com.nurture.shared.mcp;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP 工具目录 — 由 OpenAPI 契约机械映射而来。
 * MCP 工具层仅担任 OpenAPI 到 Agent Tool 定义的协议适配器；
 * 业务规则全部驻留在各业务模块，MCP 层不做任何规则复刻或旁路实现。
 */
@Component
public class McpToolCatalog {

    private final List<McpToolDescriptor> tools = List.of(
            new McpToolDescriptor("search_teachers", "找老师 · 组合筛选",
                    "按科目/学段/价格/风格筛选导师，结果附带透明推荐理由",
                    "GET", "/api/v1/matching/teachers:search",
                    List.of("matching:read"), false,
                    Map.of("type", "object",
                            "properties", Map.of(
                                    "subject", Map.of("type", "string"),
                                    "gradeBand", Map.of("type", "string"),
                                    "maxPricePerHourCents", Map.of("type", "integer"),
                                    "styleTag", Map.of("type", "string"),
                                    "keyword", Map.of("type", "string")))),

            new McpToolDescriptor("list_open_slots", "查询导师可约时段",
                    "返回指定导师当前可预约的档期时段",
                    "GET", "/api/v1/scheduling/teachers/{teacherId}/open-slots",
                    List.of("scheduling:read"), false,
                    Map.of("type", "object",
                            "required", List.of("teacherId"),
                            "properties", Map.of("teacherId", Map.of("type", "string", "format", "uuid")))),

            new McpToolDescriptor("create_booking", "创建预约",
                    "为学生在指定档期创建试听或正式课预约；写操作需 Idempotency-Key",
                    "POST", "/api/v1/booking/bookings",
                    List.of("booking:create"), false,
                    Map.of("type", "object",
                            "required", List.of("teacherId", "slotId", "subject"),
                            "properties", Map.of(
                                    "teacherId", Map.of("type", "string", "format", "uuid"),
                                    "slotId", Map.of("type", "string", "format", "uuid"),
                                    "subject", Map.of("type", "string"),
                                    "bookingType", Map.of("type", "string", "enum", List.of("TRIAL", "FORMAL"))))),

            new McpToolDescriptor("preview_booking_cancel", "取消预约 · Preview",
                    "预计算取消预约的级联影响（退款金额/影响课时），返回短效确认凭证",
                    "POST", "/api/v1/booking/bookings/{bookingId}:cancel/preview",
                    List.of("booking:cancel"), true,
                    Map.of("type", "object",
                            "required", List.of("bookingId", "reason"),
                            "properties", Map.of(
                                    "bookingId", Map.of("type", "string", "format", "uuid"),
                                    "reason", Map.of("type", "string")))),

            new McpToolDescriptor("execute_booking_cancel", "取消预约 · Execute",
                    "携带确认凭证终态取消预约；必须先调用 preview_booking_cancel",
                    "POST", "/api/v1/booking/bookings/{bookingId}:cancel/execute",
                    List.of("booking:cancel"), true,
                    Map.of("type", "object",
                            "required", List.of("bookingId", "reason", "confirmationToken"),
                            "properties", Map.of(
                                    "bookingId", Map.of("type", "string", "format", "uuid"),
                                    "reason", Map.of("type", "string"),
                                    "confirmationToken", Map.of("type", "string")))),

            new McpToolDescriptor("request_refund", "申请退款",
                    "对已支付订单发起退款申请",
                    "POST", "/api/v1/refund/cases",
                    List.of("refund:request"), false,
                    Map.of("type", "object",
                            "required", List.of("orderId", "reason"),
                            "properties", Map.of(
                                    "orderId", Map.of("type", "string", "format", "uuid"),
                                    "reason", Map.of("type", "string")))),

            new McpToolDescriptor("read_orders", "查询订单",
                    "按订单号或预约号查询订单与对账快照",
                    "GET", "/api/v1/order/me/orders",
                    List.of("order:read"), false,
                    Map.of("type", "object", "properties", Map.of())),

            new McpToolDescriptor("send_message", "发送站内消息",
                    "在既有咨询会话中发送消息",
                    "POST", "/api/v1/messaging/conversations/{conversationId}/messages",
                    List.of("messaging:write"), false,
                    Map.of("type", "object",
                            "required", List.of("conversationId", "body"),
                            "properties", Map.of(
                                    "conversationId", Map.of("type", "string", "format", "uuid"),
                                    "body", Map.of("type", "string")))),

            new McpToolDescriptor("submit_review", "提交课后评价",
                    "课程完成后提交双向评价（1~5 星 + 标签 + 文字）",
                    "POST", "/api/v1/review/reviews",
                    List.of("review:write"), false,
                    Map.of("type", "object",
                            "required", List.of("bookingId", "rating"),
                            "properties", Map.of(
                                    "bookingId", Map.of("type", "string", "format", "uuid"),
                                    "rating", Map.of("type", "integer", "minimum", 1, "maximum", 5),
                                    "tags", Map.of("type", "array", "items", Map.of("type", "string")),
                                    "comment", Map.of("type", "string")))));

    public List<McpToolDescriptor> all() {
        return tools;
    }

    public Optional<McpToolDescriptor> byId(String toolId) {
        return tools.stream().filter(t -> t.toolId().equals(toolId)).findFirst();
    }
}
