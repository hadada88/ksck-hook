# 旧 APK 自动化功能分析

## 结论
旧样本的核心逻辑集中在 `work/apktriage/ks/classes3.dex` 的 `com.beidu.ks_hook.KwaiHook`，这是一个 Xposed/LSPosed Hook 模块。它不在自身发起业务 HTTP 请求，而是在目标快手进程内 Hook 应用生命周期、公共参数构造类、Retrofit 参数类和 OkHttp 请求入口，收集快手客户端已经生成的登录/设备/公共参数，拼接成 `KS_CK` 文本后通过弹窗展示并复制到剪贴板。

## 关键入口
证据文件：
- `work/apktriage/ks/selected.txt`
- `work/apktriage/ks/kwaihook_disasm.txt`
- `work/apktriage/ks/classes3_network_strings.txt`

`handleLoadPackage(lpparam)`：
- 目标包名硬编码：
  - `com.kuaishou.nebula`：快手极速版
  - `com.smile.gifmaker`：快手主包
- 命中后注册 Hook：
  1. `hookApplicationStart(classLoader)`：Hook `Application.onCreate`
  2. `hookEClass(lpparam)`：Hook `com.kwai.framework.network.access.params.e` 构造
  3. `hookDClass(lpparam)`：Hook `com.yxcorp.retrofit.d` 构造
  4. `hookPackageManager(lpparam)`：疑似版本/包信息采集
  5. `hookSaltCapture(classLoader)`：Hook `Application.attach(Context)` 后读取当前用户
  6. `hookOkHttpNewCall(lpparam)`：Hook `okhttp3.OkHttpClient.newCall(Request)`
  7. `hookActivity(lpparam)`：Hook `Activity.onResume`

## 参数采集逻辑

### 1. 用户凭据/盐值
`fetchCredentials(classLoader)`：
- 反射加载：`com.kwai.framework.model.user.QCurrentUser`
- 调用静态方法：`QCurrentUser.me()`
- 从返回对象调用：
  - `getTokenClientSalt()` → `salt`
  - `getId()` → `ud`
- 成功后置位：`saltCaptured = true`

### 2. 网络公共参数对象
`hookEClass`：
- Hook 类：`com.kwai.framework.network.access.params.e`
- 在构造后读取对象方法：
  - `getKpn` → `kpn`
  - `getKpf` → `kpf`
  - `getDeviceId` → `did`
  - `getODid` → `oDid`
- 格式化到 `eClassParams`。

`hookDClass`：
- Hook 类：`com.yxcorp.retrofit.d`
- 在构造后读取字段/方法，重点字符串包括：
  - `os`
  - `client_key`
  - `kuaishou.api_st`
- 格式化到 `dClassParams`。

### 3. OkHttp 请求截获
`hookOkHttpNewCall`：
- Hook 类：`okhttp3.OkHttpClient`
- Hook 方法：`newCall(okhttp3.Request)`
- 回调中调用 `interceptHttpRequest(param, classLoader)`。

`interceptHttpRequest`：
- 对 `Request` 调用 `url()`，转字符串。
- 使用 `TARGET_URL_PATTERN` 过滤，静态初始化处为：`egid=`。
- 只要 URL 中出现 `egid=`，即解析 query 参数：
  - `egid` → `dynamicEgid`
  - `newOc` → `newoc`
  - `rdid` → `rdid`
- 全量 query 通过 `buildParamString` 写入 `rtyui`。

### 4. 参数拼接
`hasAllRequiredParams()` 必需字段：
- `dynamicEgid`
- `newoc`
- `ud`
- `salt`

`buildFinalParams()` 合并顺序：
1. `eClassParams`
2. 覆盖/补充：`ud`、`androidApiLevel=35`、`userId`、`appver`、`ver`、`nbh=0`、`kcv=1599`、`did_gt=<currentTimeMillis>`
3. `dClassParams`：仅补缺失字段
4. `rtyui`：来自含 `egid=` 的请求 query，覆盖已有字段
5. `#=<salt>`：展示时特殊处理为 `#salt`，不是 `#=salt`

`buildDisplayText()` 输出格式：
- 普通键值：`key=value`
- 多字段分隔：`;`
- `#` 键特殊输出：`#<salt>`

## UI/自动化呈现
- `hookActivity` 记录最后恢复的 `Activity`。
- `Application.onCreate` / `Activity.onResume` 负责展示公告弹窗和最终结果弹窗。
- `showEnhancedDialog(Activity)` 在参数齐全后只展示一次，并设置 `dialogShown = true`、`shouldStopHooking = true`。
- `copyToClipboard(Context, text)`：label 为 `KS_CK`，将最终 CK 文本写入系统剪贴板。

## 自动化本质
这不是 UI 自动点击脚本，而是进程内 Hook 自动化：被动等待快手自身初始化、登录态加载和首批网络请求，从内存对象和请求 URL 中提取已生成的参数，本地组合并展示/复制结果。
