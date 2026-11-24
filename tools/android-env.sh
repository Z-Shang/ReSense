# Source this file from the Sense project root: source tools/android-env.sh
# Resolve first so later directory changes do not affect the selected tools.
if [ ! -f settings.gradle.kts ] || [ ! -d .toolchain/jdk17 ]; then
    echo "Run from the Sense project root after toolchain setup." >&2
    return 1
fi
export JAVA_HOME="$PWD/.toolchain/jdk17"
export ANDROID_HOME="$PWD/.toolchain/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$PWD/.toolchain/gradle-home"
export ANDROID_USER_HOME="$PWD/.toolchain/android-user"
export ANDROID_AVD_HOME="$PWD/.toolchain/avd"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PWD/.toolchain/gradle-8.11.1/bin:$PATH"
