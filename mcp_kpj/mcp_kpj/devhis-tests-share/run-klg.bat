@echo off
REM ============================================================================
REM  Run the DevHIS flows against the KLG QA environment.
REM    https://klg-nhisqa.kpjhealth.com.my/  (user: Poovin)
REM  Same jar as run.bat — only the URL + credentials + output folder differ.
REM
REM  Results go to a SEPARATE folder (test-output-klg) so a KLG run never
REM  clobbers a devhis run (run.bat) and vice-versa.
REM
REM  Usage:
REM    run-klg.bat                                          Run ALL flows on KLG QA
REM    run-klg.bat Op_Page.Appointment.BookAppointmentTest  Run one flow on KLG QA
REM ============================================================================
REM  setlocal is ESSENTIAL: without it DEVHIS_OPTS and OUTDIR stay set in the Command Prompt after
REM  this script ends, so the NEXT "run.bat ..." typed in the same window silently runs against KLG
REM  and writes into test-output-klg. endlocal restores the window to how it was.
setlocal
set "DEVHIS_OPTS=-Ddevhis.url=https://klg-nhisqa.kpjhealth.com.my -Ddevhis.user=Poovin -Ddevhis.pass=Nhis@123"
set "OUTDIR=test-output-klg"
call "%~dp0run.bat" %*
endlocal ^& exit /b %ERRORLEVEL%
