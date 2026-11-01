package run.halo.bangumi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.bangumi.config.BangumiSettings;

/**
 * 读取插件设置。设置由 Halo 的 Setting 表单写入 ConfigMap，这里只负责取出并解析。
 */
@Component
@RequiredArgsConstructor
public class SettingsService {

    private final ReactiveExtensionClient client;

    public Mono<BangumiSettings> get() {
        return client.fetch(ConfigMap.class, BangumiSettings.CONFIG_MAP)
            .map(configMap -> BangumiSettings.from(configMap.getData()))
            .defaultIfEmpty(BangumiSettings.defaults());
    }
}
