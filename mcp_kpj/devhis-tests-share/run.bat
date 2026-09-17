@echo off
REM ============================================================================
REM  DevHIS Playwright flows - portable runner (no Maven needed)
REM  Keep this run.bat in the SAME folder as mcp-kpj-1.0.0-shaded.jar
REM ----------------------------------------------------------------------------
REM  Usage:
REM    run.bat                                          Run ALL flows
REM    run.bat Emergency_page.EmergencyListView         Run one flow (package path)
REM    run.bat Op_Page.Appointment.AppointmentListTest  Run one flow (package path)
REM
REM  Reports (OUTDIR defaults to test-output; run-klg.bat overrides to test-output-klg):
REM    %OUTDIR%\reports\<Page>.html     per-flow report (with screenshots)
REM    %OUTDIR%\FAILED_STEPS.html       consolidated FAILED steps + page name
REM
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM ============================================================================
setlocal
cd /d "%~dp0"
set "JARNAME=mcp-kpj-1.0.0-shaded.jar"
if not defined OUTDIR set "OUTDIR=test-output"

REM --- Default credentials for the plain devhis target. The jar's own built-in default (farisha) has been
REM     failing login intermittently, so this run.bat now defaults to the tieba account instead. Still
REM     overridable: set DEVHIS_OPTS yourself (e.g. from run-klg.bat/run-dsh.bat) before calling this script.
REM     Password reset 2026-09-10 (was User@123).
if not defined DEVHIS_OPTS set "DEVHIS_OPTS=-Ddevhis.user=tieba -Ddevhis.pass=Tieba@123"

REM --- Java present? ---
where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java not found. Install JDK 21 and make sure "java -version" works, then retry.
  pause
  exit /b 1
)

REM --- Refresh the local jar from a NEWER Maven build -------------------------
REM  This folder keeps its OWN copy of the jar and the resolution below prefers it, so after a
REM  "mvn -Pshaded -DskipTests package" the local copy is stale and the flows silently run the OLD code
REM  (that is how a fixed login kept failing). xcopy /D copies ONLY when the source is newer.
if exist "%JARNAME%" (
  if exist "..\target\%JARNAME%" xcopy /D /Y /Q "..\target\%JARNAME%" ".\" >nul 2>nul
  if exist "target\%JARNAME%"    xcopy /D /Y /Q "target\%JARNAME%"    ".\" >nul 2>nul
)

REM --- Locate the jar: same folder, then target\, then devhis-tests-share\ ---
set "JAR="
if exist "%JARNAME%" set "JAR=%JARNAME%"
if not defined JAR if exist "target\%JARNAME%" set "JAR=target\%JARNAME%"
if not defined JAR if exist "devhis-tests-share\%JARNAME%" set "JAR=devhis-tests-share\%JARNAME%"
if not defined JAR (
  echo [ERROR] %JARNAME% not found here, in target\, or in devhis-tests-share\.
  echo         Put run.bat next to the jar.
  pause
  exit /b 1
)
echo Using jar    : %JAR%
for %%F in ("%JAR%") do echo Jar built    : %%~tF
REM --- Say WHICH ENVIRONMENT this run will hit. DEVHIS_OPTS is inherited from the Command Prompt, so a
REM     run-klg.bat / run-dsh.bat executed EARLIER in the same window used to leave it set and silently
REM     redirect every later "run.bat" (fixed with setlocal in those wrappers, but an old window still
REM     carries the value). Printing it removes all doubt about where the browser is about to go.
if defined DEVHIS_OPTS (
  echo Target       : %DEVHIS_OPTS%
  echo                ^(inherited from this Command Prompt - run "set DEVHIS_OPTS=" for plain devhis^)
) else (
  echo Target       : https://devhis.sancyberhad.com  ^(built-in default^)
)
echo Output folder: %OUTDIR%

if not exist "%OUTDIR%" mkdir "%OUTDIR%"
if exist "%OUTDIR%\aggregate-exit.txt" del /q "%OUTDIR%\aggregate-exit.txt"

REM --- Full run (no arg): clear old per-flow reports so the consolidated report shows ONLY this run's pages ---
if "%~1"=="" (
  if exist "%OUTDIR%\reports" del /q "%OUTDIR%\reports\*.tsv" "%OUTDIR%\reports\*.html" >nul 2>nul
)

