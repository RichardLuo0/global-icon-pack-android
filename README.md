 <img width="200" height="200" style="display: block; border-radius: 9999px;" src="metadata/en-US/images/icon.png">

# Global Icon Pack
An Xposed module for applying icon packs globally

![GitHub Repo stars](https://img.shields.io/github/stars/RichardLuo0/global-icon-pack-android?style=for-the-badge&color=%23FF9800)
![IzzyOnDroid](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/com.richardluo.globalIconPack&style=for-the-badge&color=%234CAF50)

Some launchers support icon packs, however the icons are usually inconsistent across the whole system. For example, the Settings page and the Recent Apps screen may retain the default icons.
This module is designed to extend the customization of icon packs throughout the entire system.

[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/com.richardluo.globalIconPack)

## Preview
<div>
    <img src="metadata/en-US/images/phoneScreenshots/1.png" width="200" />
    <img src="metadata/en-US/images/phoneScreenshots/2.png" width="200" />
    <img src="metadata/en-US/images/phoneScreenshots/3.png" width="200" />
    <img src="metadata/en-US/images/phoneScreenshots/4.png" width="200" />
</div>

## Requirements
* AOSP based OS (I tested on android 14, 15)
* Magisk
* LSPosed

## Installation
1. Install the apk. 
2. Select the recommend apps in lsposed (Other launcher/apps may also work, depending on the api they use)
3. Open Global Icon Pack, choose an icon pack.
4. Open the three dot menu, click each of `Restart *`.

* Recent screen will use your default launcher unless you use quickswitch. So you will need to select pixel launcher for that to work.
* Pixel launcher saves its icon database in `/data/data/com.google.android.apps.nexuslauncher/databases/app_icons.db`.
* In icon variant, the option `Modified` indicates that you have made changes to the icon variants. If enabled, when the icon pack updates, it will only add new icons instead of replacing all icons. Note that this could cause issues if any icon entry is missing in the new version!

## Known Issues
* If the launcher is slow to boot or crashes, switch to 'local' mode.
* If it says "Please ensure the Xposed module has been enabled first" and you have the module enabled already, try force stopping then restart the app.
* Regardless of the minimum SDK version, you must test compatibility with android versions below 14 by yourself.

## Help with localization
<a href="https://crowdin.com/project/global-icon-pack-android" rel="nofollow"><img style="width:140;height:40px" src="https://badges.crowdin.net/badge/light/crowdin-on-dark.png" srcset="https://badges.crowdin.net/badge/light/crowdin-on-dark.png 1x,https://badges.crowdin.net/badge/light/crowdin-on-dark@2x.png 2x" alt="Crowdin | Agile localization for tech companies" /></a>

## Disclaimer
> [!WARNING]
> * Please note that this module may not be fully compatible with all custom ROMs. 
> * I do not take any responsibility for any damage or issues that may occur to your device.
