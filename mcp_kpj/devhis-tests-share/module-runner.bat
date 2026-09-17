@echo off
REM ============================================================================
REM  Core dispatcher for the per-MODULE, per-ENVIRONMENT runners.
REM  Do NOT double-click this one - double-click a run-<module>-<env>.bat instead:
REM
REM      run-op-devhis.bat            run-op-klg.bat            run-op-dsh.bat
REM      run-ip-devhis.bat            run-ip-klg.bat            run-ip-dsh.bat
REM      run-emergency-devhis.bat     run-emergency-klg.bat     run-emergency-dsh.bat
REM      run-sysconfig-devhis.bat     run-sysconfig-klg.bat     run-sysconfig-dsh.bat
REM      run-billing-devhis.bat       run-billing-klg.bat       run-billing-dsh.bat
REM      run-nursing-devhis.bat       run-nursing-klg.bat       run-nursing-dsh.bat
REM      run-telemedicine-devhis.bat  run-telemedicine-klg.bat  run-telemedicine-dsh.bat
REM      run-appconfig-devhis.bat     run-appconfig-klg.bat     run-appconfig-dsh.bat
REM      run-ancillary-devhis.bat     run-ancillary-klg.bat     run-ancillary-dsh.bat
REM      run-inventory-devhis.bat     run-inventory-klg.bat     run-inventory-dsh.bat
REM      run-investigation-devhis.bat run-investigation-klg.bat run-investigation-dsh.bat
REM
REM  Runs EVERY flow in the module's package, into its own folder:
REM      test-output-<module>-<env>\reports\<Page>.html
REM      test-output-<module>-<env>\FAILED_STEPS.html
REM
REM  Usage:  module-runner.bat <devhis|klg|dsh> <module-key>
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM ============================================================================
setlocal
cd /d "%~dp0"
set "JARNAME=mcp-kpj-1.0.0-shaded.jar"
set "ENVKEY=%~1"
set "MODKEY=%~2"

if "%MODKEY%"=="" (
  echo [ERROR] Usage: module-runner.bat ^<devhis^|klg^|dsh^> ^<module-key^>
  echo         Double-click a run-^<module^>-^<env^>.bat file instead.
  pause
  exit /b 1
)

REM --- Environments. NOTE: it must be https - over plain http the QA hosts serve
REM     an IIS error page, the app never loads, and every flow fails on a blank screen.
set "ENVLABEL="
if /i "%ENVKEY%"=="devhis" (
  REM The jar's own built-in default login (farisha) has been failing intermittently, so devhis now
  REM defaults to the tieba account instead. Password reset 2026-09-10 (was User@123).
  set "DEVHIS_OPTS=-Ddevhis.user=tieba -Ddevhis.pass=Tieba@123"
  set "ENVLABEL=devhis - default URL, tieba credentials"
)
if /i "%ENVKEY%"=="klg" (
  set "DEVHIS_OPTS=-Ddevhis.url=https://klg-nhisqa.kpjhealth.com.my -Ddevhis.user=Poovin -Ddevhis.pass=Nhis@123"
  set "ENVLABEL=KLG QA - https://klg-nhisqa.kpjhealth.com.my"
)
if /i "%ENVKEY%"=="dsh" (
  set "DEVHIS_OPTS=-Ddevhis.url=https://dsh-nhisqa.kpjhealth.com.my -Ddevhis.user=Poovin -Ddevhis.pass=Nhis@123"
  set "ENVLABEL=DSH QA - https://dsh-nhisqa.kpjhealth.com.my"
)
if not defined ENVLABEL (
  echo [ERROR] Unknown environment "%ENVKEY%" - use devhis, klg or dsh.
  pause
  exit /b 1
)

REM --- Modules: one line per module. Add a module ONCE here and all 3 environments follow.
set "PKG="
if /i "%MODKEY%"=="op"           ( set "PKG=com.kpj.tests.Op_Page"                     & set "MODULE=OP" )
if /i "%MODKEY%"=="ip"           ( set "PKG=com.kpj.tests.Ip"                          & set "MODULE=IP" )
if /i "%MODKEY%"=="emergency"    ( set "PKG=com.kpj.tests.Emergency_page"              & set "MODULE=Emergency" )
if /i "%MODKEY%"=="sysconfig"    ( set "PKG=com.kpj.tests.SystemConfiguration_page"    & set "MODULE=System Configuration" )
if /i "%MODKEY%"=="billing"      ( set "PKG=com.kpj.tests.Billing_page"                & set "MODULE=Billing" )
if /i "%MODKEY%"=="nursing"      ( set "PKG=com.kpj.tests.NursingStation_page"         & set "MODULE=Nursing Station" )
if /i "%MODKEY%"=="telemedicine" ( set "PKG=com.kpj.tests.Telemedicine_page"           & set "MODULE=Telemedicine" )
if /i "%MODKEY%"=="appconfig"    ( set "PKG=com.kpj.tests.ApplicationConfiguration_page" & set "MODULE=Application Configuration" )
if /i "%MODKEY%"=="ancillary"    ( set "PKG=com.kpj.tests.AncillaryServices_page"        & set "MODULE=Ancillary Services" )
if /i "%MODKEY%"=="inventory"    ( set "PKG=com.kpj.tests.Inventory_page"                & set "MODULE=Inventory" )
if /i "%MODKEY%"=="investigation" ( set "PKG=com.kpj.tests.Investigation_page"           & set "MODULE=Investigation" )
if not defined PKG (
  echo [ERROR] Unknown module "%MODKEY%".
  echo         Use: op, ip, emergency, sysconfig, billing, nursing, telemedicine, appconfig, ancillary, inventory, investigation.
  pause
  exit /b 1
)

set "OUTDIR=test-output-%MODKEY%-%ENVKEY%"

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

echo ============================================================
echo   Module       : %MODULE%  ^(%PKG%^)
echo   Environment  : %ENVLABEL%
echo   Using jar    : %JAR%
echo   Output folder: %OUTDIR%
echo ============================================================

if not exist "%OUTDIR%" mkdir "%OUTDIR%"
if exist "%OUTDIR%\aggregate-exit.txt" del /q "%OUTDIR%\aggregate-exit.txt"
if exist "%OUTDIR%\reports" del /q "%OUTDIR%\reports\*.tsv" "%OUTDIR%\reports\*.html" >nul 2>nul

echo.
echo [1/3] Ensuring Playwright Chromium is installed (first run downloads it) ...
java -cp "%JAR%" com.microsoft.playwright.CLI install chromium

type nul > "%OUTDIR%\.runmarker"

echo.
echo [2/3] Running %MODULE% flows on %ENVKEY% ...
java %DEVHIS_OPTS% -Ddevhis.outdir=%OUTDIR% -jar "%JAR%" -p %PKG% -n ".*"

echo.
echo [3/3] Building the failed-steps report ...
java -Ddevhis.outdir=%OUTDIR% -cp "%JAR%" com.kpj.core.ReportAggregator "%OUTDIR%\.runmarker"

set "FAILEXIT=1"
if exist "%OUTDIR%\aggregate-exit.txt" set /p FAILEXIT=<"%OUTDIR%\aggregate-exit.txt"

echo.
echo ============================================================
echo   Module           : %MODULE%
echo   Environment      : %ENVLABEL%
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
