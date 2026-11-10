@echo off
rem Runs ONLY the Inventory - Transfer - Return Item test (READ-ONLY - write APIs are blocked; nothing is created, saved, approved, exported or deleted).
rem   Console log -> test-output\returnitem-run.log
rem   Report      -> test-output\reports\Inventory_Transfer_ReturnItem.html
rem
rem Needs DEVHIS_USER and DEVHIS_PASS set in the environment first, e.g.:
rem     set DEVHIS_USER=yourLogin
rem     set DEVHIS_PASS=yourPassword
rem Post-run self-healing analysis is switched off: FAIL rows here are application findings, not script breakages.
cd /d "%~dp0"
if not exist test-output mkdir test-output
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% > test-output\returnitem-run.log
call mvn test "-Dtest=com.kpj.tests.Inventory_page.Transfer_page.ReturnItem" -Ddevhis.postrun=off >> test-output\returnitem-run.log 2>&1
set "RC=%ERRORLEVEL%"
echo EXIT=%RC% END %DATE% %TIME% >> test-output\returnitem-run.log
echo Report: %CD%\test-output\reports\Inventory_Transfer_ReturnItem.html
echo Log   : %CD%\test-output\returnitem-run.log
exit /b %RC%
:nocreds
echo DEVHIS_USER and DEVHIS_PASS must be set before running this test. Nothing was run.
exit /b 1
