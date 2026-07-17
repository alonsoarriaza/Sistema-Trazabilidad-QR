@echo off
setlocal EnabledDelayedExpansion

cd /d "%~dp0"

echo =======================================================
echo   Iniciando Aplicacion trazabilidad - Gestion de Inventario
echo =======================================================

:: 1. Detectar o verificar JAVA_HOME
set "TEMP_JAVA_HOME="

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "TEMP_JAVA_HOME=%JAVA_HOME%"
    )
)

if not defined TEMP_JAVA_HOME (
    if exist "D:\CND445\bin\java.exe" (
        set "TEMP_JAVA_HOME=D:\CND445"
    )
)

if not defined TEMP_JAVA_HOME (
    REM Intentar buscar java en el PATH
    for /f "delims=" %%I in ('where java.exe 2^>nul') do (
        set "JAVA_EXE_PATH=%%~dpI"
    )
    if defined JAVA_EXE_PATH (
        for %%A in ("!JAVA_EXE_PATH!\..") do set "TEMP_JAVA_HOME=%%~fA"
    )
)

if defined TEMP_JAVA_HOME (
    set "JAVA_HOME=!TEMP_JAVA_HOME!"
    echo [INFO] Usando JAVA_HOME: !JAVA_HOME!
) else (
    echo [ERROR] No se pudo encontrar una instalacion de Java (JDK/JRE).
    echo Por favor, asegurese de tener Java instalado y configurado.
    pause
    exit /b 1
)

:: 2. Arrancar la aplicacion con Spring Boot en modo offline (-o)
:: Esto evita problemas de red y utiliza las dependencias locales del repositorio .m2.
echo [INFO] Arrancando aplicacion en modo OFFLINE (-o) usando el repositorio local...
echo =======================================================
call .\mvnw.cmd spring-boot:run -o
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ADVERTENCIA] El arranque offline devolvio un codigo de error.
    echo Intentando arranque normal por si faltan dependencias...
    echo =======================================================
    call .\mvnw.cmd spring-boot:run
)

pause

