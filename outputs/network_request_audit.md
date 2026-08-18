# 旧 APK 网络请求检查

## 结论
`KwaiHook` 模块自身没有发现主动上传、HTTP POST、WebSocket 或外联 API 调用逻辑。它的网络相关代码只有一处：Hook `okhttp3.OkHttpClient.newCall(Request)`，读取目标 App 即将发送的 `Request.url()` 字符串，并在 URL 包含 `egid=` 时解析 query 参数。

## 直接证据
`work/apktriage/ks/classes3_network_strings.txt` 仅包含如下网络相关字符串：

```text
-$$Nest$minterceptHttpRequest
HTTP参数 - Egid:
Hook OkHttpClient.newCall失败:
OkHttpClient.newCall Hook成功
androidApiLevel
com.kwai.framework.network.access.params.e
hookOkHttpNewCall
interceptHttpRequest
kuaishou.api_st
okhttp3.OkHttpClient
okhttp3.Request
拦截HTTP请求失败:
```

未出现模块自身的 `http://...` / `https://...` 业务 URL。

## 模块内可见外部地址
`getAnnouncementMessage()` 内有 Base64 公告文本，解码后包含 Telegram 群链接：
- `https://t.me/pddkj1`
- `https://t.me/zqzb2025`

这些链接只作为公告文本展示，未见 `Intent.ACTION_VIEW`、OkHttp、HttpURLConnection、WebView 加载或其它主动访问逻辑。迁移时建议删除公告文本或替换为本地说明，避免交付版本携带非必要外链。

## 宿主旧 APK / 原快手资产中的网络端点
`work/apktriage/old/` 是旧样本携带的快手宿主解包，包含大量原 App/Native SDK 端点；这些不属于 `KwaiHook` 模块主动新增通信。代表性端点包括：

| 来源 | 端点/域名 | 判断 |
|---|---|---|
| `libaegon.so` | `apissl.gifshow.com`, `apissl.ksapisrv.com`, `az*-api.ksapisrv.com` | 快手 API/调度域 |
| `libaegon.so` | `apidns.kwd.inkuai.com`, `cdndns.kwd.inkuai.com`, `hdns.ksyun.com` | DNS/调度 |
| `libsnow.so` | `https://config-proxy.kwd.inkuai.com/v1/config`, `https://config-proxy-bak.kwd.inkuai.com/v1/config` | 配置拉取 |
| `libsnow.so` | `https://103.102.202.124/v1/config`, `https://103.102.202.44/v1/config`, `https://103.102.203.2/v1/config`, `https://103.107.218.240/v1/config` | 配置备用 IP |
| `libfullapk.so` | `https://d2-plat.wsukwai.com/...full-x64.apk` | 原 App/组件更新字符串 |
| `assets/npatch/loader.dex` | `https://api.xposed.info/using.html` | LSPosed/NPatch 相关说明字符串 |

## 迁移版本网络约束
迁移到新版时保留的必要网络行为应只有：
1. 在目标进程内被动观察 `OkHttpClient.newCall(Request)`。
2. 读取 URL 字符串和本地内存对象参数。
3. 本地弹窗/剪贴板输出。

需要移除/避免：
- 模块自身任何 `OkHttpClient` / `HttpURLConnection` / `WebView.loadUrl` 外联。
- 公告中的 Telegram/推广外链。
- 旧壳/旧宿主中的更新、配置、NPatch 管理器通信。
- 非快手业务域上的 `egid=` 匹配；建议新增 host 白名单，仅接受 `gifshow.com`、`ksapisrv.com`、`kuaishou.com`、`inkuai.com`、`kwai.com`、`kwimgs.com` 及本地调试域。
