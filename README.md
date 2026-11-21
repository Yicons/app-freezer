# App Freezer · 应用冻结管理

轻量应用冻结工具，支持 Android 8.0 及以上。

- 按名称或包名搜索，显示应用图标和冻结状态。
- 冻结、解冻、查看应用详情，保护部分关键系统应用。
- 优先使用已有系统权限，否则请求 Root；“权限”入口可重新验证。
- 暂不支持 ADB 激活或 Shizuku，没有广告和统计上报。

当前为 `0.1.0-alpha.1` 开发版本，仓库仅用于源码备份和追溯。

## 构建

JDK 17、Android SDK 35：

```sh
./gradlew assembleDebug
```

源码采用 PolyForm Noncommercial 1.0.0，仅授权非商业用途；商业用途需另行取得许可。Gradle Wrapper 保留原许可。
