const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const BASE_URL = process.env.PW_BASE_URL || 'http://localhost:8081';
const API_BASE = process.env.PW_API_BASE || 'http://localhost:8080';
const LOGIN_USER = process.env.PW_USERNAME || 'admin';
const LOGIN_PASSWORD = process.env.PW_PASSWORD || 'admin123';
const HEADLESS = !process.argv.includes('--headed');
const SUITE = process.env.PW_SUITE || 'all';

const ROOT_DIR = path.resolve(__dirname, '..');
const OUT_DIR = path.join(ROOT_DIR, 'runtime-logs');
const SCREENSHOT_DIR = path.join(OUT_DIR, 'playwright-pages');
const FINAL_SCREENSHOT = path.join(OUT_DIR, 'playwright-final.png');
const REPORT_PATH = path.join(OUT_DIR, 'playwright-smoke-report.json');

const ERROR_KEYWORDS = [
  'Error querying database',
  'bad SQL grammar',
  'Exception',
  '请求失败',
  '操作失败',
  'Internal Server Error'
];

const SCENARIOS = [
  { name: '首页', path: '/index', markers: ['若依后台管理框架'], suites: ['all'] },
  { name: '系统-用户管理', path: '/system/user', markers: ['用户名称'], suites: ['all'] },
  { name: '系统-角色管理', path: '/system/role', markers: ['角色名称'], suites: ['all'] },
  { name: '系统-菜单管理', path: '/system/menu', markers: ['菜单名称'], suites: ['all'] },
  { name: '系统-部门管理', path: '/system/dept', markers: ['部门名称'], suites: ['all'] },
  { name: '系统-岗位管理', path: '/system/post', markers: ['岗位编码'], suites: ['all'] },
  { name: '系统-字典管理', path: '/system/dict', markers: ['字典名称'], suites: ['all'] },
  { name: '系统-参数配置', path: '/system/config', markers: ['参数名称'], suites: ['all'] },
  { name: '系统-通知公告', path: '/system/notice', markers: ['公告标题'], suites: ['all'] },
  { name: '系统-操作日志', path: '/system/log/operlog', markers: ['系统模块'], suites: ['all'] },
  { name: '系统-登录日志', path: '/system/log/logininfor', markers: ['登录地址'], suites: ['all'] },
  {
    name: '量化-看板',
    path: '/quant/dashboard',
    markers: ['量化看板', '今日状态', '今日主动作', '下一步去哪里', '对象层摘要'],
    suites: ['all', 'quant'],
    action: runQuantDashboardExecutionShortcut
  },
  {
    name: '量化-影子对比',
    path: '/quant/shadow',
    markers: ['影子对比', '治理结论', '治理建议', '提交治理动作'],
    suites: ['all', 'quant'],
    action: runQuantShadowCompareQuery
  },
  {
    name: '量化-调度中心',
    path: '/quant/jobs',
    markers: ['量化调度中心', '调度中心', '最近 3 条调度结果', '最近进展与技术摘要', '调度定义'],
    suites: ['all', 'quant'],
    action: runQuantJobSubmission
  },
  {
    name: '量化-手工调度',
    path: '/quant/dispatch-manual',
    markers: ['手工调度', '第 1 步：选择策略', '第 2 步：确定数据范围', '第 3 步：确认执行范围'],
    suites: ['all', 'quant'],
    action: runQuantManualDispatchPreview
  },
  {
    name: '量化-自动调度',
    path: '/quant/dispatch-auto',
    markers: ['自动调度', '调度中心 / 自动触发视图', '调度定义', '最近执行历史', '异常与处理'],
    suites: ['all', 'quant']
  },
  {
    name: '量化-调度详情',
    path: resolveDispatchDetailPath,
    markers: ['调度详情', '本次执行了什么', '结果与异常', '最近一条日志', '结果明细'],
    suites: ['all', 'quant']
  },
  {
    name: '量化-运维中心',
    path: '/quant/operations',
    markers: ['运维中心', '当前阻断', '建议恢复', '高级兜底工具'],
    suites: ['all', 'quant']
  },
  {
    name: '量化-标的体系',
    path: '/quant/symbols',
    markers: ['标的体系', '当前正式范围', '股票池总览', '范围预览'],
    suites: ['all', 'quant']
  },
  {
    name: '量化-回测研究',
    path: '/quant/backtest',
    markers: ['回测研究', '当前研究配置', '研究结果摘要', '更多研究证据'],
    suites: ['all', 'quant']
  },
  {
    name: '量化-执行回写',
    path: '/quant/execution',
    markers: ['今日闭环状态', '异常优先级', '当前异常列表', '手工处理工具', '辅助核对与持仓同步'],
    suites: ['all', 'quant']
  },
  {
    name: '量化-复盘分析',
    path: '/quant/review',
    markers: ['复盘分析', '当前结论', '结论动作', '核心证据'],
    suites: ['all', 'quant']
  }
];

