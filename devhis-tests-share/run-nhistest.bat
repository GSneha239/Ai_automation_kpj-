@echo off
REM ============================================================================
REM  Run the DevHIS flows against the NHIS Test environment.
REM    https://nhistest.sancyberhad.com/  (admin: Tieba)
REM  Same jar as run.bat — only the URL + credentials + output folder differ.
REM
REM  Results go to a SEPARATE folder (test-output-nhistest) so a NHIS Test run never
REM  clobbers a devhis run (run.bat) and vice-versa.
REM
REM  Usage:
REM    run-nhistest.bat                                          Run ALL flows on NHIS Test
REM    run-nhistest.bat Op_Page.Appointment.BookAppointmentTest  Run one flow on NHIS Test
REM ============================================================================
REM  setlocal is ESSENTIAL: without it DEVHIS_OPTS and OUTDIR stay set in the Command Prompt after
REM  this script ends, so the NEXT "run.bat ..." typed in the same window silently runs against NHIS Test
REM  and writes into test-output-nhistest. endlocal restores the window to how it was.
setlocal
set "DEVHIS_OPTS=-Ddevhis.url=https://nhistest.sancyberhad.com -Ddevhis.user=Tieba -Ddevhis.pass=User@123"
set "OUTDIR=test-output-nhistest"
call "%~dp0run.bat" %*
endlocal ^& exit /b %ERRORLEVEL%
