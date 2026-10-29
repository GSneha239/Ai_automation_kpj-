---
name: self-healing-agent
description: Menu/router for two logically separate agents in this project - the Failure Analysis Agent (analyzes and categorizes test failures into a human-readable report; NEVER modifies anything) and the Self-Healing Agent (proposes and, only with explicit human approval, applies a fix). Use when the user says "@agent" (bare or with an argument - "@agent report", "@agent HospitalLocation", "@agent all", "@agent heal HospitalLocation", "@agent heal all"), "analyze test failures", "generate a failure report", "heal <TestName>", "run full self healing", "show self healing agents", or "show the latest failure analysis / healing summary/report".
---

# Self-Healing Agent (launcher)

> **Automatic post-run analysis (additive).** Supported test runs now start ONE `PostRunCoordinator` analysis of their own fresh results when they finish (Failure Analysis -> eligibility -> `propose`, then STOP). It never applies anything, never replaces the explicit `@agent heal` flow below, and its LLM fallback is off unless `DEVHIS_POSTRUN_LLM=on`. Kill switch: `DEVHIS_POSTRUN=off` / `-Ddevhis.postrun=off`. A pending proposal it prints is approved exactly like one from `@agent heal` (the `apply-pending` step below, bound to the printed hash). Coverage and the unsupported entry points: `devhis-tests-share/POST-RUN-HEALING.txt`.

This skill is the `@agent` entry point for THREE Java entry points in `com.kpj.heal`:

1. **Failure Analysis Agent** (`FailureAnalysisAgent`) — detect → analyze → categorize into a business-facing category → recommend an owner → **generate a report**. Never touches a source file, never proposes an applied patch beyond read-only analysis, never asks for approval. This is the DEFAULT, safe action for a bare test name or "all".
2. **Self-Healing Agent** (`SelfHealingAgent`) — the existing, unchanged healing pipeline: analyze → classify → root cause → candidate → confidence/risk → show before/after → **human approval** → backup → patch → compile → regression → keep/revert. Its `propose`/`apply-pending` split is still the ONLY apply mechanism.
3. **Healing Workflow Orchestrator** (`HealingWorkflowOrchestrator`) — chains, unchanged: test execution → failure detection → `FailureAnalysisAgent` → the eligibility gate → `SelfHealingAgent propose`. This is what `@agent heal <TestName>` now runs — ONE command instead of you manually sequencing the other two. It NEVER calls `apply-pending` itself.

**The primary safety rule for this whole skill: a test failing, or a report being generated, must NEVER by itself trigger healing.** Healing only ever happens when the user types the word "heal" (or its natural-language equivalent) explicitly.

**Never invent a new Java entry point or duplicate any agent's logic.** Every action below is a thin wrapper around the existing `com.kpj.heal.FailureAnalysisAgent` / `com.kpj.heal.SelfHealingAgent` / `com.kpj.heal.HealingWorkflowOrchestrator` classes. Translate the user's request to the right command deterministically (see the routing tables below) — do not call an LLM or any external service just to parse a simple instruction. For a `heal` request, always go through `HealingWorkflowOrchestrator` — never call `FailureAnalysisAgent` or `SelfHealingAgent propose` directly for it, since that would duplicate or bypass the orchestrator's own eligibility gate.

**A note on shells.** Commands below are shown in PowerShell form (`--%`) for the user's own terminal. If YOU run a safe command yourself via your own `Bash` tool, that's a POSIX shell (Git Bash) — drop `--%` and pass args plainly, e.g. `mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.FailureAnalysisAgent -Dexec.args="--all"`.

## The `@agent` command grammar

| User types | Action |
|---|---|
| `@agent` | Show the menu below + run Failure Analysis discovery (`--list` via `SelfHealingAgent`) and show the discovered agents |
| `@agent report` | **Failure Analysis Agent, `--all`** — analyze every currently-failing test, generate reports |
| `@agent <TestName>` (e.g. `@agent HospitalLocation`) | **Failure Analysis Agent, single test** — analyze and report on that one test only |
| `@agent all` | **Failure Analysis Agent, `--all`** — same as `@agent report` |
| `@agent heal <TestName>` | **`HealingWorkflowOrchestrator`, single test** — runs the test fresh, then Failure Analysis, then (only if eligible) Self-Healing propose; stops for chat approval before anything is applied |
| `@agent heal all` | Same complete workflow, run once per currently-failing test (shared attempt budget); stops for chat approval before each apply |
| `@agent dry-run` | (advanced/legacy) **Self-Healing Agent, `--all --dry-run`** — a technical preview of what would be proposed, without the business-facing categorization report |

