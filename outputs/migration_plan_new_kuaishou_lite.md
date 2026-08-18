# 新版快手极速版迁移方案

## 目标
将旧样本 `KwaiHook` 的自动化能力迁移到新版 `C:\Users\Administrator\Downloads\快手极速版.apk`，保持功能等价：
- 目标包：优先 `com.kuaishou.nebula`，兼容 `com.smile.gifmaker`。
- 自动采集：用户 ID、token client salt、公共参数、请求 query 中的 `egid/newOc/rdid`。
- 本地输出：弹窗展示并复制 `KS_CK` 到剪贴板。
- 网络约束：迁移模块自身不新增外联。

## 新版类结构命中情况
已从 `work/apktriage/new/classes*.dex` 字符串表确认：

| 旧 Hook 点 | 新版命中位置 | 迁移动作 |
|---|---:|---|
| `okhttp3.OkHttpClient.newCall(okhttp3.Request)` | 多 dex 依赖均可由目标 ClassLoader 解析 | 保持原 Hook 点 |
| `com.kwai.framework.model.user.QCurrentUser` | `classes11.dex` 明确含类；多 dex 引用 | 保持类名，运行期反射调用 `me()` |
| `QCurrentUser.getTokenClientSalt()` | `classes9.dex`、`classes11.dex`、`classes17.dex` 出现字符串 | 保持方法名，增加字段兜底 `mTokenClientSalt/mNewTokenClientSalt` |
| `com.kwai.framework.network.access.params.e` | `classes16.dex`、`classes17.dex` 命中描述符 | 保持类名；构造 Hook 需兼容多构造签名 |
| `com.yxcorp.retrofit.d` | `classes3.dex`、`classes8.dex`、`classes9.dex`、`classes14.dex`、`classes17.dex` 命中描述符 | 保持类名；构造 Hook 需兼容类加载时机 |
| `kuaishou.api_st` | `classes2/5/6/7/8/9/11/12/16/17.dex` | 保持字段/参数名 |
| `egid` / `newOc` | `classes6/11/14/17.dex` 等 | 保持 URL query 解析 |
| `getKpn/getKpf/getODid/getDeviceId` | 多 dex 命中 | 保持反射方法名，大小写兜底 |

## 迁移实现策略

### 1. 保留包名过滤，但以极速版优先
```java
private static boolean isTarget(String pkg) {
  return "com.kuaishou.nebula".equals(pkg) || "com.smile.gifmaker".equals(pkg);
}
```

### 2. Hook 注册顺序
建议维持旧逻辑，但增加类加载失败重试：
1. `Application.attach(Context)`：拿到真实 `Context` 和 `ClassLoader`，延迟 1~3 秒重试 `fetchCredentials`。
2. `Application.onCreate()`：记录 app 启动状态。
3. `Activity.onResume()`：记录当前可用 Activity，用于最终弹窗。
4. `com.kwai.framework.network.access.params.e`：构造后采集基础公共参数。
5. `com.yxcorp.retrofit.d`：构造后采集 Retrofit/鉴权参数。
6. `okhttp3.OkHttpClient.newCall(Request)`：被动解析含 `egid=` 的请求。

### 3. 反射采集增强
旧逻辑对方法名依赖较强，新版建议做多候选：

| 字段 | 优先方法 | 兜底候选 |
|---|---|---|
| `salt` | `getTokenClientSalt()` | `mTokenClientSalt`, `mNewTokenClientSalt`, `tokenClientSalt` |
| `ud/userId` | `getId()` | `getUserId()`, `id`, `mId` |
| `kpn` | `getKpn()` | `getKPN()`, 字段 `kpn` |
| `kpf` | `getKpf()` | 字段 `kpf` |
| `did` | `getDeviceId()` | `did`, `deviceId` |
| `oDid` | `getODid()` | `getOdid()`, `oDid`, `odid` |

### 4. `e` 类构造 Hook 适配
旧版只 Hook 无参构造。新版 `classes16/17.dex` 中 `com.kwai.framework.network.access.params.e` 仍存在，但可能有多个构造重载。建议：
- 优先 `findAndHookConstructor("com.kwai.framework.network.access.params.e", cl, callback)`。
- 如果失败，枚举 `getDeclaredConstructors()`，对每个构造动态注册 `XC_MethodHook`。
- 在 `afterHookedMethod` 中只使用安全反射读取，不依赖构造参数。

### 5. `d` 类构造 Hook 适配
`com.yxcorp.retrofit.d` 在新版多处出现，核心网络实现集中在 `classes9.dex`/`classes17.dex`。迁移策略：
- 保持类名 Hook。
- 类未加载时不要终止；在 `OkHttpClient.newCall` 首次命中后再次尝试注册。
- 读取 `kuaishou.api_st` 时同时尝试：字段、getter、Map/Headers 风格访问。

### 6. URL 解析收敛
旧版仅用 `egid=` 正则。新版建议继续以 `egid=` 为功能触发条件，但增加 host 白名单，避免解析第三方请求：

```text
允许 host 后缀：
.gifshow.com
.ksapisrv.com
.kuaishou.com
.inkuai.com
.kwai.com
.kwimgs.com
localhost / 127.0.0.1 仅调试
```

必须解析字段：
- `egid`
- `newOc`
- `rdid`

### 7. 输出格式兼容
保持旧版 `buildFinalParams()` 字段顺序和覆盖关系，减少下游兼容风险：

```text
eClassParams
ud=<ud>
androidApiLevel=<运行时 Build.VERSION.SDK_INT，旧版硬编码 35 建议改动态>
userId=<ud>
appver=<当前目标包 versionName>
ver=<versionName 主版本>
nbh=0
kcv=1599 或运行期采集值
did_gt=<currentTimeMillis>
dClassParams 缺省补齐
rtyui 覆盖
#<salt>
```

## 迁移后的删除项
- 删除 Base64 公告文本中的 Telegram 链接。
- 删除旧宿主/NPatch 相关更新和配置资产，不把 `work/apktriage/old/` 中 native so 端点带入迁移模块。
- 禁止模块新增上传接口；结果只走本地弹窗/剪贴板。

## 验证步骤
1. 安装新版快手极速版与迁移模块。
2. LSPosed 勾选 `com.kuaishou.nebula`。
3. 冷启动 App，登录态存在时观察日志：`Application.attach Hook成功`、`QCurrentUser.me 成功`、`OkHttpClient.newCall Hook成功`、`HTTP参数 - Egid: ... newoc: ... rdid: ...`。
4. 首个含 `egid=` 的快手请求出现后，应弹出最终 CK 对话框并写入剪贴板。
5. 抓包确认：迁移模块没有访问 Telegram、配置更新、第三方上传域名。

## 风险点
- 新版多 dex 下类加载时机更晚：必须做延迟重试，避免一次失败后放弃。
- `QCurrentUser` 可能迁移到 Kotlin/KMP 包装层：必要时增加 `com.kwai.kmp.framework.model.user.QCurrentUserKt` 观察点，但优先保持旧 Java 类。
- `com.kwai.framework.network.access.params.e` 有多构造/混淆重载：仅 Hook 无参构造可能漏采。
- `kcv=1599` 可能随新版变化：建议从运行期公共参数中采集，采集不到再用旧默认值。
