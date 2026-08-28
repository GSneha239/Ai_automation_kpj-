@echo off
REM ============================================================================
REM  Run the COMBINED OP + IP + Emergency + System Configuration flows
REM  against the DSH QA environment.
REM    https://dsh-nhisqa.kpjhealth.com.my/  (user: Poovin)
REM  NOTE: it must be https. Over plain http the host serves an IIS error page, the app never
REM  loads, and every flow fails on a blank screen rather than saying why.
REM
REM  Same jar and same flow list as run-op-ip-emergency.bat - only the URL +
REM  credentials + output folder differ.
REM
REM  Results go to a SEPARATE folder (test-output-op-ip-emergency-dsh) so a DSH
REM  run never clobbers a devhis/KLG run and vice-versa.
REM
REM  Usage:
REM    run-op-ip-emergency-dsh.bat        Run all 10 flows on DSH QA
REM
REM  Reports:  test-output-op-ip-emergency-dsh\reports\<Page>.html
REM            test-output-op-ip-emergency-dsh\FAILED_STEPS.html
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM ============================================================================
setlocal
set "DEVHIS_OPTS=-Ddevhis.url=https://dsh-nhisqa.kpjhealth.com.my -Ddevhis.user=Poovin -Ddevhis.pass=Nhis@123"
set "OUTDIR=test-output-op-ip-emergency-dsh"
call "%~dp0run-op-ip-emergency.bat"
endlocal & exit /b %ERRORLEVEL%
