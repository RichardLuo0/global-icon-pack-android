#!/bin/bash

pkg install -y aapt2 apksigner unzip curl

dir="$HOME/global_icon_pack"
androidJar="$dir"/android.jar

# Download android sdk 34. 35 doesn't work because https://github.com/termux/termux-packages/issues/22667
if [ ! -d "$dir" ]; then
  mkdir -p "$dir"
fi
if [ ! -e "$androidJar" ]; then
  echo -e "\033[34mDownloading android sdk...\033[0m"
  currentDir=$(pwd)
  cd "$dir" || exit 1
  curl -o android.zip https://dl.google.com/android/repository/android-14_r04.zip
  unzip -j android.zip android-*/android.jar
  rm android.zip
  cd "$currentDir" || exit 1
fi

echo -e "\033[34mCompiling apk...\033[0m"
aapt2 compile --dir res/ -o compiled.zip
aapt2 link compiled.zip -I "$androidJar" -o app.apk.unaligned --manifest AndroidManifest.xml
aapt add app.apk.unaligned classes.dex
zipalign -f -v -p 4 app.apk.unaligned app.apk
apksigner sign --ks dummy.jks --ks-pass pass:123456 app.apk
echo -e "\033[32mIcon pack has been generated and saved to $(pwd)/app.apk. Please install it manually.\033[0m"
