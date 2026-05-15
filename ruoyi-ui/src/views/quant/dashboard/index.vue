<template>
  <div class="app-container dashboard-page">
    <el-card shadow="never" class="box-card hero-card">
      <div class="dashboard-hero">
        <div class="dashboard-hero__copy">
          <div class="dashboard-hero__eyebrow">量化看板</div>
          <div class="dashboard-hero__title">今日状态</div>
          <div class="dashboard-hero__desc">{{ dashboardTodayStatus }}</div>
          <div class="dashboard-hero__facts">
            <div class="dashboard-hero__fact">
              <label>日期</label>
              <strong>{{ queryParams.signalDate || todayString() }}</strong>
            </div>
            <div class="dashboard-hero__fact">
              <label>市场状态</label>
              <strong>{{ marketStatus.status || '-' }}</strong>
            </div>
            <div class="dashboard-hero__fact">
              <label>待处理缺口</label>
              <strong>{{ executionFeedbackSummary.pendingSignalCount || 0 }}</strong>
            </div>
            <div class="dashboard-hero__fact">
              <label>风险等级</label>
              <strong>{{ positionRiskSummary.riskLevel || 'LOW' }}</strong>
            </div>
          </div>
        </div>
        <div class="dashboard-hero__decision">
          <div class="decision-shell">
            <div class="decision-shell__eyebrow">今日主动作</div>
            <div class="decision-shell__title">{{ dashboardNarrative.headline }}</div>
            <div class="decision-shell__line" v-for="line in dashboardNarrative.summaryLines" :key="line">{{ line }}</div>
          </div>
          <div class="dashboard-hero__actions">
            <el-button type="primary" @click="handleDashboardHeroPrimary">查看今日主动作</el-button>
            <el-button plain @click="goRouteLink({ path: '/quant/jobs' })">去调度中心</el-button>
          </div>
        </div>
      </div>
      <div class="dashboard-filter-strip">
        <el-form :inline="true" :model="queryParams" size="small" label-width="80px">
          <el-form-item label="信号日期">
            <el-date-picker
              v-model="queryParams.signalDate"
              type="date"
              value-format="yyyy-MM-dd"
              placeholder="选择日期"
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="日志条数">
            <el-input-number v-model="queryParams.logLimit" :min="1" :max="200" controls-position="right" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" :loading="loading" @click="loadAllData">刷新看板</el-button>
          </el-form-item>
        </el-form>
      </div>
      <el-alert
        v-if="topDashboardAction && topDashboardAction.actionType === 'DATA_INTEGRITY_REVIEW'"
        class="mt12"
        type="error"
        :closable="false"
        :title="topDashboardAction.title"
        :description="topDashboardAction.reason"
        show-icon
      />
    </el-card>

    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :xl="10">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>今日主动作</span>
            <span class="section-meta">今天先处理什么</span>
          </div>
          <div class="plain-list">
            <div class="todo-item todo-item--hero">
              <div class="todo-main">
                <div class="todo-title">{{ dashboardNarrative.headline }}</div>
                <div class="todo-reason" v-for="line in dashboardNarrative.summaryLines" :key="line">{{ line }}</div>
              </div>
            </div>
            <div
              v-for="item in dashboardNarrative.nextActions.slice(0, 2)"
              :key="item.renderKey"
              class="todo-item"
            >
              <div class="todo-main">
                <div class="todo-title-line">
                  <el-tag size="mini" :type="priorityTagType(item.priority)">{{ item.priority }}</el-tag>
                  <span class="todo-title">{{ item.title }}</span>
                </div>
                <div class="todo-reason">{{ item.reason }}</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="14">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>下一步去哪里</span>
            <span class="section-meta">只保留 3 个入口</span>
          </div>
          <div class="entry-card-grid">
            <div v-for="item in dashboardEntryCards" :key="item.key" class="entry-card">
              <div class="entry-card__header">
                <span class="entry-card__title">{{ item.title }}</span>
                <el-tag size="mini" :type="item.tagType">{{ item.status }}</el-tag>
              </div>
              <div class="entry-card__value">{{ item.value }}</div>
              <div class="entry-card__desc">{{ item.description }}</div>
              <div class="entry-card__expectation">{{ item.expectation }}</div>
              <div class="entry-card__actions">
                <el-button size="mini" type="primary" plain @click="goRouteLink(item.link)">{{ item.actionLabel }}</el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-collapse v-model="dashboardSecondaryPanels" class="mt16 dashboard-secondary-collapse">
      <el-collapse-item name="overview">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>更多系统概览</span>
            <span class="section-meta">运行总量与快捷入口</span>
          </div>
        </template>
        <el-row :gutter="16">
          <el-col :xs="24" :md="12" :lg="6">
            <el-card shadow="hover" class="summary-card">
              <div class="summary-title">策略数</div>
              <div class="summary-value">{{ summary.strategyCount || 0 }}</div>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="12" :lg="6">
            <el-card shadow="hover" class="summary-card">
              <div class="summary-title">今日信号数</div>
              <div class="summary-value">{{ summary.todaySignalCount || 0 }}</div>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="12" :lg="6">
            <el-card shadow="hover" class="summary-card">
              <div class="summary-title">持仓数</div>
              <div class="summary-value">{{ summary.positionCount || 0 }}</div>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="12" :lg="6">
            <el-card shadow="hover" class="summary-card">
              <div class="summary-title">风险预警数</div>
              <div class="summary-value is-risk">{{ summary.riskWarningCount || 0 }}</div>
            </el-card>
          </el-col>
        </el-row>
        <div class="summary-extra">
          <span>平均年化收益：{{ formatPercent(summary.averageAnnualReturn) }}</span>
          <span class="status-line">
            市场状态：
            <el-tag size="mini" :type="marketStatusTagType(marketStatus.status)">
              {{ marketStatus.status || '-' }}
            </el-tag>
            <span class="status-meta" v-if="marketStatus.trade_date">（{{ marketStatus.trade_date }}）</span>
          </span>
        </div>
        <div class="dashboard-shortcuts">
          <el-button
            v-for="(item, index) in dashboardDeepLinks"
            :key="dashboardDeepLinkKey(item, index)"
            size="mini"
            :type="deepLinkButtonType(item.variant)"
            plain
            @click="goRouteLink(item)"
          >
            {{ item.title }}
            <span v-if="Number(item.badge || 0) > 0" class="shortcut-badge">{{ item.badge }}</span>
          </el-button>
          <el-button size="mini" @click="goSymbolTracker">查看标的跟踪</el-button>
          <el-button size="mini" @click="goBacktestAnalysis">查看回测分析</el-button>
        </div>
      </el-collapse-item>

      <el-collapse-item name="objects">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>对象层摘要</span>
            <span class="section-meta">指数 / ETF / 个股</span>
          </div>
        </template>
        <div v-loading="loadingEtfOverview || loadingReviewCandidates || loadingValuations" class="object-layer-grid">
          <div v-for="item in objectLayers" :key="item.key" class="object-layer-card">
            <div class="object-layer-card__meta">{{ item.metricLabel }}</div>
            <div class="object-layer-card__title">{{ item.title }}</div>
            <div class="object-layer-card__summary">{{ item.summary }}</div>
            <div class="object-layer-card__desc">{{ item.description }}</div>
            <div class="risk-tags">
              <el-tag
                v-for="highlight in item.highlights"
                :key="`${item.key}-${highlight}`"
                size="mini"
                type="info"
              >
                {{ highlight }}
              </el-tag>
            </div>
            <div class="object-layer-card__actions">
              <el-button v-if="item.key === 'index'" type="text" size="mini" @click="goBacktestAnalysis()">{{ item.actionLabel }}</el-button>
              <el-button v-else-if="item.key === 'etf'" type="text" size="mini" @click="goRouteLink({ path: '/quant/jobs' })">{{ item.actionLabel }}</el-button>
              <el-button v-else type="text" size="mini" @click="goRouteLink({ path: '/quant/review' })">{{ item.actionLabel }}</el-button>
            </div>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>

    <el-dialog
      title="信号解释"
      :visible.sync="signalExplainDialogVisible"
      width="720px"
      append-to-body
    >
      <div v-loading="loadingSignalExplain" class="signal-explain-body">
        <div class="signal-explain-headline">
          <span>{{ signalExplainPayload.headline || '信号解释' }}</span>
          <el-tag size="mini" :type="feedbackTagType(signalExplainPayload.executionStatus)">
            {{ signalExplainPayload.executionStatus || 'PENDING' }}
          </el-tag>
        </div>
        <div class="signal-meta-grid">
          <div class="risk-metric">
            <span class="risk-label">标的</span>
            <span class="risk-value">{{ signalExplainPayload.stockCode || '-' }} {{ signalExplainPayload.stockName || '' }}</span>
          </div>
          <div class="risk-metric">
            <span class="risk-label">策略</span>
            <span class="risk-value">{{ signalExplainPayload.strategyName || signalExplainPayload.strategyId || '-' }}</span>
          </div>
          <div class="risk-metric">
            <span class="risk-label">信号日</span>
            <span class="risk-value">{{ signalExplainPayload.signalDate || '-' }}</span>
          </div>
          <div class="risk-metric">
            <span class="risk-label">市场状态</span>
            <span class="risk-value">{{ signalExplainPayload.marketStatus || '-' }}</span>
          </div>
        </div>
        <div class="plain-list mt16">
          <div
            v-for="(line, index) in signalExplainPayload.summaryLines || []"
            :key="`signal-summary-${index}`"
            class="plain-list-item"
          >
            {{ line }}
          </div>
        </div>
        <div class="dashboard-shortcuts mt16">
          <el-button
            v-for="item in signalExplainPayload.actions || []"
            :key="`${item.path}-${item.title}`"
            size="mini"
            type="primary"
            plain
            @click="goRouteLink(item)"
          >
            {{ item.title }}
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import ElAlert from 'element-ui/lib/alert'
import ElCollapse from 'element-ui/lib/collapse'
import ElCollapseItem from 'element-ui/lib/collapse-item'
import 'element-ui/lib/theme-chalk/alert.css'
import 'element-ui/lib/theme-chalk/collapse.css'
import {
  getCanaryLatest,
  getDashboardActionItems,
  getDashboardDeepLinks,
  getDashboardReviewCandidates,
  getDashboardSummary,
  getEtfOverview,
  getExecutionReconciliationSummary,
  getMarketStatus,
  getPositionRiskSummary,
  getSignalExplain,
  listExecutionFeedbackDetails,
  listIndexValuations,
  listPositions,
  listSignals,
  listStrategySwitchAudits,
  listStrategyLogs
} from '@/api/quant'
const { buildDashboardNarrative } = require('./dashboard-explain')
const { buildDashboardObjectLayers } = require('./dashboard-object-layer')
const { buildDashboardActionItemKey, buildDashboardDeepLinkKey } = require('./dashboard-keys')

