# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: doctorAdditionDeduction.spec.ts >> Doctor Addition / Deduction >> add, find, filter and delete an Addition entry
- Location: tests\doctorAdditionDeduction.spec.ts:307:7

# Error details

```
Error: Payable Name column shows the doctor

expect(locator).toHaveText(expected) failed

Locator:  locator('main').locator('table tbody tr').filter({ hasText: 'AUTO-TEST DAD 1790662383959' }).locator('td').nth(1)
Expected: "Demo Doctor"
Received: ""
Timeout:  10000ms

Call log:
  - Payable Name column shows the doctor locator('main').locator('table tbody tr').filter({ hasText: 'AUTO-TEST DAD 1790662383959' }).locator('td').nth(1) with timeout 10000ms
  - waiting for locator('main').locator('table tbody tr').filter({ hasText: 'AUTO-TEST DAD 1790662383959' }).locator('td').nth(1)
    23 × locator resolved to <td class="ng-binding"></td>
       - unexpected value ""

```

```yaml
- cell
```

```
Error: ALL filter includes Addition entries

expect(locator).toHaveCount(expected) failed

Locator:  locator('main').locator('table tbody tr').filter({ hasText: 'AUTO-TEST DAD 1790662383959' })
Expected: 1
Received: 0
Timeout:  10000ms

Call log:
  - ALL filter includes Addition entries locator('main').locator('table tbody tr').filter({ hasText: 'AUTO-TEST DAD 1790662383959' }) with timeout 10000ms
  - waiting for locator('main').locator('table tbody tr').filter({ hasText: 'AUTO-TEST DAD 1790662383959' })
    23 × locator resolved to 0 elements
       - unexpected value "0"

```

# Test source

