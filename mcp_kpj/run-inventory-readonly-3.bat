@echo off
rem Compiles once, then runs the three read-only Inventory tests ONE AFTER ANOTHER, each in its own JVM/browser
rem (independent runs). Logs: test-output\<name>-run.log   Reports: test-output\reports\Inventory_*.html
cd /d "%~dp0"
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
if not exist test-output mkdir test-output
echo COMPILE %DATE% %TIME% > test-output\inventory-readonly-compile.log
call mvn -q test-compile >> test-output\inventory-readonly-compile.log 2>&1
echo COMPILE_EXIT=%ERRORLEVEL% >> test-output\inventory-readonly-compile.log
call "%~dp0run-expireditemreturn.bat"
call "%~dp0run-workordercompletion.bat"
call "%~dp0run-quotationestimation.bat"
echo Done. Reports in %CD%\test-output\reports
exit /b 0
:nocreds
echo DEVHIS_USER and DEVHIS_PASS must be set before running these tests. Nothing was run.
exit /b 1
