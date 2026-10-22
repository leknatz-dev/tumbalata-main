@echo off
rem Starts the Tumbalata test phone (an Android emulator). The virtual phone lives on F:\Temp\AndroidAVD so it
rem doesn't fill up C:. Then install the game on it with:  gradlew.bat android:installDebug
set ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk
set ANDROID_AVD_HOME=F:\Temp\AndroidAVD
start "" "%ANDROID_SDK_ROOT%\emulator\emulator.exe" -avd Tumbalata_Phone -netdelay none -netspeed full