If `<TestName>` doesn't match any discovered agent, follow "Unknown agent" under Error handling below.

**Important:** `@agent <TestName>` and `@agent all` are analysis-only — this is a deliberate change from a simpler earlier version of this skill, precisely so that naming a test or asking for "all" can never accidentally trigger a real healing attempt. Only the explicit word "heal" does that.

## The menu

When the user asks for `@agent` (bare), `/self-healing-agent`, "show self healing agents", or an equivalent generic request, present this table:

| # | Agent | Responsibility | Modifies anything? |
|---|---|---|---|
| 1 | **Failure Analysis — report** | Analyze every currently-failing test, categorize, recommend an owner, generate a report | No |
| 2 | **Failure Analysis — single test** | Same, scoped to one named test | No |
| 3 | **Self-Healing — heal `<TestName>`** | Run the test fresh → Failure Analysis → eligibility gate → propose a fix; apply ONLY with explicit chat approval | Only after approval |
| 4 | **Self-Healing — heal all** | Same, across every currently-failing test (shared attempt budget) | Only after approval |
| 5 | **Dry Run** (advanced) | Technical preview of proposed patches, `--all --dry-run` — never applies | No |
| 6 | **Reports / Summary** | Show the latest failure-analysis or healing summary/index, or open a specific report | No |

Then run discovery (`--list`, read-only, reuses `TestClassResolver`) and list the actual agents (test classes) found — never a hardcoded/stale list.

## `@agent` discovery — list every available test

```powershell
mvn --% -q compile exec:java -Dexec.mainClass=com.kpj.heal.SelfHealingAgent -Dexec.args="--list"
```
Prints every discovered test class + testId + whether it currently has a recorded failure. Relay this as the numbered "Available Self-Healing Agents" list.

## 1 & 2. Failure Analysis (`@agent report` / `@agent all` / `@agent <TestName>`) — safe, never requires approval, never writes source

```powershell
# Whole project
mvn --% -q compile exec:java -Dexec.mainClass=com.kpj.heal.FailureAnalysisAgent -Dexec.args="--all"

# One test (discover the FQCN the same way as "Individual test resolution" below)
mvn --% -q compile exec:java -Dexec.mainClass=com.kpj.heal.FailureAnalysisAgent -Dexec.args="com.kpj.tests.<package>.<ClassName>"
```

Always safe to run directly in the foreground, or delegate to the `self-heal-failure-analysis` subagent (background).

