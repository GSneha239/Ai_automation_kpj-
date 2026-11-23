@echo off
rem Runs ONLY the Linen Machine test; output -> test-output\linenmachine-run.log
rem Report  -> test-output\reports\AncillaryServices_Linen_LinenMachine.html
rem
rem Needs DEVHIS_USER and DEVHIS_PASS set in the environment first, e.g.:
rem     set DEVHIS_USER=yourLogin
rem     set DEVHIS_PASS=yourPassword
rem Read-only by default. To let T3/T4 ADD a Linen Machine on the environment:
rem     run-linenmachine.bat save
cd /d "%~dp0"
if not exist test-output mkdir test-output
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
set "SAVEFLAG="
if /i "%~1"=="save" set "SAVEFLAG=-Ddevhis.allowSave=true"
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% SAVE=%SAVEFLAG% > test-output\linenmachine-run.log
call mvn test "-Dtest=com.kpj.tests.AncillaryServices_page.Linen_page.LinenMachine" %SAVEFLAG% >> test-output\linenmachine-run.log 2>&1
echo EXIT=%ERRORLEVEL% END %DATE% %TIME% >> test-output\linenmachine-run.log
