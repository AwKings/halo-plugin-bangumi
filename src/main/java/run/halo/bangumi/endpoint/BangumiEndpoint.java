package run.halo.bangumi.endpoint;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.bangumi.service.BangumiService;

/**
 * 前端脚本使用的数据接口。
 *
 * <p>沿用原 Typecho 插件的查询约定，因此主题侧 JS 可以原样复用：
 * <pre>
 * GET /apis/api.bangumi.halo.run/v1alpha1/data?from=0&amp;type=watching&amp;cate=anime
 * GET /apis/api.bangumi.halo.run/v1alpha1/data?type=calendar&amp;filter=watching
 * GET /apis/api.bangumi.halo.run/v1alpha1/data?type=subject&amp;id=253
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class BangumiEndpoint implements CustomEndpoint {

    /** 这些 type 值是收藏列表的筛选条件，其余才是接口分派。 */
    private static final Set<String> COLLECTION_TYPES = Set.of(
        "watching", "watched", "reading", "read", "playing", "played", "listening", "listened"
    );

    private final BangumiService bangumiService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("/data", this::data)
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("api.bangumi.halo.run", "v1alpha1");
    }

    private Mono<ServerResponse> data(ServerRequest request) {
        var type = request.queryParam("type").orElse("watching");

        if ("calendar".equals(type)) {
            var filter = request.queryParam("filter").orElse("all");
            return bangumiService.calendar(filter).flatMap(this::ok);
        }
        if ("subject".equals(type)) {
            var id = parseLong(request.queryParam("id").orElse("0"));
            if (id <= 0) {
                return ServerResponse.badRequest().bodyValue(java.util.Map.of("error", "缺少条目 ID"));
            }
            return bangumiService.subject(id)
                .flatMap(this::ok)
                .switchIfEmpty(ServerResponse.notFound().build());
        }
        if (type.startsWith("cover")) {
            return ServerResponse.badRequest().bodyValue(java.util.Map.of("error", "封面代理未启用"));
        }

        // 其余情况都按收藏列表处理：type 就是 watching / watched / reading ...
        var cate = request.queryParam("cate").orElse("anime");
        var from = (int) parseLong(request.queryParam("from").orElse("0"));
        var listType = COLLECTION_TYPES.contains(type) ? type : "watching";
        return bangumiService.collection(cate, listType, from).flatMap(this::ok);
    }

    private Mono<ServerResponse> ok(Object body) {
        return ServerResponse.ok().contentType(APPLICATION_JSON).bodyValue(body);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
