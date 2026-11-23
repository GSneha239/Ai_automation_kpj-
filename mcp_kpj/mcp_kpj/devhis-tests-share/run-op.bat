@echo off
REM ============================================================================
REM  DevHIS Playwright flows - OP module runner (no Maven needed)
REM  Runs every flow under com.kpj.tests.Op_Page
REM  Keep this next to mcp-kpj-1.0.0-shaded.jar.
REM
REM  Reports:  %OUTDIR%\reports\<Page>.html   +   %OUTDIR%\FAILED_STEPS.html
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM ============================================================================
setlocal
cd /d "%~dp0"
set "JARNAME=mcp-kpj-1.0.0-shaded.jar"
set "MODULE=OP"
set "PKG=com.kpj.tests.Op_Page"
if not defined OUTDIR set "OUTDIR=test-output-op"

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
echo Module       : %MODULE%  (%PKG%)
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
java %DEVHIS_OPTS% -Ddevhis.outdir=%OUTDIR% -jar "%JAR%" -p %PKG% -n ".*"

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
