function normalize(value) {
  return String(value == null ? '' : value).trim()
}

function buildDashboardActionItemKey(item = {}, index = 0) {
  return [
    normalize(item.actionType),
    normalize(item.targetType),
    normalize(item.targetId),
    normalize(item.title),
    normalize(item.reason),
    index
  ].join('|')
}

function buildDashboardDeepLinkKey(item = {}, index = 0) {
  return [
    normalize(item.path),
    normalize(item.title),
    normalize(item.badge),
    normalize(item.reason),
    index
  ].join('|')
}

function buildDashboardNarrativeActionKey(item = {}, index = 0) {
  return [
    normalize(item.title),
    normalize(item.priority),
    normalize(item.reason),
    index
  ].join('|')
}

module.exports = {
  buildDashboardActionItemKey,
  buildDashboardDeepLinkKey,
  buildDashboardNarrativeActionKey
}
