@echo off
setlocal
REM ============================================================
REM  Maha Metro CoE - Competency Assessment : local DEMO (Windows)
REM  Uses a local SQLite file - no PostgreSQL needed for the demo.
REM  Works with Python 3.11, 3.12, 3.13 or 3.14.
REM ============================================================
cd /d "%~dp0"
title Maha Metro Competency Demo
if not exist "app\main.py" goto notextracted
if not exist "requirements-demo.txt" goto notextracted

REM ---- 1. find a supported Python (3.12, 3.13, 3.14, 3.11) ----
set "PYEXE="
call :findpy
if defined PYEXE goto havepy
echo.
echo No supported Python (3.11 to 3.14) was found on this PC. Trying to install it now...
where py >nul 2>nul && py install 3.12
call :findpy
if not defined PYEXE goto nopython
:havepy
echo Using Python: %PYEXE%

REM ---- 2. create the environment once; rebuild it if a previous run did not finish ----
if exist ".venv\demo_ready.txt" goto startdemo
if exist ".venv" (
  echo Removing an incomplete setup from an earlier run...
  rmdir /s /q ".venv"
)
echo.
echo Setting up - first run only, 3 to 5 minutes, needs internet...
%PYEXE% -m venv .venv
if errorlevel 1 goto failvenv
".venv\Scripts\python.exe" -m pip install --upgrade pip
".venv\Scripts\python.exe" -m pip install -r requirements-demo.txt
if errorlevel 1 goto failpip
".venv\Scripts\python.exe" -c "import fastapi, sqlalchemy, uvicorn, jinja2, openpyxl, bcrypt, jwt, multipart, itsdangerous"
if errorlevel 1 goto failpip
echo ok> ".venv\demo_ready.txt"

:startdemo
set "DATABASE_URL=sqlite:///./demo.db"
set "DEMO_MODE=true"
set "AUTH_MODES=local"
set "MEDIA_DIR=%~dp0data\media"

rem Demo data from an older version is rebuilt so the new question bank is loaded
if exist demo.db if not exist demo_v1.3.txt del demo.db
if exist demo.db goto runserver
echo.
echo Loading framework, question bank and demo staff - about 1 minute...
".venv\Scripts\python.exe" -m seed.seed --demo
if errorlevel 1 goto failseed
echo v1.3> demo_v1.3.txt

:runserver
echo.
echo ==============================================================
echo   Demo running at  http://localhost:8000
echo   Pick any demo person on the sign-in page.
echo   Password for typed sign-in: Demo@2027
echo   Keep this window open. Close it to stop the demo.
echo ==============================================================
start "" cmd /c "timeout /t 6 >nul & start http://localhost:8000"
".venv\Scripts\python.exe" -m uvicorn app.main:app --host 127.0.0.1 --port 8000
goto end

:findpy
for %%V in (3.12 3.13 3.14 3.11) do (
  if not defined PYEXE (
    py -%%V -c "import sys" >nul 2>nul && set "PYEXE=py -%%V"
  )
)
if not defined PYEXE (
  python -c "import sys; sys.exit(0 if (3,11) <= sys.version_info[:2] <= (3,14) else 1)" >nul 2>nul && set "PYEXE=python"
)
exit /b 0

:nopython
echo.
echo  Could not find or install a supported Python (3.11 to 3.14).
echo  Please install it from https://www.python.org/downloads/release/python-31210/
echo  (Windows installer 64-bit, tick "Add python.exe to PATH"), then run this file again.
goto end
:failvenv
echo.
echo  Could not create the Python environment. Send a photo of this window to the CoE team.
goto end
:failpip
echo.
echo  Downloading the required packages failed.
echo  Check the internet connection (office proxy may block pypi.org and files.pythonhosted.org),
echo  then run this file again. It will retry automatically.
goto end
:failseed
if exist demo.db del demo.db
echo.
echo  Loading the demo data failed. Send a photo of this window to the CoE team.
goto end
:notextracted
echo.
echo  This file was started from inside the zip, or the folder is incomplete.
echo  Please extract the zip first: right-click the zip file, choose "Extract All..." (or WinRAR "Extract to ...").
echo  Then open the extracted MahaMetro_Competency_Platform folder and double-click run_demo.bat there.
goto end
:end
echo.
pause
