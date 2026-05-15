const STORAGE_KEY = 'quant.dispatch.handoff'

function normalizeHandoff(input = {}) {
  const jobId = Number(input.jobId || input.executionId || 0)
  if (!jobId) {
    return null
  }
  const payload = { jobId }
  if (input.taskName) {
    payload.taskName = input.taskName
  }
  if (input.scopeSummary) {
    payload.scopeSummary = input.scopeSummary
  }
  if (input.submittedAt) {
    payload.submittedAt = input.submittedAt
  }
  return payload
}

function readRaw(storage) {
  if (!storage || typeof storage.getItem !== 'function') {
    return null
  }
  const raw = storage.getItem(STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw)
    return normalizeHandoff(parsed)
  } catch (error) {
    return null
  }
}

function saveDispatchHandoff(storage, input = {}) {
  const normalized = normalizeHandoff(input)
  if (!storage || typeof storage.setItem !== 'function' || !normalized) {
    return null
  }
  storage.setItem(STORAGE_KEY, JSON.stringify(normalized))
  return normalized
}

function readDispatchHandoff(storage) {
  return readRaw(storage)
}

function clearDispatchHandoff(storage) {
  if (!storage || typeof storage.removeItem !== 'function') {
    return
  }
  storage.removeItem(STORAGE_KEY)
}

function pickCurrentJobId({ rows = [], storage } = {}) {
  const historyRows = Array.isArray(rows) ? rows : []
  const activeRecord = historyRows.find(item => item && item.jobId && ['QUEUED', 'PENDING', 'RUNNING'].includes(item.status))
  if (activeRecord) {
    clearDispatchHandoff(storage)
    return {
      jobId: activeRecord.jobId,
      source: 'history-active',
      row: activeRecord,
      handoff: null
    }
  }

  const fallbackRecord = historyRows.find(item => item && item.jobId)
  const handoff = readDispatchHandoff(storage)
  if (handoff) {
    return {
      jobId: handoff.jobId,
      source: 'handoff',
      row: fallbackRecord || null,
      handoff
    }
  }
  if (fallbackRecord) {
    return {
      jobId: fallbackRecord.jobId,
      source: 'history-latest',
      row: fallbackRecord,
      handoff: null
    }
  }
  return {
    jobId: undefined,
    source: 'none',
    row: null,
    handoff: null
  }
}

module.exports = {
  saveDispatchHandoff,
  readDispatchHandoff,
  clearDispatchHandoff,
  pickCurrentJobId
}
