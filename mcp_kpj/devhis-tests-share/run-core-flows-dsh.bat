@echo off
REM ============================================================================
REM  Run the 6 core screens against dsh TWICE - once per patient type:
REM
REM     pass 1  MALAYSIAN patient  (New IC + NRIC)   -> test-output-core-dsh\
REM     pass 2  FOREIGN patient    (Passport + Visa) -> test-output-core-dsh-<Nationality>\
REM
REM  The two passes exercise different rules: a Malaysian must NOT be able to enter a passport,
REM  while a foreigner is identified BY the passport and cannot be saved until the mandatory
REM  Visa Details are added. Separate folders, so neither pass overwrites the other.
REM
REM  Just double-click this file -> the foreign pass uses Australian.
REM  Or name the nationality (ADJECTIVAL, as the dropdown spells it):
REM      run-core-flows-dsh.bat American
REM
REM  Reports    : test-output-core-dsh*\reports\<Page>.html
REM  FAILED list: test-output-core-dsh*\FAILED_STEPS.html
REM  Exit code  : 0 = every step of BOTH passes passed, 1 = something failed.
REM  The screen list lives in core-flows-runner.bat.
REM ============================================================================
setlocal
set "FOREIGN=%~1"
if "%FOREIGN%"=="" set "FOREIGN=Australian"

REM  Let the runner skip its own pause - otherwise the run stops dead between the two passes.
set "CORE_NOPAUSE=1"

echo.
echo ############################################################
echo #  PASS 1 of 2  -  MALAYSIAN patient
echo ############################################################
call "%~dp0core-flows-runner.bat" dsh
set "RC1=%ERRORLEVEL%"

echo.
echo ############################################################
echo #  PASS 2 of 2  -  %FOREIGN% patient ^(Passport + Visa Details^)
echo ############################################################
call "%~dp0core-flows-runner.bat" dsh %FOREIGN%
set "RC2=%ERRORLEVEL%"

set "CORE_NOPAUSE="
echo.
echo ============================================================
echo   Environment      : DSH QA - https://dsh-nhisqa.kpjhealth.com.my
echo   Pass 1 Malaysian : test-output-core-dsh\reports\
echo   Pass 2 %FOREIGN% : test-output-core-dsh-%FOREIGN%\reports\
if "%RC1%"=="0" (echo   Pass 1 result    : ALL STEPS PASSED) else (echo   Pass 1 result    : SOME STEPS FAILED)
if "%RC2%"=="0" (echo   Pass 2 result    : ALL STEPS PASSED) else (echo   Pass 2 result    : SOME STEPS FAILED)
echo ============================================================
pause
set "RC=0"
if not "%RC1%"=="0" set "RC=1"
if not "%RC2%"=="0" set "RC=1"
endlocal ^& exit /b %RC%
