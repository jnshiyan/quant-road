<template>
  <div class="app-container quant-backtest-page">
    <el-card shadow="never" class="box-card hero-card">
      <div slot="header" class="section-header">
        <div>
          <div class="page-title">回测研究</div>
          <div class="page-subtitle">研究任务与结果</div>
        </div>
        <div class="page-actions">
          <el-button size="small" icon="el-icon-collection-tag" @click="goSymbols">去标的体系</el-button>
          <el-button size="small" icon="el-icon-s-order" @click="goJobs">去调度中心</el-button>
          <el-button size="small" icon="el-icon-data-line" @click="goShadow">去影子对比</el-button>
        </div>
      </div>
      <el-row :gutter="16" class="hero-overview">
        <el-col :xs="24" :xl="14">
          <div class="hero-panel hero-panel--flat">
            <div class="hero-panel__title">当前研究配置</div>
            <el-form :inline="true" :model="queryParams" size="small" label-width="96px" class="hero-form">
              <el-form-item label="策略">
                <el-select v-model="queryParams.strategyId" clearable filterable style="width: 260px" placeholder="请选择策略">
                  <el-option
                    v-for="item in strategyList"
                    :key="item.id"
                    :label="strategyLabel(item)"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="回测起始日">
                <el-date-picker
                  v-model="queryParams.strategyBacktestStartDate"
                  type="date"
                  value-format="yyyy-MM-dd"
                  style="width: 160px"
                />
              </el-form-item>
              <el-form-item label="总资金">
                <el-input-number v-model="queryParams.portfolioTotalCapital" :min="0" :step="10000" controls-position="right" />
              </el-form-item>
              <el-form-item label="日志条数">
                <el-input-number v-model="queryParams.logLimit" :min="10" :max="200" controls-position="right" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" icon="el-icon-search" :loading="loading" @click="loadAllData">刷新研究数据</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-col>
        <el-col :xs="24" :xl="10">
          <div class="hero-stack">
            <div class="hero-panel">
              <div class="hero-panel__title">当前研究任务</div>
              <div class="task-brief">
                <div class="task-line"><label>策略</label><span>{{ currentStrategyText }}</span></div>
                <div class="task-line"><label>范围</label><span>{{ scopeSummaryText }}</span></div>
                <div class="task-line"><label>时间范围</label><span>{{ researchTimeRangeText }}</span></div>
                <div class="task-line"><label>总资金</label><span>{{ queryParams.portfolioTotalCapital }}</span></div>
              </div>
              <div class="task-tags">
                <el-tag size="mini" type="info">标的 {{ scopePreview.resolvedCount || 0 }}</el-tag>
                <el-tag size="mini" type="success">策略 {{ strategyList.length }}</el-tag>
                <el-tag size="mini" :type="canaryTagType(canaryLatest.recommendation)">
                  Canary {{ canaryLatest.recommendation || '暂无结果' }}
                </el-tag>
              </div>
              <div class="task-actions">
                <el-button type="success" :loading="loadingRun" @click="handleExecuteResearchTask">执行当前研究任务</el-button>
                <el-button plain @click="goShadow">看影子结论</el-button>
              </div>
            </div>

            <div class="hero-panel">
              <div class="hero-panel__title">研究结果摘要</div>
              <div class="result-grid">
                <div class="result-item">
                  <label>平均年化</label>
                  <strong class="is-success">{{ avgAnnualReturn === '-' ? '-' : `${avgAnnualReturn}%` }}</strong>
                </div>
                <div class="result-item">
                  <label>当前范围</label>
                  <strong>{{ scopePreview.resolvedCount || 0 }}</strong>
                </div>
                <div class="result-item">
                  <label>Canary</label>
                  <strong>{{ canaryLatest.recommendation || '暂无结果' }}</strong>
                </div>
              </div>
              <div class="summary-hint">{{ researchSummaryHint }}</div>
              <div v-if="runResult" class="run-result run-result--compact">
                <span class="run-result__label">最近回执</span>
                <pre class="run-result__body">{{ runResult }}</pre>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :xl="13">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>研究范围</span>
            <span class="section-meta">研究口径</span>
          </div>
          <el-form :model="scopeForm" label-width="110px" size="small">
            <el-form-item label="预设范围">
              <el-select v-model="scopeForm.scopeType" style="width: 100%" @change="handleScopeTypeChange">
                <el-option
                  v-for="item in presetScopes"
                  :key="item.scopeType"
                  :label="`${item.label} · ${item.description}`"
                  :value="item.scopeType"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="正式股票池" v-if="currentScopeNeedsPool">
              <el-select v-model="scopeForm.scopePoolCode" style="width: 100%" placeholder="请选择正式股票池" @change="loadScopePreview">
                <el-option
                  v-for="item in availablePools"
                  :key="item.poolCode"
                  :label="`${item.poolName} (${item.poolCode})`"
                  :value="item.poolCode"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="直接指定标的">
              <el-input
                v-model="scopeForm.symbolsText"
                type="textarea"
                :rows="2"
                placeholder="输入后覆盖预设范围"
              />
            </el-form-item>
            <el-form-item label="白名单">
              <el-input
                v-model="scopeForm.whitelistText"
                type="textarea"
                :rows="2"
                placeholder="强制保留标的"
              />
            </el-form-item>
            <el-form-item label="黑名单">
              <el-input
                v-model="scopeForm.blacklistText"
                type="textarea"
                :rows="2"
                placeholder="强制排除标的"
              />
            </el-form-item>
            <el-form-item label="临时补充">
              <el-input
                v-model="scopeForm.adHocSymbolsText"
                type="textarea"
                :rows="2"
                placeholder="补充本次研究标的"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loadingScopePreview" @click="loadScopePreview">刷新范围预览</el-button>
              <el-button @click="goSymbols">去标的治理页</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :xl="11">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>范围预览</span>
            <span class="section-meta">范围样例</span>
          </div>
          <div class="scope-preview">
            <div class="scope-preview__headline">
              <div class="scope-preview__count">{{ scopePreview.resolvedCount || 0 }}</div>
              <div>
                <div class="scope-preview__label">{{ scopeSummaryText }}</div>
                <div class="scope-preview__caption">{{ selectedScopeNarrative }}</div>
              </div>
            </div>
            <div class="scope-preview__meta">
              <el-tag size="mini" type="success">白名单 {{ scopePreview.whitelistCount || 0 }}</el-tag>
              <el-tag size="mini" type="warning">黑名单 {{ scopePreview.blacklistCount || 0 }}</el-tag>
              <el-tag size="mini" type="info">临时补充 {{ scopePreview.adHocCount || 0 }}</el-tag>
            </div>
            <div class="scope-preview__symbols">
              <el-tag v-for="item in previewSymbols" :key="item" size="mini" class="symbol-pill">{{ item }}</el-tag>
              <el-empty v-if="!previewSymbols.length && !loadingScopePreview" description="暂无范围样例，请刷新预览" :image-size="72" />
            </div>
            <div class="scope-preview__rule">
              <div class="scope-preview__rule-title">使用提示</div>
              <div class="scope-usage-chips">
                <el-tag v-for="item in scopeUsageTags" :key="item" size="mini" type="info">{{ item }}</el-tag>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert
      v-if="!scopeForm.scopeType"
      class="mt16"
      type="info"
      :closable="false"
      show-icon
      title="请先选择研究范围"
      description="回测研究不再默认全市场。先明确范围，再配置策略与执行研究任务。"
    />

    <el-collapse v-model="backtestSecondaryPanels" class="mt16 backtest-secondary-collapse">
      <el-collapse-item name="evidence">
        <template slot="title">
          <span class="collapse-title">更多研究证据</span>
          <span class="collapse-meta">策略明细、Canary、影子结论、策略能力</span>
        </template>
        <el-row :gutter="16">
      <el-col :xs="24" :xl="14">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>策略表现明细</span>
            <span class="section-meta">{{ currentStrategyText }}</span>
          </div>
          <el-table v-loading="loadingLogs" :data="filteredLogs" border>
            <el-table-column label="策略ID" prop="strategy_id" width="90" />
            <el-table-column label="运行时间" prop="run_time" min-width="170" />
            <el-table-column label="年化(%)" min-width="100">
              <template slot-scope="scope">
                <span :class="metricClass(scope.row.annual_return, true)">
                  {{ formatPercent(scope.row.annual_return) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="回撤(%)" min-width="100">
              <template slot-scope="scope">
                <span :class="metricClass(scope.row.max_drawdown, false)">
                  {{ formatPercent(scope.row.max_drawdown) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="胜率(%)" min-width="100">
              <template slot-scope="scope">{{ formatPercent(scope.row.win_rate) }}</template>
            </el-table-column>
            <el-table-column label="总收益(%)" min-width="110">
              <template slot-scope="scope">{{ formatPercent(scope.row.total_profit) }}</template>
            </el-table-column>
            <el-table-column label="失效" width="80">
              <template slot-scope="scope">
                <el-tag size="mini" :type="Number(scope.row.is_invalid) === 1 ? 'danger' : 'success'">
                  {{ Number(scope.row.is_invalid) === 1 ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="备注" prop="remark" min-width="220" show-overflow-tooltip />
          </el-table>
          <el-empty v-if="!filteredLogs.length && !loadingLogs" description="暂无策略运行日志" />
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="10">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>最新 Canary</span>
            <span class="section-meta">前置判断</span>
          </div>
          <div v-loading="loadingCanary" class="canary-wrap">
            <template v-if="canaryLatest && canaryLatest.run_date">
              <div class="canary-line">
                <span>运行日：{{ canaryLatest.run_date }}</span>
                <span>市场状态：{{ canaryLatest.market_status || '-' }}</span>
              </div>
              <div class="canary-line">
                <span>基线策略：{{ canaryLatest.baseline_strategy_id || '-' }}</span>
                <span>候选策略：{{ canaryLatest.candidate_strategy_id || '-' }}</span>
              </div>
              <div class="canary-line">
                <span>可比月份：{{ canaryLatest.comparable_months || 0 }}</span>
                <span>年化更优：{{ canaryLatest.candidate_better_annual_months || 0 }}</span>
                <span>回撤更低：{{ canaryLatest.candidate_lower_drawdown_months || 0 }}</span>
              </div>
              <div class="canary-line canary-remark">{{ canaryLatest.remark || '' }}</div>
            </template>
            <el-empty v-else description="暂无 canary 结果" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :xl="14">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>影子结论</span>
            <span class="section-meta">基线 {{ shadowQuery.baselineStrategyId || '-' }} vs 候选 {{ shadowQuery.candidateStrategyId || '-' }}</span>
          </div>
          <el-form :inline="true" :model="shadowQuery" size="small" class="shadow-form">
            <el-form-item label="基线策略">
              <el-select v-model="shadowQuery.baselineStrategyId" filterable style="width: 220px">
                <el-option
                  v-for="item in strategyList"
                  :key="item.id"
                  :label="strategyLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="候选策略">
              <el-select v-model="shadowQuery.candidateStrategyId" filterable style="width: 220px">
                <el-option
                  v-for="item in strategyList"
                  :key="item.id"
                  :label="strategyLabel(item)"
                  :value="item.id"
                  :disabled="item.id === shadowQuery.baselineStrategyId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="月份">
              <el-input-number v-model="shadowQuery.months" :min="1" :max="24" controls-position="right" />
            </el-form-item>
            <el-form-item>
              <el-button size="small" type="primary" :loading="loadingShadow" @click="loadShadowCompare">刷新影子结果</el-button>
            </el-form-item>
          </el-form>
          <div class="shadow-summary">
            <el-tag size="mini" type="info">可比月份 {{ shadowSummary.comparable_months || 0 }}</el-tag>
            <el-tag size="mini" type="success">年化更优 {{ shadowSummary.candidate_better_annual_months || 0 }}</el-tag>
            <el-tag size="mini" type="warning">回撤更低 {{ shadowSummary.candidate_lower_drawdown_months || 0 }}</el-tag>
            <el-tag size="mini" type="danger">失效率更低 {{ shadowSummary.candidate_lower_invalid_rate_months || 0 }}</el-tag>
          </div>
          <el-table v-loading="loadingShadow" :data="shadowMonthsData" border height="280">
            <el-table-column label="月份" prop="month" width="100" />
            <el-table-column label="年化差值(%)" min-width="110">
              <template slot-scope="scope">{{ formatDelta(scope.row.delta, 'avg_annual_return') }}</template>
            </el-table-column>
            <el-table-column label="回撤差值(%)" min-width="110">
              <template slot-scope="scope">{{ formatDelta(scope.row.delta, 'avg_max_drawdown') }}</template>
            </el-table-column>
            <el-table-column label="胜率差值(%)" min-width="110">
              <template slot-scope="scope">{{ formatDelta(scope.row.delta, 'avg_win_rate') }}</template>
            </el-table-column>
            <el-table-column label="失效率差值(%)" min-width="120">
              <template slot-scope="scope">{{ formatDelta(scope.row.delta, 'invalid_rate') }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!shadowMonthsData.length && !loadingShadow" description="请选择候选策略后查看影子结果" />
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="10">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>策略能力</span>
            <span class="section-meta">参数要求</span>
          </div>
          <el-table v-loading="loadingCapabilities" :data="capabilities" border height="360">
            <el-table-column label="策略类型" prop="strategy_type" width="150" />
            <el-table-column label="描述" prop="description" min-width="180" />
            <el-table-column label="必填参数" min-width="180">
              <template slot-scope="scope">{{ joinParams(scope.row.required_params) }}</template>
            </el-table-column>
            <el-table-column label="可选参数" min-width="220">
              <template slot-scope="scope">{{ joinParams(scope.row.optional_params) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!capabilities.length && !loadingCapabilities" description="暂无策略能力数据" />
        </el-card>
      </el-col>
        </el-row>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script>
import ElAlert from 'element-ui/lib/alert'
import ElCollapse from 'element-ui/lib/collapse'
import ElCollapseItem from 'element-ui/lib/collapse-item'
import 'element-ui/lib/theme-chalk/alert.css'
import 'element-ui/lib/theme-chalk/collapse.css'
import {
  executeQuantTask,
  getCanaryLatest,
  getShadowCompare,
  getStrategyCapabilities,
  getSymbolScopeOptions,
  getSymbolScopePreview,
  listStrategies,
  listStrategyLogs
} from '@/api/quant'

function padDateUnit(value) {
  return String(value).padStart(2, '0')
}

function rollingStartDate(years, dashed = false) {
  const today = new Date()
  const target = new Date(today.getFullYear() - years, today.getMonth(), today.getDate())

  if (target.getMonth() !== today.getMonth()) {
    target.setDate(0)
  }

  const year = target.getFullYear()
  const month = padDateUnit(target.getMonth() + 1)
  const day = padDateUnit(target.getDate())

  return dashed ? `${year}-${month}-${day}` : `${year}${month}${day}`
}

function parseSymbolText(raw) {
  if (!raw) {
    return []
  }
  return Array.from(new Set(String(raw)
    .split(/[\s,，;；]+/)
    .map(item => item.trim())
    .filter(Boolean)))
}

export default {
  name: 'QuantBacktestAnalysis',
  components: {
    ElAlert,
    ElCollapse,
    ElCollapseItem
  },
  data() {
    return {
      loading: false,
      loadingLogs: false,
      loadingCapabilities: false,
      loadingShadow: false,
      loadingCanary: false,
      loadingRun: false,
      loadingScope: false,
      loadingScopePreview: false,
      strategyList: [],
      strategyLogs: [],
      capabilities: [],
      canaryLatest: {},
      shadowPayload: {},
      symbolScopeOptions: {},
      backtestSecondaryPanels: [],
      scopePreview: {},
      runResult: '',
      queryParams: {
        strategyId: undefined,
        strategyBacktestStartDate: rollingStartDate(5, true),
        portfolioTotalCapital: 100000,
        logLimit: 50,
        actor: 'ruoyi-ui-backtest'
      },
      scopeForm: {
        scopeType: '',
        scopePoolCode: '',
        symbolsText: '',
        whitelistText: '',
        blacklistText: '',
        adHocSymbolsText: ''
      },
      shadowQuery: {
        baselineStrategyId: undefined,
        candidateStrategyId: undefined,
        months: 6
      }
    }
  },
  computed: {
    presetScopes() {
      return Array.isArray(this.symbolScopeOptions.presetScopes) ? this.symbolScopeOptions.presetScopes : []
    },
    poolOptions() {
      return Array.isArray(this.symbolScopeOptions.poolOptions) ? this.symbolScopeOptions.poolOptions : []
    },
    currentScopeOption() {
      return this.presetScopes.find(item => item.scopeType === this.scopeForm.scopeType) || {}
    },
    currentScopeNeedsPool() {
      return Boolean(this.currentScopeOption.needsPool)
    },
    availablePools() {
      return this.poolOptions.filter(item => item.scopeType === this.scopeForm.scopeType)
    },
    previewSymbols() {
      return Array.isArray(this.scopePreview.symbols) ? this.scopePreview.symbols : []
    },
    filteredLogs() {
      if (!this.queryParams.strategyId) {
        return this.strategyLogs
      }
      return this.strategyLogs.filter(item => item.strategy_id === this.queryParams.strategyId)
    },
    avgAnnualReturn() {
      if (!this.filteredLogs.length) {
        return '-'
      }
      const sum = this.filteredLogs.reduce((total, item) => total + Number(item.annual_return || 0), 0)
      return (sum / this.filteredLogs.length).toFixed(2)
    },
    currentStrategyText() {
      if (!this.queryParams.strategyId) {
        return '全部策略'
      }
      const matched = this.strategyList.find(item => item.id === this.queryParams.strategyId)
      return matched ? `${matched.id} - ${matched.strategy_name}` : `策略 ${this.queryParams.strategyId}`
    },
    shadowSummary() {
      return this.shadowPayload.summary || {}
    },
    shadowMonthsData() {
      return Array.isArray(this.shadowPayload.months_data) ? this.shadowPayload.months_data : []
    },
    researchTimeRangeText() {
      return this.queryParams.strategyBacktestStartDate ? `${this.queryParams.strategyBacktestStartDate} 起` : '未设置'
    },
    scopeSummaryText() {
      if (!this.scopeForm.scopeType) {
        return '请先选择研究范围'
      }
      if (!this.scopePreview.scopeType) {
        return '等待范围预览'
      }
      const label = this.scopeLabel(this.scopePreview.scopeType)
      return this.scopePreview.scopePoolCode ? `${label} · ${this.scopePreview.scopePoolCode}` : label
    },
    researchSummaryHint() {
      if (this.runResult) {
        return '当前研究任务已经提交，可继续在下方看策略表现、Canary 与影子结论。'
      }
      if (!this.scopePreview.resolvedCount) {
        return '先确认研究范围，再执行当前研究任务。'
      }
      return '当前摘要来自最近研究结果与当前范围预览。'
    },
    selectedScopeNarrative() {
      if (!this.scopeForm.scopeType) {
        return '当前尚未选择研究范围，系统不会默认落到全市场。'
      }
      if (this.scopeForm.symbolsText) {
        return '当前使用直接指定标的，适合局部专题或策略归因验证。'
      }
      if (this.currentScopeNeedsPool && this.scopeForm.scopePoolCode) {
        return '当前范围来自正式股票池，可与任务中心、执行页、复盘页复用同一口径。'
      }
      return '当前范围来自预设范围模型，适合验证策略的广泛适用性。'
    },
    scopeUsageTags() {
      if (!this.scopeForm.scopeType) {
        return ['先选择范围']
      }
      if (this.scopeForm.scopeType === 'all_stocks') {
        return ['宽范围验证', '先看稳定性', '再决定是否收敛']
      }
      if (this.scopeForm.scopeType === 'stock_pool') {
        return ['真实交易池', '支持白名单', '支持黑名单']
      }
      if (this.scopeForm.scopeType === 'etf_pool') {
        return ['宽基 / 行业 / 风格', 'ETF 主线', '低频纪律化']
      }
      if (this.scopeForm.scopeType === 'index_mapped_etf_pool') {
        return ['指数承接', '主 ETF', '观察备选 ETF']
      }
      return ['先选范围模型']
    }
  },
  created() {
    this.syncScopeFromRoute()
    this.loadAllData()
  },
  watch: {
    '$route.query': {
      handler() {
        this.syncScopeFromRoute()
        this.loadScopePreview()
      },
      deep: true
    }
  },
  methods: {
    syncScopeFromRoute() {
      const query = this.$route && this.$route.query ? this.$route.query : {}
      if (query.scopeType) {
        this.scopeForm.scopeType = String(query.scopeType)
      }
      if (query.scopePoolCode !== undefined) {
        this.scopeForm.scopePoolCode = String(query.scopePoolCode || '')
      }
      if (query.symbols !== undefined) {
        this.scopeForm.symbolsText = Array.isArray(query.symbols) ? query.symbols.join(',') : String(query.symbols || '')
      }
      if (query.whitelist !== undefined) {
        this.scopeForm.whitelistText = Array.isArray(query.whitelist) ? query.whitelist.join(',') : String(query.whitelist || '')
      }
      if (query.blacklist !== undefined) {
        this.scopeForm.blacklistText = Array.isArray(query.blacklist) ? query.blacklist.join(',') : String(query.blacklist || '')
      }
      if (query.adHocSymbols !== undefined) {
        this.scopeForm.adHocSymbolsText = Array.isArray(query.adHocSymbols) ? query.adHocSymbols.join(',') : String(query.adHocSymbols || '')
      }
    },
    async loadAllData() {
      this.loading = true
      try {
        await Promise.all([
          this.loadStrategies(),
          this.loadLogs(),
          this.loadCapabilities(),
          this.loadCanaryLatest(),
          this.loadScopeOptions()
        ])
        await Promise.all([
          this.loadScopePreview(),
          this.loadShadowCompare()
        ])
      } finally {
        this.loading = false
      }
    },
    async loadStrategies() {
      const response = await listStrategies()
      const items = Array.isArray(response.data) ? response.data : []
      this.strategyList = items
      if (!items.length) {
        this.queryParams.strategyId = undefined
        this.shadowQuery.baselineStrategyId = undefined
        this.shadowQuery.candidateStrategyId = undefined
        return
      }
      if (!this.queryParams.strategyId) {
        this.queryParams.strategyId = items[0].id
      }
      if (!this.shadowQuery.baselineStrategyId) {
        this.shadowQuery.baselineStrategyId = items[0].id
      }
      if (!this.shadowQuery.candidateStrategyId || this.shadowQuery.candidateStrategyId === this.shadowQuery.baselineStrategyId) {
        const candidate = items.find(item => item.id !== this.shadowQuery.baselineStrategyId)
        this.shadowQuery.candidateStrategyId = candidate ? candidate.id : undefined
      }
    },
    async loadLogs() {
      this.loadingLogs = true
      try {
        const response = await listStrategyLogs({ limit: this.queryParams.logLimit })
        this.strategyLogs = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingLogs = false
      }
    },
    async loadCapabilities() {
      this.loadingCapabilities = true
      try {
        const response = await getStrategyCapabilities()
        this.capabilities = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingCapabilities = false
      }
    },
    async loadCanaryLatest() {
      this.loadingCanary = true
      try {
        const response = await getCanaryLatest()
        this.canaryLatest = response.data || {}
      } finally {
        this.loadingCanary = false
      }
    },
    async loadScopeOptions() {
      this.loadingScope = true
      try {
        const response = await getSymbolScopeOptions()
        this.symbolScopeOptions = response.data || {}
        this.alignScopePool()
      } finally {
        this.loadingScope = false
      }
    },
    async loadScopePreview() {
      if (!this.scopeForm.scopeType) {
        this.scopePreview = {}
        return
      }
      this.loadingScopePreview = true
      try {
        const response = await getSymbolScopePreview({
          scopeType: this.scopeForm.scopeType,
          scopePoolCode: this.currentScopeNeedsPool ? this.scopeForm.scopePoolCode : undefined,
          symbols: parseSymbolText(this.scopeForm.symbolsText),
          whitelist: parseSymbolText(this.scopeForm.whitelistText),
          blacklist: parseSymbolText(this.scopeForm.blacklistText),
          adHocSymbols: parseSymbolText(this.scopeForm.adHocSymbolsText)
        })
        this.scopePreview = response.data || {}
      } finally {
        this.loadingScopePreview = false
      }
    },
    handleScopeTypeChange() {
      this.alignScopePool()
      this.loadScopePreview()
    },
    alignScopePool() {
      if (!this.scopeForm.scopeType) {
        this.scopeForm.scopePoolCode = ''
        return
      }
      if (!this.currentScopeNeedsPool) {
        this.scopeForm.scopePoolCode = ''
        return
      }
      const firstPool = this.availablePools[0]
      if (!this.scopeForm.scopePoolCode || !this.availablePools.find(item => item.poolCode === this.scopeForm.scopePoolCode)) {
        this.scopeForm.scopePoolCode = firstPool ? firstPool.poolCode : ''
      }
    },
    async loadShadowCompare() {
      if (!this.shadowQuery.baselineStrategyId || !this.shadowQuery.candidateStrategyId) {
        this.shadowPayload = {}
        return
      }
      if (this.shadowQuery.baselineStrategyId === this.shadowQuery.candidateStrategyId) {
        this.$modal.msgWarning('基线策略与候选策略不能相同')
        return
      }
      this.loadingShadow = true
      try {
        const response = await getShadowCompare(this.shadowQuery)
        this.shadowPayload = response.data || {}
      } finally {
        this.loadingShadow = false
      }
    },
    async handleExecuteResearchTask() {
      if (!this.queryParams.strategyId) {
        this.$modal.msgWarning('请先选择策略')
        return
      }
      if (!this.scopeForm.scopeType) {
        this.$modal.msgWarning('请先选择研究范围')
        return
      }
      if (this.currentScopeNeedsPool && !this.scopeForm.scopePoolCode && !this.scopeForm.symbolsText) {
        this.$modal.msgWarning('请选择正式股票池，或直接指定标的')
        return
      }
      if (!this.scopePreview.resolvedCount) {
        await this.loadScopePreview()
      }
      if (!this.scopePreview.resolvedCount) {
        this.$modal.msgWarning('当前范围没有可用标的，请调整范围模型')
        return
      }
      this.loadingRun = true
      try {
        const response = await executeQuantTask({
          strategyId: this.queryParams.strategyId,
          strategyIds: [this.queryParams.strategyId],
          strategyBacktestStartDate: this.queryParams.strategyBacktestStartDate,
          portfolioTotalCapital: this.queryParams.portfolioTotalCapital,
          usePortfolio: false,
          actor: this.queryParams.actor,
          scopeType: this.scopeForm.scopeType,
          scopePoolCode: this.currentScopeNeedsPool ? this.scopeForm.scopePoolCode : undefined,
          symbols: parseSymbolText(this.scopeForm.symbolsText),
          whitelist: parseSymbolText(this.scopeForm.whitelistText),
          blacklist: parseSymbolText(this.scopeForm.blacklistText),
          adHocSymbols: parseSymbolText(this.scopeForm.adHocSymbolsText)
        })
        const payload = response.data || {}
        this.runResult = JSON.stringify(payload, null, 2)
        const resolvedExecutionId = payload.executionId || payload.jobId
        if (resolvedExecutionId) {
          this.$modal.msgSuccess(`已提交研究任务 #${resolvedExecutionId}`)
        } else {
          this.$modal.msgSuccess('研究任务已执行')
        }
      } finally {
        this.loadingRun = false
      }
    },
    scopeLabel(scopeType) {
      const matched = this.presetScopes.find(item => item.scopeType === scopeType)
      return matched ? matched.label : (scopeType || '-')
    },
    strategyLabel(item) {
      const statusText = Number(item.status) === 1 ? '启用' : '停用'
      return `${item.id} - ${item.strategy_name} (${item.strategy_type}) [${statusText}]`
    },
    formatPercent(value) {
      if (value === null || value === undefined || value === '') {
        return '-'
      }
      return `${Number(value).toFixed(2)}%`
    },
    metricClass(value, higherBetter) {
      if (value === null || value === undefined || value === '') {
        return ''
      }
      const numeric = Number(value)
      if (numeric === 0) {
        return ''
      }
      const positive = higherBetter ? numeric > 0 : numeric < 0
      return positive ? 'is-better' : 'is-worse'
    },
    formatDelta(source, key) {
      if (!source || source[key] === null || source[key] === undefined) {
        return '-'
      }
      const value = Number(source[key])
      const prefix = value > 0 ? '+' : ''
      return `${prefix}${value.toFixed(2)}`
    },
    joinParams(params) {
      return Array.isArray(params) && params.length ? params.join(', ') : '-'
    },
    canaryTagType(recommendation) {
      const normalized = (recommendation || '').toLowerCase()
      if (normalized === 'promote_candidate') {
        return 'success'
      }
      if (normalized === 'observe') {
        return 'warning'
      }
      if (normalized === 'keep_baseline') {
        return 'info'
      }
      return 'danger'
    },
    goJobs() {
      this.$router.push('/quant/jobs')
    },
    goShadow() {
      this.$router.push('/quant/shadow')
    },
    goSymbols() {
      this.$router.push('/quant/symbols')
    }
  }
}
</script>

<style scoped>
.mt16 {
  margin-top: 16px;
}

.hero-card {
  background:
    linear-gradient(140deg, rgba(26, 188, 156, 0.1), rgba(46, 134, 222, 0.08)),
    linear-gradient(180deg, #ffffff, #f6fbfb);
}

.task-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
}

.page-subtitle,
.section-meta,
.summary-hint {
  color: #606266;
  font-size: 13px;
}

.page-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hero-overview {
  margin-top: 8px;
}

.hero-panel {
  min-height: 100%;
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid #dbeaf3;
}

.hero-panel--flat {
  background: rgba(255, 255, 255, 0.68);
}

.hero-panel__title {
  margin-bottom: 12px;
  color: #1f2d3d;
  font-size: 18px;
  font-weight: 700;
}

.hero-stack {
  display: grid;
  gap: 12px;
}

.hero-form {
  margin-top: 4px;
}

.task-brief {
  display: grid;
  gap: 10px;
}

.task-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #475569;
}

.task-line label,
.result-item label {
  color: #94a3b8;
  font-size: 12px;
}

.task-line span {
  text-align: right;
  color: #1f2937;
  font-weight: 600;
}

.task-actions {
  margin-top: 14px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.result-item {
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.result-item strong {
  display: block;
  margin-top: 6px;
  color: #1f2937;
  font-size: 18px;
}

.analysis-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.analysis-heading__title {
  color: #1f2d3d;
  font-size: 20px;
  font-weight: 700;
}

.analysis-heading__caption {
  color: #606266;
  font-size: 13px;
}

.backtest-secondary-collapse {
  border-top: 0;
}

.collapse-title {
  color: #1f2d3d;
  font-weight: 600;
}

.collapse-meta {
  margin-left: 8px;
  color: #606266;
  font-size: 12px;
}

.summary-card {
  margin-bottom: 12px;
}

.summary-title {
  font-size: 13px;
  color: #606266;
}

.summary-value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.summary-value--sm {
  font-size: 16px;
}

.is-success {
  color: #67c23a;
}

.is-better {
  color: #67c23a;
  font-weight: 600;
}

.is-worse {
  color: #f56c6c;
  font-weight: 600;
}

.run-result {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid #d9ecff;
}

.run-result__label {
  display: block;
  font-size: 12px;
  color: #909399;
}

.run-result__body {
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: #303133;
  font-size: 12px;
  font-family: Consolas, 'Courier New', monospace;
}

.run-result--compact {
  margin-top: 12px;
  padding: 10px 12px;
}

.scope-preview {
  min-height: 100%;
  padding: 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fffd, #f4f9ff);
  border: 1px solid #dff3ee;
}

.scope-preview__headline {
  display: flex;
  gap: 16px;
  align-items: center;
}

.scope-preview__count {
  min-width: 84px;
  font-size: 38px;
  line-height: 1;
  font-weight: 700;
  color: #0f766e;
}

.scope-preview__label {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
}

.scope-preview__caption {
  margin-top: 8px;
  color: #606266;
  line-height: 1.7;
}

.scope-preview__meta {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.scope-preview__symbols {
  margin-top: 16px;
  min-height: 88px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-content: flex-start;
}

.scope-preview__rule {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.85);
}

.scope-preview__rule-title {
  font-weight: 600;
  color: #303133;
}

.scope-usage-chips {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.symbol-pill {
  margin-right: 0;
}

.canary-wrap {
  min-height: 220px;
}

.canary-line {
  margin-bottom: 10px;
  color: #606266;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.canary-remark {
  color: #909399;
}

.shadow-form {
  margin-bottom: 4px;
}

.shadow-summary {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 1200px) {
  .result-grid {
    grid-template-columns: 1fr;
  }

  .task-line {
    flex-direction: column;
  }
}
</style>
