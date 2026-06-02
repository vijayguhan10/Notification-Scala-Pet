@echo off
setlocal enableextensions enabledelayedexpansion

rem Resolve repo root as the parent directory of this script's folder (infra -> repo root)
for %%I in ("%~dp0..") do set "REPO_ROOT=%%~fI"

set "CMD=%~1"
if "%CMD%"=="" goto :usage

shift /1
set "EXTRA_ARGS="
:collect_extra
if "%~1"=="" goto :dispatch
set "EXTRA_ARGS=!EXTRA_ARGS! %1"
shift /1
goto :collect_extra

:dispatch
if /I "%CMD%"=="up" goto :up
if /I "%CMD%"=="down" goto :down
goto :usage

:run_compose
setlocal enabledelayedexpansion
set "SERVICE_DIR=%~1"
shift /1

set "COMPOSE_ARGS="
:collect_args
if "%~1"=="" goto :have_args
set "COMPOSE_ARGS=!COMPOSE_ARGS! %1"
shift /1
goto :collect_args

:have_args
pushd "%REPO_ROOT%\infra\%SERVICE_DIR%" >nul
docker compose !COMPOSE_ARGS!
set "EC=%ERRORLEVEL%"
popd >nul
endlocal & exit /b %EC%

:up
rem Start infra services
call :run_compose kafka up -d !EXTRA_ARGS!
if errorlevel 1 exit /b %ERRORLEVEL%
call :run_compose rabbitmq up -d !EXTRA_ARGS!
if errorlevel 1 exit /b %ERRORLEVEL%
call :run_compose redis up -d !EXTRA_ARGS!
if errorlevel 1 exit /b %ERRORLEVEL%
call :run_compose postgres up -d !EXTRA_ARGS!
exit /b %ERRORLEVEL%

:down
rem Stop infra services (reverse order is a bit nicer)
call :run_compose postgres down !EXTRA_ARGS!
call :run_compose redis down !EXTRA_ARGS!
call :run_compose rabbitmq down !EXTRA_ARGS!
call :run_compose kafka down !EXTRA_ARGS!
exit /b 0

:usage
echo Usage:
echo   compose-all.bat up
echo   compose-all.bat down
echo.
echo Notes:
echo   - Runs docker compose in: infra\kafka, infra\rabbitmq, infra\redis, infra\postgres
echo   - Bind-mount data stays on disk under infra\*\data when using .\data mounts
exit /b 2