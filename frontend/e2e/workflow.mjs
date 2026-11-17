/** 真正的隔离Chrome与Vite流程回归；后端由Testcontainers测试提供，拒绝连接用户的8080。 */
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, writeFile, rm, mkdir, access } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { createServer } from 'vite';
import { runPublicWorkflow } from './public-workflow.mjs';
const publicMode = process.env.E2E_PUBLIC === 'true';
let authCookie = '';

const backend = process.env.E2E_BACKEND_URL;
assert(
  backend && /^http:\/\/127\.0\.0\.1:\d+$/.test(backend) && !backend.endsWith(':8080'),
  '仅允许隔离测试后端，拒绝用户业务端口8080',
);
const artifacts = resolve(process.env.E2E_ARTIFACT_DIR || '../backend/target/browser-test');
await mkdir(artifacts, { recursive: true });
const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function until(action, label, timeout = 90000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    const value = await action();
    if (value) return value;
    await pause(200);
  }
  throw Error('等待超时：' + label);
}
async function api(path) {
  const response = await fetch(backend + '/api/v1' + path, {
    headers: authCookie ? { Cookie: authCookie } : {},
  });
  const body = await response.json();
  assert.equal(body.code, 'SUCCESS', path);
  return body.data;
}
if (publicMode) assert.equal((await api('/auth/status')).mode, 'PUBLIC');
else
  assert.equal(
    (await api('/market/providers')).dataScope,
    'DEMO',
    '浏览器回归必须处于明确演示范围',
  );
