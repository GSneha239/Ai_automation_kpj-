@echo off
rem Runs ONLY the Telemedicine - EMR test (READ-ONLY - nothing is saved, sent, created or deleted).
rem   Console log -> test-output\telemedicine-emr-run.log
rem   Report      -> test-output\reports\Telemedicine_EMR.html
rem
rem Needs DEVHIS_USER and DEVHIS_PASS set in the environment first, e.g.:
rem     set DEVHIS_USER=yourLogin
rem     set DEVHIS_PASS=yourPassword
rem The test refuses to log in without them (it reports every case BLOCKED instead).
rem Test data defaults to the devhis patient 1100339387 / Ahmad bin Ali / OP-00001089 / EMRCASE01923;
rem on another environment pass -Demr.mrn=... -Demr.patient=... -Demr.visit=... -Demr.case=... via EMR_ARGS.
rem Post-run self-healing analysis is switched off: FAIL rows here are application defects, not script breakages.
cd /d "%~dp0"
if not exist test-output mkdir test-output
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% > test-output\telemedicine-emr-run.log
call mvn test "-Dtest=com.kpj.tests.Telemedicine_page.TelemedicineEMR" -Ddevhis.postrun=off %EMR_ARGS% >> test-output\telemedicine-emr-run.log 2>&1
set "RC=%ERRORLEVEL%"
echo EXIT=%RC% END %DATE% %TIME% >> test-output\telemedicine-emr-run.log
echo Report: %CD%\test-output\reports\Telemedicine_EMR.html
echo Log   : %CD%\test-output\telemedicine-emr-run.log
exit /b %RC%
:nocreds
echo DEVHIS_USER and DEVHIS_PASS must be set before running this test. Nothing was run.
exit /b 1
