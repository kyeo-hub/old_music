@echo off
setlocal enabledelayedexpansion

echo ============================================
echo    老歌播放器 - APK构建脚本
echo ============================================
echo.

:: 检查Java版本
echo [1/4] 检查Java环境...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误：未找到Java环境
    echo 请安装JDK 17或更高版本
    pause
    exit /b 1
)
echo ✓ Java环境已就绪

:: 检查Gradle
echo [2/4] 检查Gradle...
if not exist gradle\wrapper\gradle-wrapper.jar (
    echo 错误：Gradle包装器不存在
    echo 请确保项目已完整下载
    pause
    exit /b 1
)
echo ✓ Gradle包装器已就绪

:: 设置执行权限
echo [3/4] 设置执行权限...
if not exist gradlew.bat (
    echo 错误：gradlew.bat不存在
    pause
    exit /b 1
)
echo ✓ 执行权限已设置

:: 开始构建
echo [4/4] 开始构建APK...
echo.
gradlew assembleRelease

if %errorlevel% equ 0 (
    echo.
    echo ============================================
    echo    构建成功！
    echo ============================================
    echo APK文件位置: app\build\outputs\apk\release\app-release.apk
    echo.
    pause
) else (
    echo.
    echo ============================================
    echo    构建失败
    echo ============================================
    echo 请检查错误信息，可能需要配置Android SDK
    echo.
    pause
)