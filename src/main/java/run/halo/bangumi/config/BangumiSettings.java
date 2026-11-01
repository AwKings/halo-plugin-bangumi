package run.halo.bangumi.config;

import java.util.Map;

/**
 * 插件设置，来自 ConfigMap {@code plugin-bangumi-configmap}。
 *
 * <p>Halo 把设置表单的每个字段以 JSON 字符串存进 ConfigMap，所以这里的值可能是
 * {@code "\"sai\""} 或 {@code "3600"}，解析时都要兼容。
 */
public record BangumiSettings(String userId, String apiBase, long cacheTtl, String accessToken) {

    public static final String CONFIG_MAP = "plugin-bangumi-configmap";

    public static BangumiSettings defaults() {
        return new BangumiSettings("", "https://api.bgm.tv", 3600L, "");
    }

    public static BangumiSettings from(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return defaults();
        }
        var base = text(data.get("apiBase"), "https://api.bgm.tv");
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return new BangumiSettings(
            text(data.get("userId"), ""),
            base,
            number(data.get("cacheTtl"), 3600L),
            text(data.get("accessToken"), "")
        );
    }

    public boolean configured() {
        return userId != null && !userId.isBlank();
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
