@echo off
REM ============================================================================
REM  DevHIS Playwright flows - COMBINED runner (no Maven needed - Java only)
REM
REM  Replaces run-op-reg-queue.bat + run-ip-admission.bat + run-emergency-reg-list-visits.bat
REM  by running all of their flows in ONE pass, into ONE report folder:
REM
REM    OP        com.kpj.tests.Op_Page.RegistrationTest                       (Registration)
REM              com.kpj.tests.Op_Page.OutPatientQueueManagementTest          (Queue Management)
REM    IP        com.kpj.tests.Ip.Admission                                   (Admission)
REM    Emergency com.kpj.tests.Emergency_page.EmergencyRegistrationConscious      (Registration - Conscious)
REM              com.kpj.tests.Emergency_page.Emergency_Registration_Unconscious  (Registration - Unconscious)
REM              com.kpj.tests.Emergency_page.EmergencyListView                   (Emergency List View)
REM              com.kpj.tests.Emergency_page.EmergencyVisits                     (Emergency Visits)
REM    SysConfig com.kpj.tests.SystemConfiguration_page.User                      (System Configuration - User)
REM              com.kpj.tests.SystemConfiguration_page.UserRole                  (System Configuration - User Role)
REM              com.kpj.tests.SystemConfiguration_page.Alerts                    (System Configuration - Alerts)
REM
REM  NB Emergency_Admission is deliberately NOT included - add
REM     "-c com.kpj.tests.Emergency_page.Emergency_Admission" to FLOWS if you want it.
REM
REM  Keep this next to mcp-kpj-1.0.0-shaded.jar.
REM
REM  Usage:
REM    run-op-ip-emergency.bat              Run all 10 flows
REM    set OUTDIR=my-folder & run-op-ip-emergency.bat    Run into a different report folder
REM
REM  Reports:  %OUTDIR%\reports\<Page>.html   +   %OUTDIR%\FAILED_STEPS.html
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM ============================================================================
setlocal
cd /d "%~dp0"
set "JARNAME=mcp-kpj-1.0.0-shaded.jar"
set "MODULE=OP + IP + Emergency + System Configuration (core flows)"

REM Specific classes (not whole packages) - one -c per flow. Add/remove flows here.
set "FLOWS=-c com.kpj.tests.Op_Page.RegistrationTest"
set "FLOWS=%FLOWS% -c com.kpj.tests.Op_Page.OutPatientQueueManagementTest"
set "FLOWS=%FLOWS% -c com.kpj.tests.Ip.Admission"
set "FLOWS=%FLOWS% -c com.kpj.tests.Emergency_page.EmergencyRegistrationConscious"
set "FLOWS=%FLOWS% -c com.kpj.tests.Emergency_page.Emergency_Registration_Unconscious"
set "FLOWS=%FLOWS% -c com.kpj.tests.Emergency_page.EmergencyListView"
set "FLOWS=%FLOWS% -c com.kpj.tests.Emergency_page.EmergencyVisits"
set "FLOWS=%FLOWS% -c com.kpj.tests.SystemConfiguration_page.User"
set "FLOWS=%FLOWS% -c com.kpj.tests.SystemConfiguration_page.UserRole"
set "FLOWS=%FLOWS% -c com.kpj.tests.SystemConfiguration_page.Alerts"

if not defined OUTDIR set "OUTDIR=test-output-op-ip-emergency"

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java not found. Install JDK 21 and make sure "java -version" works, then retry.
  pause
  exit /b 1
)

set "JAR="
if exist "%JARNAME%" set "JAR=%JARNAME%"
if not defined JAR if exist "target\%JARNAME%" set "JAR=target\%JARNAME%"
if not defined JAR if exist "devhis-tests-share\%JARNAME%" set "JAR=devhis-tests-share\%JARNAME%"
if not defined JAR (
  echo [ERROR] %JARNAME% not found here, in target\, or in devhis-tests-share\.
  pause
  exit /b 1
)
echo Module       : %MODULE%
echo Flows        : OP Registration, OP Queue Management, IP Admission,
echo                Emergency Registration (Conscious + Unconscious), Emergency List View, Emergency Visits,
echo                System Configuration - User, User Role, Alerts
echo Using jar    : %JAR%
echo Output folder: %OUTDIR%

if not exist "%OUTDIR%" mkdir "%OUTDIR%"
if exist "%OUTDIR%\aggregate-exit.txt" del /q "%OUTDIR%\aggregate-exit.txt"
if exist "%OUTDIR%\reports" del /q "%OUTDIR%\reports\*.tsv" "%OUTDIR%\reports\*.html" >nul 2>nul

echo [1/3] Ensuring Playwright Chromium is installed (first run downloads it) ...
java -cp "%JAR%" com.microsoft.playwright.CLI install chromium

type nul > "%OUTDIR%\.runmarker"

echo.
echo [2/3] Running %MODULE% flows ...
java %DEVHIS_OPTS% -Ddevhis.outdir=%OUTDIR% -jar "%JAR%" %FLOWS%

echo.
echo [3/3] Building the failed-steps report ...
java -Ddevhis.outdir=%OUTDIR% -cp "%JAR%" com.kpj.core.ReportAggregator "%OUTDIR%\.runmarker"

set "FAILEXIT=1"
if exist "%OUTDIR%\aggregate-exit.txt" set /p FAILEXIT=<"%OUTDIR%\aggregate-exit.txt"

echo.
echo ============================================================
echo   Module           : %MODULE%
echo   Per-flow reports : %CD%\%OUTDIR%\reports\
echo   FAILED steps     : %CD%\%OUTDIR%\FAILED_STEPS.html
if "%FAILEXIT%"=="0" (
  echo   RESULT           : ALL STEPS PASSED
) else (
  echo   RESULT           : SOME STEPS FAILED  ^(see FAILED_STEPS.html^)
)
echo ============================================================
pause
endlocal ^& exit /b %FAILEXIT%
