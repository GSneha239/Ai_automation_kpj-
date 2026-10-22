@echo off
REM ============================================================================
REM  Billing OP end-to-end flow  ->  NHIS Test - https://nhistest.sancyberhad.com (admin Tieba)
REM
REM  Runs EVERY flow under com.kpj.tests.Flow_page
REM  Reports : test-output-flow-nhistest\reports\<Page>.html
REM            test-output-flow-nhistest\FAILED_STEPS.html
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM
REM  Just double-click this file. The module list lives in module-runner.bat.
REM ============================================================================
call "%~dp0module-runner.bat" nhistest flow
