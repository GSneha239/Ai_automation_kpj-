@echo off
rem Runs ONLY the Inventory - Receive Issue Item test (READ-ONLY: write APIs are aborted before they reach the server;
rem nothing is received, saved, approved, issued or deleted. Save is clicked only on a form verified to be incomplete).
rem   Console log  -> test-output\receiveissueitem-run.log
rem   Case log     -> test-output\logs\Inventory_ReceiveIssueItem.log
rem   Report       -> test-output\reports\Inventory_ReceiveIssueItem.html
rem   Summary      -> test-output\reports\Inventory_ReceiveIssueItem_summary.txt
rem   FAIL/BLOCKED -> test-output\screenshots\ReceiveIssueItem\
rem
rem Needs DEVHIS_USER and DEVHIS_PASS (e.g. once:  setx DEVHIS_USER yourLogin   and   setx DEVHIS_PASS yourPassword).
rem Values saved with setx are picked up here even before the console is restarted. No built-in account is ever used.
rem Runs independently of the other Inventory pages; post-run self-healing is off (FAIL rows are application findings).
cd /d "%~dp0"
if not exist test-output mkdir test-output
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
if "%DEVHIS_USER%"=="" for /f "skip=2 tokens=2,*" %%a in ('reg query HKCU\Environment /v DEVHIS_USER 2^>nul') do set "DEVHIS_USER=%%b"
if "%DEVHIS_PASS%"=="" for /f "skip=2 tokens=2,*" %%a in ('reg query HKCU\Environment /v DEVHIS_PASS 2^>nul') do set "DEVHIS_PASS=%%b"
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% > test-output\receiveissueitem-run.log
call mvn test "-Dtest=com.kpj.tests.Inventory_page.Transfer_page.ReceiveIssueItem" -Ddevhis.postrun=off >> test-output\receiveissueitem-run.log 2>&1
set "RC=%ERRORLEVEL%"
echo EXIT=%RC% END %DATE% %TIME% >> test-output\receiveissueitem-run.log
echo Report : %CD%\test-output\reports\Inventory_ReceiveIssueItem.html
echo Summary: %CD%\test-output\reports\Inventory_ReceiveIssueItem_summary.txt
echo Log    : %CD%\test-output\logs\Inventory_ReceiveIssueItem.log
exit /b %RC%
:nocreds
echo NOCREDS %DATE% %TIME% - DEVHIS_USER / DEVHIS_PASS not set, nothing was run > test-output\receiveissueitem-run.log
echo DEVHIS_USER and DEVHIS_PASS must be set before running this test. Nothing was run.
pause
exit /b 1
