name: Build Standalone APK

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - name: Checkout code
      uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Setup Gradle
      uses: gradle/actions/setup-gradle@v4

    # 核心修复步骤：如果本地的 gradlew 损坏或格式不对，直接用系统自带的 gradle 重新生成它！
    - name: Regenerate clean Gradle Wrapper
      run: |
        echo "=== Regenerating gradlew wrapper ==="
        gradle wrapper --gradle-version 8.0 --distribution-type bin
        chmod +x gradlew

    - name: Build with Gradle
      run: ./gradlew assembleDebug

    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: app-debug
        path: "**/build/outputs/apk/debug/*.apk"
