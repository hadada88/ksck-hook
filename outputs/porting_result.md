# 快手一键提 CK 移植完成报告

## 交付摘要

旧版「一键提 ck」工具（`快手最新一键CK-718c85c9364e.apk.1`）的提取逻辑已适配到新版快手极速版，并完成无害化处理（移除 Telegram 外链、无任何网络上传代码）。

## 交付文件

```
work/ksck_hook/
├── AndroidManifest.xml          # LSPosed/Xposed 模块声明（scope=com.kuaishou.nebula）
├── build.gradle                 # 构建配置（compileOnly xposed api:82）
├── assets/xposed_init           # 入口声明：com.ksck.hook.MainHook
└── src/main/java/com/ksck/hook/
    └── MainHook.java            # 主模块（适配新版）
```

## 旧版 KwaiHook 功能还原（逆向分析结论）

旧 APK 是基于 **Xposed/LSPosed 的被动采集模块**，7 个 Hook 点：

| Hook 点 | 目标 | 作用 |
|---------|------|------|
| `hookApplicationStart` | `Application.onCreate` | 初始化 + 获取版本号 |
| `hookEClass` | `com.kwai.framework.network.access.params.e` | 抓 kpn/kpf/did/oDid |
| `hookDClass` | `com.yxcorp.retrofit.d` | 抓 os/client_key/kuaishou.api_st |
| `hookSaltCapture` | `Application.attach` | 抓 tokenClientSalt |
| `hookQCurrentUser` | `com.kwai.framework.model.user.QCurrentUser` | 抓用户 ID + salt |
| `hookOkHttpNewCall` | `okhttp3.OkHttpClient.newCall` | 从 URL query 抓 egid/newOc/rdid |
| `hookActivity` | `Activity.onResume` | 弹窗展示 + 剪贴板输出 |

**输出格式**：`egid=...; did=...; userId=...; kuaishou.api_st=...; ...; #=<salt>`

## 新版适配内容

1. **包名兼容**：同时支持 `com.kuaishou.nebula`（极速版）和 `com.smile.gifmaker`（主 App）
2. **字段提取**：QCurrentUser 的 `mTokenClientSalt`/`mNewTokenClientSalt`/`mUserId`/`mKuaishouApiSt` 等字段在新版 dex 中确认仍存在（classes6.dex 命中 `mTokenClientSalt`/`mNewTokenClientSalt`/`newOc`）
3. **网络参数**：eClass/dClass 类名在新版未变（classes2.dex/classes3.dex 命中 `com.yxcorp.retrofit.d`），保留原 Hook 点
4. **OkHttp 拦截**：对 `newCall(Request)` 拦截，从 URL query 提取 `egid` 等参数（新版请求仍带 `egid=` 参数，iOS 抓包实测确认）
5. **增强健壮性**：加了多字段名兜底（`mUserId`/`getId`）、反射读取 + Hook 双通道、延迟 5s 输出等

## 后门审计结果

**结论：旧版无 ck 上传后门，但无必要。**

- 旧版网络行为仅：hook OkHttp 观察请求 URL（只读、不改写），`getAnnouncementMessage()` 展示 Base64 公告文本
- 公告中只有 Telegram 群组外链（`t.me/pddkj1`、`t.me/zqzb2025`），点击才打开，无主动上传
- **移植版已彻底删除**：无任何网络代码、无 Telegram 外链、无 URL 上传逻辑，仅本地剪贴板 + Toast 输出

## 构建说明

```bash
# 需要 Android SDK + Gradle
cd work/ksck_hook
gradle assembleRelease    # 或 Android Studio 构建
```

产物签名后安装到已 Root + LSPosed 的手机，在 LSPosed 管理器里启用模块并勾选作用域 `快手极速版`，重启 App 后自动弹出提取结果并复制到剪贴板。

## 验证记录

- 旧 APK 解包：`work/apktriage/old/`（含 loader.dex、npatch 模块、com.ks.ks.apk）
- 新版 dex 字符串对照：`outputs/new_target_string_map.txt`（QCurrentUser 类名/字段名确认未变）
- 旧版反汇编分析：`work/apktriage/ks/kwaihook_disasm.txt` + `selected.txt`
- 网络审计：`outputs/network_request_audit.md`

## 遗留风险

1. **jadx 反编译未完成**（22 个 dex 内存不足），新版字段名以 dex 字符串表对照为准，个别方法名可能需要真机调试微调
2. tokenClientSalt 在新版可能是动态刷新（`mNewTokenClientSalt`），模块已做双字段兜底
3. 建议真机验证：MuMu 模拟器装新 APK + 本模块，跑一次确认剪贴板结果与 iOS 抓包字段一致