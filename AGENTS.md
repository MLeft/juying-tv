# AGENTS.md — 聚影 TV

本仓库是面向 Android TV 的媒体中心（Kotlin + Compose + Media3 + Room）。
电视端交互、视觉与发布有强约束，所有 Agent/开发者必须遵守。

## 开工前（必须）

1. 完整阅读 [`docs/tv-development-rules.md`](docs/tv-development-rules.md)。本文件只列红线，细节以规范为准。
2. 改动前先读相关模块，不要凭记忆假设代码现状。
3. 涉及 UI 的改动，必须准备真机验证；不能只编译通过就交付。

## 十二条红线

1. 新功能必须纯遥控器（D-pad）可达，任何状态下焦点都不能丢。
2. 焦点修饰符顺序：`focusRequester` / `onFocusChanged` / `onPreviewKeyEvent` 必须写在 `focusable()` 或 `clickable()` 之前。
3. 禁止在列表/页面中段写 `down = FocusRequester.Cancel`；边界只加在真正首尾。
4. 选中态与聚焦态必须是两种不同视觉，不能共用高亮。
5. 面板必须复用既有规格（二级菜单 340–360dp、圆角 18dp、同底色/描边/标题），不得随意加宽。
6. 选项文字必须真正水平居中（`fillMaxWidth + TextAlign.Center`），聚焦/选中不能跳动。
7. 可聚焦控件必须有明确焦点态，不能只靠颜色区分。
8. 播放器控制栏自动隐藏不得抢焦点；聚焦控件时暂停倒计时。
9. 儿童内容必须用 16:9 缩略图卡片；禁止首字/首字母封面。
10. 封面、标题、字幕匹配不确定时宁缺勿错。
11. 每次可安装改动必须：构建 + 单测 + 签名 + 真机安装 + 截图验证。
12. 只允许 `adb install -r` 覆盖安装，每次装机 `versionCode + 1`；禁止卸载。

## 设计与组件

- 画布按 **960×540dp**（1920×1080 / 320dpi），页面左右 36dp 安全边距。
- 颜色、字号、圆角只从 `JuyingColors` / `JuyingTheme` 取；禁止在业务代码新造近似色。
- 优先复用 `FocusableSurface`、`TvChip`、`TvButton`、`ProgressLine`、`PlayerMenu`、`PlayerMenuRow`。
- 表单输入统一用 `TvTextField`，点击选择类字段统一用 `TvPickerField`；标签在框外，禁止手写第二套输入框样式。
- 焦点态必须使用「暖底 `SurfaceFocused` + 2dp 金边 + `Modifier.focusGlow` 连续渐隐柔光 + 缩放」四件套；选中态只保留平面标记，禁止共用柔光/金边。
- 滚动容器与 Lazy 列表必须在四周预留至少 10dp 内边距，避免焦点柔光被裁切。
- 焦点动画：spring(damping 0.72 / stiffness 560)；颜色与泛光过渡 150ms；面板进出 120–180ms。
- 新增面板先对照规范 §4.4 的规格表选类型；没有对应类型时先补规格。

## 修改流程

1. 明确目标与影响范围，只改必要文件。
2. 实现后运行：

```bash
export JAVA_HOME=~/android-jdk/Contents/Home
export ANDROID_HOME=~/android-sdk
~/gradle-dist/gradle-8.7/bin/gradle -p /Users/hugh/code/wangzai assembleRelease testReleaseUnitTest
```

3. 签名（密码从 `release/keystore-info.txt` 读取，不回显）：

```bash
JAVA_HOME=~/android-jdk/Contents/Home \
~/android-sdk/build-tools/34.0.0/apksigner sign \
  --ks release/juying-release.jks --ks-key-alias juying \
  --ks-pass pass:<storepass> --key-pass pass:<keypass> \
  --out dist/JuyingTV-<version>.apk dist/JuyingTV-<version>.apk
```

4. 装机并验证：

```bash
ADB_USB=0 ~/ptools33/platform-tools/adb connect 192.168.1.196:5555
ADB_USB=0 ~/ptools33/platform-tools/adb install -r dist/JuyingTV-<version>.apk
ADB_USB=0 ~/ptools33/platform-tools/adb shell am force-stop com.juying.tv
ADB_USB=0 ~/ptools33/platform-tools/adb shell am start -n com.juying.tv/.MainActivity
ADB_USB=0 ~/ptools33/platform-tools/adb shell dumpsys package com.juying.tv | grep version
```

5. 用 `adb exec-out screencap -p > /tmp/xxx.png` 截图确认焦点/面板/状态；不要在未截图时声称 UI 正确。
6. 同步更新 README 的 APK 版本与已知限制。
7. 构建与测试全绿后 commit；无远端时不要假装已 push。

## 验证清单（提交前逐项确认）

- [ ] 目标功能纯 D-pad 可完成，首项、末项、返回路径都走过。
- [ ] 焦点态/选中态清晰，面板关闭后焦点回到触发控件。
- [ ] 没有使用规范外的宽度、颜色、字号；文字居中无跳动。
- [ ] 加载/空/错误/成功状态齐全；长任务有进度。
- [ ] `assembleRelease` 与 `testReleaseUnitTest` 通过。
- [ ] 真机安装成功，`dumpsys` 版本号正确，媒体库与进度未丢。
- [ ] 关键状态截图已确认。
- [ ] README 与实际版本一致。

## 已知环境陷阱

- JDK 路径是 `~/android-jdk/Contents/Home`（不是 `~/android-jdk`），否则 apksigner 报 “Unable to locate a Java Runtime”。
- ADB 必须 `ADB_USB=0` 启动，否则 USB 初始化可能卡死；5555 间歇 `offline/refused` 时先 `kill-server` 再重连。
- 电视深度休眠会断网，无法远程唤醒；连接失败时告知用户用实体遥控器开机，不能假装安装成功。
- 电视没有 DocumentsUI，本地文件选择必须用应用内浏览器。
- 电视截图不包含视频层；播放是否正常用进度、HUD、轨道信息判断，画面需人工确认。
- 小米电视拒绝版本降级；测试包也要用更高 `versionCode`。
- APK 仅含 `armeabi-v7a/arm64-v8a`，x86_64 模拟器无法安装，真机才是验收标准。

## 文档索引

- 完整规范：[`docs/tv-development-rules.md`](docs/tv-development-rules.md)
- 项目说明：[`README.md`](README.md)

新增踩坑必须回写到规范 §13 反模式表，并在提交信息中注明规则编号。
