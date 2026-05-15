const test = require('node:test')
const assert = require('node:assert/strict')

const { buildTaskCenterState } = require('../src/views/quant/jobs/jobs-task-center-state')

test('buildTaskCenterState exposes next scheduled dispatch and time range rows', () => {
  const state = buildTaskCenterState({
    nextScheduledDispatch: {
      taskName: '盘后主流程',
      nextFireTime: '2026-05-10 15:30:00'
    },
    dispatchHistory: {
      rows: [
        {
          dispatchId: 1,
          triggerMode: 'auto',
          timeRangeSummary: '2021-05-10 ~ 2026-05-10'
        }
      ]
    },
    primaryTask: {
      taskName: '盘后主流程',
      timeRangeSummary: '2021-05-10 ~ 2026-05-10'
    }
  })

  assert.equal(state.nextScheduledDispatch.taskName, '盘后主流程')
  assert.equal(state.dispatchHistoryRows[0].triggerMode, 'auto')
  assert.equal(state.primaryTaskRows.some(item => item.label === '时间范围'), true)
})
