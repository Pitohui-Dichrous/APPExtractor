@echo off
setlocal
cd /d "%~dp0"
if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not exist local.properties if not defined ANDROID_HOME goto missing_sdk
call gradlew.bat :core:test :app:assembleRelease
if errorlevel 1 goto failed
if not exist "app\build\outputs\apk\release\app-release.apk" goto unsigned
if not exist dist mkdir dist
copy /y "app\build\outputs\apk\release\app-release.apk" "dist\APPExtractor-1.2.0.apk" >nul
if errorlevel 1 goto failed
echo APK: %CD%\dist\APPExtractor-1.2.0.apk
exit /b 0
:missing_sdk
echo Set ANDROID_HOME, ANDROID_SDK_ROOT or sdk.dir in local.properties.
exit /b 1
:unsigned
echo Release APK needs .signing files. Use gradlew.bat :app:assembleDebug for a debug APK.
exit /b 1
:failed
echo Build failed. Check JDK 17 and Android SDK settings.
exit /b 1
