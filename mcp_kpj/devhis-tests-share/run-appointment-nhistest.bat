@echo off
REM ============================================================================
REM  OP - Appointment module  ->  NHIS Test - https://nhistest.sancyberhad.com (admin Tieba)
REM
REM  Runs the 5 Appointment flows under com.kpj.tests.Op_Page.Appointment:
REM      TC06_AppointmentList              (Appointment List)
REM      TC04_BookAppointment              (Book Appointment)
REM      TC05_DoctorwiseAppointmentSchedule (Doctorwise Appointment Schedule)
REM      TC01_MAB                          (Multiple Appointment Booking)
REM      OP_ExistingPatientAppointment     (Existing)
REM
REM  Reports : test-output-appointment-nhistest\reports\<Page>.html
REM            test-output-appointment-nhistest\FAILED_STEPS.html
REM  Exit code: 0 = all steps passed, 1 = one or more steps FAILED.
REM
REM  Just double-click this file. The module list lives in module-runner.bat.
REM ============================================================================
call "%~dp0module-runner.bat" nhistest appointment
