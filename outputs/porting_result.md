# Sign Bridge 移植结果

## 结论

ClassLoader 修复已完整：`AtlasSign` 的目标类加载统一使用应用进程的 `appClassLoader.loadClass(...)`，`MainHook` 在处理目标包时从 `lpparam.classLoader` 获取并传入 `AtlasSign.init(appClassLoader)`。

## 修改文件

- `app/src/main/java/com/ksck/signbridge/AtlasSign.java`
  - 保留并确认 `init(ClassLoader)` 注入应用 ClassLoader。
  - `KSecurity`、`MXSec`、配置类和 `android.util.Base64` 均通过 `appClassLoader.loadClass(...)` 加载。
- `app/src/main/java/com/ksck/hook/MainHook.java`
  - 使用 `lpparam.classLoader` 初始化 `appClassLoader`。
  - 在启动 `SimpleHttpServer` 前调用 `AtlasSign.init(appClassLoader)`。

## GitHub Actions

- Commit: `f6ff00f910249cbcac54a329cafbe9ac5b56f8a4`
- Commit message: `fix: classloader`
- Workflow run: `32162053710`
- 结果：`completed / success`
- Artifact：`ksck-hook-release`
- 本地未执行 Gradle/Android 构建，APK 由 GitHub Actions 生成。

## ADB 安装

下载 Actions artifact 并解压后执行：

```powershell
adb install -r .\app-release.apk
```

## 验证步骤

```powershell
adb shell pm path com.ksck.signbridge
adb shell am force-stop com.kuaishou.nebula
adb reverse tcp:3058 tcp:3058
curl.exe http://127.0.0.1:3058/status
adb logcat -c
adb logcat -s KS_CK SignBridge AtlasSign
```

在 LSPosed 中启用 `ksck-hook` 模块，并将作用域设置为 `com.kuaishou.nebula`（如需兼容极速版旧包，同时启用 `com.smile.gifmaker`），然后重新启动目标 App。预期可看到 `SignBridge` 监听日志，并通过 `/status` 获得 JSON 状态响应。