After it finishes, relay directly in chat (don't make the user open the HTML first):
- How many tests/steps were analyzed
- The category breakdown (Application Bug / Missing Test Data / Dropdown/UI / Locator/Automation / Environment / Authentication / Unknown)
- For each failure: **Category**, **Recommended Owner**, **Self-Healing Eligible: YES/NO** (+ the one-line reason)
- The exact individual + summary + index report paths it printed

If any failure shows `Self-Healing Eligible: YES`, mention that healing it is available via `@agent heal <TestName>` — but do NOT start healing yourself just because you see this.

## 3 & 4. Self-Healing (`@agent heal <TestName>` / `@agent heal all`) — REQUIRE human approval, entirely in chat

**`@agent heal <TestName>` now runs the COMPLETE workflow — test execution → failure detection → Failure Analysis → eligibility gate → Self-Healing propose — in ONE command you run yourself.** It always starts with a fresh run of the test (unless the user has explicitly said to skip that — see `--skip-run` below). **Never tell the user to open a terminal, paste a Maven command, or wait at a `y/N` prompt** — you run the command yourself via your own `Bash` tool; only the human decision after it happens in chat. Never call `FailureAnalysisAgent` or `SelfHealingAgent propose` directly for a `heal` request — `HealingWorkflowOrchestrator` is the ONE entry point that chains them, and it enforces the eligibility gate itself.

**Step 1 — run the orchestrator (you run this yourself, safe, no approval yet):**
```
mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.HealingWorkflowOrchestrator -Dexec.args="com.kpj.tests.<package>.<ClassName>"
```
This: runs the test fresh (the identical, unchanged Surefire invocation); detects whether any FAILED step was recorded; if so, automatically runs `FailureAnalysisAgent` (the exact same report `@agent report`/`@agent <TestName>` produces); reads that report's eligibility for every failed step; and — only if at least one step is `Self-Healing Eligible: YES` — automatically runs `SelfHealingAgent propose` for it. It NEVER calls `apply-pending` and never asks for or receives an approval decision itself. Every existing safety gate (loop protection, `ProtectedResourcePolicy`, `PatchQualityGuard`, `SemanticValidator`, confidence/risk scoring) still runs exactly as it always has, unchanged, inside the agents the orchestrator calls — this command doesn't skip or shortcut any of them.

If the test was **already run this session with fresh, representative data**, and the user has explicitly said re-running it would be redundant, add `--skip-run` to `-Dexec.args` (e.g. `"...ClassName --skip-run"`) to continue from the most recently recorded run instead. Only do this on an explicit request — the default is always a fresh run.

**Parse the orchestrator's terminal output — it always ends in exactly one of these markers:**

| Marker | Meaning | What you do |
|---|---|---|
| `WORKFLOW_COMPLETE: TEST_RUN_FAILED` | The fresh Maven run exited non-zero (or could not be launched): the build/run itself broke, so there is no result from this run | Report the Maven problem shown above the marker. Nothing was analyzed — never fall back to older results. Do **not** retry with `--skip-run` to get around it. |
| `WORKFLOW_COMPLETE: NO_FRESH_RESULTS` | The run exited cleanly but wrote no new result under this command's output folder (the tests wrote elsewhere — `-Ddevhis.outdir` / `DEVHIS_OUTDIR` ≠ this output folder) | Tell the user the two output folders must be the same; nothing was analyzed. Do **not** analyze the older results in that folder. |
| `WORKFLOW_COMPLETE: NO_FAILURES` | Test ran and passed — no FAILED step was recorded | Report that the test passed; the workflow stops here. Do **not** invoke Failure Analysis or Self-Healing yourself — there's nothing for either to analyze. |
| `WORKFLOW_COMPLETE: FAILURE_ANALYSIS_ONLY` | Failure Analysis ran; no failed step is `Self-Healing Eligible: YES` | Report, for each failed step: the step name, category, recommended owner, eligibility (NO) + the one-line reason, any Dropdown/Locator diagnostic evidence in the report, and the report paths printed. State plainly why healing did not proceed. Do **not** invoke `SelfHealingAgent` yourself — the orchestrator already determined it isn't eligible. |
| `PENDING_HEAL_FILE: <path>` | Eligible, and `SelfHealingAgent propose` produced a candidate that already passed every pre-approval safety gate | Relay the FULL proposal in chat using the response checklist below, then ask directly whether to apply it, and wait. |
| `NO_PENDING_PROPOSAL` | Eligible in principle, but no sufficiently supported candidate exists (or none survived a safety gate) | Report "no sufficiently supported automatic fix found — manual investigation required." Never retry by relaxing a gate; never invent a candidate yourself. |

**Step 2 — the human answers in chat, referring to the specific proposal you just showed.** Only an unambiguous, current, affirmative reply that clearly refers to applying THAT proposal counts as approval — "yes", "y", "approve", "apply it", "apply this change", "go ahead" and similarly clear affirmatives, said in direct response to the proposal you just displayed. Vague, low-context replies — "ok", "fine", "continue", "do it", or even a bare "yes" that doesn't clearly and unambiguously follow from the proposal you just showed — are NOT approval when there's any ambiguity about what they're agreeing to; ask a clarifying question instead of guessing. A question, "not sure", a different request, or silence is also NOT approval — ask again or treat it as declined; never infer consent, never carry an earlier approval forward to a new proposal, never batch multiple candidates under one approval. This is the same strictness the old `y`/`Y`-only terminal prompt had — the channel changed, the standard didn't.

**Step 3 — apply-pending (you run this yourself, only after a clear, unambiguous chat approval) — the existing, unchanged mechanism, and the ONLY apply path:**
```
mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.SelfHealingAgent -Dexec.args="apply-pending <the PENDING_HEAL_FILE path> approved <the PROPOSAL_HASH printed by propose>"
```
**The approval is bound to the proposal's hash.** `propose` prints `PROPOSAL_HASH: <64 hex>` (a SHA-256 over every field of the proposal plus the exact source file and failure evidence it was made from). Show the human the first 12 characters with the proposal, and pass the FULL hash of THAT proposal (copied from THAT proposal's own `propose` output, never from an earlier one) as the word after `approved`. `apply-pending` refuses — writing nothing, taking no backup — if the hash is missing or belongs to a different proposal, if the pending file was edited, if the target source file or the failure evidence has changed since the proposal, or if the protected-resource policy, quality guard, semantic validation, loop protection or attempt budget no longer permit it. A refusal is NOT a failure to retry around: report the printed reason, and if the proposal is stale, re-run the test and request a new proposal. Never fabricate or reuse a hash.
or, if the user declined (no hash needed):
```
mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.SelfHealingAgent -Dexec.args="apply-pending <the PENDING_HEAL_FILE path> declined"
```
`approved` runs the exact same backup → apply → compile → re-run → verify → keep-or-revert pipeline the old interactive prompt used, then regenerates that test's individual/healing/summary/index reports and prints their paths. `declined` writes nothing at all — the pending file is consumed either way (deleted after resolution) so it can never be re-applied later by accident. Do not implement or invoke any other apply mechanism.

