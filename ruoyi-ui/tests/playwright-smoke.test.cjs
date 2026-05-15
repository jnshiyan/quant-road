const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

const { isCriticalRequestFailure, shouldRecordRequestFailure } = require('../playwright-smoke.cjs');

test('treats sockjs streaming abort as non-critical', () => {
  assert.equal(
    isCriticalRequestFailure({
      failure: 'net::ERR_ABORTED',
      method: 'POST',
      url: 'http://localhost:8081/sockjs-node/602/gocplale/xhr_streaming?t=1778127707374'
    }),
    false
  );
});

test('keeps real API post aborts as critical', () => {
  assert.equal(
    isCriticalRequestFailure({
      failure: 'net::ERR_ABORTED',
      method: 'POST',
      url: 'http://localhost:8081/dev-api/quant/jobs/runPortfolio'
    }),
    true
  );
});

test('does not record ignorable aborted GET requests in report noise', () => {
  assert.equal(
    shouldRecordRequestFailure({
      failure: 'net::ERR_ABORTED',
      method: 'GET',
      url: 'http://localhost:8081/dev-api/quant/data/strategyCapabilities'
    }),
    false
  );
});

test('keeps record of critical aborted POST requests', () => {
  assert.equal(
    shouldRecordRequestFailure({
      failure: 'net::ERR_ABORTED',
      method: 'POST',
      url: 'http://localhost:8081/dev-api/quant/jobs/runPortfolio'
    }),
    true
  );
});

test('quant job smoke enters manual dispatch and validates symbol scope preview instead of legacy run-portfolio flow', () => {
  const smokeSource = fs.readFileSync(path.join(__dirname, '..', 'playwright-smoke.cjs'), 'utf8');
  assert.match(smokeSource, /button:has-text\("发起手工调度"\)/);
  assert.match(smokeSource, /\/quant\/dispatch-manual/);
  assert.match(smokeSource, /symbolScopePreview/);
  assert.doesNotMatch(smokeSource, /button:has-text\("run-portfolio"\)/);
});

test('quant dashboard smoke uses the simplified decision-home markers and execution entry', () => {
  const smokeSource = fs.readFileSync(path.join(__dirname, '..', 'playwright-smoke.cjs'), 'utf8');
  assert.match(smokeSource, /markers: \['量化看板', '今日状态', '今日主动作', '下一步去哪里', '对象层摘要'\]/);
  assert.match(smokeSource, /button:has-text\("去执行闭环"\)/);
});

test('dispatch-center and dispatch-detail smoke markers follow the compact page contracts', () => {
  const smokeSource = fs.readFileSync(path.join(__dirname, '..', 'playwright-smoke.cjs'), 'utf8');
  assert.match(smokeSource, /name: '量化-调度中心'/);
  assert.match(smokeSource, /markers: \['量化调度中心', '调度中心', '最近 3 条调度结果', '最近进展与技术摘要', '调度定义'\]/);
  assert.match(smokeSource, /name: '量化-调度详情'/);
  assert.match(smokeSource, /markers: \['调度详情', '本次执行了什么', '结果与异常', '最近一条日志', '结果明细'\]/);
});

test('smoke covers the operations center route and markers', () => {
  const smokeSource = fs.readFileSync(path.join(__dirname, '..', 'playwright-smoke.cjs'), 'utf8');
  assert.match(smokeSource, /name: '量化-运维中心'/);
  assert.match(smokeSource, /path: '\/quant\/operations'/);
  assert.match(smokeSource, /markers: \['运维中心', '当前阻断', '建议恢复', '高级兜底工具'\]/);
});

test('smoke covers updated research and auto-dispatch route markers', () => {
  const smokeSource = fs.readFileSync(path.join(__dirname, '..', 'playwright-smoke.cjs'), 'utf8');
  assert.match(smokeSource, /name: '量化-自动调度'/);
  assert.match(smokeSource, /markers: \['自动调度', '调度中心 \/ 自动触发视图', '调度定义', '最近执行历史', '异常与处理'\]/);
  assert.match(smokeSource, /name: '量化-回测研究'/);
  assert.match(smokeSource, /markers: \['回测研究', '当前研究配置', '研究结果摘要', '更多研究证据'\]/);
  assert.match(smokeSource, /markers: \['今日闭环状态', '异常优先级', '当前异常列表', '手工处理工具', '辅助核对与持仓同步'\]/);
});
