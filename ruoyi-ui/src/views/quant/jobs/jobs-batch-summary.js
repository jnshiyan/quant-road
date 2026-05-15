function parseParams(raw) {
  if (!raw) {
    return {}
  }
  if (typeof raw === 'object' && !Array.isArray(raw)) {
    return raw
  }
  if (typeof raw !== 'string') {
    return {}
  }
  const text = raw.trim()
  if (!text) {
    return {}
  }
  try {
    const parsed = JSON.parse(text)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch (error) {
    return {}
  }
}

function scopeLabel(scopeType, scopePoolCode) {
  if (scopeType === 'stock_pool') return scopePoolCode ? `个股池 ${scopePoolCode}` : '个股池'
  if (scopeType === 'etf_pool') return scopePoolCode ? `ETF池 ${scopePoolCode}` : 'ETF池'
  if (scopeType === 'index_mapped_etf_pool') return scopePoolCode ? `指数映射ETF池 ${scopePoolCode}` : '指数映射ETF池'
  return '全市场'
}

function resolveSymbolCount(params) {
  if (!Array.isArray(params.symbols)) {
    return 0
  }
  return params.symbols.filter(item => typeof item === 'string' && item.trim()).length
}

function summarizeBatchScope(row = {}) {
  const params = parseParams(row.params)
  const symbolCount = resolveSymbolCount(params)
  const label = scopeLabel(params.scope_type, params.scope_pool_code)

  if (!params.scope_type || params.scope_type === 'all_stocks') {
    return {
      scopeLabel: '全市场',
      loadLabel: '重型',
      tagType: 'danger',
      detail: '默认广范围主链'
    }
  }
  if (symbolCount <= 10) {
    return {
      scopeLabel: label,
      loadLabel: '轻量',
      tagType: 'success',
      detail: symbolCount > 0 ? `${symbolCount} 个标的` : '受限范围'
    }
  }
  if (symbolCount <= 100) {
    return {
      scopeLabel: label,
      loadLabel: '中等',
      tagType: 'warning',
      detail: `${symbolCount} 个标的`
    }
  }
  return {
    scopeLabel: label,
    loadLabel: '重型',
    tagType: 'danger',
    detail: `${symbolCount} 个标的`
  }
}

module.exports = {
  summarizeBatchScope
}