**Reading the `apply-pending approved` outcome.** It ends with `SelfHealingAgent (apply-pending): final result = <status>`. Report exactly that status; never describe an outcome the output did not print:

| Final result | Meaning | What you do |
|---|---|---|
| `KEPT` | The patched test was re-run and a fresh PASS was recorded for the target step, with no new downstream failure. The change stays. | Report KEPT, the file changed, and the report paths printed. |
| `REVERTED` | Compile failed, the re-run still failed, a new failure appeared, or there was no positive PASS evidence — and the original file was restored AND verified byte-for-byte. | Report the printed reason, that the original file was restored, and that manual investigation is needed. Do not propose the same fix again. |
| `RESTORE_FAILED` | The rollback could NOT be verified: the source file **may still contain the patch**. | Say so plainly, as the first line. Relay the printed `RESTORE_FAILED` line and the backup path from it. Do not edit the file yourself and do not start another heal for it. Tell the user to restore it from that backup, then run `SelfHealingAgent verify-restore <sourceFile> [outDir]` (read-only; it prints `RESTORE_STATUS: RECOVERED`, `UNRESOLVED` or `NO_RECORD`) and relay its result. Healing for that file may continue only after `RECOVERED`. |
| `NOT_APPLIED` | Nothing was written: the human declined, or the apply-time checks refused it (the output then also prints `REFUSED - <reason>` and "nothing was changed and no backup was taken"). | Report the printed reason; the source file is untouched. For a refusal the step is not exhausted, so a fresh proposal can be requested once the cause (changed file, stale evidence) is dealt with. |
| `REJECTED_VERIFICATION_FAILED` | Only after `apply-pending ... rejected [reason]`: the human said this exact candidate must never be offered again. Nothing was written. | Report that it was recorded as rejected and will not be re-offered; a genuinely new candidate may still be proposed later. |

A `REFUSED` message can also appear on its own, before any final result, when the hash is missing or wrong or the pending file or source changed since `propose`; treat that exactly as a refusal (nothing changed), never as a failure to retry around.

After any of these, the individual / healing / summary / index reports are regenerated, and a **unified report** for the test is written to `<outDir>/unified-report/` (the output prints `Unified report: <path>`; it only reads existing artifacts and never overwrites an earlier report). Always give the user both the Failure Analysis report paths and the unified report path.

**If the test has more failing steps**, repeat from Step 1 for that same test (using `--skip-run` since it was just run and nothing about it has changed) — the attempt budget and loop-protection persist across these calls (in a small per-test session file under `test-output/self-healing/session/`), so they still can't be bypassed by asking again.

**`@agent heal all`** — ONE native command, not a manual loop:
```
mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.HealingWorkflowOrchestrator -Dexec.args="--all"
```
This discovers every currently-failing testId (`FailureScanner.listFailingTestIds()` — the exact same discovery `SelfHealingAgent --all`/`FailureAnalysisAgent --all` already use), resolves each to a class (`TestClassResolver`, same as those), then runs the identical Step 1 single-test workflow above for each test in turn — same fresh-run default, same `--skip-run` option (add it after `--all`, e.g. `"--all --skip-run"`), same eligibility gate, same propose-only boundary, per test. It never calls `apply-pending`. It ends with a per-test summary line (`<FQCN> -> NO_FAILURES / FAILURE_ANALYSIS_ONLY / PENDING_PROPOSAL / NO_PENDING_PROPOSAL / RESOLUTION_FAILED`) and a final `WORKFLOW_COMPLETE: ALL_DONE` marker.

