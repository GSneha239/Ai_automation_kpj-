@echo off
rem Runs ONLY the Inventory - POS - CDR Dispense Process test (READ-ONLY - write calls are aborted in the browser; nothing is saved, settled, dispensed or deleted).
rem   Console log -> test-output\cdrdispenseprocess-run.log
rem   Report      -> test-output\reports\POS_CDRDispenseProcess.html
rem Needs DEVHIS_USER and DEVHIS_PASS set first:   set DEVHIS_USER=yourLogin   /   set DEVHIS_PASS=yourPassword
cd /d "%~dp0"
if not exist test-output mkdir test-output
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% > test-output\cdrdispenseprocess-run.log
call mvn test "-Dtest=com.kpj.tests.Inventory_page.POS_page.CDRDispenseProcess" -Ddevhis.postrun=off >> test-output\cdrdispenseprocess-run.log 2>&1
set "RC=%ERRORLEVEL%"
echo EXIT=%RC% END %DATE% %TIME% >> test-output\cdrdispenseprocess-run.log
echo Report: %CD%\test-output\reports\POS_CDRDispenseProcess.html
echo Log   : %CD%\test-output\cdrdispenseprocess-run.log
exit /b %RC%
:nocreds
echo DEVHIS_USER and DEVHIS_PASS must be set before running this test. Nothing was run.
exit /b 1
