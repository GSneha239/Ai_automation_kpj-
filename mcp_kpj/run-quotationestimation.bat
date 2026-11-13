@echo off
rem Runs ONLY the Inventory - POS - Quotation Estimation test (READ-ONLY - write calls are aborted in the browser; nothing is saved, completed, submitted or deleted).
rem   Console log -> test-output\quotationestimation-run.log
rem   Report      -> test-output\reports\Inventory_POS_QuotationEstimation.html
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
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% > test-output\quotationestimation-run.log
call mvn test "-Dtest=com.kpj.tests.Inventory_page.POS_page.QuotationEstimationTest" -Ddevhis.postrun=off >> test-output\quotationestimation-run.log 2>&1
set "RC=%ERRORLEVEL%"
echo EXIT=%RC% END %DATE% %TIME% >> test-output\quotationestimation-run.log
echo Report: %CD%\test-output\reports\Inventory_POS_QuotationEstimation.html
echo Log   : %CD%\test-output\quotationestimation-run.log
exit /b %RC%
:nocreds
echo DEVHIS_USER and DEVHIS_PASS must be set before running this test. Nothing was run.
exit /b 1
