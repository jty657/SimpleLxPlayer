---
name: android-build-env
description: 容器内 Android 构建环境要点：aarch64 需 aapt2 override、Google Maven 不通改用阿里云镜像、SDK/Gradle 路径
---
# Android 构建环境（aarch64 容器）

## 已确认事实（2026-09 验证）
- 容器架构：**aarch64**（arm64），不是 x86_64
- Java 17（PATH 中有 java，无独立 JAVA_HOME 设置）
- Android SDK：`/root/android/sdk`（platforms: android-34/35；build-tools: 33.0.1/34.0.0/35.0.0；licenses 已接受）
- Gradle 8.10.2 二进制在 `~/.gradle/wrapper/dists/gradle-8.10.2-bin/*/gradle-8.10.2/bin/gradle`
- AGP 缓存版本：7.3.0/8.1.0/8.5.0/8.6.0（完整）/8.7.0（缺 jar）/9.0.1 —— **8.6.0 缓存最完整，用它**
- 大量 androidx / com.android.tools.* 依赖已在 ~/.gradle 缓存（可离线命中）

## 关键坑与解法
1. **aapt2 是 x86_64 二进制，arm 容器跑不了** → 必须用 `/root/armtools35/build-tools/aapt2`（arm64 原生，已验证可执行），通过 `gradle.properties` 里 `android.aapt2FromMavenOverride=/root/armtools35/build-tools/aapt2` 覆盖
2. **Google Maven（dl.google.com / maven.google.com）在本容器不可达** → 仓库换阿里云镜像：`https://maven.aliyun.com/repository/google`（已验证 200）、`.../central`（200）；Maven Central 官方 repo1.maven.org 也可达
3. `local.properties` 写 `sdk.dir=/root/android/sdk`
4. 缓存中**没有 r8/d8 工件**（com.android.tools:r8），首次在线构建需从阿里云镜像下载

## 构建命令
```bash
cd ~/workspace/android && ~/.gradle/wrapper/dists/gradle-8.10.2-bin/*/gradle-8.10.2/bin/gradle assembleDebug --no-daemon
```

## 其他工具
- `/root/armtools35/` 另有 platform-tools（adb 等 arm 版）、zipalign、aapt、dexdump