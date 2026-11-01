package run.halo.bangumi;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * PandaBangumi 的 Halo 版本。
 *
 * <p>没有自定义模型，所有数据都通过接口实时（带缓存）从 Bangumi 读取。
 */
@Component
public class BangumiPlugin extends BasePlugin {

    public BangumiPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }
}
