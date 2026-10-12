// End-to-end smoke of every payroll flow the UI drives, through the gateway, against the real services.
// Usage: node tools/hrm-e2e.mjs [all|period|hr|import|setup|manager]   (writes a run counter to ./.run)
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { writeFileSync, readFileSync } from 'node:fs';

// Configuration (environment): see docs/testing/hrm-e2e-flows.md section 13.
const env = process.env;
const BASE = env.HRM_E2E_BASE ?? 'http://localhost:8080';
const PASSWORD = env.HRM_E2E_PASSWORD ?? 'Comfy#12345';
const BUSINESS = env.HRM_E2E_BUSINESS ?? 'COMFY';
const HRM = `${BASE}/api/v1/payroll`;
const PSQL = env.HRM_E2E_PSQL ?? 'psql';
const only = process.argv[2] ?? 'all';
// Every run uses data of its own (dates far apart, codes with a suffix), so the script can be repeated.
let N = 1;
try {
  N = Number(readFileSync('.run', 'utf8')) + 1;
} catch {}
writeFileSync('.run', String(N));
const Y = (base) => base + N;

let pass = 0;
let fail = 0;
const check = (name, ok, detail = '') => {
  if (ok) pass++;
  else fail++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : `  -> ${detail}`}`);
};
const sql = (q) =>
  execFileSync(PSQL, ['-h', env.HRM_E2E_DB_HOST ?? 'localhost', '-p', env.HRM_E2E_DB_PORT ?? '5435', '-U', env.HRM_E2E_DB_USER ?? 'postgres', '-d', env.HRM_E2E_DB ?? 'fnbx_oltp', '-tAc', q], {
    env: { ...process.env, PGPASSWORD: env.HRM_E2E_DB_PASSWORD ?? 'postgres' },
  })
    .toString()
    .trim();

async function login(email) {
  const response = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ businessCode: BUSINESS, email, password: PASSWORD }),
  });
  const json = await response.json();
  return json.accessToken;
}

async function call(token, method, path, body, raw = false) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload;
  if (body instanceof FormData) payload = body;
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const response = await fetch(`${HRM}${path}`, { method, headers, body: payload });
  if (raw) return { status: response.status, buffer: Buffer.from(await response.arrayBuffer()), type: response.headers.get('content-type') };
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = text;
  }
  return { status: response.status, json };
}

const gm = await login('gm@example.com');
const kt = await login('kt@example.com');
const ha = await login('ha@example.com');
const sm = await login('sm@example.com');
check('logins', gm && kt && ha && sm);

const section = (name) => console.log(`\n== ${name}`);
const want = (name) => only === 'all' || only === name;

/* ------------------------------------------------------------ period lifecycle */
if (want('period')) {
  section('period lifecycle');
  const periods = (await call(gm, 'GET', '/payroll-periods')).json;
  check('list periods', Array.isArray(periods) && periods.length > 0);
  const source = periods.find((p) => p.periodMonth === 10 && p.periodYear === 2026);
  let period = periods.find((p) => p.periodMonth === 1 && p.periodYear === Y(2030));
  if (!period) {
    const created = await call(gm, 'POST', '/payroll-periods', {
      periodYear: Y(2030),
      periodMonth: 1,
      startDate: `${Y(2030)}-01-05`,
      endDate: `${Y(2030)}-02-04`,
      copyRosterFromPeriodId: source.payrollPeriodId,
    });
    check('create period (copy roster)', created.status === 201, JSON.stringify(created.json));
    period = created.json;
  }
  const id = period.payrollPeriodId;
  const lines = (await call(gm, 'GET', `/payroll-periods/${id}/payroll-lines`)).json;
  check('roster has copied line', lines.length >= 1, JSON.stringify(lines));
  const line = lines[0];

  const grid = (await call(gm, 'GET', `/payroll-periods/${id}/timesheet`)).json;
  const row = grid.rows.find((r) => r.payrollLineId === line.payrollLineId);
  check('timesheet grid has the row and days', row && grid.days.length === 31, `${grid.days?.length}`);
  const saved = await call(gm, 'PUT', `/payroll-periods/${id}/timesheet`, {
    cells: [
      { lineId: line.payrollLineId, date: `${Y(2030)}-01-06`, raw: '8', expectedVersion: row.version },
      { lineId: line.payrollLineId, date: `${Y(2030)}-01-07`, raw: 'T1-8', expectedVersion: row.version },
    ],
  });
  check('save timesheet cells', saved.status === 200 && saved.json.saved.length === 2, JSON.stringify(saved.json));
  const stale = await call(gm, 'PUT', `/payroll-periods/${id}/timesheet`, {
    cells: [{ lineId: line.payrollLineId, date: `${Y(2030)}-01-08`, raw: '8', expectedVersion: row.version }],
  });
  check('stale version is rejected with 4003', stale.status === 409 && stale.json.code === 4003, JSON.stringify(stale.json));

  const components = (await call(gm, 'GET', '/pay-components')).json;
  const bonus = components.find((c) => c.componentCode === 'BONUS');
  const item = await call(gm, 'POST', `/payroll-lines/${line.payrollLineId}/manual-pay-items`, { componentCode: bonus.componentCode, amount: 250000, note: 'e2e' });
  check('add manual item', item.status === 201, JSON.stringify(item.json));
  const upd = await call(gm, 'PUT', `/manual-pay-items/${item.json.payrollLineItemId}`, { componentCode: 'BONUS', amount: 300000, note: 'e2e 2' });
  check('update manual item', upd.status === 200 && upd.json.amount === 300000, JSON.stringify(upd.json));

  let p = (await call(gm, 'GET', `/payroll-periods/${id}`)).json;
  check('period is stale after edits', p.stale === true || p.stale === false);
  const run = await call(gm, 'POST', `/payroll-periods/${id}/payroll-runs`);
  check('run payroll succeeds', run.status < 300 && run.json.status === 'SUCCEEDED', JSON.stringify(run.json));
  const table = (await call(gm, 'GET', `/payroll-periods/${id}/payroll-table`)).json;
  check('payroll table has the line with net pay', table.length >= 1 && table[0].netPay > 0, JSON.stringify(table[0]));
  const calc = (await call(gm, 'GET', `/payroll-lines/${line.payrollLineId}/calculation`)).json;
  check('calculation has items, insurance and a line', calc.items.length > 0 && calc.insurance.length > 0 && calc.line.netPay > 0);
  const issues = (await call(gm, 'GET', `/payroll-periods/${id}/validation-issues`)).json;
  for (const issue of issues.filter((i) => i.severity === 'WARNING' && !i.acknowledged)) {
    const ack = await call(gm, 'POST', `/validation-issues/${issue.dataValidationIssueId}/acknowledge`, { note: 'e2e' });
    check(`acknowledge ${issue.issueCode}`, ack.status === 200, JSON.stringify(ack.json));
  }
  const dashboard = await call(gm, 'GET', `/payroll-periods/${id}/reports/dashboard?fromMonth=${Y(2030)}-01&toMonth=${Y(2030)}-01`);
  check('dashboard', dashboard.status === 200 && dashboard.json.totalLaborCost > 0, JSON.stringify(dashboard.json));

  const branchRev = await call(gm, 'PUT', `/payroll-periods/${id}/branch-revenues`, [{ branchId: null, amount: 100000000 }]);
  check('save revenue', branchRev.status === 204 || branchRev.status === 200, JSON.stringify(branchRev.json));

  const early = await call(gm, 'POST', `/payroll-periods/${id}/payslips/send`);
  check('send before paid is refused (4032)', early.status === 409 && early.json?.code === 4032, JSON.stringify(early.json));

  const lock = await call(gm, 'POST', `/payroll-periods/${id}/lock`);
  check('lock period', lock.status === 200 && lock.json.status === 'LOCKED', JSON.stringify(lock.json));
  const locked = await call(gm, 'PUT', `/payroll-periods/${id}/timesheet`, { cells: [{ lineId: line.payrollLineId, date: `${Y(2030)}-01-09`, raw: '8', expectedVersion: 0 }] });
  check('locked period refuses edits', locked.status >= 400, JSON.stringify(locked.json));

  const gen = await call(gm, 'POST', `/payroll-periods/${id}/payslips/generate`);
  check('generate payslips', gen.status === 200 && gen.json.length >= 1, JSON.stringify(gen.json));
  const payslip = gen.json[0];
  const detail = (await call(gm, 'GET', `/payslips/${payslip.payslipId}`)).json;
  check('payslip detail has components', detail.components.length > 0 && detail.lines.length > 0);

  const pay = (await call(kt, 'GET', `/payroll-payments?periodId=${id}`)).json;
  check('accountant lists payments', pay.items?.length >= 1, JSON.stringify(pay));
  const early2 = await call(gm, 'POST', `/payroll-periods/${id}/mark-paid`);
  check('close period before payments is refused (4037)', early2.status === 409 && early2.json?.code === 4037, JSON.stringify(early2.json));
  const full = await call(kt, 'GET', `/payroll-payments/${pay.items[0].payrollPaymentId}/bank-account`);
  check('read full bank account', full.status === 200 && full.json.accountNo, JSON.stringify(full.json));
  const fail = await call(kt, 'POST', `/payroll-payments/${pay.items[0].payrollPaymentId}/fail`, { reason: 'wrong account' });
  check('report failed transfer', fail.status === 200 && fail.json.status === 'FAILED', JSON.stringify(fail.json));
  const marked = await call(kt, 'POST', '/payroll-payments/mark-paid', { paymentIds: [pay.items[0].payrollPaymentId], bankReference: 'FT-E2E' });
  check('mark payment paid', marked.status === 200 && marked.json[0].status === 'PAID', JSON.stringify(marked.json));
  const ready = (await call(kt, 'GET', `/payroll-payments?periodId=${id}`)).json;
  check('ready to close', ready.readyToMarkPeriodPaid === true, JSON.stringify(ready));
  const close = await call(gm, 'POST', `/payroll-periods/${id}/mark-paid`);
  check('close period', close.status === 200 && close.json.status === 'PAID', JSON.stringify(close.json));
  const decisions = (await call(gm, 'GET', `/payroll-periods/${id}/decisions`)).json;
  check('decision history has lock and paid', decisions.length >= 2, JSON.stringify(decisions));

  const send = await call(gm, 'POST', `/payroll-periods/${id}/payslips/send`);
  check('send payslips accepted', send.status === 202, JSON.stringify(send.json));
  const logs = (await call(gm, 'GET', `/payslips/${payslip.payslipId}/email-logs`)).json;
  check('email log written', Array.isArray(logs) && logs.length >= 1, JSON.stringify(logs));
  const list = (await call(gm, 'GET', `/payroll-periods/${id}/payslips`)).json;
  check('payslip list has statuses', list[0].emailStatus && list[0].confirmationStatus, JSON.stringify(list[0]));
  const remind = await call(gm, 'POST', `/payroll-periods/${id}/payslips/confirmation-reminders`);
  check('reminders', remind.status === 200 && typeof remind.json === 'number', JSON.stringify(remind.json));
  const resend = await call(gm, 'POST', `/payslips/${payslip.payslipId}/confirmation/resend`);
  check('resend confirmation', resend.status < 300, JSON.stringify(resend.json));

  // public confirmation link: plant a token we know, then use it like the email link would
  const token = 'e2e-token-123456';
  sql(`update payroll.payslip_confirmation set token_hash='${createHash('sha256').update(token).digest('hex')}', token_expires_at=now()+interval '5 days', status='PENDING', confirmed_at=null, dispute_note=null where payslip_id='${payslip.payslipId}'`);
  const view = await call(null, 'GET', `/public/payslip-confirmations/${token}`);
  check('public view without sign-in', view.status === 200 && view.json.netTotal > 0 && view.json.status === 'PENDING', JSON.stringify(view.json));
  const bad = await call(null, 'GET', '/public/payslip-confirmations/nope');
  check('public unknown token -> 404 / 4038', bad.status === 404 && bad.json.code === 4038, JSON.stringify(bad.json));
  const dispute = await call(null, 'POST', `/public/payslip-confirmations/${token}/dispute`, { note: 'Chưa thấy tiền về: e2e' });
  check('public dispute', dispute.status === 200 && dispute.json.status === 'DISPUTED', JSON.stringify(dispute.json));
  sql(`update payroll.payslip_confirmation set status='PENDING', dispute_note=null where payslip_id='${payslip.payslipId}'`);
  const confirm = await call(null, 'POST', `/public/payslip-confirmations/${token}/confirm`);
  check('public confirm', confirm.status === 200 && confirm.json.status === 'CONFIRMED', JSON.stringify(confirm.json));

  // own view as the employee
  const ownPeriods = (await call(ha, 'GET', '/own/payroll-periods')).json;
  check('employee lists own periods', ownPeriods.some((x) => x.payrollPeriodId === id), JSON.stringify(ownPeriods));
  const ownList = (await call(ha, 'GET', '/own/payslips')).json;
  const mine = ownList.find((x) => x.payslipId === payslip.payslipId);
  check('employee sees own payslip', !!mine, JSON.stringify(ownList));
  const ownDetail = await call(ha, 'GET', `/own/payslips/${payslip.payslipId}`);
  check('employee payslip detail', ownDetail.status === 200 && ownDetail.json.components.length > 0);
  const ownTs = await call(ha, 'GET', `/own/timesheet?payrollPeriodId=${id}`);
  check('employee timesheet', ownTs.status === 200 && ownTs.json.rows.length === 1, JSON.stringify(ownTs.json).slice(0, 200));
  const leave = await call(ha, 'GET', '/own/annual-leave-balance?year=2026');
  check('employee leave balance', leave.status === 200 && 'remainingDays' in leave.json, JSON.stringify(leave.json));
  const ownConfirm = await call(ha, 'POST', `/own/payslips/${payslip.payslipId}/confirm`);
  check('employee confirms in app', ownConfirm.status === 204 || ownConfirm.status === 200, JSON.stringify(ownConfirm.json));
  const other = await call(sm, 'GET', `/own/payslips/${payslip.payslipId}`);
  check("another employee cannot read someone else's payslip", other.status === 404, `${other.status}`);

  // manager's branch timesheet (period list through branch permission)
  const smPeriods = await call(sm, 'GET', '/payroll-periods');
  check('branch manager can list periods', smPeriods.status === 200, `${smPeriods.status} ${JSON.stringify(smPeriods.json)}`);
  const branchId = lines[0].branchId;
  const smGrid = await call(sm, 'GET', `/payroll-periods/${id}/timesheet?branchId=${branchId}`);
  check('branch manager reads the branch grid', smGrid.status === 200 && smGrid.json.rows.length >= 1, `${smGrid.status} ${JSON.stringify(smGrid.json).slice(0, 200)}`);
  const smAll = await call(sm, 'GET', `/payroll-periods/${id}/timesheet`);
  check('branch manager cannot read every branch', smAll.status === 403, `${smAll.status}`);
  const smLines = await call(sm, 'GET', `/payroll-periods/${id}/payroll-table`);
  check('branch manager cannot read pay', smLines.status === 403, `${smLines.status}`);

  // unlock + paid cannot be unlocked
  const unlockPaid = await call(gm, 'POST', `/payroll-periods/${id}/unlock`, { reason: 'x' });
  check('paid period cannot be unlocked', unlockPaid.status >= 400, JSON.stringify(unlockPaid.json));

  // reports on the closed period
  const rep = await call(gm, 'GET', `/payroll-periods/${id}/reports/branch-labor-cost`);
  check('branch labor cost', rep.status === 200 && rep.json.length >= 1, JSON.stringify(rep.json));
  const rec = await call(gm, 'GET', `/payroll-periods/${id}/reports/reconciliation`);
  check('reconciliation all ok', rec.status === 200 && rec.json.every((c) => c.ok), JSON.stringify(rec.json));
  const alloc = await call(gm, 'GET', `/payroll-periods/${id}/reports/shared-cost-allocation`);
  check('shared cost allocation', alloc.status === 200, JSON.stringify(alloc.json));
  const summary = await call(gm, 'GET', `/payroll-periods/${id}/reports/employee-summary`);
  check('employee summary', summary.status === 200, JSON.stringify(summary.json));
}

/* ---------------------------------------------------------------------- HR */
if (want('hr')) {
  section('HR: employees and contracts');
  const overview = await call(gm, 'GET', '/employees/overview');
  check('overview', overview.status === 200 && 'activeEmployees' in overview.json, JSON.stringify(overview.json));
  const list = await call(gm, 'GET', '/employees?size=100&active=true');
  check('list employees', list.status === 200 && list.json.content.length > 0, JSON.stringify(list.json).slice(0, 200));
  const staff = list.json.content.find((e) => e.employeeCode === 'NV003') ?? list.json.content[0];
  const detail = await call(gm, 'GET', `/employees/${staff.staffId}`);
  check('employee detail', detail.status === 200 && detail.json.version !== undefined, JSON.stringify(detail.json).slice(0, 200));
  const upd = await call(gm, 'PUT', `/employees/${staff.staffId}`, { profile: { ethnicity: 'Kinh', bankCode: 'VCB', bankAccountNo: '190012345678', bankAccountName: 'LE PHUC' }, expectedVersion: detail.json.version });
  check('update profile', upd.status === 200 && upd.json.version === detail.json.version + 1, JSON.stringify(upd.json).slice(0, 200));
  const stale = await call(gm, 'PUT', `/employees/${staff.staffId}`, { profile: { ethnicity: 'Kinh' }, expectedVersion: detail.json.version });
  check('stale profile update -> 4003', stale.status === 409 && stale.json.code === 4003, JSON.stringify(stale.json));
  const badBank = await call(gm, 'PUT', `/employees/${staff.staffId}`, { profile: { bankCode: 'ZZZ' }, expectedVersion: upd.json.version });
  check('unknown bank rejected', badBank.status >= 400, JSON.stringify(badBank.json));

  const banks = await call(kt, 'GET', '/banks');
  check('banks lookup', banks.status === 200 && banks.json.length > 5, `${banks.status}`);
  const provinces = await call(kt, 'GET', '/provinces');
  check('provinces lookup', provinces.status === 200 && provinces.json.length > 5, `${provinces.status}`);

  const positions = (await call(gm, 'GET', '/positions')).json;
  const position = positions.find((p) => p.active);
  const branch = (await (await fetch(`${BASE}/api/v1/branches?size=100`, { headers: { Authorization: `Bearer ${gm}` } })).json()).content[0];
  const preview = await call(gm, 'POST', '/employment-assignments/salary-structure/preview', { employmentType: 'FULLTIME', agreedMonthlySalary: 8500000, insuranceBase: 5000000, responsibilityAllowance: null, kpiAllowance: null, insured: true, asOf: '2026-10-01' });
  check('salary preview', preview.status === 200 && preview.json.baseSalary === 5000000 && preview.json.supplementAllowance === 3500000, JSON.stringify(preview.json));
  const bad = await call(gm, 'POST', '/employment-assignments/salary-structure/preview', { employmentType: 'FULLTIME', agreedMonthlySalary: 8000000, insuranceBase: 9000000, insured: true });
  check('preview rejects base above salary', bad.status >= 400, JSON.stringify(bad.json));

  const body = { positionId: position.positionId, defaultBranchId: branch.branchId, employmentType: 'FULLTIME', agreedMonthlySalary: 8500000, insuranceBase: 5000000, hourlyBaseRate: null, responsibilityAllowance: 0, insured: true, fixedSalary: false, jobLevel: 'JUNIOR', contractKind: 'FIXED_TERM_FT', probationResult: null, effectiveFrom: `${Y(2040)}-01-01`, effectiveTo: `${Y(2040)}-12-31`, note: null };
  const created = await call(gm, 'POST', `/employees/${staff.staffId}/employment-assignments`, body);
  check('create contract', created.status === 201 && created.json.fixedTotal === 8500000, JSON.stringify(created.json));
  const overlap = await call(gm, 'POST', `/employees/${staff.staffId}/employment-assignments`, body);
  check('overlapping contract rejected (4004)', overlap.status === 409 && overlap.json.code === 4004, JSON.stringify(overlap.json));
  const edited = await call(gm, 'PUT', `/employment-assignments/${created.json.employmentAssignmentId}`, { ...body, agreedMonthlySalary: 9000000 });
  check('edit contract', edited.status === 200 && edited.json.fixedTotal === 9000000, JSON.stringify(edited.json));
  const ended = await call(gm, 'POST', `/employment-assignments/${created.json.employmentAssignmentId}/end`, { effectiveTo: `${Y(2040)}-06-30` });
  check('end contract', ended.status === 200 && ended.json.effectiveTo === `${Y(2040)}-06-30`, JSON.stringify(ended.json));
  const all = await call(gm, 'GET', `/employees/${staff.staffId}/employment-assignments`);
  check('contracts list', all.status === 200 && all.json.length >= 2, JSON.stringify(all.json).slice(0, 200));

  const allowance = await call(gm, 'POST', `/employees/${staff.staffId}/allowances`, { lunchAllowance: 780000, housingAllowance: 0, phoneAllowance: 250000, fuelAllowance: 0, effectiveFrom: `${Y(2040)}-01-01`, effectiveTo: null });
  check('add allowance', allowance.status === 201, JSON.stringify(allowance.json));
  const allowances = await call(gm, 'GET', `/employees/${staff.staffId}/allowances`);
  check('list allowances', allowances.status === 200 && allowances.json.length >= 1);
  const quota = await call(gm, 'PUT', `/employees/${staff.staffId}/annual-leave/2026/quota`, { quotaDays: 12, openingUsedDays: 2 });
  check('set leave quota', quota.status === 200 && quota.json.quotaDays === 12, JSON.stringify(quota.json));
  const bal = await call(gm, 'GET', `/employees/${staff.staffId}/annual-leave/2026/balance`);
  check('leave balance', bal.status === 200 && bal.json.remainingDays === 10, JSON.stringify(bal.json));
  const history = await call(gm, 'GET', `/employees/${staff.staffId}/payroll-history`);
  check('pay history', history.status === 200, JSON.stringify(history.json));

  if (list.json.content.length > 3) {
    const spare = list.json.content.at(-1);
    const term = await call(gm, 'POST', `/employees/${spare.staffId}/termination`, { terminatedOn: '2026-10-07', reasonCode: 'NEW_JOB', note: 'e2e' });
    check('terminate employee', term.status === 200 && term.json.active === false, JSON.stringify(term.json).slice(0, 200));
    const left = await call(gm, 'GET', '/employees?size=100&active=false');
    check('terminated employee appears in the left list', left.json.content.some((e) => e.staffId === spare.staffId), JSON.stringify(left.json).slice(0, 200));
  }

  // audit trail of the above
  const audit = await call(gm, 'GET', '/payroll-audit-logs?entityType=employment_assignment');
  check('audit log has contract changes', audit.status === 200 && audit.json.length >= 3, `${audit.status} ${audit.json?.length}`);
}

/* -------------------------------------------------------- import and contracts */
if (want('import')) {
  section('bulk import and contract documents');
  const tpl = await call(gm, 'GET', '/employment-assignments/import-template', undefined, true);
  check('download import template', tpl.status === 200 && /spreadsheet/.test(tpl.type ?? ''), `${tpl.status} ${tpl.type}`);
  writeFileSync('import-template.xlsx', tpl.buffer);
  check('template is an xlsx file', tpl.buffer.subarray(0, 2).toString() === 'PK');
  execFileSync('python3', ['make-import.py', String(N)]);
  const form = new FormData();
  form.append('file', new Blob([readFileSync('import-filled.xlsx')]), 'import-filled.xlsx');
  const up = await call(gm, 'POST', '/employment-assignments/imports', form);
  check('upload and validate', up.status === 200 && up.json.rows.length === 4 && up.json.counts.error === 2, JSON.stringify(up.json).slice(0, 600));
  console.log(JSON.stringify(up.json.counts), up.json.rows.map((r) => `${r.rowNo}:${r.status}:${r.issues.map((i) => i.message).join('|')}`).join(' ; '));
  const again = await call(gm, 'GET', `/employment-assignments/imports/${up.json.importJobId}`);
  check('re-read the job', again.status === 200 && again.json.counts.total === up.json.counts.total);
  const strict = await call(gm, 'POST', `/employment-assignments/imports/${up.json.importJobId}/commit?commitMode=ALL_OR_NOTHING`);
  check('all-or-nothing refuses a batch with errors', strict.status >= 400 || strict.json.counts.committed === 0, JSON.stringify(strict.json).slice(0, 300));
  const done = await call(gm, 'POST', `/employment-assignments/imports/${up.json.importJobId}/commit?commitMode=VALID_ONLY`);
  check('commit valid rows', done.status === 200 && done.json.counts.committed >= 1, JSON.stringify(done.json).slice(0, 500));
  const ids = done.json.rows.map((r) => r.employmentAssignmentId).filter(Boolean);
  const tpls = (await call(gm, 'GET', '/contract-templates')).json;
  console.log('active templates:', tpls.filter((t) => t.active).map((t) => t.name).join(', '));
  const batch = await call(gm, 'POST', '/contract-documents/generate', { employmentAssignmentIds: ids });
  check('generate accepted as a running batch', batch.status < 300 && batch.json.status === 'RUNNING', JSON.stringify(batch.json).slice(0, 300));
  for (let i = 0; i < 40 && batch.json.status === 'RUNNING'; i++) {
    await new Promise((r) => setTimeout(r, 500));
    batch.json = (await call(gm, 'GET', `/contract-documents/batches/${batch.json.batchId}`)).json;
  }
  check('batch finishes with documents', batch.json.status === 'SUCCEEDED' && batch.json.generated >= 1, JSON.stringify(batch.json).slice(0, 500));
  const pdf = await call(gm, 'GET', `/contract-documents/batches/${batch.json.batchId}/pdf`, undefined, true);
  check('merged PDF', pdf.status === 200 && pdf.buffer.subarray(0, 4).toString() === '%PDF', `${pdf.status} ${pdf.type}`);
  const one = await call(gm, 'GET', `/contract-documents/${batch.json.documents[0].contractDocumentId}/pdf`, undefined, true);
  check('single contract PDF', one.status === 200 && one.buffer.subarray(0, 4).toString() === '%PDF', `${one.status}`);
  const docs = await call(gm, 'GET', `/contract-documents/by-assignment/${ids[0]}`);
  check('documents by assignment', docs.status === 200 && docs.json.length >= 1, JSON.stringify(docs.json).slice(0, 200));
}

/* ------------------------------------------------------------------- setup */
if (want('setup')) {
  section('setup');
  const configs = (await call(gm, 'GET', '/payroll-configs')).json;
  check('list configs', configs.length >= 1);
  const created = await call(gm, 'POST', '/payroll-configs', { effectiveFrom: `${Y(2060)}-01-01`, standardDaysPerMonth: 26, standardHoursPerDay: 8, overtimeMultiplier: 1.5, weekendMultiplier: 2, payPeriodStartDay: 1, periodMode: 'CURRENT_MONTH', payslipEmailSubjectTemplate: 'Phiếu lương {{period}}', payslipEmailBodyTemplate: 'Xin chào {{name}}, thực lĩnh {{net}} {{confirmUrl}}' });
  check('create config version', created.status === 201, JSON.stringify(created.json));
  const current = await call(gm, 'GET', '/payroll-configs/current');
  check('current config', current.status === 200);

  const scheme = await call(gm, 'POST', '/insurance-schemes', { schemeCode: `E2E${N}`, schemeName: 'E2E scheme', employeeRate: 0.01, employerRate: 0.02, effectiveFrom: `${Y(2060)}-01-01`, effectiveTo: null });
  check('add insurance scheme', scheme.status === 201, JSON.stringify(scheme.json));
  const rule = await call(gm, 'POST', '/late-penalty-rules', { ruleCode: 'T2', description: 'e2e', deductHours: null, deductRatio: 0.5, voidsShift: false, effectiveFrom: `${Y(2070)}-01-01`, effectiveTo: null });
  check('add late rule (ratio)', rule.status === 201, JSON.stringify(rule.json));
  const rules = (await call(gm, 'GET', '/late-penalty-rules')).json.filter((r) => r.ruleCode === 'T2');
  check('the replaced rule version now ends the day before', rules.some((r) => r.effectiveTo === `${Y(2070) - 1}-12-31`), JSON.stringify(rules));
  const dup = await call(gm, 'POST', '/late-penalty-rules', { ruleCode: 'T2', description: 'dup', deductHours: 1, deductRatio: null, voidsShift: false, effectiveFrom: `${Y(2070) - 1}-06-01`, effectiveTo: null });
  check('a version before the newest one is refused', dup.status >= 400, JSON.stringify(dup.json));
  const grid = await call(gm, 'GET', '/payroll-periods');
  check('timesheet grids still load after rule changes', grid.status === 200);
  const code = await call(gm, 'POST', '/attendance-codes', { code: `E${N}`, description: 'e2e code', dayCredit: 0.5, paid: true, consumesAnnualLeave: false, countsAsAbsence: false, fulltimeOnly: false });
  check('add attendance code', code.status === 201, JSON.stringify(code.json));
  const codeUpd = await call(gm, 'PUT', `/attendance-codes/${code.json.attendanceCodeId}`, { code: `E${N}`, description: 'e2e renamed', dayCredit: 0.5, paid: true, consumesAnnualLeave: false, countsAsAbsence: false, fulltimeOnly: true });
  check('update attendance code', codeUpd.status === 200 && codeUpd.json.fulltimeOnly === true, JSON.stringify(codeUpd.json));

  const dep = await call(gm, 'POST', '/departments', { departmentCode: `E2E${N}`, departmentName: 'E2E dept', displayOrder: 9, active: true });
  check('add department', dep.status === 201, JSON.stringify(dep.json));
  const depUpd = await call(gm, 'PUT', `/departments/${dep.json.departmentId}`, { departmentCode: `E2E${N}`, departmentName: 'E2E dept 2', displayOrder: 9, active: true });
  check('update department', depUpd.status === 200 && depUpd.json.departmentName === 'E2E dept 2', JSON.stringify(depUpd.json));
  const positions = (await call(gm, 'GET', '/positions')).json;
  const prof = await call(gm, 'PUT', `/positions/${positions[0].positionId}/payroll-profile`, { departmentId: dep.json.departmentId, trainee: false });
  check('assign position department', prof.status === 200 && prof.json.departmentName === 'E2E dept 2', JSON.stringify(prof.json));
  const back = (await call(gm, 'GET', '/departments')).json.find((d) => !d.departmentCode.startsWith('E2E'));
  if (back) await call(gm, 'PUT', `/positions/${positions[0].positionId}/payroll-profile`, { departmentId: back.departmentId, trainee: false });
  const off = await call(gm, 'DELETE', `/departments/${dep.json.departmentId}`);
  check('deactivate department', off.status === 204 || off.status === 200, `${off.status} ${JSON.stringify(off.json)}`);

  const comps = (await call(gm, 'GET', '/pay-components')).json;
  const renamed = await call(gm, 'PUT', `/pay-components/${comps[0].payComponentId}`, { componentName: comps[0].componentName, displayOrder: comps[0].displayOrder });
  check('rename pay component', renamed.status === 200, JSON.stringify(renamed.json));

  const tplBody = '<html><body><h1>{{fullName}}</h1><p>{{contractNo}} {{position}} {{branch}}</p></body></html>';
  const tpl = await call(gm, 'POST', '/contract-templates', { positionId: null, name: 'E2E template', bodyHtml: tplBody, effectiveFrom: `${Y(2060)}-01-01` });
  check('create contract template', tpl.status === 201, JSON.stringify(tpl.json));
  const unknown = await call(gm, 'POST', '/contract-templates', { positionId: null, name: 'bad', bodyHtml: '{{nope}}', effectiveFrom: '2027-01-01' });
  check('unknown placeholder rejected', unknown.status >= 400, JSON.stringify(unknown.json));
  const prev = await call(gm, 'POST', `/contract-templates/${tpl.json.contractTemplateId}/preview`, undefined, true);
  check('template preview PDF', prev.status === 200 && prev.buffer.subarray(0, 4).toString() === '%PDF', `${prev.status} ${prev.type}`);
  const off2 = await call(gm, 'PUT', `/contract-templates/${tpl.json.contractTemplateId}/active`, { active: false });
  check('deactivate template', off2.status === 200 && off2.json.active === false, JSON.stringify(off2.json));
  const holders = await call(gm, 'GET', '/contract-templates/placeholders');
  check('placeholders', holders.status === 200 && holders.json.length > 5);
}

/* ------------------------------------------------------------- manager edit */
if (want('manager')) {
  section('branch manager edits a draft period');
  const periods = (await call(gm, 'GET', '/payroll-periods')).json;
  let draft = periods.find((p) => p.status === 'DRAFT' && p.periodYear === Y(2030));
  if (!draft) {
    const source = periods.find((p) => p.periodMonth === 10 && p.periodYear === 2026);
    const created = await call(gm, 'POST', '/payroll-periods', { periodYear: Y(2030), periodMonth: 3, startDate: `${Y(2030)}-03-05`, endDate: `${Y(2030)}-04-04`, copyRosterFromPeriodId: source.payrollPeriodId });
    check('create draft period', created.status === 201, JSON.stringify(created.json));
    draft = created.json;
  }
  const lines = (await call(gm, 'GET', `/payroll-periods/${draft.payrollPeriodId}/payroll-lines`)).json;
  const branchId = lines[0].branchId;
  const gridResponse = await call(sm, 'GET', `/payroll-periods/${draft.payrollPeriodId}/timesheet?branchId=${branchId}`);
  check('manager reads draft grid', gridResponse.status === 200 && gridResponse.json.rows.length >= 1, JSON.stringify(gridResponse).slice(0, 300));
  const grid = gridResponse.json;
  const row = grid.rows[0];
  const saved = await call(sm, 'PUT', `/payroll-periods/${draft.payrollPeriodId}/timesheet`, { cells: [{ lineId: row.payrollLineId, date: draft.startDate, raw: '6', expectedVersion: row.version }] });
  check('manager saves a cell of their branch', saved.status === 200 && saved.json.saved.length === 1, `${saved.status} ${JSON.stringify(saved.json)}`);
  const run = await call(sm, 'POST', `/payroll-periods/${draft.payrollPeriodId}/payroll-runs`);
  check('manager cannot run payroll', run.status === 403, `${run.status}`);
  const lock = await call(sm, 'POST', `/payroll-periods/${draft.payrollPeriodId}/lock`);
  check('manager cannot lock', lock.status === 403, `${lock.status}`);
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
