function toNumberOrUndefined(value) {
  if (value === null || value === undefined || String(value).trim() === '') {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function parseSymbolList(value) {
  if (Array.isArray(value)) {
    return Array.from(new Set(value.map(item => String(item || '').trim()).filter(Boolean)));
  }
  if (value === null || value === undefined) {
    return [];
  }
  return Array.from(new Set(String(value)
    .split(/[\s,，;；]+/)
    .map(item => item.trim())
    .filter(Boolean)));
}

function serializeSymbolList(value) {
  const items = parseSymbolList(value);
  return items.length ? items.join(',') : undefined;
}

function normalizeReviewContext(query) {
  const source = query || {};
  const caseId = toNumberOrUndefined(source.caseId);
  const stockCode = String(source.stockCode || '').trim();
  const strategyId = toNumberOrUndefined(source.strategyId);
  const signalId = toNumberOrUndefined(source.signalId);
  const baselineStrategyId = toNumberOrUndefined(source.baselineStrategyId);
  const candidateStrategyId = toNumberOrUndefined(source.candidateStrategyId);
  const months = toNumberOrUndefined(source.months);
  const dateRangeStart = String(source.dateRangeStart || '').trim();
  const dateRangeEnd = String(source.dateRangeEnd || '').trim();
  const scopeType = String(source.scopeType || '').trim();
  const scopePoolCode = String(source.scopePoolCode || '').trim();
  const symbols = parseSymbolList(source.symbols);
  const whitelist = parseSymbolList(source.whitelist);
  const blacklist = parseSymbolList(source.blacklist);
  const adHocSymbols = parseSymbolList(source.adHocSymbols);
  const reviewLevel = String(source.reviewLevel || 'trade').trim() || 'trade';
  const sourcePage = String(source.sourcePage || '').trim();
  const sourceAction = String(source.sourceAction || '').trim();

  return {
    caseId,
    stockCode,
    strategyId,
    signalId,
    baselineStrategyId,
    candidateStrategyId,
    months,
    dateRangeStart,
    dateRangeEnd,
    scopeType,
    scopePoolCode,
    symbols,
    whitelist,
    blacklist,
    adHocSymbols,
    reviewLevel,
    sourcePage,
    sourceAction,
    hasContext: !!(
      stockCode ||
      caseId ||
      strategyId ||
      signalId ||
      baselineStrategyId ||
      candidateStrategyId ||
      months ||
      dateRangeStart ||
      dateRangeEnd ||
      scopeType ||
      scopePoolCode ||
      symbols.length ||
      whitelist.length ||
      blacklist.length ||
      adHocSymbols.length
    )
  };
}

function buildReviewRouteQuery(payload) {
  const context = normalizeReviewContext(payload);
  const query = {
    reviewLevel: context.reviewLevel || 'trade'
  };

  if (context.caseId !== undefined) {
    query.caseId = String(context.caseId);
  }
  if (context.stockCode) {
    query.stockCode = context.stockCode;
  }
  if (context.strategyId !== undefined) {
    query.strategyId = String(context.strategyId);
  }
  if (context.signalId !== undefined) {
    query.signalId = String(context.signalId);
  }
  if (context.baselineStrategyId !== undefined) {
    query.baselineStrategyId = String(context.baselineStrategyId);
  }
  if (context.candidateStrategyId !== undefined) {
    query.candidateStrategyId = String(context.candidateStrategyId);
  }
  if (context.months !== undefined) {
    query.months = String(context.months);
  }
  if (context.dateRangeStart) {
    query.dateRangeStart = context.dateRangeStart;
  }
  if (context.dateRangeEnd) {
    query.dateRangeEnd = context.dateRangeEnd;
  }
  if (context.scopeType) {
    query.scopeType = context.scopeType;
  }
  if (context.scopePoolCode) {
    query.scopePoolCode = context.scopePoolCode;
  }
  if (serializeSymbolList(context.symbols)) {
    query.symbols = serializeSymbolList(context.symbols);
  }
  if (serializeSymbolList(context.whitelist)) {
    query.whitelist = serializeSymbolList(context.whitelist);
  }
  if (serializeSymbolList(context.blacklist)) {
    query.blacklist = serializeSymbolList(context.blacklist);
  }
  if (serializeSymbolList(context.adHocSymbols)) {
    query.adHocSymbols = serializeSymbolList(context.adHocSymbols);
  }
  if (context.sourcePage) {
    query.sourcePage = context.sourcePage;
  }
  if (context.sourceAction) {
    query.sourceAction = context.sourceAction;
  }

  return query;
}

function filterExecutionFeedbackRows(rows, context) {
  const list = Array.isArray(rows) ? rows : [];
  const ctx = context || {};
  return list.filter(row => {
    if (ctx.signalId && Number(row.signal_id) !== Number(ctx.signalId)) {
      return false;
    }
    if (ctx.stockCode && String(row.stock_code || '') !== String(ctx.stockCode)) {
      return false;
    }
    if (ctx.strategyId && Number(row.strategy_id) !== Number(ctx.strategyId)) {
      return false;
    }
    return true;
  });
}

function filterStrategyLogs(rows, context) {
  const list = Array.isArray(rows) ? rows : [];
  const ctx = context || {};
  if (!ctx.strategyId) {
    return list;
  }
  return list.filter(row => Number(row.strategy_id) === Number(ctx.strategyId));
}

module.exports = {
  buildReviewRouteQuery,
  normalizeReviewContext,
  filterExecutionFeedbackRows,
  filterStrategyLogs
};