Relay that summary in chat, then work through any `PENDING_PROPOSAL` tests one at a time using the SAME Step 2/3 approval flow above for each — never batch multiple proposals into one chat question; ask once per candidate, wait for that answer, run its `apply-pending` (if approved) before moving to the next pending one.

### Response checklist for a single "heal" step

1. Test selected, 2. Test ID, 3. Confirmation the test was run fresh (or that `--skip-run` was used, and why), 4. Failed step(s) found, 5. Failure Analysis category + recommended owner, 6. Self-Healing Eligible (YES/NO) + the reason, 7. **If eligible and a proposal was generated:** affected file, old snippet, new snippet, root cause, evidence, confidence, adaptive confidence, risk, semantic validation result + semantic justification, the `PENDING_HEAL_FILE` path, 8. **Ask for approval in chat, referring to that exact proposal, then wait** — this replaces the old terminal hand-off entirely, 9. Verification result (compile/re-run/regression), 10. Final result (KEPT/REVERTED/NOT_APPLIED), 11. Report location (individual + healing + summary; if kept, the resolution is now visible in the NEXT Failure Analysis report for that test too).

### Individual test resolution

Never require a fully-qualified class name unless the user already gave one (contains dots, starts with `com.kpj.tests`) or explicitly asks for "advanced mode". Otherwise: prefer `--list` (the authoritative, always-current source) to match the given name to a discovered simple name; fall back to `Grep`/`Glob` under `src/test/java/com/kpj/tests` only if `--list` hasn't been run yet this conversation.

## 5. Dry Run (advanced/legacy)

```powershell
mvn --% -q compile exec:java -Dexec.mainClass=com.kpj.heal.SelfHealingAgent -Dexec.args="--all --dry-run"
```
State explicitly: `DRY RUN — no source files will be modified, no patches applied, no approval prompt shown.` This is a lower-level, technical preview (proposed diffs/confidence) — prefer Failure Analysis for the business-facing categorized report; offer this only if the user specifically wants the old-style preview.

## 6. Reports / Summary

Two independent report trees, never confuse them:
- **Failure analysis** (`test-output/failure-analysis/`): `reports/<TestName>_<timestamp>/<TestName>_FailureAnalysisReport.html`, `summary/FailureAnalysisOverallSummary_<timestamp>.html`, `index/index_<timestamp>.html`.
- **Self-healing** (`test-output/self-healing/`): `reports/<TestName>_<timestamp>/<TestName>_SelfHealingReport.html`, `summary/SelfHealingOverallSummary_<timestamp>.html`, `index/index_<timestamp>.html`, and `healing/<TestName>_<timestamp>/healing-report.html` (only for a test that reached a real approval decision).

Find the latest of any of these by listing the directory and sorting by modified time (not by parsing the filename timestamp). Delegate either to the matching subagent (`self-heal-failure-analysis` / `self-heal-report`) for background/delegated use.

### Opening a report in the browser
```powershell
Start-Process "test-output/failure-analysis/reports/<TestName>_<timestamp>/<TestName>_FailureAnalysisReport.html"
```

## Natural-language routing

| User says (examples) | Route to |
|---|---|
| "Analyze current/latest test failures", "generate a failure report", "show me what's broken and who should fix it" | **Failure Analysis, `--all`** |
| "Analyze HospitalLocation", "why is RegistrationTest failing", "report on X" | **Failure Analysis, single test** |
| "Heal HospitalLocation", "run HospitalLocation healing", "fix RegistrationTest" | **Self-Healing, single test** (`HealingWorkflowOrchestrator`, fresh run by default) |
| "Heal all", "run full self healing", "heal every eligible failure" | **Self-Healing, all currently-failing tests**, one at a time |
| "Preview/dry run self healing" | **Dry Run** (advanced) |
| "Show self healing agents", "show agents", "@agent" | **`@agent` discovery** |
| "Show latest failure analysis / healing summary/report/index" | **Reports / Summary** |

If ambiguous (e.g. just "run healing" with no test named and no "heal all"), ask which they want rather than guessing — a bare name now means analysis, so don't silently treat it as if they'd said "heal".

## Error handling

Never let a Java stack trace or raw Maven failure reach the user as the whole answer.

