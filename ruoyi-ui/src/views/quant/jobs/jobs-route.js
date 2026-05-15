function normalizeRouteQuery(query) {
  const normalized = {}
  Object.keys(query || {}).forEach(key => {
    const value = query[key]
    if (value !== undefined && value !== null && value !== '') {
      normalized[key] = value
    }
  })
  return normalized
}

function parseTargetParams(targetParams) {
  if (!targetParams || typeof targetParams !== 'string') {
    return {}
  }
  const text = targetParams.trim()
  if (!text) {
    return {}
  }
  try {
    const parsed = JSON.parse(text)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? normalizeRouteQuery(parsed) : {}
  } catch (error) {
    return {}
  }
}

function buildSopRouteLocation(item = {}) {
  return {
    path: String(item.targetPage || '').trim(),
    query: parseTargetParams(item.targetParams)
  }
}

module.exports = {
  buildSopRouteLocation
}