function selectScenarios() {
  const selected = SCENARIOS.filter((scenario) => {
    const suites = Array.isArray(scenario.suites) ? scenario.suites : ['all'];
    return suites.includes(SUITE);
  });
  if (!selected.length) {
    throw new Error(`Unknown or empty suite: ${SUITE}`);
  }
  return selected;
}

function ensureDirs() {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function sanitizeName(input) {
  return input.replace(/[^\w\u4e00-\u9fa5-]+/g, '_');
}

function isLocalUrl(url) {
  return url.includes('localhost') || url.includes('127.0.0.1');
}

function isIgnorableHttpError(url) {
  return url.endsWith('/favicon.ico');
}

function isCriticalRequestFailure(entry) {
  if (entry.failure === 'net::ERR_ABORTED' && entry.method === 'POST' && /\/(dev-api\/)?login(?:\?|$)/.test(entry.url)) {
    return false;
  }
  if (entry.failure === 'net::ERR_ABORTED' && /\/sockjs-node\/.*\/xhr_streaming(?:\?|$)/.test(entry.url)) {
    return false;
  }
  return !(entry.failure === 'net::ERR_ABORTED' && entry.method === 'GET');
}

function shouldRecordRequestFailure(entry) {
  return isCriticalRequestFailure(entry);
}

async function postJson(url, payload, timeoutMs = 60000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal: controller.signal
    });
    let data = {};
    try {
      data = await response.json();
    } catch (error) {
      data = {};
    }
    return { ok: response.ok, status: response.status, data };
  } finally {
    clearTimeout(timer);
  }
}

async function getJson(url, timeoutMs = 60000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      method: 'GET',
      signal: controller.signal
    });
    let data = {};
    try {
      data = await response.json();
    } catch (error) {
      data = {};
    }
    return { ok: response.ok, status: response.status, data };
  } finally {
    clearTimeout(timer);
  }
}

