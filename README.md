# Debate Arena

An Android application for debates between independently prompted AI agents and an AI judge.

## Download APK

**[Download Debate-Arena.apk directly](https://github.com/ninamizuki27/debate-arena/releases/download/latest/Debate-Arena.apk)**

The APK is automatically published to the rolling [GitHub Release](https://github.com/ninamizuki27/debate-arena/releases/tag/latest) after a successful Android build on `main`. If the direct link returns 404, the build or release has not succeeded yet. Check [Actions](https://github.com/ninamizuki27/debate-arena/actions).

## Build locally

JDK 17, Android SDK 35 and Gradle 8.10.2: `gradle :app:assembleDebug`.

## Status

**Version 0.3.0:** ChatGPT OAuth now sends the required persistent `ext_agent_host_id`, checks plan permission, and submits a supported Responses array input. It also provides an AI connection test. These network flows still need on-device verification.\n\n**Updating a development APK:** GitHub-hosted builds currently use a temporary Android debug signing certificate. If Android refuses to install the new APK over an older version, uninstall the old app first, then install the new APK. This clears locally stored sign-in and debate history. For seamless future upgrades, configure a persistent secure signing key.
