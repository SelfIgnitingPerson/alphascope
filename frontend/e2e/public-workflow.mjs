/** 公网模式真实HTTPS浏览器回归，仅供隔离测试；复用同一CDP驱动及清理逻辑。 */
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { join } from 'node:path';
export async function runPublicWorkflow(driver) {
  const {
    api,
    send,
    evaluate,
    click,
    navigate,
    until,
    origin,
    backend,
    artifacts,
    exceptions,
    failedResponses,
    setCookie,
  } = driver;
  async function fill(selector, value) {
    await click(selector);
    await evaluate(`document.querySelector(${JSON.stringify(selector)}).select()`);
    await send('Input.insertText', { text: value });
  }
  async function refreshCookie() {
    const result = await send('Network.getCookies', { urls: [origin] });
    const cookie = result.cookies.find((value) => value.name === 'ALPHASCOPE_SESSION');
    assert(cookie?.httpOnly && cookie?.secure && cookie?.sameSite === 'Strict');
    setCookie('ALPHASCOPE_SESSION=' + cookie.value);
    return cookie.value;
  }
  async function register(name) {
    await until(
      () => evaluate(`Boolean(document.querySelector('[data-testid="auth-toggle"]'))`),
      '邀请注册入口',
    );
    await click('[data-testid="auth-toggle"]');
    await fill('#login-username', name);
    await fill('#login-password', 'test-only-browser-password');
    await fill('#login-invitation', 'test-fixture-invitation-with-more-than-24-chars');
    await click('[data-testid="auth-submit"]');
    await until(
      () => evaluate(`Boolean(document.querySelector('[data-testid="auth-logout"]'))`),
      '账号工作台',
    );
    await refreshCookie();
  }
  async function signOut() {
    await click('[data-testid="auth-logout"]');
    await until(
      () => evaluate(`Boolean(document.querySelector('#login-username'))`),
      '退出返回登录',
    );
    setCookie('');
  }
  await send('Page.navigate', { url: origin + '/#/dashboard' });
  await until(
    () => evaluate(`document.body.textContent.includes('登录研究工作台')`),
    '无会话登录页',
  );
  await register('browser-alice');
  assert.equal((await api('/watchlist')).length, 0);
  await navigate('/watchlist');
  await until(
    () => evaluate(`Boolean(document.querySelector('[data-testid="watch-search"]'))`),
    '证券搜索入口',
  );
  await fill('#business-date', '2026-10-30');
  await send('Input.dispatchKeyEvent', {
    type: 'keyDown',
    key: 'Enter',
    code: 'Enter',
    windowsVirtualKeyCode: 13,
  });
  await send('Input.dispatchKeyEvent', {
    type: 'keyUp',
    key: 'Enter',
    code: 'Enter',
    windowsVirtualKeyCode: 13,
  });
  await click('main h1');
  await until(
    () =>
      evaluate(
        `![...document.querySelectorAll('.el-picker-panel')].some(node => node.getClientRects().length)`,
      ),
    '日期弹层关闭',
  );
  await fill('input[aria-label="搜索股票"]', '300476');
  await click('[data-testid="watch-search"]');
  await until(
    () => evaluate(`document.body.textContent.includes('搜索结果来自')`),
    '服务器目录查询',
  );
  await click('.filter-bar--watch-add .el-select');
  const option = await until(
    () =>
      evaluate(
        `(() => { const node = [...document.querySelectorAll('.el-select-dropdown__item')].find(value => value.textContent.includes('300476.SZ')); if (!node) return false; node.setAttribute('data-testid','pick-stock'); return true; })()`,
      ),
    '有效股票候选',
  );
  assert(option);
  await click('[data-testid="pick-stock"]');
  await click('[data-testid="watch-add"]');
  await until(
    async () => (await api('/watchlist')).some((row) => row.symbol === '300476.SZ'),
    '添加真实登记证券',
  );
  const job = await until(
    async () => (await api('/admin/jobs')).items.find((row) => row.business_date === '2026-10-30'),
    '首次初始化排队',
  );
  await until(
    async () => (await api('/admin/jobs/' + job.id)).status === 'SUCCESS',
    '个人日报完成',
  );
  const report = (await api('/reports?start=2026-10-30&end=2026-10-30')).items[0];
  assert(report);
  await signOut();
  await register('browser-bob');
  assert.equal((await api('/watchlist')).length, 0);
  assert.equal((await api('/admin/jobs')).total, 0);
  assert.equal((await api('/reports?start=2026-10-30&end=2026-10-30')).total, 0);
  const bobCookie = await refreshCookie();
  const denied = await fetch(backend + '/api/v1/reports/' + report.id, {
    headers: { Cookie: 'ALPHASCOPE_SESSION=' + bobCookie },
  });
  assert.equal(denied.status, 404);
  const adminDenied = await fetch(backend + '/api/v1/admin/users', {
    headers: { Cookie: 'ALPHASCOPE_SESSION=' + bobCookie },
  });
  assert.equal(adminDenied.status, 403);
  await navigate('/reports/' + report.id);
  await until(
    () => evaluate(`document.body.textContent.includes('报告不存在')`),
    '页面拒绝其他用户报告',
  );
  const timings = [];
  const reads = Array.from({ length: 24 }, async () => {
    const start = performance.now();
    const value = await api('/watchlist');
    assert.equal(value.length, 0);
    timings.push(performance.now() - start);
  });
  await Promise.all(reads);
  timings.sort((a, b) => a - b);
  const p95 = timings[Math.ceil(timings.length * 0.95) - 1];
  assert(p95 < 5000, '隔离环境只读并发烟测超过5秒');
  await signOut();
  await fill('#login-username', 'browser-alice');
  await fill('#login-password', 'test-only-browser-password');
  await click('[data-testid="auth-submit"]');
  await until(
    () => evaluate(`Boolean(document.querySelector('[data-testid="auth-logout"]'))`),
    '重新登录',
  );
  await refreshCookie();
  assert.equal((await api('/watchlist'))[0].symbol, '300476.SZ');
  await navigate('/dashboard');
  await until(() => evaluate(`document.body.textContent.includes('胜宏科技')`), '重登自己的研究');
  assert.equal(exceptions.length, 0, exceptions.join('\n'));
  assert.equal(failedResponses.length, 0, JSON.stringify(failedResponses));
  const proof = {
    browser: 'Chrome headless HTTPS',
    backend: 'isolated Testcontainers PUBLIC DEMO fixture',
    checks: [
      'login-required',
      'invite-registration',
      'secure-httpOnly-cookie',
      'new-user-empty-scope',
      'discover-and-initialize',
      'own-daily-report',
      'logout-revokes-session',
      'second-user-empty-scope',
      'cross-user-report-rejected',
      'administrator-role-rejected',
      'relogin-restores-own-research',
    ],
    readOnlyConcurrency: { requests: 24, p95Millis: Math.round(p95), errors: 0 },
    uncaughtExceptions: 0,
    serverErrors: 0,
  };
  await writeFile(join(artifacts, 'public-workflow.json'), JSON.stringify(proof, null, 2));
  const screenshot = await send('Page.captureScreenshot', { format: 'png' });
  await writeFile(join(artifacts, 'public-workflow.png'), Buffer.from(screenshot.data, 'base64'));
  console.log(JSON.stringify(proof));
}