REM --- First-run: install the Playwright Chromium browser (only downloads once) ---
echo [1/3] Ensuring Playwright Chromium is installed (first run downloads it) ...
java -cp "%JAR%" com.microsoft.playwright.CLI install chromium

REM --- Mark the run start so the report only counts THIS run's flows ---
type nul > "%OUTDIR%\.runmarker"

echo.
echo [2/3] Running flows ...
if "%~1"=="" (
  REM --- Run MODULE BY MODULE, in the order listed below -----------------------------------------
  REM  A single "-p com.kpj.tests" scan runs all 134 flows in the jar's classpath-scan order, which
  REM  interleaves modules arbitrarily (CostCenterMaster, PrescriptionType, DepartmentGroup, ...,
  REM  Preadmission at #6, BookAppointmentTest at #20). One java run per package instead, so the
  REM  modules execute in a predictable order. Everything still lands in the SAME %OUTDIR%, and the
  REM  report is aggregated once at the end.
  REM  NOTE: order WITHIN a module is still the classpath-scan order - this fixes module order only.
  REM  Change the order by reordering this list.
  for %%M in (
    com.kpj.tests.Op_Page
    com.kpj.tests.Ip
    com.kpj.tests.Emergency_page
    com.kpj.tests.SystemConfiguration_page
    com.kpj.tests.Telemedicine_page
    com.kpj.tests.Billing_page
    com.kpj.tests.ApplicationConfiguration_page
    com.kpj.tests.NursingStation_page
    com.kpj.tests.AncillaryServices_page
    com.kpj.tests.Inventory_page
    com.kpj.tests.Investigation_page
  ) do (
    echo.
    echo ---- module: %%M ----
    java %DEVHIS_OPTS% -Ddevhis.outdir=%OUTDIR% -jar "%JAR%" -p %%M -n ".*"
  )
) else (
  REM --fail-if-no-tests: without it a MIS-TYPED flow name makes the launcher print its help text, run
  REM nothing, and the script still finished with "ALL STEPS PASSED" on 0 steps - a green result for a run
  REM that never happened. Now a name that matches nothing is an error.
  java %DEVHIS_OPTS% -Ddevhis.outdir=%OUTDIR% -jar "%JAR%" --fail-if-no-tests -c com.kpj.tests.%~1
  REM BOTH tests are needed. "if errorlevel 1" only matches codes >= 1, and the launcher answers a bad
  REM selector with -1, which slipped straight through and let the run finish green on 0 steps.
  REM "if not errorlevel 0" is true only for NEGATIVE codes, so together they catch any non-zero exit.
  if errorlevel 1 set "NOTESTS=1"
  if not errorlevel 0 set "NOTESTS=1"
)
if defined NOTESTS (
  echo.
  echo ============================================================
  echo  [ERROR] No test matched "com.kpj.tests.%~1" - nothing was run.
  echo.
  echo  Give the PACKAGE PATH + CLASS name, and mind the capitals:
  echo      run.bat Op_Page.RegistrationTest
  echo      run.bat Ip.Admission
  echo      run.bat Emergency_page.EmergencyListView
  echo      run.bat Op_Page.Appointment.AppointmentListTest
  echo      run.bat SystemConfiguration_page.User
  echo.
  echo  Packages: Op_Page, Ip, Emergency_page, SystemConfiguration_page,
  echo            Billing_page, NursingStation_page, Telemedicine_page,
  echo            ApplicationConfiguration_page, AncillaryServices_page,
  echo            Inventory_page, Investigation_page
  echo ============================================================
  pause
  endlocal ^& exit /b 1
)

REM --- Build the consolidated failed-steps report + exit code ---
echo.
echo [3/3] Building the failed-steps report ...
java -Ddevhis.outdir=%OUTDIR% -cp "%JAR%" com.kpj.core.ReportAggregator "%OUTDIR%\.runmarker"

set "FAILEXIT=1"
if exist "%OUTDIR%\aggregate-exit.txt" set /p FAILEXIT=<"%OUTDIR%\aggregate-exit.txt"

echo.
echo ============================================================
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
