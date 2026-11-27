@echo off
rem Runs ONLY the Billing - Discount Manager/CR - List test (READ-ONLY - write calls are aborted in the browser; Save / Approve / Print are never clicked).
rem   Console log -> test-output\billing-discountmanagerlist-run.log
rem   Report      -> test-output\reports\Billing_DiscountManagerList.html
rem Needs DEVHIS_USER and DEVHIS_PASS set first:   set DEVHIS_USER=yourLogin   /   set DEVHIS_PASS=yourPassword
rem DEVHIS_URL defaults to hisdemo. The test data (MRN 1000338059 / 1100338591) was verified on devhis; scenarios whose data
rem is missing on the chosen environment are reported SKIPPED (TEST DATA NOT AVAILABLE), not failed.
cd /d "%~dp0"
if not exist test-output mkdir test-output
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% > test-output\billing-discountmanagerlist-run.log
call mvn test "-Dtest=com.kpj.tests.Billing_page.DiscountManagerList" -Ddevhis.postrun=off >> test-output\billing-discountmanagerlist-run.log 2>&1
set "RC=%ERRORLEVEL%"
echo EXIT=%RC% END %DATE% %TIME% >> test-output\billing-discountmanagerlist-run.log
echo Report: %CD%\test-output\reports\Billing_DiscountManagerList.html
echo Log   : %CD%\test-output\billing-discountmanagerlist-run.log
rem A non-zero exit is expected while DEFECT-01 / DEFECT-02 (and the other reported defects) are still open.
exit /b %RC%
:nocreds
echo DEVHIS_USER and DEVHIS_PASS must be set before running this test. Nothing was run.
exit /b 1
