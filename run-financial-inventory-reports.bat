@echo off
rem Report Console - Financial Reports + Inventory Reports (READ-ONLY; write API calls are aborted in the browser).
rem   Reports : test-output\reports\Financial_Reports.html, test-output\reports\Inventory_Reports.html
rem   Logs    : test-output\reports-compile.log, test-output\financial-reports-run.log, test-output\inventory-reports-run.log
rem Needs DEVHIS_USER / DEVHIS_PASS (DEVHIS_URL defaults to https://hisdemo.sancyberhad.com).
rem FULL RUN: every report the screen lists is tested. Any REPORTS_ARGS left in the shell (e.g. -Dreports.limit=3) is ignored.
rem For a subset run mvn directly, e.g.  mvn test -Dtest=...FinancialReportsTest -Dreports.only="Bill Status"
cd /d "%~dp0"
set "REPORTS_ARGS="
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://hisdemo.sancyberhad.com"
if not exist test-output mkdir test-output

echo COMPILE %DATE% %TIME% > test-output\reports-compile.log
call mvn -q test-compile >> test-output\reports-compile.log 2>&1
set "CRC=%ERRORLEVEL%"
echo COMPILE_EXIT=%CRC% >> test-output\reports-compile.log
if not "%CRC%"=="0" (
  echo COMPILE FAILED - see test-output\reports-compile.log. Nothing was run.
  exit /b %CRC%
)

rem Prove the classes Maven will run were compiled from the current sources (stale-build guard).
powershell -NoProfile -Command ^
  "$ok=$true; foreach($c in 'tests\Report_page\Report_Console\FinancialReportsTest','tests\Report_page\Report_Console\InventoryReportsTest','tests\Report_page\Report_Console\ReportConsoleSuite','pages\Report_page\ReportConsolePage'){ $s=Get-Item ('src\test\java\com\kpj\'+$c+'.java'); $k=Get-Item ('target\test-classes\com\kpj\'+$c+'.class') -ErrorAction SilentlyContinue; if(-not $k -or $k.LastWriteTime -lt $s.LastWriteTime){$ok=$false; Write-Output ('STALE: '+$c)} else { Write-Output ($c+'  source '+$s.LastWriteTime+'  class '+$k.LastWriteTime) } }; if(-not $ok){exit 1}" >> test-output\reports-compile.log 2>&1
if errorlevel 1 (
  echo STALE BUILD - a .class is older than its .java. See test-output\reports-compile.log. Nothing was run.
  exit /b 1
)
type test-output\reports-compile.log

echo START %DATE% %TIME% > test-output\financial-reports-run.log
call mvn test "-Dtest=com.kpj.tests.Report_page.Report_Console.FinancialReportsTest" -Ddevhis.postrun=off -Ddevhis.testTimeoutMin=120 >> test-output\financial-reports-run.log 2>&1
echo EXIT=%ERRORLEVEL% END %DATE% %TIME% >> test-output\financial-reports-run.log

echo START %DATE% %TIME% > test-output\inventory-reports-run.log
call mvn test "-Dtest=com.kpj.tests.Report_page.Report_Console.InventoryReportsTest" -Ddevhis.postrun=off -Ddevhis.testTimeoutMin=120 >> test-output\inventory-reports-run.log 2>&1
echo EXIT=%ERRORLEVEL% END %DATE% %TIME% >> test-output\inventory-reports-run.log

echo Reports: %CD%\test-output\reports\Financial_Reports.html
echo          %CD%\test-output\reports\Inventory_Reports.html
exit /b 0
:nocreds
echo DEVHIS_USER and DEVHIS_PASS must be set before running these tests. Nothing was run.
exit /b 1
