@echo off
rem Runs ONLY the Billing - Cancellation - Bill test (READ-ONLY - write calls are aborted in the browser; Save is never clicked and no bill is cancelled).
rem   Console log -> test-output\billing-billcancellation-run.log
rem   Report      -> test-output\reports\Billing_BillCancellation.html
rem Needs DEVHIS_USER and DEVHIS_PASS set first:   set DEVHIS_USER=yourLogin   /   set DEVHIS_PASS=yourPassword
rem DEVHIS_URL defaults to devhis, where the test data (patients / bills) was verified.
cd /d "%~dp0"
if not exist test-output mkdir test-output
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://devhis.sancyberhad.com"
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% > test-output\billing-billcancellation-run.log
call mvn test "-Dtest=com.kpj.tests.Billing_page.Cancellation_page.BillCancellation" -Ddevhis.postrun=off >> test-output\billing-billcancellation-run.log 2>&1
set "RC=%ERRORLEVEL%"
echo EXIT=%RC% END %DATE% %TIME% >> test-output\billing-billcancellation-run.log
echo Report: %CD%\test-output\reports\Billing_BillCancellation.html
echo Log   : %CD%\test-output\billing-billcancellation-run.log
exit /b %RC%
:nocreds
echo DEVHIS_USER and DEVHIS_PASS must be set before running this test. Nothing was run.
exit /b 1
