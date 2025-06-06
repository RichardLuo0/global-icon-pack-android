#!/bin/bash
set -e
shopt -s nullglob

pkg install -y aapt2 apksigner unzip zip curl

dir="$HOME/global_icon_pack"
androidJar="$dir"/android-14.jar

if [ ! -d "$dir" ]; then
  mkdir -p "$dir"
fi

# Download android sdk 34. 35 doesn't work because https://github.com/termux/termux-packages/issues/22667
if [ ! -e "$androidJar" ]; then
  read -r -p "Download android sdk to $androidJar? (around 50MB) (y/n): " answer
  if [[ "$answer" != "y" && "$answer" != "Y" ]]; then
      exit 1
  fi

  echo -e "\033[34mDownloading android sdk...\033[0m"
  currentDir=$(pwd)
  cd "$dir" || exit 1
  curl -o android.zip https://dl.google.com/android/repository/platform-34-ext7_r03.zip
  unzip -o -j android.zip android-*/android.jar
  rm android.zip
  mv android.jar "$androidJar"
  cd "$currentDir" || exit 1
fi

echo -e "\033[34mCompiling apk...\033[0m"
mkdir -p compiled
for compiledXML in res/drawable/*.compiledXML
do
  aapt2 compile "$compiledXML" --source-path "${compiledXML%.compiledXML}.xml" -o compiled/
  rm "$compiledXML"
  echo -e "Compiled $compiledXML"
done
aapt2 compile --dir res/ -o compiled/
zip -r compiled.zip compiled/
aapt2 link compiled.zip -I "$androidJar" -o app.apk.unaligned --manifest AndroidManifest.xml --stable-ids resIds.txt
aapt add app.apk.unaligned classes.dex
zipalign -f -v -p 4 app.apk.unaligned app.apk
apksigner sign --ks dummy.jks --ks-pass pass:123456 app.apk
echo -e "\033[32mandroid.jar is saved in $androidJar. You can delete it if you want.\033[32m"
echo -e "\033[32mThe icon pack has been successfully generated at $(pwd)/app.apk. Please install it manually.\033[0m"
