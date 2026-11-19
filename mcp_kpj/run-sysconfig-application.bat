@echo off
rem ============================================================================
rem  System Configuration > Application  (READ-ONLY - the save API is blocked; nothing is saved)
rem    1) mvn clean test-compile -DskipTests   -> test-output\sysconfig-application-compile.log
rem    2) mvn test -Dtest=...Application       -> test-output\sysconfig-application-run.log
rem    Report -> test-output\reports\SystemConfiguration_Application.html
rem
rem  Credentials: uses DEVHIS_USER / DEVHIS_PASS if already set. If not, asks for them here
rem  (the password is typed hidden via PowerShell Read-Host -AsSecureString). They are kept only for
rem  this window and are never written to a file. DEVHIS_URL defaults to devhis.
rem  Post-run self-healing is switched off (-Ddevhis.postrun=off) so no script is auto-edited.
rem ============================================================================
cd /d "%~dp0"
if not exist test-output mkdir test-output
if "%DEVHIS_URL%"=="" set "DEVHIS_URL=https://devhis.sancyberhad.com"

if "%DEVHIS_USER%"=="" for /f "usebackq delims=" %%U in (`powershell -NoProfile -Command "Read-Host 'DevHIS login name'"`) do set "DEVHIS_USER=%%U"
if "%DEVHIS_PASS%"=="" for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$s=Read-Host 'DevHIS password' -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))"`) do set "DEVHIS_PASS=%%P"
if "%DEVHIS_USER%"=="" goto :nocreds
if "%DEVHIS_PASS%"=="" goto :nocreds

echo Compiling ...
echo START %DATE% %TIME% > test-output\sysconfig-application-compile.log
call mvn clean test-compile -DskipTests >> test-output\sysconfig-application-compile.log 2>&1
set "CRC=%ERRORLEVEL%"
echo EXIT=%CRC% END %DATE% %TIME% >> test-output\sysconfig-application-compile.log
if not "%CRC%"=="0" (
  echo COMPILE FAILED - see test-output\sysconfig-application-compile.log
  pause
  exit /b %CRC%
)

echo Running the Application test against %DEVHIS_URL% as %DEVHIS_USER% ...
echo START %DATE% %TIME% DEVHIS_URL=%DEVHIS_URL% USER=%DEVHIS_USER% > test-output\sysconfig-application-run.log
call mvn test "-Dtest=com.kpj.tests.SystemConfiguration_page.Application" -Ddevhis.postrun=off >> test-output\sysconfig-application-run.log 2>&1
set "RC=%ERRORLEVEL%"
echo EXIT=%RC% END %DATE% %TIME% >> test-output\sysconfig-application-run.log
echo.
echo Report: %CD%\test-output\reports\SystemConfiguration_Application.html
echo Log   : %CD%\test-output\sysconfig-application-run.log
echo Done (exit %RC%). You can close this window.
pause
exit /b %RC%

:nocreds
echo DEVHIS_USER / DEVHIS_PASS not supplied. Nothing was run.
pause
exit /b 1