process.env.ALPHASCOPE_API_PROXY = backend;
const server = await createServer({
  server: {
    host: '127.0.0.1',
    port: Number(process.env.E2E_WEB_PORT || 5188),
    strictPort: true,
    ...(publicMode
      ? {
          https: {
            key: await readFile(process.env.E2E_TLS_KEY),
            cert: await readFile(process.env.E2E_TLS_CERT),
          },
        }
      : {}),
  },
  logLevel: 'error',
});
await server.listen();
const origin = server.resolvedUrls.local[0].replace(/\/$/, '');
const profile = await mkdtemp(join(tmpdir(), 'alphascope-browser-'));
const candidates = [
  process.env.CHROME_BINARY,
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/usr/bin/google-chrome',
  '/usr/bin/google-chrome-stable',
  '/usr/bin/chromium',
  '/usr/bin/chromium-browser',
].filter(Boolean);
let executable;
for (const candidate of candidates) {
  try {
    await access(candidate);
    executable = candidate;
    break;
  } catch {
    /* 检查下一个已安装浏览器。 */
  }
}
assert(executable, '请通过CHROME_BINARY提供Chrome或Chromium可执行文件');
const chrome = spawn(
  executable,
  [
    '--headless=new',
    ...(publicMode ? ['--ignore-certificate-errors'] : []),
    '--no-sandbox',
    '--disable-gpu',
    '--disable-background-networking',
    '--disable-component-update',
    '--disable-sync',
    '--metrics-recording-only',
    '--no-first-run',
    '--no-default-browser-check',
    '--remote-debugging-port=0',
    '--user-data-dir=' + profile,
    'about:blank',
  ],
  { stdio: ['ignore', 'ignore', 'pipe'] },
);
let diagnostics = '';
chrome.stderr.on('data', (data) => {
  diagnostics = (diagnostics + data.toString()).slice(-12000);
});
let socket, session;
let sequence = 0;
const pending = new Map();
const exceptions = [];
const failedResponses = [];
function send(method, params = {}, sessionId = session) {
  return new Promise((resolve, reject) => {
    const id = ++sequence;
    const timer = setTimeout(() => {
      pending.delete(id);
      reject(Error('CDP命令超时：' + method));
    }, 30000);
    pending.set(id, { resolve, reject, timer });
    socket.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
  });
}
async function evaluate(expression) {
  const value = await send('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true,
  });
  if (value.exceptionDetails)
    throw Error(value.exceptionDetails.text + ': ' + value.exceptionDetails.exception?.description);
  return value.result.value;
}
async function click(selector) {
  const point = await until(
    () =>
      evaluate(
        `(() => {
          const node = document.querySelector(${JSON.stringify(selector)});
          if (!node || node.disabled || !node.getClientRects().length) return null;
          node.scrollIntoView({ block: 'center' });
          const rect = node.getBoundingClientRect();
          const point = { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 };
          const hit = document.elementFromPoint(point.x, point.y);
          // 弹层、抽屉和动画期间，坐标可能命中另一个元素，必须等待真正可点击。
          return hit && (hit === node || node.contains(hit)) ? point : null;
        })()`,
      ),
    '按钮可交互 ' + selector,
  );
  await send('Input.dispatchMouseEvent', {
    type: 'mousePressed',
    button: 'left',
    clickCount: 1,
    ...point,
  });
  await send('Input.dispatchMouseEvent', {
    type: 'mouseReleased',
    button: 'left',
    clickCount: 1,
    ...point,
  });
}
async function closeDrawer() {
  if (
    await evaluate(
      `Boolean(document.querySelector('.el-drawer__close-btn')?.getClientRects().length)`,
    )
  ) {
    // 取消响应会先展开详情，再完成列表刷新；等写操作及入场动画结束后关闭，
    // 避免点击移动中的按钮，或被尚未结束的响应再次展开。
    await until(
      () =>
        evaluate(
          `!document.querySelector('.el-button.is-loading') && !document.querySelector('.el-drawer')?.getAnimations().some(animation => animation.playState === 'running')`,
        ),
      '抽屉操作与入场动画完成',
    );
    await click('.el-drawer__close-btn');
    await until(
      () =>
        evaluate(
          `![...document.querySelectorAll('.el-overlay.is-drawer')].some(node => node.getClientRects().length)`,
        ),
      '任务抽屉关闭完成',
    );
  }
}
async function navigate(path) {
  await evaluate(`location.hash=${JSON.stringify(path)}`);
  await until(() => evaluate(`document.querySelector('main h1')?.textContent?.trim()`), '页面标题');
  await pause(200);
}
try {
  const portFile = await until(
    async () => {
      try {
        return await readFile(join(profile, 'DevToolsActivePort'), 'utf8');
      } catch {
        return null;
      }
    },
    'Chrome启动',
    30000,
  );
  const [port, path] = portFile.trim().split('\n');
  socket = new WebSocket('ws://127.0.0.1:' + port + path);
  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data);
    if (message.id) {
      const item = pending.get(message.id);
      if (item) {
        clearTimeout(item.timer);
        pending.delete(message.id);
        message.error ? item.reject(Error(message.error.message)) : item.resolve(message.result);
      }
    } else if (message.method === 'Runtime.exceptionThrown') {
      exceptions.push(
        message.params.exceptionDetails.exception?.description ||
          message.params.exceptionDetails.text,
      );
    } else if (
      message.method === 'Network.responseReceived' &&
      message.params.response.status >= 500
    ) {
      failedResponses.push({
        url: message.params.response.url,
        status: message.params.response.status,
      });
    }
  });
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true });
    socket.addEventListener('error', reject, { once: true });
  });
  const target = await send('Target.createTarget', { url: 'about:blank' }, null);
  session = (
    await send('Target.attachToTarget', { targetId: target.targetId, flatten: true }, null)
  ).sessionId;
  await send('Runtime.enable');
  await send('Page.enable');
  await send('Network.enable');
  if (publicMode) {
    await runPublicWorkflow({
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
      setCookie: (value) => {
        authCookie = value;
      },
    });
  } else {
    await send('Page.navigate', { url: origin + '/#/jobs' });
    await until(
      () =>
        evaluate(
          `Boolean(document.querySelector('#business-date')&&document.querySelector('[data-testid="submit-daily"]'))`,
        ),
      '任务页',
    );
    await evaluate(
      `(()=>{const input=document.querySelector('#business-date');input.focus();Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'2000-01-04');input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));})()`,
    );
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
    await evaluate(`document.querySelector('#business-date').blur()`);
    await until(
      () => evaluate(`document.querySelector('.job-toolbar')?.textContent.includes('2000-01-04')`),
      '日期绑定',
    );
    await until(
      () =>
        evaluate(`new URLSearchParams(location.hash.split('?')[1]).get('date') === '2000-01-04'`),
      '手选日期写入链接',
    );
    // 完整重载也必须沿用明确日期，不被默认收盘日覆盖。
    const beforeReload = await evaluate('performance.timeOrigin');
    await send('Page.reload');
    await until(
      () =>
        evaluate(
          `performance.timeOrigin !== ${beforeReload} && document.querySelector('.job-toolbar')?.textContent.includes('2000-01-04') && document.querySelector('#business-date')?.value === '2000-01-04'`,
        ),
      '重载保留研究日期',
    );
    // 真实点击空白标题以关闭日期弹层，单纯input.blur()不足以关闭Element Plus弹层。
    await click('main h1');
    await until(
      () =>
        evaluate(
          `![...document.querySelectorAll('.el-picker-panel')].some(node => node.getClientRects().length)`,
        ),
      '日期弹层关闭',
    );
    await click('[data-testid="submit-daily"]');
    const first = await until(async () => {
      const page = await api('/admin/jobs');
      return page.items.find((job) => job.business_date === '2000-01-04');
    }, '持久任务接收');
    await until(async () => {
      const job = await api('/admin/jobs/' + first.id);
      return (
        job.status === 'RUNNING' &&
        job.stages.some((stage) => stage.stage_name === 'CALENDAR' && stage.status === 'RUNNING')
      );
    }, '实际进入日历阶段');
    await until(
      () =>
        evaluate(
          `Boolean(document.querySelector('.el-drawer__close-btn')?.getClientRects().length)`,
        ),
      '任务抽屉已展开',
    );
    await closeDrawer();
    await pause(1100);
    await click('[data-testid="cancel-job-' + first.id + '"]');
    await until(
      async () => (await api('/admin/jobs/' + first.id)).status === 'CANCELLED',
      '取消持久终态',
    );
    await until(
      () => evaluate(`document.querySelector('.drawer-summary')?.textContent.includes('已取消')`),
      '页面取消状态',
    );
    await closeDrawer();
    await pause(1100);
    await click('[data-testid="retry-job-' + first.id + '"]');
    const retry = await until(async () => {
      const page = await api('/admin/jobs');
      return page.items.find((job) => job.id === first.id && job.attempt > first.attempt);
    }, '原任务编号重试');
    await until(async () => {
      const job = await api('/admin/jobs/' + retry.id);
      if (['FAILED', 'INTERRUPTED', 'TIMED_OUT', 'CANCELLED'].includes(job.status))
        throw Error('重试失败：' + JSON.stringify(job));
      return job.status === 'SUCCESS';
    }, '重试成功');
    assert.equal(retry.id, first.id);
    const pastAttempts = await api('/admin/jobs/' + first.id + '/attempts');
    assert.equal(pastAttempts.items[0].status, 'CANCELLED');
    await closeDrawer();
    await navigate('/reports');
    await until(
      () => evaluate(`Boolean(document.querySelector('a[href^="#/reports/"]'))`),
      '报告列表',
    );
    await click('a[href^="#/reports/"]');
    await until(
      () =>
        evaluate(
          `document.querySelector('main h1')?.textContent.includes('报告详情')&&document.body.textContent.includes('2000-01-04')`,
        ),
      '报告详情及冻结内容',
    );
    assert(
      await evaluate(
        `!document.querySelector('.date-toolbar') && !document.querySelector('#business-date')`,
      ),
      '冻结报告不提供无效日期筛选',
    );
    const report = (await api('/reports?start=2000-01-04&end=2000-01-04')).items[0];
    assert(report);
    const detail = await api('/reports/' + report.id);
    assert.equal(detail.is_synthetic, true);
    await until(
      () =>
        evaluate(
          `document.querySelectorAll('.report-section-tabs [role="tab"]').length === 5 && Boolean(document.querySelector('.report-focus'))`,
        ),
      '报告默认突出重点',
    );
    assert(
      await evaluate(
        `!document.querySelector('.price-volume-study') && !document.querySelector('.report-evidence')`,
      ),
      '概览不堆叠完整研究和JSON',
    );
    await send('Emulation.setDeviceMetricsOverride', {
      width: 1440,
      height: 1000,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await evaluate(`window.scrollTo(0, 0)`);
    await evaluate(
      `new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))`,
    );
    const reportOverview = await send('Page.captureScreenshot', { format: 'png' });
    await writeFile(
      join(artifacts, 'report-overview-desktop.png'),
      Buffer.from(reportOverview.data, 'base64'),
    );
    await send('Emulation.setDeviceMetricsOverride', {
      width: 390,
      height: 844,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await evaluate(
      `new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))`,
    );
    assert(
      await evaluate(
        `document.querySelector('.report-document').getBoundingClientRect().right <= window.innerWidth + 1`,
      ),
      '报告窄屏不溢出',
    );
    const reportMobile = await send('Page.captureScreenshot', { format: 'png' });
    await writeFile(
      join(artifacts, 'report-overview-mobile.png'),
      Buffer.from(reportMobile.data, 'base64'),
    );
    await send('Emulation.setDeviceMetricsOverride', {
      width: 1440,
      height: 1000,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await evaluate(
      `Array.from(document.querySelectorAll('.report-section-tabs [role="tab"]')).find(tab => tab.textContent === '个股研究').click()`,
    );
    await until(
      () => evaluate(`Boolean(document.querySelector('.price-volume-study'))`),
      '量价研究卡片已展示',
    );
    assert(
      detail.analysis.priceVolume.every(
        (item) => item.status === 'DEMO' && item.stopReference == null,
      ),
    );
    assert(
      await evaluate(
        `document.querySelectorAll('.price-volume-study .price-volume-levels').length === 0`,
      ),
      '演示输入不得展示真实压力和止损价格',
    );
    assert(
      detail.analysis.priceVolume.every(
        (item) =>
          item.strategies.length === 4 &&
          item.strategies.every(
            (plan) => plan.status === 'UNAVAILABLE' && plan.observationZone == null,
          ),
      ),
    );
    await until(
      () => evaluate(`document.querySelectorAll('[data-strategy]').length >= 4`),
      '条件式策略卡片已展示',
    );
    assert(
      await evaluate(`document.querySelectorAll('.position-plan .reference-price').length === 0
      && !document.querySelector('.price-reference-summary')`),
      '演示不生成候选买入价格或关键价位速览',
    );
    assert(
      await evaluate(
        `Array.from(document.querySelectorAll('main a')).some(link => link.getAttribute('href')?.startsWith('#/stocks/') && link.getAttribute('href').includes('date=2000-01-04'))`,
      ),
      '从报告回看显式指定冻结日期',
    );
    await evaluate(`document.querySelector('.rule-status-guide').open = true`);
    await click('button[aria-label="未触发：查看说明"]');
    await until(
      () =>
        evaluate(
          `document.querySelector('[data-testid="research-help-popover"]')?.textContent.includes('必要数据足够')`,
        ),
      '规则状态解释可打开',
    );
    await evaluate(
      `new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))`,
    );
    assert(
      await evaluate(
        `(() => { const node = document.querySelector('[data-testid="research-help-popover"]'); if (!node) return false; const rect = node.getBoundingClientRect(); return rect.width > 0 && rect.height > 0 && rect.top >= 0 && rect.bottom <= innerHeight && rect.left >= 0 && rect.right <= innerWidth; })()`,
      ),
      '说明弹层稳定可见且未超出视口',
    );
    const stateHelpImage = await send('Page.captureScreenshot', { format: 'png' });
    await writeFile(
      join(artifacts, 'rule-status-help.png'),
      Buffer.from(stateHelpImage.data, 'base64'),
    );
    await send('Input.dispatchKeyEvent', {
      type: 'keyDown',
      key: 'Escape',
      code: 'Escape',
      windowsVirtualKeyCode: 27,
    });
    await until(
      () => evaluate(`!document.querySelector('[data-testid="research-help-popover"]')`),
      'Escape关闭帮助',
    );
    await navigate('/help');
    await until(
      () => evaluate(`Boolean(document.querySelector('[data-testid="product-guide"]'))`),
      '产品使用说明',
    );
    assert(
      await evaluate(
        `document.querySelectorAll('.guide-steps > li').length === 6 && document.querySelector('[data-testid="product-guide"]').textContent.includes('怎样读懂买卖计划')`,
      ),
      '六步操作和买卖计划说明',
    );
    assert(
      await evaluate(`!document.querySelector('.research-help-search')`),
      '使用说明不渲染术语搜索',
    );
    await navigate('/help?view=terms');
    await until(
      () =>
        evaluate(
          `document.querySelector('main h1')?.textContent.includes('术语与状态') && Boolean(document.querySelector('.research-help-search')?.getClientRects().length)`,
        ),
      '术语帮助入口',
    );
    await evaluate(
      `Array.from(document.querySelectorAll('.help-category-nav button')).find(button => button.textContent === '价格与量能').click()`,
    );
    await until(
      () =>
        evaluate(
          `Boolean(document.querySelector('#term-ma')) && !document.querySelector('#term-cashflow') && !document.querySelector('.rule-status-guide')`,
        ),
      '术语分类独立显示',
    );
    const categoryImage = await send('Page.captureScreenshot', { format: 'png' });
    await writeFile(
      join(artifacts, 'research-categories.png'),
      Buffer.from(categoryImage.data, 'base64'),
    );
    await evaluate(
      `Array.from(document.querySelectorAll('.help-category-nav button')).find(button => button.textContent === '数据与任务状态').click()`,
    );
    await until(
      () =>
        evaluate(
          `document.querySelectorAll('.status-group').length === 3 && Array.from(document.querySelectorAll('.status-group')).every(group => !group.open)`,
        ),
      '运行状态按用途折叠',
    );
    await evaluate(
      `(() => { const input = document.querySelector('[aria-label="搜索术语或状态"]'); input.value = 'ATR14'; input.dispatchEvent(new Event('input', { bubbles: true })); })()`,
    );
    await until(
      () =>
        evaluate(
          `document.querySelector('#term-atr14')?.textContent.includes('Wilder') && document.querySelector('#term-atr14').open`,
        ),
      '术语搜索与实际指标口径',
    );
    await evaluate(`document.querySelector('#term-atr14').scrollIntoView({ block: 'center' })`);
    await evaluate(
      `new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))`,
    );
    const glossaryImage = await send('Page.captureScreenshot', { format: 'png' });
    await writeFile(
      join(artifacts, 'research-terms.png'),
      Buffer.from(glossaryImage.data, 'base64'),
    );
    await navigate('/channels');
    await until(
      () =>
        evaluate(
          `Boolean(document.querySelector('.channel-selector button')) && document.body.textContent.includes('通道原因与处理')`,
        ),
      '通道分组与实际证据',
    );
    const budget = await api('/market/providers/baostock-policy');
    assert.equal(budget.error, null, '只读预算接口正常');
    assert(Number.isInteger(budget.used) && budget.remaining >= 0);
    assert(
      await evaluate(
        `document.querySelectorAll('.channel-issues .failure-explanation').length === 0`,
      ),
      '演示中未支持的研究能力不能伪装成访问故障',
    );
    await evaluate(
      `Array.from(document.querySelectorAll('.section-tabs__navigation [role="tab"]')).find(tab => tab.textContent.trim() === '能力对照').click()`,
    );
    assert(
      await evaluate(
        `document.querySelector('.capability-matrix').textContent.includes('正常跳过')`,
      ),
      '不支持能力正常跳过',
    );
    await evaluate(
      `Array.from(document.querySelectorAll('.section-tabs__navigation [role="tab"]')).find(tab => tab.textContent.trim() === '已入库数据').click()`,
    );
    assert(
      await evaluate(
        `document.querySelector('[role="tabpanel"]:not([style*="display: none"])').textContent.includes('已入库数据来源')`,
      ),
      '实际入库证据单独查看',
    );
    const failureImage = await send('Page.captureScreenshot', { format: 'png' });
    await writeFile(
      join(artifacts, 'channel-classification.png'),
      Buffer.from(failureImage.data, 'base64'),
    );
    await evaluate(
      `Array.from(document.querySelectorAll('.section-tabs__navigation button')).find(tab => tab.textContent.trim() === '通道测试').click()`,
    );
    await until(
      () =>
        evaluate(
          `Boolean(document.querySelector('[data-testid="test-provider"]')?.getClientRects().length)`,
        ),
      '显式通道测试入口',
    );
    await click('[data-testid="test-provider"]');
    await until(
      () =>
        evaluate(
          `document.querySelector('[data-testid="provider-test-result"]')?.textContent.includes('演示模式不核验真实连通性')`,
        ),
      '演示测试不能冒充真实连通',
    );
    assert(
      await evaluate(
        `document.querySelector('[data-testid="provider-test-result"]').textContent.includes('本次未执行上游核验') && !document.querySelector('.probe-sample')`,
      ),
      '跳过不能显示零条成功',
    );
    const probeImage = await send('Page.captureScreenshot', { format: 'png' });
    await writeFile(
      join(artifacts, 'channel-probe-demo.png'),
      Buffer.from(probeImage.data, 'base64'),
    );
    await navigate('/backtest');
    await until(
      () =>
        evaluate(
          `Boolean(document.querySelector('.section-tabs__navigation [aria-selected="true"]'))`,
        ),
      '历史信号分类',
    );
    assert.equal(
      await evaluate(
        `document.querySelector('.section-tabs__navigation [aria-selected="true"]').textContent.trim()`,
      ),
      '历史规律验证',
    );
    assert(
      await evaluate(
        `document.querySelector('.section-tabs__panel[aria-labelledby$="-validation"]').getClientRects().length > 0`,
      ),
      '默认展示逐日重建验证',
    );
    await click('.section-tabs__navigation button:nth-child(2)');
    for (const [name, width, height] of [
      ['desktop', 1440, 1000],
      ['mobile', 390, 844],
    ]) {
      await send('Emulation.setDeviceMetricsOverride', {
        width,
        height,
        deviceScaleFactor: 1,
        mobile: false,
      });
      await until(
        () =>
          evaluate(
            `!document.querySelector('.section-tabs__panel[aria-labelledby$="-signals"] .loading-state')`,
          ),
        '信号回顾加载',
      );
      await evaluate(
        `window.scrollTo(0, 0); new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))`,
      );
      assert(
        await evaluate(
          `document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1`,
        ),
        '信号回顾页面不溢出 ' + name,
      );
      const image = await send('Page.captureScreenshot', { format: 'png' });
      await writeFile(
        join(artifacts, 'backtest-signals-' + name + '.png'),
        Buffer.from(image.data, 'base64'),
      );
    }
    await click('.section-tabs__navigation button:nth-child(1)');
    await evaluate(`document.querySelector('[data-testid="validation-create"]').open = true`);
    await until(
      () =>
        evaluate(`Boolean(document.querySelector('[data-testid="submit-validation"]')?.disabled)`),
      '演示模式后台验证禁用',
    );
    assert(
      await evaluate(`document.querySelector('[data-testid="submit-validation"]').disabled`),
      '演示模式禁止提交真实后台验证',
    );
    assert(
      await evaluate(
        `Boolean(document.querySelector('[aria-label="验证开始日期"]') && document.querySelector('[aria-label="验证截止日期"]'))`,
      ),
      '验证日期可以独立选择',
    );
    await send('Emulation.setDeviceMetricsOverride', {
      width: 1440,
      height: 1000,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await until(
      () => evaluate(`document.querySelectorAll('.validation-form .el-date-editor').length === 2`),
      '日期控件统一为项目组件',
    );
    await evaluate(
      `document.querySelector('.validation-form').scrollIntoView({ block: 'center' })`,
    );
    await evaluate(
      `new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))`,
    );
    assert(
      await evaluate(
        `(() => { const form = document.querySelector('.validation-form'); const fields = [...form.querySelectorAll('.el-date-editor, .el-select')]; return fields.length === 4 && fields.every(el => Math.abs(el.getBoundingClientRect().height - 32) < 2) && form.scrollWidth <= form.clientWidth + 1; })()`,
      ),
      '桌面表单控件等高且不溢出',
    );
    const desktopForm = await send('Page.captureScreenshot', { format: 'png' });
    await writeFile(
      join(artifacts, 'validation-form-desktop.png'),
      Buffer.from(desktopForm.data, 'base64'),
    );
    await send('Emulation.setDeviceMetricsOverride', {
      width: 390,
      height: 844,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await evaluate(
      `new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))`,
    );
    await evaluate(
      `document.querySelector('.validation-form').scrollIntoView({ block: 'center' })`,
    );
    assert(
      await evaluate(
        `(() => { const form = document.querySelector('.validation-form'); const bounds = form.getBoundingClientRect(); return form.scrollWidth <= form.clientWidth + 1 && [...form.children].every(el => { const box = el.getBoundingClientRect(); return box.left >= bounds.left - 1 && box.right <= bounds.right + 1; }); })()`,
      ),
      '窄屏表单自动分行且不溢出',
    );
    const mobileForm = await send('Page.captureScreenshot', { format: 'png' });
    await writeFile(
      join(artifacts, 'validation-form-mobile.png'),
      Buffer.from(mobileForm.data, 'base64'),
    );
    await send('Emulation.setDeviceMetricsOverride', {
      width: 1000,
      height: 900,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await click('.section-tabs__navigation button:nth-child(3)');
    await until(
      () =>
        evaluate(`Boolean(document.querySelector('[data-testid="save-experiment"]')?.disabled)`),
      '演示模式阻止保存真实实验',
    );
    // 尊重全局写接口的一秒节流，不因新增通道测试而让后续独立写操作过快。
    await pause(1100);
    await click('[data-testid="reproduce-experiment-' + process.env.E2E_EXPERIMENT_ID + '"]');
    await until(
      () =>
        evaluate(
          `document.querySelector('[data-testid="experiment-replay-result"]')?.textContent.includes('复算结果一致')`,
        ),
      '冻结实验浏览器复算',
    );
    await navigate('/operations');
    await until(
      () =>
        evaluate(
          `document.querySelector('[data-testid="operations-overview"]')?.textContent.includes('未配置')`,
        ),
      '第二存储未配置状态',
    );
    assert(
      await evaluate(
        `document.querySelector('[data-testid="operations-overview"]').textContent.includes('未启用')`,
      ),
    );
    const themes = await api('/themes');
    const layoutRoutes = [
      ['/dashboard', 'dashboard'],
      ['/themes', 'themes'],
      ['/themes/' + themes[0].code, 'theme-detail'],
      ['/watchlist', 'watchlist'],
      ['/stocks/300476.SZ', 'stock-detail'],
      ['/expectations', 'expectations'],
      ['/portfolio', 'portfolio'],
      ['/reports', 'reports'],
      ['/jobs', 'jobs'],
      ['/channels', 'channels'],
      ['/operations', 'operations'],
      ['/product', 'product'],
      ['/help', 'help'],
      ['/terms', 'terms'],
    ];
    const layoutChecks = [];
    for (const [path, name] of layoutRoutes) {
      await navigate(path);
      await until(
        () =>
          evaluate(
            `![...document.querySelectorAll('.async-state[aria-busy="true"]')].some(el => el.getClientRects().length)`,
          ),
        name + ' 已加载',
      );
      if (name === 'stock-detail') {
        await until(
          () =>
            evaluate(`document.querySelectorAll('.holding-horizon-selector button').length === 3`),
          '三周期研究入口',
        );
        await click('.holding-horizon-selector button:nth-child(3)');
        assert(
          await evaluate(
            `document.querySelector('[data-testid="holding-horizon"]').textContent.includes('当前不输出可执行价位') && !document.querySelector('.holding-action__prices')`,
          ),
          '演示不生成长期真实买卖价',
        );
        await click('.holding-horizon-selector button:first-child');
      }
      if (name === 'themes') {
        assert(
          await evaluate(
            `document.body.textContent.includes('待建设与待核对') && document.querySelectorAll('.theme-card').length === 5`,
          ),
          '演示空分类明确显示建设状态',
        );
      }
      if (name === 'theme-detail') {
        assert(
          await evaluate(
            `document.body.textContent.includes('该主题尚未建立有效研究样本') && !document.querySelector('.theme-observation')`,
          ),
          '空分类不伪造评分与覆盖',
        );
      }
      for (const [size, width, height] of [
        ['desktop', 1440, 1000],
        ['mobile', 390, 844],
      ]) {
        await send('Emulation.setDeviceMetricsOverride', {
          width,
          height,
          deviceScaleFactor: 1,
          mobile: false,
        });
        await evaluate(
          `window.scrollTo(0, 0); new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))`,
        );
        assert(
          await evaluate(
            `document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1`,
          ),
          name + ' 页面不溢出 ' + size,
        );
        const image = await send('Page.captureScreenshot', { format: 'png' });
        await writeFile(
          join(artifacts, 'layout-' + name + '-' + size + '.png'),
          Buffer.from(image.data, 'base64'),
        );
        await evaluate(
          `document.querySelectorAll('details[data-testid="legacy-theme-sample"]').forEach(item => item.open = true)`,
        );
        const tabSelectors = await evaluate(
          `(() => { const nav=Array.from(document.querySelectorAll('.section-tabs__navigation')).find(x=>x.getClientRects().length); return nav?Array.from(nav.querySelectorAll('button')).map(x=>'#'+CSS.escape(x.id)):[]; })()`,
        );
        for (let index = 1; index < tabSelectors.length; index++) {
          await click(tabSelectors[index]);
          await evaluate(
            `new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))`,
          );
          assert(
            await evaluate(
              `document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1`,
            ),
            name + ' 分类 ' + index + ' 不溢出 ' + size,
          );
        }
        if (tabSelectors.length) await click(tabSelectors[0]);
        layoutChecks.push(name + '-' + size);
      }
    }
    await navigate('/dashboard');
    await until(
      () => evaluate(`document.body.textContent.includes('MARKET_RULES_V3')`),
      '透明五维模型',
    );
    assert.equal(failedResponses.length, 0, JSON.stringify(failedResponses));
    assert.equal(exceptions.length, 0, exceptions.join('\n'));
    const proof = {
      browser: 'Chrome headless',
      backend: 'isolated Testcontainers DEMO',
      firstJob: first.id,
      retryJob: retry.id,
      report: report.id,
      checks: [
        'submit',
        'running-cancel',
        'durable-cancelled',
        'same-id-retry-history',
        'report-detail',
        'report-tabs-and-responsive-overview',
        'synthetic-guard',
        'price-volume-demo-guard',
        'position-strategy-demo-guard',
        'rule-status-popover-and-keyboard',
        'terminology-search',
        'terminology-categories-and-status-groups',
        'baostock-readonly-budget-diagnostics',
        'provider-classification-and-no-false-access-failure',
        'explicit-provider-probe-demo-guard',
        'research-validation-entry-and-demo-guard',
        'validation-form-desktop-and-mobile-layout',
        'research-date-survives-full-reload',
        'frozen-report-hides-unrelated-date-filter',
        'report-review-links-carry-frozen-date',
        'backtest-categorized-reading',
        'workspace-routes-desktop-mobile-overflow',
        'theme-research-coverage-and-empty-state',
        'product-guide-and-module-workflow',
        'separate-guide-glossary-and-legacy-deep-links',
        'holding-periods-and-demo-price-guard',
        'frozen-experiment-replay',
        'operations-status',
        'scoped-score',
      ],
      layoutChecks,
      uncaughtExceptions: exceptions.length,
      serverErrors: failedResponses.length,
    };
    await writeFile(join(artifacts, 'workflow.json'), JSON.stringify(proof, null, 2));
    const screenshot = await send('Page.captureScreenshot', { format: 'png' });
    await writeFile(join(artifacts, 'workflow.png'), Buffer.from(screenshot.data, 'base64'));
    console.log(JSON.stringify(proof));
  }
} catch (error) {
  if (socket && session) {
    try {
      const shot = await send('Page.captureScreenshot', { format: 'png' });
      await writeFile(join(artifacts, 'failure.png'), Buffer.from(shot.data, 'base64'));
      await writeFile(
        join(artifacts, 'failure-dom.html'),
        await evaluate('document.documentElement.outerHTML'),
      );
    } catch {
      /* 原异常优先。 */
    }
  }
  await writeFile(join(artifacts, 'browser-diagnostics.log'), diagnostics);
  await writeFile(
    join(artifacts, 'failure-network.json'),
    JSON.stringify({ failedResponses, exceptions }, null, 2),
  );
  throw error;
} finally {
  socket?.close();
  for (const item of pending.values()) {
    clearTimeout(item.timer);
    item.reject(Error('浏览器会话结束'));
  }
  pending.clear();
  chrome.kill('SIGTERM');
  await Promise.race([new Promise((resolve) => chrome.once('exit', resolve)), pause(3000)]);
  if (chrome.exitCode === null) chrome.kill('SIGKILL');
  // Chrome辅助进程可能继承stderr，必须关闭本测试持有的管道，避免成功后Node仍等待退出。
  chrome.stderr.destroy();
  server.httpServer?.closeAllConnections();
  console.log('browser cleanup: closing Vite');
  await server.close();
  console.log('browser cleanup: removing isolated profile');
  await rm(profile, { recursive: true, force: true });
  console.log('browser cleanup completed', process.getActiveResourcesInfo());
}
