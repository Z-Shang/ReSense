# Android 15 emulator

The project-local `Sense_API_35` AVD uses the Pixel 7 profile (1080×2400, density 420), Android 15 / API 35 Google APIs x86_64 image revision 9, 4 GB RAM and four virtual CPU cores. Emulator version: 37.2.13. KVM acceleration was verified as installed and usable. SwiftShader provides software graphics rendering; CPU execution remains hardware accelerated.

AVD data is under ignored `.toolchain/avd/`. `tools/android-env.sh` sets `ANDROID_AVD_HOME` and adds the emulator to PATH. The emulator window was launched on the host desktop and left running after verification.

## Start and install

If it is already running, use the existing window. Otherwise, from the project root:

```sh
./tools/run-emulator.sh
```

To run without a desktop window, add `-no-window`. In a second terminal, wait for Android to finish booting, then:

```sh
source tools/android-env.sh
adb devices -l
adb -s emulator-5554 install -r launcher/build/outputs/apk/debug/launcher-debug.apk
adb -s emulator-5554 shell am start -W -n dev.sense.launcher/.MainActivity
```

The first emulator normally uses `emulator-5554`. Check `adb devices -l` and adjust the serial when other emulators are running. Commands must explicitly target the emulator to avoid changing a connected phone.

Sense Launcher is already the default home app on the configured virtual device. To select it on a recreated emulator:

```sh
adb -s emulator-5554 shell cmd package set-home-activity dev.sense.launcher/.MainActivity
adb -s emulator-5554 shell input keyevent KEYCODE_HOME
```

To stop this emulator, close its window, or run:

```sh
adb -s emulator-5554 emu kill
```

## Recreate the AVD

After SDK setup and loading the project environment:

```sh
sdkmanager 'emulator' 'system-images;android-35;google_apis;x86_64'
mkdir -p "$ANDROID_AVD_HOME"
avdmanager create avd --name Sense_API_35 --package 'system-images;android-35;google_apis;x86_64' --device pixel_7 --path "$ANDROID_AVD_HOME/Sense_API_35.avd"
```

Answer `no` to the custom hardware profile prompt. The configured device's RAM was increased from the generated 2 GB default to 4 GB in its `config.ini`; its emulated camera was disabled. The creation tool printed a missing optional `devices.xml` diagnostic but created the Pixel 7 AVD successfully, and the resulting virtual device booted and ran the APK.

## Verified behavior

- APK installation succeeded; activity launch returned `Status: ok`.
- Guest reported Android 15 and SDK 35.
- Default HOME resolution returned `dev.sense.launcher/.MainActivity`.
- Live clock/date and dock rendered in the home screenshot.
- Drawer displayed 20 launchable apps.
- Selecting Settings from the drawer resumed `com.android.settings/.Settings`.
- Home returned to Sense Launcher; Back from the drawer restored the home clock/dock.
- Crash log buffer was empty after these checks.

Local artifacts: `.toolchain/emulator-home.png`, `.toolchain/emulator-drawer.png`, UI hierarchy XML files, `.toolchain/emulator.log` and `.toolchain/emulator-crash.log`. These checks do not cover all device sizes, widget hosting, profiles, weather or visual fidelity to Sense 3.6.

See Google's [emulator command-line reference](https://developer.android.com/studio/run/emulator-commandline) and [acceleration documentation](https://developer.android.com/studio/run/emulator-acceleration) for host configuration details.
