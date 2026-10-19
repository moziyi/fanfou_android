# Fanfan

第三方饭否（Fanfou）Android 客户端，使用 Kotlin + Jetpack Compose 构建。

## 功能

- **XAuth 登录**：账号密码直接换取 OAuth token（无需浏览器授权），token 持久化到 DataStore
- **首页时间线**：首页 / 随便看看双 Tab
- **嘟文卡片**：头像、昵称、相对时间、正文、转发引用块、回复引用块、图片、来源、回复/转发/收藏
- **图片查看器**、**发嘟 / 回复 / 转发**、**草稿**、**收藏**

## 技术栈

| 类别 | 选型 |
|---|---|
| UI | Jetpack Compose（BOM 2025.09.01）+ Material 3 + Navigation Compose |
| 依赖注入 | Hilt 2.58 |
| 网络 | Retrofit 3.0.0 + OkHttp 5.1.0 + kotlinx.serialization |
| 持久化 | Room（缓存）+ DataStore Preferences（token） |
| 图片 | Coil |
| 构建 | Gradle 8.14.3 + AGP 8.13.2 + Kotlin 2.2.21 |

## 模块结构

单模块 `app`，包 `com.kansen.fanfan`，按层分包：

```
core/
  network/    OAuth1Signer（纯 Kotlin 签名器）、OAuthSigningInterceptor、UserAgentInterceptor
  network/api/    FanfouApi（Retrofit 接口，饭否 v1 API）
  network/dto/    kotlinx.serialization DTO
  network/token/  TokenCache（@Volatile 内存镜像）+ TokenProvider（DataStore）
  database/   Room：status/user/draft 表
  model/      领域模型与 payload 解析规则
data/       Repository 层（auth / timeline 等）
ui/         Compose 界面（login / timeline / detail / compose / me / components）
di/         Hilt Module
```

## 构建要求

- JDK：JetBrains Runtime 21（`gradle.properties` 已钉 `org.gradle.java.home`）
- Android SDK 36（`local.properties` 或 `ANDROID_HOME`）
- **必须使用 `./gradlew`**，系统全局 Gradle 版本不兼容
- 国内网络已配置阿里云 Maven 镜像（`settings.gradle.kts`）

```bash
./gradlew assembleDebug     # 构建
./gradlew test              # 单元测试
./gradlew installDebug      # 安装到真机
```

## 饭否 API 关键事实（实测）

- 使用 **v1 API**（`https://api.fanfou.com`）；v2 草稿 API 已评估否决
- 饭否已停止审核新应用：自建 app 的 consumer key 的 token 一律 401，本项目复用 Nofan 的已注册 consumer key（见 `app/build.gradle.kts` 注释）
- 必须发送自定义 `User-Agent`，否则网关直接 404 HTML
- OAuth 签名 base string 必须用 `http://` scheme（即使实际请求走 HTTPS）

## License

个人项目，仅供学习交流。
