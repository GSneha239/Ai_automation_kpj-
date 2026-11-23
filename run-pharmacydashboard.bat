@echo off
rem Runs ONLY the Pharmacy Dashboard test (READ-ONLY - nothing is added, edited or saved).
rem   Console log -> test-output\pharmacydashboard-run.log
rem   Report      -> test-output\reports\Pharmacy_Dashboard.html
rem
rem Needs DEVHIS_USER and DEVHIS_PASS set in the environment first, e.g.:
rem     set DEVHIS_USER=yourLogin
rem     set DEVHIS_PASS=yourPassword
rem The test refuses to log in without them (no built-in or fallback accounts are used).
rem Post-run self-healing analysis is switched off: FAIL rows here are application defects, not script breakages.
cd /d "%~dp0"
if not exist test-output mkdir test-output
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% > test-output\pharmacydashboard-run.log
call mvn test "-Dtest=com.kpj.tests.Pharmacy_page.PharmacyDashboardTest" -Ddevhis.postrun=off >> test-output\pharmacydashboard-run.log 2>&1
set "RC=%ERRORLEVEL%"
echo EXIT=%RC% END %DATE% %TIME% >> test-output\pharmacydashboard-run.log
echo Report: %CD%\test-output\reports\Pharmacy_Dashboard.html
echo Log   : %CD%\test-output\pharmacydashboard-run.log
exit /b %RC%
:nocreds
echo DEVHIS_USER and DEVHIS_PASS must be set before running this test. Nothing was run.
exit /b 1
