@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
title Smart Campus Request Router - Start

echo ===============================================================
echo   Smart Campus Request Router  -  arranque automatico
echo ===============================================================
echo.

REM --- Moverse a la carpeta del script (raiz del proyecto) ---
cd /d "%~dp0"

set "TMP_DIR=%TEMP%\campus-setup"
if not exist "%TMP_DIR%" mkdir "%TMP_DIR%"

REM --- 1) Verificar (e instalar si falta) las herramientas necesarias ---
echo [1/6] Verificando herramientas requeridas...
call :need docker  "Docker.DockerDesktop"        || goto :fail
call :need java    "EclipseAdoptium.Temurin.17.JDK" || goto :fail
call :need mvn     "Apache.Maven"                 || goto :fail
echo.

REM --- 2) Asegurar que el motor de Docker este corriendo ---
echo [2/6] Verificando el motor de Docker...
docker info >nul 2>&1
if errorlevel 1 (
    echo     Docker no responde. Intentando iniciar Docker Desktop...
    start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" >nul 2>&1
    set /a _try=0
    :waitdocker
    set /a _try+=1
    docker info >nul 2>&1
    if not errorlevel 1 goto :dockerok
    if !_try! GEQ 60 ( echo     [X] Docker no inicio a tiempo. Abrelo manualmente y reintenta. & goto :fail )
    <nul set /p "=."
    timeout /t 3 /nobreak >nul
    goto :waitdocker
)
:dockerok
echo     Docker operativo.
echo.

REM --- 3) Levantar RabbitMQ ---
echo [3/6] Levantando RabbitMQ (docker compose up -d)...
docker compose up -d || goto :fail
echo     Esperando a que la API de administracion responda...
set /a _try=0
:waitmq
set /a _try+=1
curl -s -u guest:guest http://localhost:15672/api/overview >nul 2>&1
if not errorlevel 1 goto :mqok
if !_try! GEQ 60 ( echo     [X] RabbitMQ no respondio a tiempo. & goto :fail )
<nul set /p "=."
timeout /t 2 /nobreak >nul
goto :waitmq
:mqok
echo  listo.
echo.

REM --- 4) Crear exchange, colas y bindings ---
echo [4/6] Configurando exchange, colas y bindings...
>"%TMP_DIR%\exchange.json" echo {"type":"direct","durable":true}
>"%TMP_DIR%\durable.json"  echo {"durable":true}

curl -s -u guest:guest -H "content-type:application/json" -X PUT ^
  "http://localhost:15672/api/exchanges/%%2F/campus.exchange" ^
  -d @"%TMP_DIR%\exchange.json" >nul
echo     - exchange: campus.exchange

for %%Q in (campus.requests.in campus.admissions.queue campus.payments.queue campus.support.queue campus.academic.queue campus.manual-review.queue) do (
    curl -s -u guest:guest -H "content-type:application/json" -X PUT ^
      "http://localhost:15672/api/queues/%%2F/%%Q" -d @"%TMP_DIR%\durable.json" >nul
    >"%TMP_DIR%\bind.json" echo {"routing_key":"%%Q"}
    curl -s -u guest:guest -H "content-type:application/json" -X POST ^
      "http://localhost:15672/api/bindings/%%2F/e/campus.exchange/q/%%Q" -d @"%TMP_DIR%\bind.json" >nul
    echo     - cola + binding: %%Q
)
echo.

REM --- 5) Compilar el proyecto (descarga dependencias) ---
echo [5/6] Compilando el proyecto (mvn clean package)...
call mvn -q clean package -DskipTests || goto :fail
echo     Build correcto: target\smart-campus-request-router.jar
echo.

REM --- 6) Ejecutar la aplicacion ---
echo [6/6] Iniciando la aplicacion Camel...
echo ---------------------------------------------------------------
echo  RabbitMQ UI : http://localhost:15672   (guest / guest)
echo  Para publicar mensajes de prueba, en OTRA terminal ejecuta:
echo      powershell -ExecutionPolicy Bypass -File scripts\publish-messages.ps1
echo  Detener: Ctrl + C   ^|   Apagar RabbitMQ: docker compose down
echo ---------------------------------------------------------------
echo.
java -jar target\smart-campus-request-router.jar

goto :eof

REM ===================== Subrutinas =====================
:need
REM  %1 = comando a buscar   %2 = id de winget para instalarlo
where %1 >nul 2>&1
if %errorlevel%==0 ( echo     [OK] %1 & exit /b 0 )
echo     [..] %1 no encontrado, intentando instalar con winget...
where winget >nul 2>&1
if errorlevel 1 ( echo     [X] winget no disponible. Instala %1 manualmente y reintenta. & exit /b 1 )
winget install -e --id %2 --accept-source-agreements --accept-package-agreements
where %1 >nul 2>&1
if errorlevel 1 ( echo     [X] %1 sigue sin estar disponible. Cierra y abre una nueva terminal tras instalarlo. & exit /b 1 )
echo     [OK] %1 instalado
exit /b 0

:fail
echo.
echo *** El arranque se detuvo por un error. Revisa el mensaje anterior. ***
pause
exit /b 1
