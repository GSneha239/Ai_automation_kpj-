/**
 * Playwright reporter that writes the SAME standard-format HTML report the Java suite writes
 * (src/main/java/com/kpj/core/HtmlReport.java), e.g. reports/NursingStation_Mortuary.html:
 *   - purple gradient header: "<Module path> - Test Execution Report", Application, Module
 *   - the yellow note box (what the spec does / whether it can write anything)
 *   - Test Summary table, ending with "Total / Passed / Manual / Skipped / Failed" and "Overall Result"
 *   - Execution Steps table: # / Step Name / Description / Expected / Actual / Status / Screenshot
 *   - screenshots embedded as base64; the Screenshot column links to in-page #shot_N anchors
 *
 * One report per spec file, named like the Java ones, e.g. tests/Billing_page/doctorShare.spec.ts ->
 * reports/Billing_DoctorShare.html. As in the Java reports, step 1 is "Login" and every following step is one check
 * (here: one test). The module path and the note come from the spec's own header comment.
 *
 * Use it with playwright.devhis.config.ts (it turns screenshots on, so every step carries one):
 *   npx playwright test -c playwright.devhis.config.ts tests/Billing_page/doctorShare.spec.ts
 *
 * Output folder: $DEVHIS_REPORT_DIR, else mcp_kpj/devhis-tests-share/test-output/reports.
 */
import type { FullConfig, FullResult, Reporter, TestCase, TestResult, TestStep } from '@playwright/test/reporter';
import * as fs from 'node:fs';
import * as path from 'node:path';

const CSS =
  "*{box-sizing:border-box}body{margin:0;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#f4f5f7;color:#2d2d2d;line-height:1.5}" +
  '.header{background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:#fff;padding:36px 40px;box-shadow:0 4px 12px rgba(0,0,0,.15)}' +
  '.header h1{margin:0 0 6px;font-size:28px;font-weight:600}.header p{margin:2px 0;opacity:.92;font-size:14px}' +
  '.container{max-width:1180px;margin:28px auto;padding:0 20px 60px}' +
  'h2{font-size:20px;color:#4a3b78;border-left:4px solid #764ba2;padding-left:12px;margin:34px 0 14px}' +
  'table{width:100%;border-collapse:collapse;background:#fff;box-shadow:0 1px 4px rgba(0,0,0,.08);border-radius:6px;overflow:hidden}' +
  'th,td{padding:11px 14px;text-align:left;border-bottom:1px solid #ececf1;font-size:14px;vertical-align:top}' +
  'thead th{background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:#fff;font-weight:600;letter-spacing:.3px}' +
  'tbody tr:nth-child(even){background:#faf9fc}tbody tr:hover{background:#f0ecfa}' +
  '.summary-table td:first-child{font-weight:600;width:280px;color:#4a3b78}' +
  '.status{display:inline-block;padding:3px 10px;border-radius:12px;font-size:12px;font-weight:700;letter-spacing:.4px}' +
  '.pass{background:#e3f7e8;color:#1b8a3d;border:1px solid #b6e6c4}.manual{background:#fff3d6;color:#9a6b00;border:1px solid #f3dca0}' +
  '.fail{background:#fde2e2;color:#b3261e;border:1px solid #f3b0b0}' +
  '.skip{background:#e8eaf2;color:#4a5578;border:1px solid #c8cee0}' +
  '.step-num{text-align:center;font-weight:700;color:#764ba2;width:42px}' +
  'td a{color:#667eea;text-decoration:none;font-weight:600}td a:hover{text-decoration:underline;color:#764ba2}' +
  '.note{background:#fff8e6;border:1px solid #f0d99b;border-radius:6px;padding:12px 16px;margin:18px 0;font-size:13.5px;color:#7a5b10}' +
  '.footer{text-align:center;color:#9a9aa5;font-size:12px;margin-top:40px}' +
  '.gallery figure{margin:0 0 26px;background:#fff;border:1px solid #ececf1;border-radius:6px;box-shadow:0 1px 4px rgba(0,0,0,.08);overflow:hidden}' +
  '.gallery figcaption{background:#f0ecfa;color:#4a3b78;font-weight:600;font-size:13px;padding:8px 14px;display:flex;justify-content:space-between;align-items:center}' +
  '.gallery figcaption .back{font-weight:500;font-size:12px}.gallery img{display:block;max-width:100%;height:auto}';

