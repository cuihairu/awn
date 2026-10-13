package site.awn.shared.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Outbox 载荷读取助手：事件载荷为 JSON 文本（落库 JSONB）。
 */
public final class OutboxPayloads {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
