@echo off
rem Runs ONLY the Ancillary Services - MRD - MRD (file list) test (READ-ONLY - write calls are aborted in the browser;
rem Add / Edit / Scanning Status Update / Scrap / Print are never clicked; pop-ups are only read and closed).
rem   Console log -> test-output\mrd-filelist-run.log
rem   Report      -> test-output\reports\AncillaryServices_MRD_MRD.html   (descriptive scenario names, no numbering)
rem Needs DEVHIS_USER and DEVHIS_PASS set first:   set DEVHIS_USER=yourLogin   /   set DEVHIS_PASS=yourPassword
rem DEVHIS_URL defaults to hisdemo. The test data (MRN 1000338068 / ED000322 / 1000338059) was verified on devhis; scenarios whose
rem data is missing on the chosen environment are reported SKIPPED (TEST DATA NOT AVAILABLE), not failed.
cd /d "%~dp0"
if not exist test-output mkdir test-output
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% > test-output\mrd-filelist-run.log
call mvn test "-Dtest=com.kpj.tests.AncillaryServices_page.MRD_page.MRD" -Ddevhis.postrun=off -Ddevhis.report.stepNumbers=off >> test-output\mrd-filelist-run.log 2>&1
set "RC=%ERRORLEVEL%"
echo EXIT=%RC% END %DATE% %TIME% >> test-output\mrd-filelist-run.log
echo Report: %CD%\test-output\reports\AncillaryServices_MRD_MRD.html
echo Log   : %CD%\test-output\mrd-filelist-run.log
rem A non-zero exit means at least one scenario FAILED - the two known MRD issues are reported as application defects.
exit /b %RC%
:nocreds
echo DEVHIS_USER and DEVHIS_PASS must be set before running this test. Nothing was run.
exit /b 1
