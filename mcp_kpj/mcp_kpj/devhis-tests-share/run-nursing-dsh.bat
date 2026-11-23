@echo off
REM ============================================================================
REM  Nursing Station module  ->  DSH QA - https://dsh-nhisqa.kpjhealth.com.my (user Poovin)
REM
REM  Runs EVERY flow under com.kpj.tests.NursingStation_page
REM  Reports : test-output-nursing-dsh\reports\<Page>.html
REM            test-output-nursing-dsh\FAILED_STEPS.html
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM
REM  Just double-click this file. The module list lives in module-runner.bat.
REM ============================================================================
call "%~dp0module-runner.bat" dsh nursing
