@echo off
REM ============================================================================
REM  Core dispatcher for the per-environment core-screens runners.
REM  Do NOT double-click this one - double-click instead:
REM      run-core-flows-devhis.bat
REM      run-core-flows-klg.bat
REM      run-core-flows-dsh.bat
REM      run-core-flows-ks.bat
REM
REM  Runs these 6 screens in ONE pass, into ONE report folder:
REM      com.kpj.tests.Op_Page.RegistrationTest                      (OP Registration)
REM      com.kpj.tests.Op_Page.OutPatientQueueManagementTest         (OP Queue Management)
REM      com.kpj.tests.Ip.Admission                                  (IP Admission)
REM      com.kpj.tests.Emergency_page.EmergencyRegistrationConscious     (Emergency Reg - Conscious)
REM      com.kpj.tests.Emergency_page.Emergency_Registration_Unconscious (Emergency Reg - Unconscious)
REM      com.kpj.tests.SystemConfiguration_page.User                 (System Configuration - User)
REM
REM  Add or remove a screen ONCE here and all four environments follow.
REM
REM  Usage:  core-flows-runner.bat <devhis|klg|dsh|ks> [nationality]
REM
REM  The optional 2nd argument registers/admits a FOREIGN patient instead of the default Malaysian one:
REM  Identification Type becomes Passport (not New IC), the next of kin takes the same nationality, and the
REM  mandatory Visa Details are filled — a passport holder cannot be saved without them. Give it the
REM  ADJECTIVAL name the dropdown uses ("Australian", "American", …), not the country.
REM
REM      core-flows-runner.bat devhis                 -> Malaysian  -> test-output-core-devhis\
REM      core-flows-runner.bat devhis Australian      -> Australian -> test-output-core-devhis-australian\
REM
REM  Reports:  test-output-core-<env>\reports\<Page>.html
REM            test-output-core-<env>\FAILED_STEPS.html
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM ============================================================================
setlocal
cd /d "%~dp0"
set "JARNAME=mcp-kpj-1.0.0-shaded.jar"
set "ENVKEY=%~1"

REM --- Environments. NOTE: it must be https - over plain http the QA hosts serve
REM     an IIS error page, the app never loads, and every flow fails on a blank
REM     screen rather than saying why.
set "ENVLABEL="
if /i "%ENVKEY%"=="devhis" (
  REM The jar's own built-in default login (farisha) has been failing intermittently, so devhis now
  REM defaults to the tieba account instead.
  set "DEVHIS_OPTS=-Ddevhis.user=tieba -Ddevhis.pass=User@123"
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
if /i "%ENVKEY%"=="ks" (
  set "DEVHIS_OPTS=-Ddevhis.url=https://ks-nhisqa.kpjhealth.com.my -Ddevhis.user=Poovin -Ddevhis.pass=Nhis@123"
  set "ENVLABEL=KS QA - https://ks-nhisqa.kpjhealth.com.my"
)
if not defined ENVLABEL (
  echo [ERROR] Unknown environment "%ENVKEY%" - use devhis, klg, dsh or ks.
  echo         Double-click run-core-flows-devhis.bat / -klg.bat / -dsh.bat / -ks.bat instead.
  pause
  exit /b 1
)

REM --- Specific classes (not whole packages) - one -c per flow. Edit here. ----
set "FLOWS=-c com.kpj.tests.Op_Page.RegistrationTest"
set "FLOWS=%FLOWS% -c com.kpj.tests.Op_Page.OutPatientQueueManagementTest"
set "FLOWS=%FLOWS% -c com.kpj.tests.Ip.Admission"
set "FLOWS=%FLOWS% -c com.kpj.tests.Emergency_page.EmergencyRegistrationConscious"
set "FLOWS=%FLOWS% -c com.kpj.tests.Emergency_page.Emergency_Registration_Unconscious"
set "FLOWS=%FLOWS% -c com.kpj.tests.SystemConfiguration_page.User"

REM --- Optional nationality (2nd arg). Its own OUTPUT FOLDER so a foreign run never overwrites the
REM     Malaysian one — the two passes are meant to be compared side by side.
set "NATIONALITY=%~2"
set "OUTDIR=test-output-core-%ENVKEY%"
set "NATOPTS="
set "NATLABEL=Malaysian (default)"
set "FLOWLABEL=all 6 core screens"
if not "%NATIONALITY%"=="" (
  set "NATOPTS=-Ddevhis.nationality=%NATIONALITY%"
  set "NATLABEL=%NATIONALITY% (foreign: Passport + Visa Details)"
  set "OUTDIR=test-output-core-%ENVKEY%-%NATIONALITY%"
  REM  Only THESE three screens read the nationality — they switch to Passport, put the kin on the same
  REM  nationality and fill the mandatory Visa Details. The other three ignore it, so re-running them here
  REM  would just repeat pass 1 for no extra coverage.
  set "FLOWS=-c com.kpj.tests.Op_Page.RegistrationTest -c com.kpj.tests.Ip.Admission -c com.kpj.tests.Emergency_page.EmergencyRegistrationConscious"
  set "FLOWLABEL=the 3 nationality-aware screens (Registration, Admission, Emergency Conscious)"
)

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
echo   Environment  : %ENVLABEL%
echo   Patient      : %NATLABEL%
echo   Screens      : %FLOWLABEL%
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
echo [2/3] Running %FLOWLABEL% on %ENVKEY% as a %NATLABEL% patient ...
java %DEVHIS_OPTS% %NATOPTS% -Ddevhis.outdir=%OUTDIR% -jar "%JAR%" %FLOWS%

echo.
echo [3/3] Building the failed-steps report ...
java -Ddevhis.outdir=%OUTDIR% -cp "%JAR%" com.kpj.core.ReportAggregator "%OUTDIR%\.runmarker"

set "FAILEXIT=1"
if exist "%OUTDIR%\aggregate-exit.txt" set /p FAILEXIT=<"%OUTDIR%\aggregate-exit.txt"

echo.
echo ============================================================
echo   Environment      : %ENVLABEL%
echo   Per-flow reports : %CD%\%OUTDIR%\reports\
echo   FAILED steps     : %CD%\%OUTDIR%\FAILED_STEPS.html
if "%FAILEXIT%"=="0" (
  echo   RESULT           : ALL STEPS PASSED
) else (
  echo   RESULT           : SOME STEPS FAILED  ^(see FAILED_STEPS.html^)
)
echo ============================================================
REM  The per-environment wrapper runs this twice (Malaysian, then foreign) and pauses once at the end —
REM  a pause here would stop the run dead between the two passes.
if not defined CORE_NOPAUSE pause
endlocal ^& exit /b %FAILEXIT%
