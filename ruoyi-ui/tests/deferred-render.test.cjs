const test = require('node:test');
const assert = require('node:assert/strict');

const {
  scheduleDeferredMount
} = require('../src/utils/deferred-render');

test('scheduleDeferredMount prefers requestIdleCallback when available', async () => {
  const events = [];
  const callback = () => {
    events.push('run');
  };

  const handle = scheduleDeferredMount(callback, {
    requestIdleCallback(run, options) {
      events.push({ type: 'idle', options });
      run();
      return 'idle-handle';
    },
    cancelIdleCallback(value) {
      events.push({ type: 'cancel-idle', value });
    }
  });

  assert.equal(handle.strategy, 'idle');
  assert.deepEqual(events, [
    { type: 'idle', options: { timeout: 200 } },
    'run'
  ]);

  handle.cancel();
  assert.deepEqual(events, [
    { type: 'idle', options: { timeout: 200 } },
    'run'
  ]);
});

test('scheduleDeferredMount falls back to setTimeout and can cancel before execution', async () => {
  const events = [];
  let scheduledRunner = null;

  const handle = scheduleDeferredMount(() => {
    events.push('run');
  }, {
    setTimeout(run, delay) {
      scheduledRunner = run;
      events.push({ type: 'timeout', delay });
      return 'timeout-handle';
    },
    clearTimeout(value) {
      events.push({ type: 'clear-timeout', value });
    }
  });

  assert.equal(handle.strategy, 'timeout');
  assert.deepEqual(events, [
    { type: 'timeout', delay: 48 }
  ]);

  handle.cancel();
  assert.deepEqual(events, [
    { type: 'timeout', delay: 48 },
    { type: 'clear-timeout', value: 'timeout-handle' }
  ]);

  scheduledRunner();
  assert.deepEqual(events, [
    { type: 'timeout', delay: 48 },
    { type: 'clear-timeout', value: 'timeout-handle' }
  ]);
});
