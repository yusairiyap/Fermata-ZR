## !!!Be aware of scam!!! The sites like https :// fermata-auto . com  are 100% scam. This app is free and opensource!


## zrAuto
zrAuto is a modded, Android-Auto-focused fork of [Fermata Media Player](https://github.com/AndreyPavlenko/Fermata), rebranded and enhanced by [Yusairi Yap](https://github.com/yusairiyap).

[Download the latest release](https://github.com/yusairiyap/zrAuto/releases/latest)

## What's different from upstream Fermata
This fork isn't just a re-skin — since forking from upstream, it has picked up its own UI work and identity changes:

* **Rebranded identity** — new app name, icon, Android Auto media-service name, and application ID (`com.yusairiyap.zrauto`), independent from the original app.
* **Material 3 redesign** — migrated theming to Material 3 with a card-based UI, new "Modern"/"Classic" theme naming, configurable nav bar/control panel icon-size sliders, and numerous visual-contrast fixes.
* **YouTube videos in Favourites/Playlists** — YouTube items can now be saved to and shown in Favourites and Playlists, not just browsed live, with an animated video-info overlay and several UX fixes from real-device testing.
* **Android Auto-tuned UI defaults** — nav bar, tool bar, and control panel sizing now default specifically for the car form factor.
* **Donate menu removed** from the in-app UI (see the [Donation](#donation) section below for how to still support the original author).
* **Self-hosted update checker** — the in-app "check for updates" feature now checks this repository's own GitHub Releases instead of upstream's, so you get zrAuto updates, not upstream Fermata ones.
* **Own CI/release pipeline** — GitHub Actions builds installable Android Auto APKs for every push/PR, and publishes tagged releases automatically (see [Building the project](#building-the-project)).

Everything else — the media engines, addons, and general feature set below — is inherited from upstream Fermata, not original to this fork.

## About
zrAuto (based on Fermata Media Player) is a free, open source audio, video and TV player with a simple and intuitive interface. It is focused on playing media files organized in folders and playlists.

Supported features:

* Play media files organized in folders
* IPTV addon with support for XMLTV EPG and Catchup
* Remembers the last played track and position for each folder
* Support for favorites and playlists
* Support for CUE and M3U playlists
* Support for bookmarks
* Audio effects: Equalizer, Bass/Volume Boost and Virtualizer
* Configure audio effects for individual tracks and folders
* Configure playback speed for individual tracks and folders
* Customizable titles and subtitles
* Support for Android Auto
* Support for Android TV
* Show favorites and playlists on Android TV home screen
* Pluggable media engines: MediaPlayer, ExoPlayer and VLC
* Video player with support for subtitles and audio streams (VLC Engine only)

## Building the project
* Download and install the latest Android SDK or Android Studio from https://developer.android.com/studio/
* Set the environment variable ANDROID_SDK_ROOT pointing to the SDK directory
```bash
export ANDROID_SDK_ROOT=<path to android SDK>
```

### Clone the repository
```bash
git clone --recurse-submodules https://github.com/yusairiyap/zrAuto.git
cd zrAuto
```

### Build AAB
```bash
./gradlew bundleAutoRelease -PAPP_ID_SFX=.type.your.pkg.sfx.here
find $PWD -name *.aab
```

### Build APK
```bash
./gradlew bundleAutoRelease -PAPP_ID_SFX=.type.your.pkg.sfx.here
find $PWD -name *.apk
```

The default application ID is `com.yusairiyap.zrauto` (Android Auto builds get a `.auto` suffix). Override it with `-PAPP_ID=your.own.id` if you need a different identity for your own build.

## Donation
zrAuto itself does not solicit donations, but it's built on top of Andrey Pavlenko's original Fermata Media Player. If you'd like to support the original author, here are his donation links:

[PayPal](https://www.paypal.com/donate/?hosted_button_id=NP5Q3YDSCJ98N)

[CloudTips](https://pay.cloudtips.ru/p/a03a73da)

[Yandex Money](https://money.yandex.ru/to/410014661137336)
