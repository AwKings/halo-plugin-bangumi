package run.halo.bangumi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Bangumi API 访问层：带 UA、超时、内存 TTL 缓存。
 *
 * <p>Bangumi 对请求频率敏感，所有列表数据都在这里按 URL 缓存，
 * 上游抖动时尽量沿用上一次的结果。
 */
@Component
public class BangumiClient {

    private static final Logger log = LoggerFactory.getLogger(BangumiClient.class);

    /** Bangumi 要求带可识别的 UA，否则可能被拒绝。 */
    private static final String USER_AGENT =
        "AwKings/halo-plugin-bangumi/1.0 (https://github.com/AwKings/halo-plugin-bangumi)";

    private static final int MAX_CACHE_ENTRIES = 256;

    private final ObjectMapper mapper = new ObjectMapper();

    private final WebClient webClient = WebClient.builder()
        .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
        .build();

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 请求一个 JSON 资源。404 返回空 Mono，其它错误抛出。
     */
    public Mono<JsonNode> get(String url, String accessToken) {
        return webClient.get()
            .uri(url)
            .headers(headers -> {
                if (accessToken != null && !accessToken.isBlank()) {
                    headers.setBearerAuth(accessToken);
                }
            })
            .exchangeToMono(response -> {
                var status = response.statusCode();
                if (status.value() == 404) {
                    return Mono.empty();
                }
                if (!status.is2xxSuccessful()) {
                    return response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new BangumiApiException(
                            "Bangumi 接口返回 " + status.value() + "：" + truncate(body))));
                }
                return response.bodyToMono(String.class).map(this::readTree);
            })
            .timeout(Duration.ofSeconds(25))
            .doOnError(error -> log.warn("请求 Bangumi 失败 {}：{}", url, error.getMessage()));
    }

    /**
     * 命中缓存直接返回，否则执行 loader 并写入缓存。
     */
    public Mono<JsonNode> getCached(String key, long ttlSeconds, Mono<JsonNode> loader) {
        var now = System.currentTimeMillis();
        var hit = cache.get(key);
        if (hit != null && hit.expireAt() > now) {
            return Mono.just(hit.value());
        }
        var ttl = Math.max(60L, ttlSeconds);
        return loader.doOnNext(value -> {
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(key, new CacheEntry(value, now + ttl * 1000L));
        });
    }

    public void invalidate() {
        cache.clear();
    }

    private JsonNode readTree(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new BangumiApiException("Bangumi 返回的内容不是合法 JSON");
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 120 ? body.substring(0, 120) + "…" : body;
    }

    private record CacheEntry(JsonNode value, long expireAt) {
    }

    /** 上游接口异常，用于向前端给出可读提示。 */
    public static class BangumiApiException extends RuntimeException {
        public BangumiApiException(String message) {
            super(message);
        }
    }
}