```ts
  252 |       await expect(screen.dateInput('date')).toHaveValue(today);
  253 |       await expect(screen.entryAddition()).toBeChecked();
  254 |       await expect(screen.entryDeduction()).not.toBeChecked();
  255 |       await expect(screen.chosen('departmentid')).toHaveText('--Select--');
  256 |       await expect(screen.chosen('doctorid')).toHaveText('--Select--');
  257 |       await expect(screen.amount()).toBeEmpty();
  258 |       await expect(screen.remark()).toBeEmpty();
  259 |       await expect(screen.saveBtn()).toBeEnabled();
  260 |     });
  261 |   });
  262 | 
  263 |   test('Save validates required fields in order', async ({ page }) => {
  264 |     await test.step('Payable Type required', async () => {
  265 |       await screen.expectToast(() => screen.saveBtn().click(), 'Please Select Doctor Type');
  266 |     });
  267 |     await test.step('Payable Type loads the Payable Name list', async () => {
  268 |       await screen.selectPayableType(PAYABLE_TYPE);
  269 |       const names = page.locator("select[ng-model='DoctorAdditionDeduction.doctorid'] option");
  270 |       await expect.poll(() => names.count()).toBeGreaterThan(1);
  271 |       await expect(names.filter({ hasText: PAYABLE_NAME })).toHaveCount(1);
  272 |     });
  273 |     await test.step('Payable Name required', async () => {
  274 |       await screen.expectToast(() => screen.saveBtn().click(), 'Please Select Doctor!');
  275 |     });
  276 |     await test.step('Amount required', async () => {
  277 |       await screen.pickSelect2('doctorid', PAYABLE_NAME);
  278 |       await screen.expectToast(() => screen.saveBtn().click(), 'Please Enter Amount');
  279 |     });
  280 |     await test.step('Amount must be non-zero', async () => {
  281 |       await screen.amount().fill('0');
  282 |       await screen.expectToast(() => screen.saveBtn().click(), 'Please Enter Amount');
  283 |     });
  284 |   });
  285 | 
  286 |   test('Amount accepts numbers only, max 10 characters', async () => {
  287 |     await screen.amount().pressSequentially('abc');
  288 |     await expect(screen.amount()).toBeEmpty();
  289 |     await screen.amount().fill('12345678901234');
  290 |     await expect(screen.amount()).toHaveValue('1234567890');
  291 |   });
  292 | 
  293 |   test('invalid entry Date is rejected on Save', async () => {
  294 |     await screen.selectPayableType(PAYABLE_TYPE);
  295 |     await screen.pickSelect2('doctorid', PAYABLE_NAME);
  296 |     await screen.amount().fill('1');
  297 |     await screen.setDate('date', '45/13/2026');
  298 |     // the app reuses its "Invalid From Date" message for the entry Date
  299 |     await screen.expectToast(() => screen.saveBtn().click(), 'Invalid From Date');
  300 |   });
  301 | 
  302 |   test('Search rejects an invalid From Date', async () => {
  303 |     await screen.setDate('fromdate', '99/99/2026');
  304 |     await screen.expectToast(() => screen.searchBtn().click(), 'Invalid From Date');
  305 |   });
  306 | 
  307 |   test('add, find, filter and delete an Addition entry', async ({ page }) => {
  308 |     const remark = `AUTO-TEST DAD ${Date.now()}`;
  309 |     let created = false;
  310 |     try {
  311 |       await test.step('save a valid Addition entry', async () => {
  312 |         await screen.entryAddition().check();
  313 |         await screen.selectPayableType(PAYABLE_TYPE);
  314 |         await screen.pickSelect2('doctorid', PAYABLE_NAME);
  315 |         await screen.amount().fill('1');
  316 |         await screen.remark().fill(remark);
  317 |         await screen.clearToasts();
  318 |         const [resp] = await Promise.all([
  319 |           page.waitForResponse(r => r.url().includes('/api/DoctorAddition-Deduction/IUD')),
  320 |           screen.saveBtn().click(),
  321 |         ]);
  322 |         created = resp.ok() && (await resp.text()).includes('"ResultStatus":1');
  323 |         await expect(screen.toast()).toHaveText(/^\s*Doctor Addition deduction Details Added Successfully/);
  324 |         expect(created, 'server confirmed the insert').toBe(true);
  325 |       });
  326 | 
  327 |       await test.step('form is reset after save', async () => {
  328 |         await expect(screen.amount()).toBeEmpty();
  329 |         await expect(screen.remark()).toBeEmpty();
  330 |         await expect(screen.chosen('departmentid')).toHaveText('--Select--');
  331 |         await expect(screen.chosen('doctorid')).toHaveText('--Select--');
  332 |       });
  333 | 
  334 |       await test.step('Search (Addition) lists the entry with its values', async () => {
  335 |         await screen.searchToday('Addition');
  336 |         const row = screen.rowWith(remark);
  337 |         await expect(row).toHaveCount(1);
  338 |         await expect(row.locator('td')).toHaveText([
  339 |           PAYABLE_TYPE, /.*/, today, /^1(\.00)?$/, /^addition$/i, remark, /Delete/,
  340 |         ]);
  341 |         // soft: a wrong value is reported as a failure but the flow (and the clean-up) continues
  342 |         await expect.soft(row.locator('td').nth(1), 'Payable Name column shows the doctor').toHaveText(PAYABLE_NAME);
  343 |       });
  344 | 
  345 |       await test.step('Search (Deduction) does not list it', async () => {
  346 |         await screen.searchToday('Deduction');
  347 |         await expect(screen.rowWith(remark)).toHaveCount(0);
  348 |       });
  349 | 
  350 |       await test.step('Search (ALL) lists it', async () => {
  351 |         await screen.searchToday('ALL');
> 352 |         await expect.soft(screen.rowWith(remark), 'ALL filter includes Addition entries').toHaveCount(1);
      |                                                                                           ^ Error: ALL filter includes Addition entries
  353 |       });
  354 |     } finally {
  355 |       if (created) {
  356 |         await test.step('Delete removes the entry', async () => {
  357 |           await screen.clearToasts();
  358 |           await screen.searchToday('Addition');
  359 |           await expect(screen.rowWith(remark)).toHaveCount(1);
  360 |           await Promise.all([
  361 |             page.waitForResponse(r => r.url().includes('/api/DoctorAddition-Deduction/IUD')),
  362 |             screen.rowWith(remark).getByRole('button', { name: 'Delete' }).click(),
  363 |           ]);
  364 |           await expect(screen.toast()).toHaveText(/^\s*Deleted successfully/);
  365 |           await expect(screen.rowWith(remark)).toHaveCount(0);
  366 |         });
  367 |       }
  368 |     }
  369 |   });
  370 | });
  371 | 
```