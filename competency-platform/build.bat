@echo off
REM Builds the project and runs the tests. Output is saved to build-log.txt in this folder.
REM Needs JDK 17 or newer and internet (first run downloads Maven and the libraries, 5 to 10 minutes).
cd /d "%~dp0"
echo Building... this window closes by itself. Output is in build-log.txt
echo ==== java ==== > build-log.txt
java -version >> build-log.txt 2>&1
echo JAVA_HOME=%JAVA_HOME% >> build-log.txt
echo ==== build ==== >> build-log.txt
call mvnw.cmd -B -ntp clean package >> build-log.txt 2>&1
echo ==== exit code %ERRORLEVEL% ==== >> build-log.txt
