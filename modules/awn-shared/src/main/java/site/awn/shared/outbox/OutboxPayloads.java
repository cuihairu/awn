package site.awn.shared.outbox;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox 载荷读取助手：事件载荷为 JSON 文本（落库 JSONB）。
 */
public final class OutboxPayloads {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private OutboxPayloads() {
    }

    /** 读取字符串字段；缺失返回 null */
    public static String string(String payload, String field) {
        try {
            JsonNode node = MAPPER.readTree(payload);
            return node.path(field).isMissingNode() ? null : node.get(field).asText();
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid outbox payload: " + e.getMessage(), e);
        }
    }

    /** 读取数值字段；缺失返回 null */
    public static Long longValue(String payload, String field) {
        try {
            JsonNode node = MAPPER.readTree(payload);
            return node.path(field).isMissingNode() || node.get(field).isNull()
                    ? null : node.get(field).asLong();
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid outbox payload: " + e.getMessage(), e);
        }
    }
}
