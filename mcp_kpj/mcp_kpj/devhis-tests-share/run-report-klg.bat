@echo off
REM ============================================================================
REM  Report module  ->  KLG QA - https://klg-nhisqa.kpjhealth.com.my (user Poovin)
REM
REM  Runs EVERY flow under com.kpj.tests.Report_page
REM  Reports : test-output-report-klg\reports\<Page>.html
REM            test-output-report-klg\FAILED_STEPS.html
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM
REM  Just double-click this file. The module list lives in module-runner.bat.
REM ============================================================================
call "%~dp0module-runner.bat" klg report
