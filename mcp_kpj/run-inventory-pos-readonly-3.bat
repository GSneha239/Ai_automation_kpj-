@echo off
rem Compiles once, then runs Payment Settlement - Payor, CDR Dispense Process and Inventory Dashboard ONE AFTER ANOTHER,
rem each in its own JVM and browser (independent). Logs: test-output\<name>-run.log
rem Reports: test-output\reports\POS_PaymentSettlement_Payor.html, POS_CDRDispenseProcess.html, Inventory_Dashboard.html
cd /d "%~dp0"
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
if not exist test-output mkdir test-output
echo COMPILE %DATE% %TIME% > test-output\inventory-pos-compile.log
call mvn -q test-compile >> test-output\inventory-pos-compile.log 2>&1
echo COMPILE_EXIT=%ERRORLEVEL% >> test-output\inventory-pos-compile.log
call "%~dp0run-paymentsettlementpayor.bat"
call "%~dp0run-cdrdispenseprocess.bat"
call "%~dp0run-inventorydashboard.bat"
rem File check: git status if this folder is a git repo, otherwise a dated listing of the source tree.
git rev-parse --is-inside-work-tree >nul 2>&1 && (git status --porcelain > test-output\inventory-pos-git-status.txt 2>&1) || (dir /s /t:w /o:d src\test\java\com\kpj\pages\Inventory_page src\test\java\com\kpj\tests\Inventory_page > test-output\inventory-pos-git-status.txt 2>&1)
echo Done. Reports in %CD%\test-output\reports
exit /b 0
:nocreds
echo DEVHIS_USER and DEVHIS_PASS must be set before running these tests. Nothing was run.
exit /b 1
