# global-icon-pack-android
An xposed module to apply icon pack globally

Some launchers support icon packs. However the icons are usually not consistent across the whole system. For example, while the app icons may change on the home screen, the Settings page and the Recent Apps screen often retain the default icons, not reflecting the updated look.

This module is designed to extend the customization of icon packs throughout the entire system.

## Requirements
* AOSP based OS (I only tested on android 14)
* Magisk
* LSPosed

## Installation
1. Install the apk. 
2. Open Global Icon Pack. Remember to fill the `Icon pack` settings with an icon pack package name.
3. Select the recommend apps in lsposed (Other launcher/apps may also work, depending on the api they use)
4. For pixel launcher and probably other similar launcher 3 based launcher, you will need to delete `/data/data/com.google.android.apps.nexuslauncher/databases/app_icons.db` and then restart the launcher through its settings page.

## Settings
* `Icon pack`: The package name to be used as icon pack.
* `No force shape`: This setting is specific to pixel launcher. Turning it on will prevent it from forcing a uniform icon shape.
* `Icon pack settings`: This section controls whether to use "fallback" elements such as masks. Some icon packs include predefined masks or backgrounds that ensure all icons have a consistent shape or style, even for apps that don't have dedicated icons in the pack. You can choose to enable or disable them.

## Known Issues
* ~~When clicking an app, Pixel Launcher triggers an animation, whose starting frame displays an icon with a white border.~~
* ~~Currently, this does not apply to the Clock and Calendar apps.~~

## Disclaimer
> [!WARNING]
> * Please note that this module may not be fully compatible with all custom ROMs. 
> * I do not take any responsibility for any damage or issues that may occur to your device.