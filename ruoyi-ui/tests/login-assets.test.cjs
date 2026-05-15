const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const loginBackgroundPath = path.resolve(__dirname, '../src/assets/images/login-background.jpg');
const MAX_LOGIN_BACKGROUND_BYTES = 260 * 1024;

test('login background asset stays within the front-door performance budget', () => {
  const stat = fs.statSync(loginBackgroundPath);
  assert.ok(
    stat.size <= MAX_LOGIN_BACKGROUND_BYTES,
    `login-background.jpg is ${stat.size} bytes, expected <= ${MAX_LOGIN_BACKGROUND_BYTES}`
  );
});