const {
  buildDashboardReviewCandidateQuery,
  reviewAssetTagLabel
} = require('./dashboard-context')
const {
  getDashboardBootstrapCriticalKeys,
  getDashboardBootstrapDeferredKeys
} = require('./dashboard-page-state')

export default {
  name: 'QuantDashboard',
  components: {
    ElAlert,
    ElCollapse,
    ElCollapseItem
  },
  data() {
    return {
      loading: false,
      loadingSignals: false,
      loadingPositions: false,
      loadingLogs: false,
      loadingValuations: false,
      loadingSwitchAudits: false,
      loadingFeedback: false,
      loadingCanary: false,
      loadingActionItems: false,
      loadingRiskSummary: false,
      loadingReviewCandidates: false,
      loadingEtfOverview: false,
      loadingSignalExplain: false,
      summary: {},
      marketStatus: {},
      signals: [],
      positions: [],
      strategyLogs: [],
      indexValuations: [],
      strategySwitchAudits: [],
      executionFeedbackSummary: {},
      executionFeedbackDetails: [],
      canaryLatest: {},
      dashboardActionItems: [],
      positionRiskSummary: {},
      dashboardDeepLinks: [],
      reviewCandidates: [],
      etfOverview: {},
      signalExplainDialogVisible: false,
      signalExplainPayload: {},
      dashboardSecondaryPanels: [],
      dashboardLoadRequestId: 0,
      queryParams: {
        signalDate: this.todayString(),
        logLimit: 20,
        switchLimit: 20,
        feedbackLimit: 20
      }
    }
  },
  computed: {
    topDashboardAction() {
      return Array.isArray(this.dashboardActionItems) && this.dashboardActionItems.length
        ? this.dashboardActionItems[0]
        : null
    },
    dashboardNarrative() {
      return buildDashboardNarrative({
        actionItems: this.dashboardActionItems,
        marketStatus: this.marketStatus,
        riskSummary: this.positionRiskSummary,
        reviewCandidates: this.reviewCandidates,
        executionPendingCount: this.executionFeedbackSummary.pendingSignalCount
      })
    },
    dashboardTodayStatus() {
      if (this.topDashboardAction && this.topDashboardAction.actionType === 'DATA_INTEGRITY_REVIEW') {
        return '需处理'
      }
      if ((this.executionFeedbackSummary.pendingSignalCount || 0) > 0 || (this.positionRiskSummary.overBudgetCount || 0) > 0) {
        return '需处理'
      }
      if ((this.reviewCandidates || []).length || (this.dashboardActionItems || []).length) {
        return '观察中'
      }
      return '已完成'
    },
    objectLayers() {
      return buildDashboardObjectLayers({
        marketStatus: this.marketStatus,
        etfOverview: this.etfOverview,
        reviewCandidates: this.reviewCandidates,
        positionRiskSummary: this.positionRiskSummary,
        executionFeedbackSummary: this.executionFeedbackSummary,
        indexValuationCount: Array.isArray(this.indexValuations) ? this.indexValuations.length : 0
        })
    },
    dashboardHeroPrimaryLink() {
      if (this.topDashboardAction && this.topDashboardAction.path) {
        return {
          path: this.topDashboardAction.path,
          query: this.topDashboardAction.query || {}
        }
      }
      return {
        path: '/quant/jobs',
        query: {}
      }
    },
    dashboardEntryCards() {
      return [
        {
          key: 'jobs',
          title: '调度中心',
          status: this.taskStatusLabel(this.summary.runningJobCount || this.executionFeedbackSummary.pendingSignalCount || 0),
          tagType: this.summary.runningJobCount > 0 ? 'warning' : 'info',
          value: `${this.summary.runningJobCount || 0} 个运行中任务`,
          description: this.summary.runningJobCount > 0 ? '当前已有任务在跑，先看进度再决定是否重提。' : '当前没有运行中任务，可从这里发起或查看调度。',
          expectation: '进入后可查看当前任务、最近 3 条调度和下一次自动计划。',
          actionLabel: this.summary.runningJobCount > 0 ? '查看调度' : '去调度中心',
          link: { path: '/quant/jobs' }
        },
        {
          key: 'execution',
          title: '执行闭环',
          status: this.taskStatusLabel(this.executionFeedbackSummary.pendingSignalCount || this.executionFeedbackSummary.unmatchedExecutionCount || 0),
          tagType: (this.executionFeedbackSummary.pendingSignalCount || this.executionFeedbackSummary.unmatchedExecutionCount || 0) > 0 ? 'danger' : 'success',
          value: `${(this.executionFeedbackSummary.pendingSignalCount || 0) + (this.executionFeedbackSummary.unmatchedExecutionCount || 0)} 条待处理异常`,
          description: '优先看未匹配成交、漏执行和持仓差异是否影响今日动作。',
          expectation: '进入后会直接聚焦异常优先级和当前异常列表。',
          actionLabel: '去执行闭环',
          link: { path: '/quant/execution' }
        },
        {
          key: 'review',
          title: '复盘治理',
          status: this.taskStatusLabel((this.reviewCandidates || []).length),
          tagType: (this.reviewCandidates || []).length > 0 ? 'warning' : 'info',
          value: `${(this.reviewCandidates || []).length} 个待沉淀对象`,
          description: '把已经处理完成或反复出现的问题沉淀成正式结论。',
          expectation: '进入后可直接看到当前对象、当前结论和核心证据。',
          actionLabel: '去复盘治理',
          link: { path: '/quant/review' }
        }
      ]
    }
  },
  created() {
    this.loadAllData()
  },
  methods: {
    todayString() {
      const now = new Date()
      const year = now.getFullYear()
      const month = `${now.getMonth() + 1}`.padStart(2, '0')
      const day = `${now.getDate()}`.padStart(2, '0')
      return `${year}-${month}-${day}`
    },
    taskStatusLabel(count) {
      return Number(count || 0) > 0 ? '需处理' : '观察中'
    },
    async loadAllData() {
      const requestId = this.dashboardLoadRequestId + 1
      this.dashboardLoadRequestId = requestId
      this.loading = true
      const taskMap = {
        summary: () => this.loadSummary(),
        marketStatus: () => this.loadMarketStatus(),
        dashboardDeepLinks: () => this.loadDashboardDeepLinks(),
        dashboardActionItems: () => this.loadDashboardActionItems(),
        positionRiskSummary: () => this.loadPositionRiskSummary(),
        signals: () => this.loadSignals(),
        positions: () => this.loadPositions(),
        executionFeedbackSummary: () => this.loadExecutionFeedbackSummary(requestId),
        executionFeedbackDetails: () => this.loadExecutionFeedbackDetails(requestId),
        reviewCandidates: () => this.loadReviewCandidates(),
        etfOverview: () => this.loadEtfOverview(),
        logs: () => this.loadLogs(),
        valuations: () => this.loadValuations(),
        switchAudits: () => this.loadSwitchAudits(),
        canaryLatest: () => this.loadCanaryLatest()
      }
      try {
        await Promise.all(getDashboardBootstrapCriticalKeys().map(key => taskMap[key]()))
      } finally {
        if (requestId === this.dashboardLoadRequestId) {
          this.loading = false
        }
      }
      this.scheduleDeferredWork(async () => {
        if (requestId !== this.dashboardLoadRequestId) {
          return
        }
        await Promise.all(getDashboardBootstrapDeferredKeys().map(key => taskMap[key]()))
      })
    },
    scheduleDeferredWork(task) {
      const runner = () => Promise.resolve()
        .then(task)
        .catch(() => {})
      if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
        window.requestAnimationFrame(() => runner())
        return
      }
      setTimeout(() => runner(), 0)
    },
    async loadSummary() {
      const response = await getDashboardSummary()
      this.summary = response.data || {}
    },
    async loadSignals() {
      this.loadingSignals = true
      try {
        const response = await listSignals({ signalDate: this.queryParams.signalDate })
        this.signals = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingSignals = false
      }
    },
    async loadPositions() {
      this.loadingPositions = true
      try {
        const response = await listPositions()
        this.positions = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingPositions = false
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
    async loadMarketStatus() {
      const response = await getMarketStatus()
      this.marketStatus = response.data || {}
    },
    async loadValuations() {
      this.loadingValuations = true
      try {
        const response = await listIndexValuations({ limit: 20 })
        this.indexValuations = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingValuations = false
      }
    },
    async loadSwitchAudits() {
      this.loadingSwitchAudits = true
      try {
        const response = await listStrategySwitchAudits({ limit: this.queryParams.switchLimit })
        this.strategySwitchAudits = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingSwitchAudits = false
      }
    },
    async loadExecutionFeedbackSummary(requestId = this.dashboardLoadRequestId) {
      const response = await getExecutionReconciliationSummary()
      if (requestId === this.dashboardLoadRequestId) {
        this.executionFeedbackSummary = response.data || {}
      }
    },
    async loadExecutionFeedbackDetails(requestId = this.dashboardLoadRequestId) {
      this.loadingFeedback = true
      try {
        const response = await listExecutionFeedbackDetails({ limit: this.queryParams.feedbackLimit })
        if (requestId === this.dashboardLoadRequestId) {
          this.executionFeedbackDetails = Array.isArray(response.data) ? response.data : []
        }
      } finally {
        if (requestId === this.dashboardLoadRequestId) {
          this.loadingFeedback = false
        }
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
    async loadDashboardActionItems() {
      this.loadingActionItems = true
      try {
        const response = await getDashboardActionItems({ limit: 8 })
        this.dashboardActionItems = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingActionItems = false
      }
    },
    async loadPositionRiskSummary() {
      this.loadingRiskSummary = true
      try {
        const response = await getPositionRiskSummary()
        this.positionRiskSummary = response.data || {}
      } finally {
        this.loadingRiskSummary = false
      }
    },
    async loadDashboardDeepLinks() {
      const response = await getDashboardDeepLinks()
      this.dashboardDeepLinks = Array.isArray(response.data) ? response.data : []
    },
    async loadReviewCandidates() {
      this.loadingReviewCandidates = true
      try {
        const response = await getDashboardReviewCandidates({ limit: 8 })
        this.reviewCandidates = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingReviewCandidates = false
      }
    },
    dashboardActionItemKey(item, index) {
      return buildDashboardActionItemKey(item, index)
    },
    dashboardDeepLinkKey(item, index) {
      return buildDashboardDeepLinkKey(item, index)
    },
    async loadEtfOverview() {
      this.loadingEtfOverview = true
      try {
        const response = await getEtfOverview()
        this.etfOverview = response.data || {}
      } finally {
        this.loadingEtfOverview = false
      }
    },
    async openSignalExplain(row) {
      if (!row || !row.id) {
        return
      }
      this.signalExplainDialogVisible = true
      this.loadingSignalExplain = true
      try {
        const response = await getSignalExplain(row.id)
        this.signalExplainPayload = response.data || {}
      } finally {
        this.loadingSignalExplain = false
      }
    },
    normalizeRouteQuery(query) {
      const normalized = {}
      Object.keys(query || {}).forEach(key => {
        const value = query[key]
        if (value !== undefined && value !== null && value !== '') {
          normalized[key] = value
        }
      })
      return normalized
    },
    goRouteLink(item) {
      if (!item || !item.path) {
        return
      }
      this.$router.push({
        path: item.path,
        query: this.normalizeRouteQuery(item.query || {})
      }).catch(() => {})
    },
    handleDashboardHeroPrimary() {
      this.goRouteLink(this.dashboardHeroPrimaryLink)
    },
    goExecutionFocus(focus) {
      this.$router.push({
        path: '/quant/execution',
        query: this.normalizeRouteQuery({ focus })
      }).catch(() => {})
    },
    goExecutionFromSignal(row) {
      if (!row) {
        return
      }
      this.$router.push({
        path: '/quant/execution',
        query: this.normalizeRouteQuery({
          signalId: row.id,
          stockCode: row.stock_code,
          strategyId: row.strategy_id,
          focus: 'all'
        })
      }).catch(() => {})
    },
    goReviewFromSignal(row) {
      if (!row) {
        return
      }
      this.$router.push({
        path: '/quant/review',
        query: this.normalizeRouteQuery({
          reviewLevel: 'trade',
          signalId: row.id,
          stockCode: row.stock_code,
          strategyId: row.strategy_id,
          sourcePage: 'dashboard',
          sourceAction: 'signal'
        })
      }).catch(() => {})
    },
    goReviewCandidate(row) {
      if (!row) {
        return
      }
      this.$router.push({
        path: '/quant/review',
        query: this.normalizeRouteQuery(buildDashboardReviewCandidateQuery(row))
      }).catch(() => {})
    },
    goShadowCompareFromCanary() {
      this.$router.push({
        path: '/quant/shadow',
        query: this.normalizeRouteQuery({
          baselineStrategyId: this.canaryLatest.baseline_strategy_id,
          candidateStrategyId: this.canaryLatest.candidate_strategy_id
        })
      }).catch(() => {})
    },
    goGovernanceReviewFromCanary() {
      this.$router.push({
        path: '/quant/review',
        query: this.normalizeRouteQuery({
          reviewLevel: 'governance',
          baselineStrategyId: this.canaryLatest.baseline_strategy_id,
          candidateStrategyId: this.canaryLatest.candidate_strategy_id,
          sourcePage: 'dashboard',
          sourceAction: 'governanceCandidate'
        })
      }).catch(() => {})
    },
    goSymbolTracker() {
      this.$router.push('/quant/symbols').catch(() => {})
    },
    goBacktestAnalysis() {
      this.$router.push('/quant/backtest').catch(() => {})
    },
    goEtfBacktest(scopeType = 'etf_pool', scopePoolCode = 'ETF_CORE') {
      this.$router.push({
        path: '/quant/backtest',
        query: this.normalizeRouteQuery({
          scopeType,
          scopePoolCode
        })
      }).catch(() => {})
    },
    goEtfReview() {
      this.$router.push({
        path: '/quant/review',
        query: this.normalizeRouteQuery({
          reviewLevel: 'trade',
          scopeType: 'etf_pool',
          scopePoolCode: 'ETF_CORE',
          sourcePage: 'dashboard',
          sourceAction: 'etfOverview'
        })
      }).catch(() => {})
    },
    goEtfSymbols() {
      this.$router.push({
        path: '/quant/symbols',
        query: this.normalizeRouteQuery({
          scopeType: this.etfOverview.mappedScopeType || 'index_mapped_etf_pool',
          scopePoolCode: this.etfOverview.mappedScopePoolCode || 'INDEX_ETF_DEFAULT'
        })
      }).catch(() => {})
    },
    reviewAssetTagLabel(assetType) {
      return reviewAssetTagLabel(assetType)
    },
    joinEtfNames(codes, names) {
      const codeList = Array.isArray(codes) ? codes : []
      const nameList = Array.isArray(names) ? names : []
      if (!codeList.length) {
        return '-'
      }
      return codeList.map((code, index) => {
        const name = nameList[index]
        return name ? `${code} ${name}` : code
      }).join(' / ')
    },
    formatNumber(value) {
      if (value === null || value === undefined || value === '') {
        return '-'
      }
      return Number(value).toFixed(2)
    },
    formatPercent(value) {
      if (value === null || value === undefined || value === '') {
        return '-'
      }
      return `${Number(value).toFixed(2)}%`
    },
    profitClass(value) {
      if (value === null || value === undefined) {
        return ''
      }
      const numeric = Number(value)
      if (numeric > 0) {
        return 'profit-up'
      }
      if (numeric < 0) {
        return 'profit-down'
      }
      return ''
    },
    marketStatusTagType(status) {
      const normalized = (status || '').toLowerCase()
      if (normalized === 'bull') {
        return 'success'
      }
      if (normalized === 'bear' || normalized === 'panic') {
        return 'danger'
      }
      if (normalized === 'volatile') {
        return 'warning'
      }
      return 'info'
    },
    valuationClass(percentile) {
      if (percentile === null || percentile === undefined) {
        return ''
      }
      const value = Number(percentile)
      if (value <= 30) {
        return 'valuation-low'
      }
      if (value >= 70) {
        return 'valuation-high'
      }
      return ''
    },
    decisionTagType(decision) {
      const normalized = (decision || '').toUpperCase()
      if (normalized === 'ALLOW') {
        return 'success'
      }
      if (normalized === 'BLOCK') {
        return 'danger'
      }
      return 'info'
    },
    feedbackTagType(status) {
      const normalized = (status || '').toUpperCase()
      if (normalized === 'EXECUTED') {
        return 'success'
      }
      if (normalized === 'MISSED') {
        return 'danger'
      }
      if (normalized === 'PENDING') {
        return 'warning'
      }
      return 'info'
    },
    canaryTagType(recommendation) {
      const normalized = (recommendation || '').toLowerCase()
      if (normalized === 'promote_candidate') {
        return 'success'
      }
      if (normalized === 'keep_baseline') {
        return 'info'
      }
      if (normalized === 'observe') {
        return 'warning'
      }
      return 'danger'
    },
    priorityTagType(priority) {
      if (priority === 'P0') return 'danger'
      if (priority === 'P1') return 'warning'
      return 'info'
    },
    riskLevelTagType(level) {
      if (level === 'HIGH') return 'danger'
      if (level === 'MEDIUM') return 'warning'
      return 'success'
    },
    deepLinkButtonType(variant) {
      if (variant === 'warning') return 'warning'
      if (variant === 'danger') return 'danger'
      if (variant === 'primary') return 'primary'
      return 'success'
    }
  }
}
</script>

<style scoped>
.dashboard-page {
  background:
    radial-gradient(circle at top right, rgba(238, 204, 126, 0.18), transparent 28%),
    linear-gradient(180deg, #f7f4ec 0%, #f5f7fa 48%, #eef2f7 100%);
}

.mt16 {
  margin-top: 16px;
}

.mt12 {
  margin-top: 12px;
}

.mb12 {
  margin-bottom: 12px;
}

.summary-card {
  margin-bottom: 12px;
  min-height: 120px;
  border: 1px solid rgba(208, 190, 154, 0.24);
  background: linear-gradient(160deg, rgba(255, 248, 234, 0.96), rgba(246, 249, 255, 0.94));
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

.is-risk {
  color: #f56c6c;
}

.hero-card {
  border: 1px solid #e6dcc8;
  background: linear-gradient(135deg, rgba(255, 248, 232, 0.94), rgba(247, 250, 255, 0.98));
}

.dashboard-hero {
  display: flex;
  justify-content: space-between;
  gap: 22px;
  align-items: flex-start;
}

.dashboard-hero__copy {
  flex: 1;
}

.dashboard-hero__eyebrow {
  color: #8a5c12;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.dashboard-hero__title {
  margin-top: 10px;
  color: #172033;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.35;
}

.dashboard-hero__desc {
  margin-top: 10px;
  max-width: 760px;
  color: #606266;
  line-height: 1.8;
}

.dashboard-hero__facts {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.dashboard-hero__fact {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(208, 190, 154, 0.28);
}

.dashboard-hero__fact label {
  display: block;
  color: #7c8595;
  font-size: 12px;
}

.dashboard-hero__fact strong {
  display: block;
  margin-top: 6px;
  color: #18283d;
  font-size: 16px;
  line-height: 1.5;
}

.dashboard-hero__decision {
  min-width: 340px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.decision-shell {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(208, 190, 154, 0.28);
  box-shadow: 0 12px 28px rgba(24, 40, 61, 0.08);
}

.decision-shell__eyebrow {
  color: #8a5c12;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.decision-shell__title {
  margin-top: 8px;
  color: #172033;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.45;
}

.decision-shell__line {
  margin-top: 10px;
  color: #5f6b7a;
  line-height: 1.7;
}

.dashboard-hero__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.dashboard-filter-strip {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed rgba(208, 190, 154, 0.4);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-meta {
  color: #909399;
  font-size: 12px;
}

.summary-extra {
  color: #606266;
  font-size: 13px;
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.dashboard-shortcuts {
  margin-bottom: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.object-layer-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.object-layer-card {
  padding: 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(221, 228, 239, 0.96);
}

.object-layer-card__meta {
  color: #909399;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.object-layer-card__title {
  margin-top: 8px;
  color: #303133;
  font-size: 18px;
  font-weight: 700;
}

.object-layer-card__summary {
  margin-top: 8px;
  color: #18283d;
  font-size: 22px;
  font-weight: 700;
}

.object-layer-card__desc {
  margin-top: 10px;
  color: #606266;
  line-height: 1.7;
  min-height: 44px;
}

.object-layer-card__actions {
  margin-top: 12px;
}

.shortcut-badge {
  margin-left: 4px;
  color: #909399;
}

.status-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.status-meta {
  color: #909399;
}

.todo-list {
  min-height: 220px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.todo-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid rgba(230, 220, 200, 0.78);
  background: rgba(255, 255, 255, 0.82);
}

.todo-main {
  flex: 1;
}

.todo-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.todo-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.todo-reason {
  margin-top: 8px;
  color: #606266;
  line-height: 1.6;
}

.risk-summary-grid,
.signal-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.risk-metric {
  padding: 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(235, 238, 245, 0.92);
}

.risk-label {
  display: block;
  color: #909399;
  font-size: 12px;
}

.risk-value {
  display: block;
  margin-top: 8px;
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}

.risk-tags,
.feedback-summary {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.plain-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.plain-list-item {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(235, 238, 245, 0.92);
  background: rgba(255, 255, 255, 0.8);
  color: #606266;
  line-height: 1.7;
}

.entry-card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.entry-card {
  padding: 16px 14px;
  border-radius: 14px;
  border: 1px solid rgba(230, 220, 200, 0.78);
  background: rgba(255, 255, 255, 0.84);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.entry-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.entry-card__title {
  color: #303133;
  font-size: 15px;
  font-weight: 700;
}

.entry-card__value {
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
}

.entry-card__desc,
.entry-card__expectation {
  color: #606266;
  line-height: 1.6;
}

.entry-card__expectation {
  color: #909399;
  font-size: 12px;
}

.entry-card__actions {
  margin-top: auto;
}

.dashboard-secondary-collapse {
  border-radius: 12px;
  overflow: hidden;
}

.collapse-title-shell {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 600;
}

.feedback-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.feedback-shortcuts {
  display: inline-flex;
  gap: 8px;
  flex-wrap: wrap;
}

.canary-wrap {
  min-height: 140px;
}

.canary-line {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  margin-bottom: 8px;
  color: #606266;
}

.canary-remark {
  color: #909399;
}

.signal-explain-body {
  min-height: 220px;
}

.signal-explain-headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.profit-up {
  color: #67c23a;
  font-weight: 600;
}

.profit-down {
  color: #f56c6c;
  font-weight: 600;
}

.valuation-low {
  color: #67c23a;
  font-weight: 600;
}

.valuation-high {
  color: #f56c6c;
  font-weight: 600;
}

@media (max-width: 1200px) {
  .dashboard-hero {
    flex-direction: column;
  }

  .dashboard-hero__decision {
    min-width: auto;
    width: 100%;
  }

  .object-layer-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-hero__facts {
    grid-template-columns: 1fr;
  }

  .entry-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