async function getAuthJson(url, token, timeoutMs = 60000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`
      },
      signal: controller.signal
    });
    let data = {};
    try {
      data = await response.json();
    } catch (error) {
      data = {};
    }
    return { ok: response.ok, status: response.status, data };
  } finally {
    clearTimeout(timer);
  }
}

async function launchBrowser() {
  const attempts = [
    { channel: 'chrome', headless: HEADLESS },
    { channel: 'msedge', headless: HEADLESS },
    { headless: HEADLESS }
  ];
  const errors = [];
  for (const option of attempts) {
    try {
      const browser = await chromium.launch(option);
      return { browser, used: option, fallbackErrors: errors };
    } catch (error) {
      errors.push({ option, error: String(error) });
    }
  }
  throw new Error(`Failed to launch browser: ${JSON.stringify(errors, null, 2)}`);
}

async function waitForText(page, text, timeout = 30000) {
  await page.waitForFunction(
    (target) => {
      if (!document.body) return false;
      return document.body.innerText.includes(target);
    },
    text,
    { timeout }
  );
}

async function login(page, report) {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 });
  report.steps.push('打开登录页');

  let captchaCheck = await getJson(`${BASE_URL}/dev-api/captchaImage`, 30000).catch(() => null);
  if (!captchaCheck || !captchaCheck.ok) {
    captchaCheck = await getJson(`${API_BASE}/captchaImage`, 30000);
  }
  const captchaPayload = (captchaCheck && captchaCheck.data) || {};
  report.steps.push(`captchaEnabled=${captchaPayload.captchaEnabled}`);
  if (captchaPayload.captchaEnabled) {
    throw new Error('当前环境验证码仍启用，自动化登录无法继续。请先关闭验证码后重试。');
  }

  await page.waitForFunction(() => !document.querySelector('input[placeholder="验证码"]'), null, {
    timeout: 15000
  });

  let uiLoginOk = false;
  let loginToken = null;
  try {
    const usernameInput = page.locator('input[placeholder="账号"], input[placeholder="用户名"], input[name="username"]').first();
    const passwordInput = page.locator('input[placeholder="密码"], input[type="password"], input[name="password"]').first();
    const loginButton = page.locator('.login-form .el-button--primary, button:has-text("登 录"), button:has-text("登录")').first();

    await usernameInput.fill(LOGIN_USER);
    await passwordInput.fill(LOGIN_PASSWORD);

    const loginResponsePromise = page
      .waitForResponse(
        (response) => /\/(dev-api\/)?login(?:\?|$)/.test(response.url()) && response.request().method() === 'POST',
        { timeout: 30000 }
      )
      .catch(() => null);

    await loginButton.click();
    const loginResponse = await loginResponsePromise;
    if (loginResponse) {
      const payload = await loginResponse.json().catch(() => ({}));
      report.steps.push(`登录接口返回 code=${payload.code}`);
      if (payload.code !== 200) {
        throw new Error(`登录失败: ${JSON.stringify(payload)}`);
      }
      loginToken = payload.token || null;
    } else {
      report.steps.push('未捕获到登录接口响应，改用前端状态判定');
    }

    await page.waitForFunction(() => !window.location.pathname.includes('/login'), null, { timeout: 30000 });
    await page.waitForSelector('.sidebar-container', { timeout: 30000 });
    uiLoginOk = true;
    report.steps.push('UI 登录成功并进入后台');
  } catch (error) {
    report.steps.push(`UI 登录失败，切换 API 登录: ${String(error)}`);
  }

  if (!uiLoginOk) {
    let loginPayload = null;
    try {
      const proxyResponse = await postJson(`${BASE_URL}/dev-api/login`, { username: LOGIN_USER, password: LOGIN_PASSWORD }, 30000);
      if (proxyResponse.ok) {
        loginPayload = proxyResponse.data;
      } else {
        report.steps.push(`代理登录接口 HTTP ${proxyResponse.status}，切换直连后端`);
      }
    } catch (error) {
      report.steps.push(`代理登录接口异常，切换直连后端: ${String(error)}`);
    }

    if (!loginPayload || loginPayload.code !== 200 || !loginPayload.token) {
      const directResponse = await postJson(`${API_BASE}/login`, { username: LOGIN_USER, password: LOGIN_PASSWORD }, 60000);
      if (!directResponse.ok) {
        throw new Error(`API 登录失败，HTTP ${directResponse.status}`);
      }
      loginPayload = directResponse.data;
    }
    if (loginPayload.code !== 200 || !loginPayload.token) {
      throw new Error(`API 登录失败: ${JSON.stringify(loginPayload)}`);
    }
    loginToken = loginPayload.token;
    const baseHost = new URL(BASE_URL).hostname;
    await page.context().addCookies([
      {
        name: 'Admin-Token',
        value: String(loginPayload.token),
        domain: baseHost,
        path: '/'
      }
    ]);
    await page.goto(`${BASE_URL}/index`, { waitUntil: 'domcontentloaded', timeout: 60000 });
    await page.waitForSelector('.sidebar-container', { timeout: 60000 });
    report.steps.push('API 登录注入成功并进入后台');
  }

  report._authToken = loginToken;
}

async function prepareQuantContext(report) {
  if (!report._authToken) {
    return;
  }
  const historyResponse = await getAuthJson(`${API_BASE}/quant/data/dispatchHistory?pageNum=1&pageSize=10`, report._authToken, 60000);
  const rows = historyResponse && historyResponse.data && historyResponse.data.data && Array.isArray(historyResponse.data.data.rows)
    ? historyResponse.data.data.rows
    : [];
  const latestJob = rows.find(item => item && item.jobId) || null;
  report.quantContext = {
    latestDispatchJobId: latestJob ? latestJob.jobId : null
  };
  if (latestJob && latestJob.jobId) {
    report.steps.push(`量化上下文: latestDispatchJobId=${latestJob.jobId}`);
  } else {
    report.steps.push('量化上下文: 未找到可用调度详情 jobId');
  }
}

async function runQuantShadowCompareQuery(page, scenarioResult) {
  const queryButton = page.locator('button:has-text("查询")').first();
  if (!(await queryButton.count())) {
    scenarioResult.warnings.push('未找到影子对比查询按钮，跳过主动查询');
    return;
  }
  const responsePromise = page
    .waitForResponse(
      (response) => response.url().includes('/quant/data/shadowCompare') && response.request().method() === 'GET',
      { timeout: 60000 }
    )
    .catch(() => null);
  await queryButton.click();
  const response = await responsePromise;
  if (!response) {
    scenarioResult.warnings.push('影子对比查询未捕获到接口响应');
    return;
  }
  if (response.status() >= 400) {
    scenarioResult.errors.push(`影子对比查询返回 HTTP ${response.status()}`);
  } else {
    scenarioResult.steps.push(`影子对比查询 HTTP ${response.status()}`);
  }
}

async function runQuantJobSubmission(page, scenarioResult) {
  const manualDispatchButton = page.locator('button:has-text("发起手工调度")').first();
  if (!(await manualDispatchButton.count())) {
    scenarioResult.errors.push('未找到“发起手工调度”按钮');
    return;
  }

  await manualDispatchButton.click();
  await page.waitForURL((url) => url.pathname.includes('/quant/dispatch-manual'), {
    timeout: 30000
  });
  scenarioResult.steps.push('已从调度中心进入手工调度页');

  await waitForText(page, '第 3 步：确认执行范围', 30000);

  const symbolsInput = page.locator('textarea[placeholder*="逗号或换行分隔"], textarea[placeholder*="直接覆盖预设范围"]').first();
  if (!(await symbolsInput.count())) {
    scenarioResult.errors.push('未找到手工调度页的范围输入框');
    return;
  }
  await symbolsInput.fill('510300');

  const previewButton = page.locator('button:has-text("刷新范围预览")').first();
  if (!(await previewButton.count())) {
    scenarioResult.errors.push('未找到“刷新范围预览”按钮');
    return;
  }

  const previewResponsePromise = page
    .waitForResponse(
      (response) => /\/(dev-api\/)?quant\/data\/symbolScopePreview(?:\?|$)/.test(response.url()) && response.request().method() === 'GET',
      { timeout: 60000 }
    )
    .catch(() => null);
  await previewButton.click();
  const previewResponse = await previewResponsePromise;
  if (!previewResponse) {
    scenarioResult.errors.push('未捕获到范围预览响应');
    return;
  }
  if (previewResponse.status() >= 400) {
    scenarioResult.errors.push(`范围预览失败: HTTP ${previewResponse.status()}`);
    return;
  }
  const previewPayload = await previewResponse.json().catch(() => ({}));
  const previewData = previewPayload.data || {};
  scenarioResult.steps.push(`范围预览 resolvedCount=${previewData.resolvedCount || 0}`);
  if (previewPayload.code !== 200 || Number(previewData.resolvedCount || 0) <= 0) {
    scenarioResult.errors.push(`范围预览无可执行标的: code=${previewPayload.code} resolvedCount=${previewData.resolvedCount || 0}`);
    return;
  }
  scenarioResult.steps.push('手工调度页范围预览成功');
}

async function runQuantManualDispatchPreview(page, scenarioResult) {
  const previewButton = page.locator('button:has-text("刷新范围预览")').first();
  if (!(await previewButton.count())) {
    scenarioResult.errors.push('未找到手工调度页“刷新范围预览”按钮');
    return;
  }

  const previewResponsePromise = page
    .waitForResponse(
      (response) => /\/(dev-api\/)?quant\/data\/symbolScopePreview(?:\?|$)/.test(response.url()) && response.request().method() === 'GET',
      { timeout: 60000 }
    )
    .catch(() => null);
  await previewButton.click();
  const previewResponse = await previewResponsePromise;
  if (!previewResponse) {
    scenarioResult.errors.push('手工调度页未捕获到范围预览响应');
    return;
  }
  if (previewResponse.status() >= 400) {
    scenarioResult.errors.push(`手工调度页范围预览失败: HTTP ${previewResponse.status()}`);
    return;
  }
  const previewPayload = await previewResponse.json().catch(() => ({}));
  const previewData = previewPayload.data || {};
  scenarioResult.steps.push(`手工调度页范围预览 resolvedCount=${previewData.resolvedCount || 0}`);
}

function resolveDispatchDetailPath(report) {
  const jobId = report.quantContext && report.quantContext.latestDispatchJobId;
  if (!jobId) {
    return null;
  }
  return `/quant/dispatch-detail/${jobId}`;
}

async function runQuantDashboardExecutionShortcut(page, scenarioResult) {
  const shortcutButton = page.locator('button:has-text("去执行闭环")').first();
  if (!(await shortcutButton.count())) {
    scenarioResult.errors.push('未找到“去执行闭环”入口按钮');
    return;
  }
  await shortcutButton.click();
  await page.waitForURL((url) => url.pathname.includes('/quant/execution'), {
    timeout: 30000
  });
  scenarioResult.steps.push('执行页快捷入口跳转成功');
  await waitForText(page, '今日闭环状态', 30000);
}

async function visitScenario(page, scenario, report) {
  const scenarioStart = new Date().toISOString();
  const scenarioResult = {
    name: scenario.name,
    path: scenario.path,
    startedAt: scenarioStart,
    pass: false,
    errors: [],
    warnings: [],
    missingMarkers: [],
    keywordHits: [],
    steps: []
  };

  try {
    const resolvedPath = typeof scenario.path === 'function' ? scenario.path(report) : scenario.path;
    if (!resolvedPath) {
      scenarioResult.errors.push('场景缺少可访问路径');
      return;
    }
    scenarioResult.path = resolvedPath;
    await page.goto(`${BASE_URL}${resolvedPath}`, { waitUntil: 'domcontentloaded', timeout: 60000 });
    scenarioResult.steps.push('路由跳转成功');

    await page.waitForSelector('.app-main', { timeout: 60000 });
    scenarioResult.steps.push('主内容容器已加载');

    for (const marker of scenario.markers || []) {
      try {
        await waitForText(page, marker, 30000);
      } catch (error) {
        scenarioResult.missingMarkers.push(marker);
      }
    }

    if (typeof scenario.action === 'function') {
      await scenario.action(page, scenarioResult);
    }

    await page.waitForTimeout(600);
    const bodyText = await page.locator('body').innerText();
    for (const keyword of ERROR_KEYWORDS) {
      if (bodyText.includes(keyword)) {
        scenarioResult.keywordHits.push(keyword);
      }
    }
    const notFound = bodyText.includes('404') && bodyText.includes('抱歉');
    if (notFound) {
      scenarioResult.errors.push('页面命中 404');
    }
    if (scenarioResult.missingMarkers.length) {
      scenarioResult.errors.push(`关键内容缺失: ${scenarioResult.missingMarkers.join(', ')}`);
    }
    if (scenarioResult.keywordHits.length) {
      scenarioResult.errors.push(`页面错误关键字命中: ${scenarioResult.keywordHits.join(', ')}`);
    }

    const shotName = `${sanitizeName(scenario.name)}.png`;
    const shotPath = path.join(SCREENSHOT_DIR, shotName);
    await page.screenshot({ path: shotPath, fullPage: true });
    scenarioResult.screenshot = shotPath;

    scenarioResult.pass = scenarioResult.errors.length === 0;
  } catch (error) {
    scenarioResult.errors.push(String(error));
  } finally {
    scenarioResult.finishedAt = new Date().toISOString();
    report.scenarios.push(scenarioResult);
    report.steps.push(`${scenario.name}: ${scenarioResult.pass ? 'PASS' : 'FAIL'}`);
  }
}

async function main() {
  ensureDirs();

  const report = {
    startedAt: new Date().toISOString(),
    baseUrl: BASE_URL,
    suite: SUITE,
    launch: null,
    account: LOGIN_USER,
    headless: HEADLESS,
    steps: [],
    scenarios: [],
    consoleErrors: [],
    pageErrors: [],
    requestFailed: [],
    ignoredRequestFailed: [],
    httpErrors: [],
    success: false,
    failureReason: null
  };

  let browser;
  let context;
  let page;
  let activeScenario = '初始化';

  try {
    const launched = await launchBrowser();
    browser = launched.browser;
    report.launch = {
      used: launched.used,
      fallbackErrors: launched.fallbackErrors
    };

    context = await browser.newContext({ viewport: { width: 1600, height: 900 } });
    page = await context.newPage();

    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        report.consoleErrors.push({
          scenario: activeScenario,
          text: msg.text(),
          location: msg.location()
        });
      }
    });
    page.on('pageerror', (error) => {
      report.pageErrors.push({
        scenario: activeScenario,
        error: String(error)
      });
    });
    page.on('requestfailed', (request) => {
      const url = request.url();
      if (isLocalUrl(url)) {
        const entry = {
          scenario: activeScenario,
          url,
          method: request.method(),
          failure: request.failure() ? request.failure().errorText : 'unknown'
        };
        if (shouldRecordRequestFailure(entry)) {
          report.requestFailed.push(entry);
        } else {
          report.ignoredRequestFailed.push(entry);
        }
      }
    });
    page.on('response', (response) => {
      const url = response.url();
      if (!isLocalUrl(url)) return;
      if (response.status() < 400) return;
      if (isIgnorableHttpError(url)) return;
      report.httpErrors.push({
        scenario: activeScenario,
        url,
        method: response.request().method(),
        status: response.status()
      });
    });

    activeScenario = '登录';
    await login(page, report);
    await prepareQuantContext(report);

    const scenarios = selectScenarios();
    for (const scenario of scenarios) {
      activeScenario = scenario.name;
      await visitScenario(page, scenario, report);
    }

    await page.screenshot({ path: FINAL_SCREENSHOT, fullPage: true });

    const failedScenarios = report.scenarios.filter((item) => !item.pass);
    const hasCriticalErrors =
      failedScenarios.length > 0 ||
      report.pageErrors.length > 0 ||
      report.requestFailed.length > 0 ||
      report.httpErrors.length > 0;

    report.success = !hasCriticalErrors;
    if (hasCriticalErrors) {
      report.failureReason = [
        failedScenarios.length ? `${failedScenarios.length} 个场景失败` : '',
        report.pageErrors.length ? `${report.pageErrors.length} 个 pageError` : '',
        report.requestFailed.length ? `${report.requestFailed.length} 个 requestFailed` : '',
        report.httpErrors.length ? `${report.httpErrors.length} 个 httpError` : ''
      ]
        .filter(Boolean)
        .join('; ');
    }
  } catch (error) {
    report.success = false;
    report.failureReason = String(error);
    try {
      if (page) {
        await page.screenshot({ path: FINAL_SCREENSHOT, fullPage: true });
      }
    } catch (shotError) {
      report.steps.push(`截图失败: ${String(shotError)}`);
    }
  } finally {
    delete report._authToken;
    report.finishedAt = new Date().toISOString();
    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), 'utf-8');
    if (context) await context.close();
    if (browser) await browser.close();
  }

  const failedScenarios = report.scenarios.filter((item) => !item.pass);
  console.log(
    JSON.stringify(
      {
        reportPath: REPORT_PATH,
        screenshotPath: FINAL_SCREENSHOT,
        success: report.success,
        suite: report.suite,
        failureReason: report.failureReason,
        scenarioTotal: report.scenarios.length,
        scenarioFailed: failedScenarios.map((item) => item.name),
        consoleErrors: report.consoleErrors.length,
        pageErrors: report.pageErrors.length,
        requestFailed: report.requestFailed.length,
        ignoredRequestFailed: report.ignoredRequestFailed.length,
        httpErrors: report.httpErrors.length
      },
      null,
      2
    )
  );

  if (!report.success) {
    process.exit(2);
  }
}

module.exports = {
  isCriticalRequestFailure,
  shouldRecordRequestFailure,
  main
};

if (require.main === module) {
  main();
}
