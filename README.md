# PandaBangumi for Halo

把 [Bangumi](https://bgm.tv) 的收藏、每日放送和条目卡片带到 Halo。

移植自 [mikusaa/Typecho-Plugin-PandaBangumi](https://github.com/mikusaa/Typecho-Plugin-PandaBangumi)（原作者 AlanDecode、Izumiko、mikusaa），
移植者 AwKings。前端脚本与样式沿用原插件，只把数据接口换成了 Halo 插件端点。

## 功能

- 展示动画、书籍、游戏、三次元、音乐的在看与已看收藏（带进度、评分）
- 展示 Bangumi 每日放送，支持只保留自己正在看的动画
- 在文章 / 独立页面里插入条目卡片
- 收藏列表支持「加载更多」分页，封面懒加载
- 服务端缓存（默认 1 小时）
- 响应式布局，样式与脚本由插件自动注入，主题无需改模板

## 安装

1. 下载 `plugin-bangumi-<version>.jar`
2. 后台「插件 → 安装插件」上传，启用
3. 到「插件 → PandaBangumi → 设置」填写 Bangumi 用户 ID（例如 `sai`）

## 设置

| 字段 | 说明 |
| --- | --- |
| Bangumi 用户 ID | 你的 bgm.tv 用户名或 UID，收藏与「仅在看」日历都按它读取 |
| API 地址 | 默认 `https://api.bgm.tv`，可换成自建镜像 |
| 缓存时长 | 服务端缓存秒数，默认 3600 |

## 用法

在文章或独立页面里写短代码（推荐）：

```
[bgm-collection cate=anime type=watching]
[bgm-calendar filter=watching]
[bgm-card id=253]
```

也支持原插件的原始 HTML，两者可以混用：

```html
<div class="bgm-collection" data-cate="anime" data-type="watched"></div>
<div class="bgm-calendar" data-filter="all"></div>
<div class="bgm-card" data-id="253"></div>
```

参数取值：

| 参数 | 取值 |
| --- | --- |
| `cate` | `anime` 动画 / `book` 书籍 / `game` 游戏 / `real` 三次元 / `music` 音乐 |
| `type` | 在看类 `watching` `reading` `playing` `listening`；已看类 `watched` `read` `played` `listened` |
| `filter` | `watching` 只看自己在看的 / `all` 全部放送 |
| `id` | Bangumi 条目 ID（`https://bgm.tv/subject/253` 里的 253） |

完整示例见 <https://blog.sanmizu.com/bangumi>。

## 接口

插件注册了一个匿名可读的端点，前端脚本与第三方页面都可以直接调用：

```
GET /apis/api.bangumi.halo.run/v1alpha1/data?from=0&type=watching&cate=anime
GET /apis/api.bangumi.halo.run/v1alpha1/data?type=calendar&filter=watching
GET /apis/api.bangumi.halo.run/v1alpha1/data?type=subject&id=253
```

返回结构与原 Typecho 插件一致，所以原版 `PandaBangumi.js` 可以不改逻辑直接跑。

## 静态资源

脚本与样式通过 `ReverseProxy` 扩展暴露：

```
/plugins/plugin-bangumi/assets/static/bangumi.js
/plugins/plugin-bangumi/assets/static/bangumi.css
```

页面里出现 `.bgm-card` / `.bgm-collection` / `.bgm-calendar` 时，脚本会自动把样式注入 `<head>`，
所以其他主题只要引入脚本即可：

```html
<script src="/plugins/plugin-bangumi/assets/static/bangumi.js" defer></script>
```

本插件渲染正文时会自动追加这行脚本，用短代码时无需手动引入。

## 与原插件的差异

| 项目 | Typecho 版 | Halo 版 |
| --- | --- | --- |
| 数据接口 | Typecho 路由 `/PandaBangumi` | 自定义端点 `/apis/api.bangumi.halo.run/v1alpha1/data` |
| 缓存 | 磁盘 `.cache/` 目录 | 进程内 TTL 缓存 |
| 封面 | 可选下载到本地 | 直连 `lain.bgm.tv` |
| 编辑器按钮 | 后台编辑器插入面板 | 短代码，编辑器里手写一行 |
| 访问控制 | 无 | 走 Halo RBAC，匿名只读 |
| 代码块保护 | 无 | 解析时跳过 `<pre>` / `<code>` |

未移植：后台编辑器里的可视化插入面板（Halo 需要额外的前端插件工程）、封面本地缓存与维护任务。

## 构建

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew build -x test
# 产物：build/libs/plugin-bangumi-<version>.jar
```

## 许可

MIT。原项目版权归 AlanDecode、Izumiko、mikusaa 所有；本移植保留原始版权声明。
