package run.halo.bangumi.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * 插件设置，来自 ConfigMap {@code plugin-bangumi-configmap}。
 *
 * <p>Halo 保存设置有两种形态：
 * <ul>
 *   <li>后台表单按分组保存：{@code data.basic} 是一段 JSON，
 *       形如 {@code {"userId":"sai","apiBase":"https://api.bgm.tv","cacheTtl":3600}}</li>
 *   <li>用 json-config 接口写入：每个字段单独一个键，值是 JSON 字符串，
 *       形如 {@code data.userId = "\"sai\""}</li>
 * </ul>
 * 两种都要认，且分组优先（那是用户在后台真正改的值）。
 */
public record BangumiSettings(String userId, String apiBase, long cacheTtl, String accessToken) {

    public static final String CONFIG_MAP = "plugin-bangumi-configmap";

    /** 后台表单的分组名，与 setting.yaml 里的 group 一致。 */
    private static final String GROUP = "basic";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static BangumiSettings defaults() {
        return new BangumiSettings("", "https://api.bgm.tv", 3600L, "");
    }

    public static BangumiSettings from(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return defaults();
        }
        var group = parseGroup(data.get(GROUP));
        var base = text(pick(group, data, "apiBase"), "https://api.bgm.tv");
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return new BangumiSettings(
            text(pick(group, data, "userId"), ""),
            base,
            number(pick(group, data, "cacheTtl"), 3600L),
            text(pick(group, data, "accessToken"), "")
        );
    }

    public boolean configured() {
        return userId != null && !userId.isBlank();
    }

    private static JsonNode parseGroup(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            var node = MAPPER.readTree(raw);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 分组里的值优先，其次才是扁平键。 */
    private static String pick(JsonNode group, Map<String, String> data, String key) {
        if (group != null && group.hasNonNull(key)) {
            var node = group.get(key);
            return node.isNumber() ? node.asText() : node.asText(null);
        }
        return data.get(key);
    }

    private static String text(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        var value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
        }
        return value.isEmpty() ? fallback : value;
    }

    private static long number(String raw, long fallback) {
        var value = text(raw, "");
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
