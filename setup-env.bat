@echo off
REM Sanctuary Development Environment Setup (Windows)
REM This script loads API keys from .env file into the current command prompt session
REM
REM Usage:
REM   setup-env.bat
REM   gradlew :composeApp:installDebug

setlocal enabledelayedexpansion

REM Check if .env file exists
if not exist .env (
    echo X .env file not found!
    echo   Please copy .env.example to .env and fill in your API keys:
    echo   copy .env.example .env
    exit /b 1
)

REM Load .env file line by line
for /f "usebackq delims=" %%a in (.env) do (
    REM Skip comments and empty lines
    if not "%%a"=="" (
        if not "%%a:~0,1%%" == "#" (
            set "%%a"
        )
    )
)

echo.

REM Verify required keys are set
if not defined GROQ_API_KEY if not defined CLAUDE_API_KEY (
    echo X ERROR: No API keys configured!
    echo   Please set GROQ_API_KEY or CLAUDE_API_KEY in .env
    exit /b 1
)

REM Show what's loaded
if defined GROQ_API_KEY (
    set "GROQ_DISPLAY=!GROQ_API_KEY:~0,10!...!GROQ_API_KEY:~-4!"
    echo O GROQ_API_KEY: !GROQ_DISPLAY!
)

if defined CLAUDE_API_KEY (
    set "CLAUDE_DISPLAY=!CLAUDE_API_KEY:~0,10!...!CLAUDE_API_KEY:~-4!"
    echo O CLAUDE_API_KEY: !CLAUDE_DISPLAY!
)

echo O AI_PROVIDER: %AI_PROVIDER%
echo.
echo Environment ready! You can now run:
echo   gradlew :composeApp:installDebug    (Build and install Android)
echo.

endlocal & setlocal
