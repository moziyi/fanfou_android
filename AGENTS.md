# AGENTS.md

## 构建与测试
- 项目是单模块 Android 应用 `:app`；依赖版本集中在 `gradle/libs.versions.toml`，不要擅自升级已验证的 Gradle 8.14.3、AGP 8.13.2、Kotlin 2.2.21、Hilt 2.58、Compose BOM 2025.09.01。
- 使用 `./gradlew`，不要用系统 Gradle。构建：`./gradlew :app:assembleDebug`；测试：`./gradlew :app:testDebugUnitTest`；单测类：`./gradlew :app:testDebugUnitTest --tests 'com.kansen.fanfan.core.network.auth.OAuth1SignerTest'`；安装：`./gradlew :app:installDebug`。
- 构建要求 JBR 21 和 Android SDK 36。`gradle.properties` 中的 JBR 路径是本机绝对路径，换环境时先确认或本地调整；SDK 通过 `local.properties` 或 `ANDROID_HOME` 配置。仓库已优先使用阿里云 Maven 镜像。

## 易错约束
- 使用饭否 v1 API。请求必须带自定义 User-Agent；网络传输用 HTTPS，但 OAuth 签名 base string 的 URL 使用 HTTP。XAuth 的三个 `x_auth_*` 参数要参与签名并放入 Authorization 头；改签名逻辑时保留 `OAuth1SignerTest` 的黄金用例。本项目复用已审核的 Nofan consumer key，新申请应用的 key 无法调用 API。
- 时间线以 Room 为数据源。修改实体时同步递增数据库版本并编写迁移；1→2 迁移只清理 status/timeline 缓存，保留草稿、收藏覆盖和 token。
- 回复父帖由 `ReplyTargetResolver` 在 `TimelineRepository` 完成 `store.trim()` 后缓存；不要将 `replyTargets.warm(page)` 移到 trim 前。`StatusDao.pruneOrphans()` 的父帖子查询必须保留 `IS NOT NULL`，否则 SQLite 的 `NOT IN` 遇到 NULL 会导致清理失效。
- Kotlin KDoc 中避免写 `**xxx://**` 这类文本：其中的 `//*` 会被解析成嵌套注释起始符，导致语法错误。
- 新功能先完成设计方案并获用户批准，再保存到项目根目录 `.doc/中文名-YYYY-MM-DD.md`，之后再实现。
