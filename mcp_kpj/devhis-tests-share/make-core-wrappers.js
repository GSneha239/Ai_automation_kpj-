// Regenerates run-core-flows-<env>.bat so each one runs BOTH passes: the default Malaysian patient and a
// foreign (Passport + Visa Details) one. Kept as a script because the three wrappers must stay identical
// apart from the environment key — editing them by hand is how they drift apart.
const fs = require('fs');
const BS = String.fromCharCode(92); // a single backslash, kept out of the template strings below

const envs = {
  devhis: 'devhis - default URL/credentials built into the jar',
  klg:    'KLG QA - https://klg-nhisqa.kpjhealth.com.my',
  dsh:    'DSH QA - https://dsh-nhisqa.kpjhealth.com.my',
  ks:     'KS QA - https://ks-nhisqa.kpjhealth.com.my',
};

for (const [env, label] of Object.entries(envs)) {
  const out = 'test-output-core-' + env;
  const lines = [
    '@echo off',
    'REM ============================================================================',
    'REM  Run the 6 core screens against ' + env + ' TWICE - once per patient type:',
    'REM',
    'REM     pass 1  MALAYSIAN patient  (New IC + NRIC)   -> ' + out + BS,
    'REM     pass 2  FOREIGN patient    (Passport + Visa) -> ' + out + '-<Nationality>' + BS,
    'REM',
    'REM  The two passes exercise different rules: a Malaysian must NOT be able to enter a passport,',
    'REM  while a foreigner is identified BY the passport and cannot be saved until the mandatory',
    'REM  Visa Details are added. Separate folders, so neither pass overwrites the other.',
    'REM',
    'REM  Just double-click this file -> the foreign pass uses Australian.',
    'REM  Or name the nationality (ADJECTIVAL, as the dropdown spells it):',
    'REM      run-core-flows-' + env + '.bat American',
    'REM',
    'REM  Reports    : ' + out + '*' + BS + 'reports' + BS + '<Page>.html',
    'REM  FAILED list: ' + out + '*' + BS + 'FAILED_STEPS.html',
    'REM  Exit code  : 0 = every step of BOTH passes passed, 1 = something failed.',
    'REM  The screen list lives in core-flows-runner.bat.',
    'REM ============================================================================',
    'setlocal',
    'set "FOREIGN=%~1"',
    'if "%FOREIGN%"=="" set "FOREIGN=Australian"',
    '',
    'REM  Let the runner skip its own pause - otherwise the run stops dead between the two passes.',
    'set "CORE_NOPAUSE=1"',
    '',
    'echo.',
    'echo ############################################################',
    'echo #  PASS 1 of 2  -  MALAYSIAN patient',
    'echo ############################################################',
    'call "%~dp0core-flows-runner.bat" ' + env,
    'set "RC1=%ERRORLEVEL%"',
    '',
    'echo.',
    'echo ############################################################',
    'echo #  PASS 2 of 2  -  %FOREIGN% patient ^(Passport + Visa Details^)',
    'echo ############################################################',
    'call "%~dp0core-flows-runner.bat" ' + env + ' %FOREIGN%',
    'set "RC2=%ERRORLEVEL%"',
    '',
    'set "CORE_NOPAUSE="',
    'echo.',
    'echo ============================================================',
    'echo   Environment      : ' + label,
    'echo   Pass 1 Malaysian : ' + out + BS + 'reports' + BS,
    'echo   Pass 2 %FOREIGN% : ' + out + '-%FOREIGN%' + BS + 'reports' + BS,
    'if "%RC1%"=="0" (echo   Pass 1 result    : ALL STEPS PASSED) else (echo   Pass 1 result    : SOME STEPS FAILED)',
    'if "%RC2%"=="0" (echo   Pass 2 result    : ALL STEPS PASSED) else (echo   Pass 2 result    : SOME STEPS FAILED)',
    'echo ============================================================',
    'pause',
    'set "RC=0"',
    'if not "%RC1%"=="0" set "RC=1"',
    'if not "%RC2%"=="0" set "RC=1"',
    'endlocal ^& exit /b %RC%',
    '',
  ];
  fs.writeFileSync('run-core-flows-' + env + '.bat', lines.join('\r\n'));
  console.log('wrote run-core-flows-' + env + '.bat');
}