type Status = 'PASS' | 'FAIL' | 'SKIPPED';

interface Rec {
  title: string;
  describes: string[];
  status: Status;
  seconds: string;
  actual: string;
  errorText: string;   // raw error text of a failure ('' otherwise)
  innerSteps: string[];
  shot: string;
}

interface Group {
  module: string;      // "Billing > Doctor Share"
  note: string;
  specFile: string;
  recs: Rec[];
}

const esc = (s: string | undefined): string =>
  (s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

// eslint-disable-next-line no-control-regex
const stripAnsi = (s: string): string => s.replace(/\u001b\[[0-9;]*m/g, '');
const upperFirst = (s: string): string => (s ? s[0].toUpperCase() + s.slice(1) : s);
const oneLine = (s: string): string => stripAnsi(s).replace(/\s+/g, ' ').trim();
const clip = (s: string, n: number): string => (s.length > n ? `${s.slice(0, n)} …` : s);

/** Module path and the "what does this spec do / can it write" note, both from the spec's first /** header comment. */
function readHeader(file: string): { module: string; note: string } {
  let text = '';
  try { text = fs.readFileSync(file, 'utf8'); } catch { /* fall through with no header */ }
  const m = /\/\*\*([\s\S]*?)\*\//.exec(text);
  if (!m) return { module: '', note: '' };
  const lines = m[1].split(/\r?\n/).map((l) => l.replace(/^\s*\*\s?/, ''));
  const firstIdx = lines.findIndex((l) => l.trim());
  const first = firstIdx >= 0 ? lines[firstIdx].trim() : '';
  // "Billing > Doctor Share > Doctor Share  (#/Doctorshare)" -> "Billing > Doctor Share"
  const segs = first.replace(/\s*\(#.*$/, '').replace(/\s+[—-]\s+tests?\b.*$/i, '').split('>').map((s) => s.trim()).filter(Boolean);
  const module = segs.filter((s, i) => i === 0 || s !== segs[i - 1]).join(' > ');

  const paras: string[] = [];
  let cur: string[] = [];
  for (const l of lines.slice(firstIdx + 1)) {
    if (!l.trim()) { if (cur.length) paras.push(cur.join(' ')); cur = []; } else cur.push(l.trim());
  }
  if (cur.length) paras.push(cur.join(' '));
  const note = paras.find((p) => /read.only|nothing is saved|safety/i.test(p)) ?? '';
  return { module, note: clip(oneLine(note), 430) };
}

function screenshotOf(result: TestResult): string {
  const shot = result.attachments.find((a) => a.name === 'screenshot' && (a.contentType ?? '').startsWith('image/'));
  if (!shot) return '';
  try {
    if (shot.body) return shot.body.toString('base64');
    if (shot.path && fs.existsSync(shot.path)) return fs.readFileSync(shot.path).toString('base64');
  } catch { /* a missing screenshot must not break the report */ }
  return '';
}

class DevhisJavaStyleReporter implements Reporter {
  private rootDir = process.cwd();
  private baseUrl = '';
  private readonly groups = new Map<string, Group>();
  private readonly stepTitles = new Map<string, string[]>();   // test.id -> its top-level test.step titles

  onBegin(config: FullConfig): void {
    this.rootDir = config.rootDir;
    this.baseUrl = (config.projects[0]?.use as { baseURL?: string } | undefined)?.baseURL ?? process.env.DEVHIS_URL ?? '';
  }

  onStepEnd(test: TestCase, _result: TestResult, step: TestStep): void {
    if (step.category !== 'test.step' || step.parent) return;   // only the test's own top-level steps
    const list = this.stepTitles.get(test.id) ?? [];
    list.push(step.title);
    this.stepTitles.set(test.id, list);
  }

  onTestEnd(test: TestCase, result: TestResult): void {
    const rel = path.relative(this.rootDir, test.location.file).split(path.sep);   // ['Billing_page', 'doctorShare.spec.ts']
    const moduleFolder = rel.length > 1 ? rel[rel.length - 2].replace(/_page$/i, '') : 'Playwright';
    const pageName = upperFirst(path.basename(test.location.file).replace(/\.spec\.[tj]s$/i, ''));
    const key = `${moduleFolder}_${pageName}`;

    let group = this.groups.get(key);
    if (!group) {
      const head = readHeader(test.location.file);
      group = { module: head.module || `${moduleFolder} > ${pageName}`, note: head.note, specFile: rel.join('/'), recs: [] };
      this.groups.set(key, group);
    }

    // titlePath() = ['', project, file, ...describes, title]
    const describes = test.titlePath().slice(3, -1);
    const seconds = (result.duration / 1000).toFixed(1);
    const anns = [...test.annotations, ...(((result as unknown as { annotations?: typeof test.annotations }).annotations) ?? [])];
    const annNote = (type: RegExp): string =>
      anns.filter((a) => type.test(a.type) && a.description).map((a) => `${a.type}: ${a.description}`).filter((v, i, arr) => arr.indexOf(v) === i).join(' | ');
    const defect = annNote(/^defect$/i);

    const outcome = test.outcome();   // 'expected' | 'unexpected' | 'flaky' | 'skipped'
    let status: Status;
    let actual: string;
    let errorText = '';
    if (outcome === 'skipped') {
      status = 'SKIPPED';
      const why = annNote(/^(blocked|skip)$/i) || 'skipped';
      actual = `Skipped — ${why}`;
    } else if (outcome === 'unexpected') {
      status = 'FAIL';
      errorText = oneLine(result.error?.message ?? result.errors[0]?.message ?? result.status);
      actual = `${clip(errorText, 700)}  (after ${seconds}s)${defect ? `  ||  ${defect}` : ''}`;
    } else {
      status = 'PASS';
      actual = test.expectedStatus === 'failed'
        ? `Known defect reproduced, as the test expects — ${clip(oneLine(result.error?.message ?? ''), 300)}`
        : `As expected — passed in ${seconds}s`;
      if (defect) actual += `  ||  ${defect}`;
    }

    group.recs.push({
      title: test.title,
      describes,
      status,
      seconds,
      actual,
      errorText,
      innerSteps: this.stepTitles.get(test.id) ?? [],
      shot: screenshotOf(result),
    });
  }

  onEnd(_result: FullResult): void {
    const dir = process.env.DEVHIS_REPORT_DIR || path.resolve(__dirname, '..', '..', 'devhis-tests-share', 'test-output', 'reports');
    fs.mkdirSync(dir, { recursive: true });
    for (const [key, g] of this.groups) {
      const file = path.join(dir, `${key.replace(/[^A-Za-z0-9._-]/g, '_')}.html`);
      fs.writeFileSync(file, this.render(g), 'utf8');
      // eslint-disable-next-line no-console
      console.log(`Report: ${file}`);
    }
  }

  /** Rows of the Execution Steps table: [#, name, description, expected, actual, status, screenshotBase64]. */
  private steps(g: Group): string[][] {
    const user = process.env.DEVHIS_USER ?? '';
    const screen = g.module.split('>').pop()?.trim() ?? g.module;
    const ran = g.recs.filter((r) => r.status !== 'SKIPPED');
    // Every test logs in on its own, so "Login" is judged on all of them: it fails only when every test that ran failed on login.
    const loginFailed = ran.length > 0 && ran.every((r) => r.status === 'FAIL' && /login|DEVHIS_USER|DEVHIS_PASS/i.test(r.errorText));

    const rows: string[][] = [[
      '1', 'Login', `${user} login`,
      'Authenticated; Patient Dashboard with the navigation menu',
      loginFailed ? 'Login failed — see the first failing step' : 'Logged in (every test logs in by itself)',
      loginFailed ? 'FAIL' : 'PASS', '',
    ]];
    g.recs.forEach((r, i) => {
      const bare = r.title.replace(/^T\d+\s+/, '');
      rows.push([
        String(i + 2),
        r.title,
        `Open the ${screen} screen and run: ${bare}${r.innerSteps.length ? ` (checks: ${r.innerSteps.join(', ')})` : ''}`,
        upperFirst(bare),
        r.actual,
        r.status,
        r.shot,
      ]);
    });
    return rows;
  }

  private render(g: Group): string {
    const steps = this.steps(g);
    const count = (s: Status) => steps.filter((x) => x[5] === s).length;
    const pass = count('PASS'), skip = count('SKIPPED'), fail = count('FAIL');
    const title = g.module.split('>').map((s) => s.trim()).join(' - ');   // "Billing - Doctor Share", as the Java titles read
    const firstFail = g.recs.find((r) => r.status === 'FAIL');
    const result = firstFail
      ? `${fail} of ${steps.length} steps failed — first: ${firstFail.title}: ${clip(firstFail.errorText, 200)}`
      : `All ${steps.length - skip} steps passed${skip ? ` (${skip} skipped)` : ''}`;

    const summary: Array<[string, string]> = [['Spec file', g.specFile], ['Result', result]];
    let sum = '';
    for (const [k, v] of summary) sum += `<tr><td>${esc(k)}</td><td>${esc(v)}</td></tr>`;
    sum += `<tr><td>Total / Passed / Manual / Skipped / Failed</td><td>${steps.length} / ${pass} / 0 / ${skip} / ${fail}</td></tr>`;
    sum += `<tr><td>Overall Result</td><td><span class='status ${fail === 0 ? "pass'>PASS" : "fail'>FAIL"}</span></td></tr>`;

    let rows = '', gal = '';
    for (const s of steps) {
      const cls = s[5] === 'PASS' ? 'pass' : s[5] === 'SKIPPED' ? 'skip' : 'fail';
      const hasShot = s[6] !== '';
      rows += `<tr><td class='step-num'>${s[0]}</td><td>${esc(s[1])}</td><td>${esc(s[2])}</td><td>${esc(s[3])}</td><td>${esc(s[4])}</td>` +
        `<td><span class='status ${cls}'>${s[5]}</span></td><td>${hasShot ? `<a href='#shot_${s[0]}'>View</a>` : '&ndash;'}</td></tr>`;
      if (hasShot) {
        gal += `<figure id='shot_${s[0]}'><figcaption>Step ${s[0]}: ${esc(s[1])} <a href='#top' class='back'>back to top</a></figcaption>` +
          `<img alt='Step ${s[0]}' src='data:image/png;base64,${s[6]}'></figure>`;
      }
    }

    const noteHtml = g.note ? `<div class='note'>&#9888; ${esc(g.note)}</div>` : '';
    return `<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>` +
      `<meta name='viewport' content='width=device-width, initial-scale=1.0'><title>${esc(title)} Report</title>` +
      `<style>${CSS}</style></head><body>` +
      `<div class='header'><h1>${esc(title)} &ndash; Test Execution Report</h1>` +
      `<p><strong>Application:</strong> ${esc(this.baseUrl)}</p>` +
      `<p><strong>Module:</strong> ${esc(g.module)}</p></div>` +
      `<div class='container' id='top'>${noteHtml}` +
      `<h2>Test Summary</h2><table class='summary-table'><tbody>${sum}</tbody></table>` +
      `<h2>Execution Steps</h2><table><thead><tr><th>#</th><th>Step Name</th><th>Description</th><th>Expected</th>` +
      `<th>Actual</th><th>Status</th><th>Screenshot</th></tr></thead><tbody>${rows}</tbody></table>` +
      `<h2>Screenshots</h2><div class='gallery'>${gal}</div>` +
      `<div class='footer'>Generated by mcp_kpj (Playwright TypeScript) &middot; DevHIS</div></div></body></html>`;
  }
}

export default DevhisJavaStyleReporter;
