@echo off
REM ============================================================================
REM  Inventory module  ->  devhis (default URL built into the jar)
REM
REM  Runs EVERY flow under com.kpj.tests.Inventory_page
REM  Reports : test-output-inventory-devhis\reports\<Page>.html
REM            test-output-inventory-devhis\FAILED_STEPS.html
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM
REM  Just double-click this file. The module list lives in module-runner.bat.
REM ============================================================================
call "%~dp0module-runner.bat" devhis inventory
