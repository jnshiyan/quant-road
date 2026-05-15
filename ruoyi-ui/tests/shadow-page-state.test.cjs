const test = require('node:test');
const assert = require('node:assert/strict');

const {
  applyShadowRouteQuery,
  createShadowRefreshKey,
  getShadowBootstrapCriticalKeys,
  getShadowBootstrapDeferredKeys,
  getShadowBootstrapPrefetchKeys,
  getShadowDeferredRefreshKeys,
  getShadowPrimaryRefreshKeys,
  didShadowQueryChange,
  didShadowRouteContextChange
} = require('../src/views/quant/shadow/shadow-page-state');

test('applyShadowRouteQuery clears stale ids when route query is empty', () => {
  const next = applyShadowRouteQuery({}, [], {
    baselineStrategyId: 1,
    candidateStrategyId: 2,
    months: 12
  });

  assert.deepEqual(next, {
    baselineStrategyId: undefined,
    candidateStrategyId: undefined,
    months: 12
  });
});

test('applyShadowRouteQuery fills defaults from strategy pool when ids are missing or invalid', () => {
  const next = applyShadowRouteQuery({
    baselineStrategyId: '99',
    months: '3'
  }, [
    { id: 11, status: 1 },
    { id: 12, status: 1 },
    { id: 13, status: 0 }
  ], {
    baselineStrategyId: undefined,
    candidateStrategyId: undefined,
    months: 6
  });

  assert.deepEqual(next, {
    baselineStrategyId: 11,
    candidateStrategyId: 12,
    months: 3
  });
});

test('didShadowQueryChange detects route-driven target changes', () => {
  assert.equal(
    didShadowQueryChange(
      { baselineStrategyId: 1, candidateStrategyId: 2, months: 6 },
      { baselineStrategyId: 1, candidateStrategyId: 3, months: 6 }
    ),
    true
  );
  assert.equal(
    didShadowQueryChange(
      { baselineStrategyId: 1, candidateStrategyId: 2, months: 6 },
      { baselineStrategyId: 1, candidateStrategyId: 2, months: 6 }
    ),
    false
  );
});

test('shadow bootstrap prefetch excludes action items that refreshGovernance will reload', () => {
  assert.deepEqual(getShadowBootstrapPrefetchKeys(), [
    'strategies',
    'capabilities'
  ]);
});

test('shadow bootstrap separates strategy availability from non-critical capabilities metadata', () => {
  assert.deepEqual(getShadowBootstrapCriticalKeys(), ['strategies']);
  assert.deepEqual(getShadowBootstrapDeferredKeys(), ['capabilities']);
});

test('shadow refresh separates core governance decision data from secondary evidence panels', () => {
  assert.deepEqual(getShadowPrimaryRefreshKeys(), [
    'compare',
    'summary',
    'actionItems'
  ]);
  assert.deepEqual(getShadowDeferredRefreshKeys(), [
    'charts',
    'applicability',
    'links',
    'history'
  ]);
});

test('didShadowRouteContextChange ignores route churn when effective target is unchanged', () => {
  assert.equal(
    didShadowRouteContextChange(
      {
        baselineStrategyId: '11',
        candidateStrategyId: '12',
        months: '6'
      },
      {
        baselineStrategyId: 11,
        candidateStrategyId: 12,
        months: 6
      }
    ),
    false
  );

  assert.equal(
    didShadowRouteContextChange(
      {
        baselineStrategyId: '11',
        candidateStrategyId: '13',
        months: '6'
      },
      {
        baselineStrategyId: 11,
        candidateStrategyId: 12,
        months: 6
      }
    ),
    true
  );
});

test('createShadowRefreshKey collapses equivalent governance params to the same refresh signature', () => {
  const a = createShadowRefreshKey({
    baselineStrategyId: 11,
    candidateStrategyId: 12,
    months: 6
  });
  const b = createShadowRefreshKey({
    baselineStrategyId: '11',
    candidateStrategyId: '12',
    months: '6'
  });
  const c = createShadowRefreshKey({
    baselineStrategyId: 11,
    candidateStrategyId: 13,
    months: 6
  });

  assert.equal(a, b);
  assert.notEqual(a, c);
});
