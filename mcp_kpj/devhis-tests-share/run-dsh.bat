@echo off
REM ============================================================================
REM  Run the DevHIS flows against the DSH QA environment.
REM    https://dsh-nhisqa.kpjhealth.com.my/  (user: Poovin)
REM  NOTE: it must be https. Over plain http the host serves an IIS error page, the app never
REM  loads, and every flow fails on a blank screen rather than saying why.
REM  Same jar as run.bat — only the URL + credentials + output folder differ.
REM
REM  Results go to a SEPARATE folder (test-output-dsh) so a DSH run never
REM  clobbers a devhis/KLG run and vice-versa.
REM
REM  Usage:
REM    run-dsh.bat                                          Run ALL flows on DSH QA
REM    run-dsh.bat Op_Page.Appointment.BookAppointmentTest  Run one flow on DSH QA
REM ============================================================================
REM  setlocal is ESSENTIAL: without it DEVHIS_OPTS and OUTDIR stay set in the Command Prompt after
REM  this script ends, so the NEXT "run.bat ..." typed in the same window silently runs against DSH
REM  and writes into test-output-dsh. endlocal restores the window to how it was.
setlocal
set "DEVHIS_OPTS=-Ddevhis.url=https://dsh-nhisqa.kpjhealth.com.my -Ddevhis.user=Poovin -Ddevhis.pass=Nhis@123"
set "OUTDIR=test-output-dsh"
call "%~dp0run.bat" %*
endlocal ^& exit /b %ERRORLEVEL%
