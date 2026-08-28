@echo off
REM ============================================================================
REM  Nursing Station module  ->  devhis (default URL built into the jar)
REM
REM  Runs EVERY flow under com.kpj.tests.NursingStation_page
REM  Reports : test-output-nursing-devhis\reports\<Page>.html
REM            test-output-nursing-devhis\FAILED_STEPS.html
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM
REM  Just double-click this file. The module list lives in module-runner.bat.
REM ============================================================================
call "%~dp0module-runner.bat" devhis nursing
