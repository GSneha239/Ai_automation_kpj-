@echo off
REM ============================================================================
REM  Application Configuration module  ->  KLG QA - https://klg-nhisqa.kpjhealth.com.my (user Poovin)
REM
REM  Runs EVERY flow under com.kpj.tests.ApplicationConfiguration_page
REM  Reports : test-output-appconfig-klg\reports\<Page>.html
REM            test-output-appconfig-klg\FAILED_STEPS.html
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM
REM  Just double-click this file. The module list lives in module-runner.bat.
REM ============================================================================
call "%~dp0module-runner.bat" klg appconfig
