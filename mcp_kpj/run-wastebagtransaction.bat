@echo off
rem Runs ONLY the Waste Bag Transaction test; output -> test-output\wastebagtransaction-run.log
rem Report  -> test-output\reports\AncillaryServices_BiomedicalWaste_WasteBagTransaction.html
rem
rem Needs DEVHIS_USER and DEVHIS_PASS set in the environment first, e.g.:
rem     set DEVHIS_USER=yourLogin
rem     set DEVHIS_PASS=yourPassword
rem Read-only by default. To let T12/T13 SAVE a real Waste Bag transaction (approved runs only):
rem     run-wastebagtransaction.bat save
cd /d "%~dp0"
if not exist test-output mkdir test-output
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
set "SAVEFLAG="
if /i "%~1"=="save" set "SAVEFLAG=-Ddevhis.allowSave=true"
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% SAVE=%SAVEFLAG% > test-output\wastebagtransaction-run.log
call mvn test "-Dtest=com.kpj.tests.AncillaryServices_page.BiomedicalWaste_page.WasteBagTransaction" %SAVEFLAG% >> test-output\wastebagtransaction-run.log 2>&1
echo EXIT=%ERRORLEVEL% END %DATE% %TIME% >> test-output\wastebagtransaction-run.log
