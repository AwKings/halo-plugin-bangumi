package run.halo.bangumi.content;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.theme.ReactiveSinglePageContentHandler;

/**
 * 独立页面正文里的番组短代码（追番页面通常就是一个独立页面）。
 */
@Component
public class BangumiSinglePageContentHandler implements ReactiveSinglePageContentHandler {

    private static final Logger log = LoggerFactory.getLogger(BangumiSinglePageContentHandler.class);

    @Override
    public Mono<SinglePageContentContext> handle(SinglePageContentContext context) {
        try {
            var content = context.getContent();
            if (content != null && !content.isBlank()) {
                var rendered = BangumiShortcode.render(content);
                if (!rendered.equals(content)) {
                    context.setContent(rendered);
                }
            }
        } catch (Exception e) {
            log.warn("PandaBangumi 页面解析失败，已跳过：{}", e.getMessage());
        }
        return Mono.just(context);
    }
}
