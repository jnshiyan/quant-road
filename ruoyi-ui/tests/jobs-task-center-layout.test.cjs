const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

test('task center first screen uses the new command-center components', () => {
  const source = fs.readFileSync(path.join(__dirname, '..', 'src/views/quant/jobs/index.vue'), 'utf8')

  assert.match(source, /<today-status-card/)
  assert.match(source, /<primary-task-card/)
  assert.match(source, /<task-action-panel/)
  assert.match(source, /<task-progress-timeline/)
  assert.doesNotMatch(source, /日常运营动作/)
  assert.doesNotMatch(source, /高级排障动作/)
})
