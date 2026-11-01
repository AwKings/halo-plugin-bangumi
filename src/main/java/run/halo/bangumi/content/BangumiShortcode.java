package run.halo.bangumi.content;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 PandaBangumi 的短代码转换成前端脚本认识的占位元素。
 *
 * <p>原 Typecho 插件由编辑器插入原始 HTML，Halo 的编辑器不一定保留这些片段，
 * 所以这里额外支持短代码写法：
 * <pre>
 * [bgm-card id=253]
 * [bgm-collection cate=anime type=watching]
 * [bgm-calendar filter=watching]
 * </pre>
 * 已经写成 {@code <div class="bgm-card" data-id="253"></div>} 的内容也会被识别。
 */
public final class BangumiShortcode {

    private BangumiShortcode() {
    }

    private static final Pattern SHORTCODE =
        Pattern.compile("(?:<p>)?\\s*\\[(bgm-(?:card|collection|calendar))"
                + "((?:\\s+[a-zA-Z-]+=[^\\s\\]]+)*)\\]\\s*(?:</p>)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ATTR =
        Pattern.compile("([a-zA-Z-]+)=([^\\s\\]]+)");

    /** 代码区域：里面的短代码只当示例，不转换。 */
    private static final Pattern CODE_BLOCK =
        Pattern.compile("(<pre\\b[^>]*>.*?</pre>|<code\\b[^>]*>.*?</code>)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final String SCRIPT_TAG =
        "<script src=\"/plugins/plugin-bangumi/assets/static/bangumi.js\" defer></script>";

    /** 正文里是否出现了需要渲染的番组元素。 */
    public static boolean hasPlaceholder(String html) {
        if (html == null) {
            return false;
        }
        return html.contains("class=\"bgm-card\"") || html.contains("class='bgm-card'")
            || html.contains("class=\"bgm-collection\"") || html.contains("class='bgm-collection'")
            || html.contains("class=\"bgm-calendar\"") || html.contains("class='bgm-calendar'");
    }

    /**
     * 替换短代码，并在需要时追加前端脚本。
     */
    public static String render(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        var out = replaceShortcodes(html);
        if (hasPlaceholder(out) && !out.contains("bangumi.js")) {
            out = out + "\n" + SCRIPT_TAG;
        }
        return out;
    }

    private static String replaceShortcodes(String html) {
        var sb = new StringBuilder();
        var codeMatcher = CODE_BLOCK.matcher(html);
        int last = 0;
        while (codeMatcher.find()) {
            sb.append(replaceOutside(html.substring(last, codeMatcher.start())));
            // 代码块原样保留
            sb.append(codeMatcher.group());
            last = codeMatcher.end();
        }
        sb.append(replaceOutside(html.substring(last)));
        return sb.toString();
    }

    private static String replaceOutside(String html) {
        var matcher = SHORTCODE.matcher(html);
        var sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb,
                Matcher.quoteReplacement(toElement(matcher.group(1), matcher.group(2))));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String toElement(String name, String rawAttrs) {
        var attrs = new java.util.HashMap<String, String>();
        var matcher = ATTR.matcher(rawAttrs == null ? "" : rawAttrs);
        while (matcher.find()) {
            attrs.put(matcher.group(1).toLowerCase(), matcher.group(2));
        }
        return switch (name.toLowerCase()) {
            case "bgm-card" -> "<div class=\"bgm-card\" data-id=\""
                + digits(attrs.getOrDefault("id", "0")) + "\"></div>";
            case "bgm-collection" -> "<div class=\"bgm-collection\" data-cate=\""
                + safe(attrs.getOrDefault("cate", "anime"))
                + "\" data-type=\"" + safe(attrs.getOrDefault("type", "watching")) + "\"></div>";
            case "bgm-calendar" -> "<div class=\"bgm-calendar\" data-filter=\""
                + ("watching".equals(attrs.get("filter")) ? "watching" : "all") + "\"></div>";
            default -> "";
        };
    }

    private static String digits(String value) {
        var cleaned = value.replaceAll("[^0-9]", "");
        return cleaned.isEmpty() ? "0" : cleaned;
    }

    private static String safe(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
