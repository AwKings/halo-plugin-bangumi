package run.halo.bangumi.content;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.theme.ReactivePostContentHandler;

/**
 * 文章正文里的番组短代码。
 */
@Component
public class BangumiPostContentHandler implements ReactivePostContentHandler {

    private static final Logger log = LoggerFactory.getLogger(BangumiPostContentHandler.class);

    @Override
    public Mono<PostContentContext> handle(PostContentContext context) {
        try {
            var content = context.getContent();
            if (content != null && !content.isBlank()) {
                var rendered = BangumiShortcode.render(content);
                if (!rendered.equals(content)) {
                    context.setContent(rendered);
                }
            }
        } catch (Exception e) {
            log.warn("PandaBangumi 正文解析失败，已跳过：{}", e.getMessage());
        }
        return Mono.just(context);
    }
}
