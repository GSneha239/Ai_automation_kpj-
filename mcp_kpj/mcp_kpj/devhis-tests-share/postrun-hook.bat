@echo off
REM ============================================================================
REM  Post-run hook - called as the LAST step of the shared runners (module-runner.bat, core-flows-runner.bat, run.bat).
REM  Do NOT double-click this; it is not a runner.
REM
REM  Usage:  call "%~dp0postrun-hook.bat" "<OUTDIR>" "<entry-point name>" "<jar used>"
REM
REM  What it does: after a run has finished and its reports are built, it starts ONE automatic Failure Analysis of THAT run's fresh
REM  results (Failure Analysis -> eligibility check -> Self-Healing PROPOSAL) and then STOPS. It runs no test, connects to no
REM  environment, applies no patch and approves nothing. Whatever it concludes (or if it cannot run at all), the runner's own
REM  results and exit code are untouched: this script always returns 0, and a runner takes its exit code from FAILEXIT, not from here.
REM
REM  Switch it off:   set DEVHIS_POSTRUN=off      (or put -Ddevhis.postrun=off in DEVHIS_OPTS)
REM  Needs:           the project source tree next to this folder (pom.xml + src\test\java) and Maven on PATH. Without them
REM                   (for example a teammate who only has the jar) it prints why and does nothing.
REM  LLM fallback:    OFF in this automatic path unless you set DEVHIS_POSTRUN_LLM=on (and the usual LLM_* settings).
REM  Credentials:     this script never reads, prints or passes on DEVHIS_OPTS values; it only hands over folder names.
REM  Details:         devhis-tests-share\POST-RUN-HEALING.txt
REM ============================================================================
setlocal
set "HOOK_OUT=%~1"
set "HOOK_ENTRY=%~2"
set "HOOK_JAR=%~3"

if /i "%DEVHIS_POSTRUN%"=="off" (
  echo.
  echo POSTRUN_SKIPPED: DISABLED - automatic post-run analysis is switched off ^(DEVHIS_POSTRUN=off^).
  exit /b 0
)
if not defined HOOK_OUT (
  echo POSTRUN_SKIPPED: INVALID_CONTEXT - no output folder was given to the post-run hook.
  exit /b 0
)

REM --- absolute paths, resolved from the runner's current folder BEFORE we move to the project root ---
for %%I in ("%HOOK_OUT%") do set "DEVHIS_POSTRUN_OUT=%%~fI"
set "DEVHIS_POSTRUN_ENTRY=%HOOK_ENTRY%"
if defined HOOK_JAR (for %%I in ("%HOOK_JAR%") do set "DEVHIS_POSTRUN_JAR=%%~fI")
for %%I in ("%~dp0..") do set "HOOK_PROJECT=%%~fI"

if not exist "%HOOK_PROJECT%\pom.xml" goto :nosource
if not exist "%HOOK_PROJECT%\src\test\java" goto :nosource
where mvn >nul 2>nul
if errorlevel 1 (
  echo.
  echo POSTRUN_SKIPPED: NO_MAVEN - Maven ^(mvn^) is not on PATH, so the post-run analysis cannot start. The test results are unaffected.
  exit /b 0
)

echo.
echo ============================================================
echo   Automatic post-run analysis ^(analysis and proposal only - nothing is applied^)
echo   Switch off with:  set DEVHIS_POSTRUN=off
echo ============================================================
pushd "%HOOK_PROJECT%"
call mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.PostRunCoordinator -Dexec.args=--batch
set "HOOK_RC=%ERRORLEVEL%"
popd
if not "%HOOK_RC%"=="0" (
  echo POSTRUN_SKIPPED: COORDINATOR_FAILED - the analysis process exited with code %HOOK_RC%. The test results and the runner's exit code are unaffected.
)
exit /b 0

:nosource
echo.
echo POSTRUN_SKIPPED: NO_SOURCE_TREE - the project source ^(pom.xml and src\test\java^) was not found next to this folder, so the
echo                  agents cannot run here. Nothing was analysed; the test results are unaffected.
exit /b 0
