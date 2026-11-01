package run.halo.bangumi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.bangumi.config.BangumiSettings;

/**
 * 把 Bangumi 的原始数据结构转换成主题前端使用的形状。
 *
 * <p>前端脚本沿用原 Typecho 插件，约定的接口是：
 * <ul>
 *   <li>{@code ?from=0&type=watching&cate=anime} → {@code {items, next_offset, has_more, more_url}}</li>
 *   <li>{@code ?type=calendar&filter=watching|all} → {@code [{id, date_en, date_cn, items[]}]}</li>
 *   <li>{@code ?type=subject&id=123} → 条目详情对象</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BangumiService {

    private final BangumiClient client;
    private final SettingsService settingsService;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Bangumi 的 subject_type：1 书籍 / 2 动画 / 3 音乐 / 4 游戏 / 6 三次元。 */
    private static final Map<String, Integer> SUBJECT_TYPES = Map.of(
        "anime", 2,
        "book", 1,
        "game", 4,
        "real", 6,
        "music", 3
    );

    /** 在看的收藏类型（type=3），其余归为「已看」（type=2）。 */
    private static final Set<String> ACTIVE_TYPES = Set.of("watching", "reading", "playing", "listening");

    private static final int FIRST_PAGE_SIZE = 11;
    private static final int PAGE_SIZE = 12;
    private static final int MAX_ITEMS = 400;
    private static final int FETCH_LIMIT = 50;
    private static final int MIN_RATING_COUNT = 10;

    // ------------------------------------------------------------ 收藏列表

    public Mono<JsonNode> collection(String cate, String type, int from) {
        var category = SUBJECT_TYPES.containsKey(cate) ? cate : "anime";
        var listType = type == null || type.isBlank() ? "watching" : type;
        var status = ACTIVE_TYPES.contains(listType) ? 3 : 2;
        var subjectType = SUBJECT_TYPES.get(category);
        var offset = Math.max(0, from);

        return settingsService.get().flatMap(config -> {
            if (!config.configured()) {
                return Mono.just(error("还没有在插件设置里填写 Bangumi 用户 ID"));
            }
            var key = "collection:" + config.userId() + ":" + category + ":" + listType;
            return client.getCached(key, config.cacheTtl(), fetchAllCollections(config, subjectType, status))
                .flatMap(all -> {
                    // 空列表可能是「没有收藏」，也可能是「用户不存在」，后者要说清楚
                    if (all.isArray() && all.isEmpty() && offset == 0) {
                        return userExists(config)
                            .map(exists -> exists
                                ? (JsonNode) page(all, offset, category, listType, config.userId())
                                : error("Bangumi 上找不到用户「" + config.userId() + "」，请检查插件设置里的用户 ID"));
                    }
                    return Mono.just(page(all, offset, category, listType, config.userId()));
                });
        });
    }

    private Mono<Boolean> userExists(BangumiSettings config) {
        var url = config.apiBase() + "/v0/users/" + encode(config.userId());
        return client.getCached("user:" + config.userId(), config.cacheTtl(),
                client.get(url, config.accessToken()))
            .map(node -> true)
            .defaultIfEmpty(false);
    }

    private Mono<JsonNode> fetchAllCollections(BangumiSettings config, int subjectType, int status) {
        var acc = mapper.createArrayNode();
        return fetchCollectionPage(config, subjectType, status, 0, acc);
    }

    private Mono<JsonNode> fetchCollectionPage(BangumiSettings config, int subjectType, int status,
                                               int offset, ArrayNode acc) {
        var url = config.apiBase() + "/v0/users/" + encode(config.userId()) + "/collections"
            + "?subject_type=" + subjectType + "&type=" + status
            + "&limit=" + FETCH_LIMIT + "&offset=" + offset;

        return client.get(url, config.accessToken())
            .flatMap(root -> {
                var data = root.path("data");
                var added = 0;
                if (data.isArray()) {
                    for (var collection : data) {
                        var item = toCollectionItem(collection);
                        if (item != null) {
                            acc.add(item);
                            added++;
                        }
                    }
                }
                var total = root.path("total").asInt(0);
                var next = offset + (data.isArray() ? data.size() : 0);
                if (added == 0 || next >= total || acc.size() >= MAX_ITEMS) {
                    return Mono.just((JsonNode) acc);
                }
                return fetchCollectionPage(config, subjectType, status, next, acc);
            })
            .defaultIfEmpty(acc);
    }

    private ObjectNode toCollectionItem(JsonNode collection) {
        var subject = collection.path("subject");
        var id = subject.path("id").asLong(0);
        if (id <= 0) {
            return null;
        }
        var item = mapper.createObjectNode();
        item.put("id", id);
        item.put("name", subject.path("name").asText(""));
        item.put("name_cn", subject.path("name_cn").asText(""));
        item.put("url", "https://bgm.tv/subject/" + id);
        item.put("status", collection.path("ep_status").asInt(0));
        item.put("vol_status", collection.path("vol_status").asInt(0));
        item.put("count", subject.path("eps").asInt(0));
        item.put("vol_count", subject.path("volumes").asInt(0));
        item.put("air_date", subject.path("date").asText(""));
        item.put("score", subject.path("score").asDouble(0));
        var images = subject.path("images");
        item.put("img", pickImage(images));
        item.put("img_fallback", pickFallbackImage(images));
        return item;
    }

    private ObjectNode page(JsonNode all, int offset, String cate, String listType, String userId) {
        var page = mapper.createObjectNode();
        var items = mapper.createArrayNode();
        var size = all.isArray() ? all.size() : 0;
        // 首屏 11 张、之后每页 12 张，跟原插件保持一致
        var limit = offset <= 0 ? FIRST_PAGE_SIZE : PAGE_SIZE;
        for (var i = offset; i < Math.min(size, offset + limit); i++) {
            items.add(all.get(i));
        }
        var next = offset + items.size();
        page.set("items", items);
        page.put("next_offset", next);
        page.put("has_more", next < size);
        page.put("more_url", moreUrl(cate, listType, userId));
        return page;
    }

    private static String moreUrl(String cate, String listType, String userId) {
        var status = ACTIVE_TYPES.contains(listType) ? "do" : "collect";
        return "https://bgm.tv/" + cate + "/list/" + encode(userId) + "/" + status;
    }

    // ------------------------------------------------------------ 每日放送

    public Mono<JsonNode> calendar(String filter) {
        var watching = "watching".equals(filter);

        return settingsService.get().flatMap(config -> {
            if (watching && !config.configured()) {
                return Mono.just(error("「仅在看」需要在插件设置里填写 Bangumi 用户 ID"));
            }
            var key = "calendar:" + (watching ? config.userId() : "all");
            var loader = client.get(config.apiBase() + "/calendar", config.accessToken())
                .map(this::toCalendar);

            return client.getCached(key, config.cacheTtl(), loader)
                .flatMap(days -> watching ? filterWatching(config, days) : Mono.just(days));
        });
    }

    private JsonNode toCalendar(JsonNode root) {
        var days = mapper.createArrayNode();
        if (!root.isArray()) {
            return days;
        }
        for (var day : root) {
            var weekday = day.path("weekday");
            var items = new ArrayList<ObjectNode>();
            for (var raw : day.path("items")) {
                items.add(toCalendarItem(raw));
            }
            items.sort(Comparator.comparingInt((ObjectNode item) -> item.path("doing").asInt(0)).reversed());

            var node = mapper.createObjectNode();
            node.put("id", weekday.path("id").asInt(0));
            node.put("date_en", weekday.path("en").asText(""));
            node.put("date_cn", weekday.path("cn").asText(""));
            var array = mapper.createArrayNode();
            items.forEach(array::add);
            node.set("items", array);
            days.add(node);
        }
        return days;
    }

    private ObjectNode toCalendarItem(JsonNode raw) {
        var id = raw.path("id").asLong(0);
        var rating = raw.path("rating");
        var collection = raw.path("collection");
        var item = mapper.createObjectNode();
        item.put("id", id);
        item.put("name", raw.path("name").asText(""));
        item.put("name_cn", raw.path("name_cn").asText(""));
        item.put("url", id > 0 ? "https://bgm.tv/subject/" + id : "");
        var images = raw.path("images");
        item.put("img", pickImage(images));
        item.put("img_fallback", pickFallbackImage(images));
        // 评分人数太少时分数没有参考价值，按原版逻辑不展示
        var score = rating.path("total").asInt(0) >= MIN_RATING_COUNT
            ? Math.round(rating.path("score").asDouble(0) * 10) / 10.0
            : 0.0;
        item.put("score", score);
        item.put("doing", Math.max(0, collection.path("doing").asInt(0)));
        var airDate = raw.path("air_date").asText("");
        item.put("air_date", airDate.matches("\\d{4}-\\d{2}-\\d{2}") ? airDate : "");
        return item;
    }

    /** 只保留自己正在看的动画，并附带收视进度。 */
    private Mono<JsonNode> filterWatching(BangumiSettings config, JsonNode days) {
        var key = "progress:" + config.userId();
        var loader = fetchAllCollections(config, SUBJECT_TYPES.get("anime"), 3);
        return client.getCached(key, config.cacheTtl(), loader).map(all -> {
            Map<Long, ObjectNode> progress = new HashMap<>();
            if (all.isArray()) {
                for (var item : all) {
                    var node = mapper.createObjectNode();
                    node.put("status", item.path("status").asInt(0));
                    node.put("count", item.path("count").asInt(0));
                    progress.put(item.path("id").asLong(0), node);
                }
            }
            var result = mapper.createArrayNode();
            for (var day : days) {
                var node = mapper.createObjectNode();
                node.put("id", day.path("id").asInt(0));
                node.put("date_en", day.path("date_en").asText(""));
                node.put("date_cn", day.path("date_cn").asText(""));
                var items = mapper.createArrayNode();
                for (var item : day.path("items")) {
                    var extra = progress.get(item.path("id").asLong(0));
                    if (extra != null) {
                        var merged = ((ObjectNode) item).deepCopy();
                        merged.setAll(extra);
                        items.add(merged);
                    }
                }
                node.set("items", items);
                result.add(node);
            }
            return (JsonNode) result;
        });
    }

    // -------------------------------------------------------------- 条目

    public Mono<JsonNode> subject(long id) {
        return settingsService.get().flatMap(config -> client
            .getCached("subject:" + id, config.cacheTtl(),
                client.get(config.apiBase() + "/v0/subjects/" + id, config.accessToken()))
            .map(root -> {
                if (!root.isObject()) {
                    return root;
                }
                var node = ((ObjectNode) root).deepCopy();
                var images = root.path("images");
                node.put("img", pickImage(images));
                node.put("img_fallback", pickFallbackImage(images));
                return (JsonNode) node;
            }));
    }

    // -------------------------------------------------------------- 工具

    private static String pickImage(JsonNode images) {
        for (var size : List.of("large", "common", "medium", "grid", "small")) {
            var url = images.path(size).asText("");
            if (!url.isBlank()) {
                return url;
            }
        }
        return "";
    }

    private static String pickFallbackImage(JsonNode images) {
        for (var size : List.of("common", "medium", "grid", "small")) {
            var url = images.path(size).asText("");
            if (!url.isBlank()) {
                return url;
            }
        }
        return "";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private ObjectNode error(String message) {
        var node = mapper.createObjectNode();
        node.put("error", message);
        // 保持字段结构完整，前端脚本照常渲染空列表，同时能读到错误说明
        node.set("items", mapper.createArrayNode());
        node.put("next_offset", 0);
        node.put("has_more", false);
        node.put("more_url", "");
        return node;
    }
}
