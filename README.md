# Smooth Player

一个独立的原生 Android 本地视频播放器示例，使用系统硬解链路播放指定路径或系统文件选择器授权的视频。

## 功能

- 通过系统文件选择器浏览所有可打开文件，并持久化读取授权
- 内置 Shizuku 文件浏览器，可浏览系统文件选择器隐藏的 `/sdcard/Android/data` 与 `/sdcard/Android/obb`
- 兼容输入 `content://`、`file://` 或部分设备允许访问的本地路径
- `MediaPlayer + TextureView` 播放，优先走设备系统解码器
- 支持常见本地视频容器和编码，实际能力取决于设备系统 codec
- 播放/暂停、进度拖动、当前时间/总时长
- 系统浏览与 Shizuku 浏览分离，避免混用系统文件选择器和受限目录浏览
- 倍速：`0.5x / 0.75x / 1x / 1.25x / 1.5x / 2x`
- 双击左半屏后退 10 秒，双击右半屏快进 10 秒
- 长按临时 3 倍速播放，松手恢复原倍速
- 适配/填充画面模式、控制层锁定、自动隐藏控制层
- 保存最近播放的视频、进度、倍速和画面模式，Surface 重建后自动恢复
- 应用内检查更新、下载 APK 并跳转系统安装器
- 顶部“权限”入口可跳转 Android 11+ 的所有文件访问权限设置，方便直接路径访问更多本地目录
- 对 `/Android/data/`、`/Android/obb/` 等受限路径，支持 Shizuku UserService 直接打开原文件 fd 播放，避免复制大视频；root/su bind mount 保留为兜底
- 深色沉浸式界面，保持屏幕常亮

## 构建

本项目复用同工作区 `lightroom_android` 的本地构建环境：

```bash
JAVA_HOME=/home/mi/WorkSpace/Projects/VibeCoding/lightroom_android/.build-env/jdk-17 \
ANDROID_HOME=/home/mi/WorkSpace/Projects/VibeCoding/lightroom_android/.build-env/android-sdk \
ANDROID_SDK_ROOT=/home/mi/WorkSpace/Projects/VibeCoding/lightroom_android/.build-env/android-sdk \
GRADLE_USER_HOME=/home/mi/WorkSpace/Projects/VibeCoding/lightroom_android/.gradle-home \
/home/mi/WorkSpace/Projects/VibeCoding/lightroom_android/.build-env/gradle-7.6.4/bin/gradle --no-daemon :app:assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

生成可发布 beta APK，并同步到 `dist/`：

```bash
./build_beta_apk.sh
```

发布文件：

```text
dist/SmoothPlayer-beta.apk
dist/version.json
dist/download-qr.png
```

下载链接：

```text
https://github.com/zhouhaoran-TJU/VibeReplay/raw/main/dist/SmoothPlayer-beta.apk
```

## 设计边界

当前版本不打包 FFmpeg、LibVLC 或 Media3 扩展，因此不能承诺播放所有编码格式。这样做的好处是体积小、构建稳定、优先使用系统硬解；如果需要覆盖更多冷门格式、外挂字幕、多音轨和网络流，下一步应在本地依赖缓存或网络可用后切换到 Media3 或 LibVLC。
