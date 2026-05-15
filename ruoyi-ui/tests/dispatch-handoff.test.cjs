const test = require('node:test')
const assert = require('node:assert/strict')

const {
  saveDispatchHandoff,
  readDispatchHandoff,
  clearDispatchHandoff,
  pickCurrentJobId
} = require('../src/views/quant/jobs/dispatch-handoff')

function createStorage() {
  const state = new Map()
  return {
    getItem(key) {
      return state.has(key) ? state.get(key) : null
    },
    setItem(key, value) {
      state.set(key, String(value))
    },
    removeItem(key) {
      state.delete(key)
    }
  }
}

test('dispatch handoff round-trips recent submitted job', () => {
  const storage = createStorage()

  saveDispatchHandoff(storage, {
    jobId: 88,
    taskName: '盘后主流程',
    submittedAt: '2026-05-10 20:00:00'
  })

  const result = readDispatchHandoff(storage)
  assert.deepEqual(result, {
    jobId: 88,
    taskName: '盘后主流程',
    submittedAt: '2026-05-10 20:00:00'
  })
})

test('pickCurrentJobId prefers active history record and clears stale handoff', () => {
  const storage = createStorage()
  saveDispatchHandoff(storage, { jobId: 77, submittedAt: '2026-05-10 20:00:00' })

  const result = pickCurrentJobId({
    rows: [{ jobId: 99, status: 'RUNNING' }],
    storage
  })

  assert.equal(result.jobId, 99)
  assert.equal(result.source, 'history-active')
  assert.equal(readDispatchHandoff(storage), null)
})

test('pickCurrentJobId falls back to recent handoff when history has not caught up', () => {
  const storage = createStorage()
  saveDispatchHandoff(storage, { jobId: 66, submittedAt: '2026-05-10 20:00:00', scopeSummary: 'ETF 池 / 2 个标的' })

  const result = pickCurrentJobId({
    rows: [],
    storage
  })

  assert.equal(result.jobId, 66)
  assert.equal(result.source, 'handoff')
  assert.equal(result.handoff.scopeSummary, 'ETF 池 / 2 个标的')
})

test('clearDispatchHandoff removes saved job context', () => {
  const storage = createStorage()
  saveDispatchHandoff(storage, { jobId: 55, submittedAt: '2026-05-10 20:00:00' })

  clearDispatchHandoff(storage)

  assert.equal(readDispatchHandoff(storage), null)
})
