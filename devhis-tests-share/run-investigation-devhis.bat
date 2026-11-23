@echo off
REM ============================================================================
REM  Investigation module  ->  devhis (default URL built into the jar)
REM
REM  Runs EVERY flow under com.kpj.tests.Investigation_page
REM  Reports : test-output-investigation-devhis\reports\<Page>.html
REM            test-output-investigation-devhis\FAILED_STEPS.html
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM
REM  Just double-click this file. The module list lives in module-runner.bat.
REM ============================================================================
call "%~dp0module-runner.bat" devhis investigation
