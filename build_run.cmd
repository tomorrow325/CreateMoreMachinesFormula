@echo off
REM more_formula build entry point. Real logic lives in tools\build.js.
REM This is the offline fallback for when Gradle cannot reach the plugin/maven repos.
cd /d "%~dp0"

REM Point JAVA_HOME at a JDK 21 install. Adjust if yours is elsewhere.
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"

set "JAVA_TOOL_OPTIONS=-Duser.language=en -Duser.country=US"
node "tools\build.js"
exit /b %ERRORLEVEL%
