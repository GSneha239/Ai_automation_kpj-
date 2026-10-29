@echo off
rem Runs ONLY the Charges Details test; output -> test-output\chargesdetails-run.log
cd /d "%~dp0"
if not exist test-output mkdir test-output
rem Target environment: HIS Demo unless DEVHIS_URL is already set (URL only - credentials come from DEVHIS_USER/DEVHIS_PASS or the project defaults)
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% > test-output\chargesdetails-run.log
call mvn test "-Dtest=com.kpj.tests.AncillaryServices_page.Linen_page.ChargesDetails" >> test-output\chargesdetails-run.log 2>&1
echo EXIT=%ERRORLEVEL% END %DATE% %TIME% >> test-output\chargesdetails-run.log