| Situation | What to say |
|---|---|
| **Unknown agent name** | `Agent "<name>" was not found.` then run `--list` and show discovered agents. |
| **No failure data for a test** | No recorded FAILED step under `test-output/aggregate-data` — it needs to run at least once first (or is currently passing). |
| **Compilation failure** | Report that `mvn compile`/`test-compile` failed and show the first real error line(s) — don't analyze or heal on top of a broken build. |
| **Category: Authentication/Permission or Network/Infrastructure Issue** | Explain why (login/cert/timeout/network/db-shaped) and that it's routed to Admin/Environment or DevOps — never auto-healed. |
| **Category: Missing Test Data / Test Data Issue / Application Bug** | Explain the recommended owner (Manual Tester/QA or Developer) and that `Self-Healing Eligible` is NO by design — no automation code change should be attempted. |
| **No healing candidate found (during a real `heal` run)** | Neither the deterministic rules nor (if enabled) the LLM fallback found a confident fix — needs human triage. |
| **Patch rejected / regression detected / patch reverted** | State the reason and that the backup was restored; nothing was left half-applied. |
| **`RESTORE_FAILED` after a rollback** | The file may still contain the patch — see "Reading the `apply-pending approved` outcome" above. Give the backup path and `verify-restore`; never edit the file or heal it again until `RESTORE_STATUS: RECOVERED`. |
| **A test run was killed / interrupted** | There is no final PASS/FAIL result and nothing may be analysed or healed from it. Inspect the folder read-only with `mvn -q compile exec:java -Dexec.mainClass=com.kpj.heal.PostRunCoordinator -Dexec.args="--recover <outDir>"` and report its `RECOVER_COMPLETE:` status (`INTERRUPTED_NO_RESULTS`, `INTERRUPTED_PARTIAL_RESULTS`, `UNANALYSED_COMPLETED_RUN`, `ALREADY_ANALYSED`, `NO_RUN_RECORD`). Never present in-flight failure files as a test result. |
| **A `VERDICT:` line appears under a failed step** | Failure Analysis cross-checked what the script looks for against what the live page showed. Relay the verdict exactly and keep its wording ("likely", never certain): `SCRIPT LIKELY OUT OF DATE` (a similar control exists under a new label — the script needs a person-confirmed update), `APPLICATION OR STATE LIKELY` (the control is not on the page at all — route to development / check the account or record state, a script edit will not help), or `SCRIPT LOCATOR OK` (the script found its target; look at what happened after). None of these is Self-Healing Eligible; never propose or apply a change from a verdict alone. |
| **Attempt budget exhausted** | State the configured `maxAttempts`, shared across the whole run. |

## Safety (unchanged from the core agent — never override these)

- A failing test or a generated failure-analysis report NEVER by itself triggers healing — only an explicit "heal" request does.
- Only an unambiguous, current, affirmative reply IN CHAT approves a real patch — never type/pass approval for the user, never assume consent from an earlier approval or an earlier proposal, never batch multiple candidates under one approval.
- `PatchApplier` is still the only writer, for both the old stdin-driven overload and the new chat-driven one (`confirmAndApply(..., boolean approved)`) — the diff-check/backup/write logic is identical either way; only how the decision arrives differs.
- `BackupManager` creates and verifies a backup before every write; `ProtectedResourcePolicy` and `PatchQualityGuard` are unconditionally still enforced; the attempt budget and loop-protection (`HealingSession`) persist across `propose`/`apply-pending` calls via a small per-test session file, so repeating a request can't quietly reset them.
- `propose`, `--dry-run`, and the Failure Analysis Agent all never prompt, never back up, never write, never apply.
- Never modify credentials, `.env`, `LoginPage.java`, `DevHisBase.java`, authentication-related resources, or any file outside the one `CodeLocator`-approved Page Object.
- Never run `git commit`/`git push`/`git reset`/`git checkout` on the user's behalf.
- `Fixed By` / `Fixing Role` in a failure-analysis report are never invented — they read "Pending" until `SelfHealingAgent` itself records a real, approved, verified fix.

## Subagents

- `self-heal-failure-analysis` — runs the Failure Analysis Agent (`--all` or a named test) and reports back categories, owners, eligibility, and report paths. Always safe to run unattended.
- `self-heal-dry-run` — runs Self-Healing's `--all --dry-run` technical preview. Always safe to run unattended.
- `self-heal-report` — reads existing failure-analysis / self-healing reports and answers questions about past runs. Read-only.

`@agent heal <TestName>` / `@agent heal all` are **not** offered as subagents — even though `HealingWorkflowOrchestrator` (and `apply-pending`) need no stdin, the human approval step still has to happen as a genuine back-and-forth in THIS chat (ask, then wait for the next message), which only the main session can do; a subagent runs to completion and reports back once, with no way to pause mid-task for a reply.
