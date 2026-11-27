@echo off
rem Runs ONLY the Ancillary Services - MRD - ICD Entry test (READ-ONLY - write calls are aborted in the browser;
rem no ICD / procedure is picked or saved; Save is clicked only with every row unticked, to prove its validation).
rem   Console log -> test-output\mrd-icdentry-run.log
rem   Report      -> test-output\reports\AncillaryServices_MRD_ICDEntry.html   (descriptive scenario names, no step numbers)
rem Needs DEVHIS_USER and DEVHIS_PASS set first:   set DEVHIS_USER=yourLogin   /   set DEVHIS_PASS=yourPassword
rem DEVHIS_URL defaults to hisdemo. The test data (MRN 1100338591 / 1000338059) was verified on devhis; scenarios whose data
rem is missing on the chosen environment are reported SKIPPED (TEST DATA NOT AVAILABLE), not failed.
cd /d "%~dp0"
if not exist test-output mkdir test-output
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% > test-output\mrd-icdentry-run.log
call mvn test "-Dtest=com.kpj.tests.AncillaryServices_page.MRD_page.ICDEntry" -Ddevhis.postrun=off -Ddevhis.report.stepNumbers=off >> test-output\mrd-icdentry-run.log 2>&1
set "RC=%ERRORLEVEL%"
echo EXIT=%RC% END %DATE% %TIME% >> test-output\mrd-icdentry-run.log
echo Report: %CD%\test-output\reports\AncillaryServices_MRD_ICDEntry.html
echo Log   : %CD%\test-output\mrd-icdentry-run.log
rem A non-zero exit means at least one scenario FAILED (see the report for the reason).
exit /b %RC%
:nocreds
echo DEVHIS_USER and DEVHIS_PASS must be set before running this test. Nothing was run.
exit /b 1
