<template>
  <div class="app-container">
    <el-card shadow="never" class="box-card">
      <div slot="header" class="section-header">
        <span>影子对比</span>
        <span class="section-meta">治理判断</span>
      </div>
      <el-form :model="queryParams" :inline="true" size="small" label-width="96px">
        <el-form-item label="基线策略">
          <el-select v-model="queryParams.baselineStrategyId" placeholder="请选择基线策略" filterable style="width: 280px">
            <el-option
              v-for="item in strategyList"
              :key="item.id"
              :label="strategyLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="候选策略">
          <el-select v-model="queryParams.candidateStrategyId" placeholder="请选择候选策略" filterable style="width: 280px">
            <el-option
              v-for="item in strategyList"
              :key="item.id"
              :label="strategyLabel(item)"
              :value="item.id"
              :disabled="item.id === queryParams.baselineStrategyId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="统计月份">
          <el-input-number v-model="queryParams.months" :min="1" :max="24" controls-position="right" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" :loading="loadingCompare" @click="refreshGovernance">查询</el-button>
          <el-button icon="el-icon-refresh" :loading="loadingCompare" @click="loadAll">刷新</el-button>
          <el-button
            v-if="$auth.hasPermi('quant:job:run')"
            icon="el-icon-document"
            :loading="loadingReport"
            @click="handleGenerateReport"
          >
            生成报告
          </el-button>
        </el-form-item>
      </el-form>
      <div class="header-actions">
        <el-button size="mini" @click="goBacktestAnalysis">去回测分析</el-button>
        <el-button size="mini" @click="goReviewOverview">去复盘总览</el-button>
      </div>
      <div v-if="reportMessage" class="report-message">{{ reportMessage }}</div>
    </el-card>

    <el-card shadow="never" class="box-card mt16">
      <div slot="header" class="section-header">
        <span>治理结论</span>
        <span class="section-meta">先看建议，再看证据</span>
      </div>
      <div class="shadow-conclusion-grid">
        <div class="shadow-conclusion-card">
          <label>系统建议</label>
          <strong>
            <el-tag size="mini" :type="recommendationTagType(summaryPayload.recommendation)">
              {{ summaryPayload.recommendation || '暂无结论' }}
            </el-tag>
          </strong>
          <span>{{ summaryPayload.recommendationReason || '请先查询影子对比结果' }}</span>
        </div>
        <div class="shadow-conclusion-card">
          <label>治理动作</label>
          <strong>
            <el-tag size="mini" :type="governanceActionTagType(summaryPayload.governanceAction)">
              {{ summaryPayload.governanceAction || '待确认' }}
            </el-tag>
          </strong>
          <span>置信度 {{ summaryPayload.confidenceLevel || '-' }}</span>
        </div>
        <div class="shadow-conclusion-card">
          <label>最近治理</label>
          <strong>
            <el-tag size="mini" :type="governanceActionTagType(latestDecision.governanceAction)">
              {{ latestDecision.governanceAction || '暂无记录' }}
            </el-tag>
          </strong>
          <span>{{ latestDecision.actor || '待人工确认' }}</span>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="box-card mt16" v-loading="loadingActionItems">
      <div slot="header" class="section-header">
        <span>治理建议</span>
        <span class="section-meta">{{ shadowActionPlan.headline }}</span>
      </div>
      <div class="plain-list">
        <div v-for="line in shadowActionPlan.summaryLines" :key="line" class="plain-list-item">
          {{ line }}
        </div>
      </div>
      <div v-if="shadowActionPlan.nextActions.length" class="ops-action-list">
        <div
          v-for="item in shadowActionPlan.nextActions"
          :key="item.renderKey"
          class="ops-action-item"
        >
          <div class="ops-action-main">
            <div class="ops-action-title">
              <el-tag size="mini" :type="priorityTagType(item.priority)">{{ item.priority }}</el-tag>
              <span>{{ item.title }}</span>
              <el-tag v-if="item.isCurrentPage" size="mini" type="success">当前页</el-tag>
              <el-tag v-else-if="item.isCrossPage" size="mini" type="info">跨页</el-tag>
            </div>
            <div class="ops-action-reason">{{ item.reason }}</div>
          </div>
          <el-button type="text" size="mini" @click="handleActionItem(item)">
            {{ item.isCurrentPage ? '去决策' : '去处理' }}
          </el-button>
        </div>
      </div>
      <el-empty v-else description="当前没有额外运营动作，可直接完成治理判断" :image-size="60" />
    </el-card>

    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :xl="8">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>治理摘要</span>
            <span class="section-meta">{{ compareTargetText }}</span>
          </div>
          <div class="evidence-list">
            <div v-for="item in summaryPayload.coreEvidences || []" :key="item" class="evidence-item">
              <el-tag size="mini" type="success">核心证据</el-tag>
              <span>{{ item }}</span>
            </div>
            <el-empty v-if="!(summaryPayload.coreEvidences || []).length" description="暂无治理证据摘要" :image-size="60" />
          </div>
          <div class="risk-list">
            <div class="sub-title">风险提示</div>
            <div v-if="(summaryPayload.riskNotes || []).length" class="risk-item" v-for="item in summaryPayload.riskNotes" :key="item">
              {{ item }}
            </div>
            <el-empty v-else description="暂无额外风险提示" :image-size="60" />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="8">
        <el-card shadow="never" class="box-card" v-loading="loadingApplicability">
          <div slot="header" class="section-header">
            <span>适用边界</span>
            <span class="section-meta">治理结论</span>
          </div>
          <div class="sub-title">候选优势</div>
          <div v-if="(applicabilityPayload.strengths || []).length" class="plain-list">
            <div v-for="item in applicabilityPayload.strengths" :key="item" class="plain-list-item">{{ item }}</div>
          </div>
          <el-empty v-else description="暂无候选优势说明" :image-size="60" />
          <div class="sub-title">适用边界</div>
          <div v-if="(applicabilityPayload.riskNotes || []).length" class="plain-list">
            <div v-for="item in applicabilityPayload.riskNotes" :key="item" class="plain-list-item plain-list-item--risk">{{ item }}</div>
          </div>
          <el-empty v-else description="暂无适用边界说明" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="8">
        <el-card ref="decisionSection" shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>提交治理动作</span>
            <span class="section-meta">直接做结论</span>
          </div>
          <el-form :model="decisionForm" label-width="108px" size="small">
            <el-form-item label="治理动作">
              <el-select v-model="decisionForm.governanceAction" style="width: 100%">
                <el-option label="KEEP" value="KEEP" />
                <el-option label="OBSERVE" value="OBSERVE" />
                <el-option label="REPLACE" value="REPLACE" />
                <el-option label="DISABLE" value="DISABLE" />
              </el-select>
            </el-form-item>
            <el-form-item label="置信度">
              <el-select v-model="decisionForm.confidenceLevel" style="width: 100%">
                <el-option label="HIGH" value="HIGH" />
                <el-option label="MEDIUM" value="MEDIUM" />
                <el-option label="LOW" value="LOW" />
              </el-select>
            </el-form-item>
            <el-form-item label="审批状态">
              <el-select v-model="decisionForm.approvalStatus" style="width: 100%">
                <el-option label="PENDING" value="PENDING" />
                <el-option label="APPROVED" value="APPROVED" />
                <el-option label="REJECTED" value="REJECTED" />
              </el-select>
            </el-form-item>
            <el-form-item label="生效日期">
              <el-date-picker v-model="decisionForm.effectiveFrom" type="date" value-format="yyyy-MM-dd" style="width: 100%" />
            </el-form-item>
            <el-form-item label="补充说明">
              <el-input v-model="decisionForm.remark" type="textarea" :rows="3" placeholder="记录人工决策原因、审批备注或上线前提" />
            </el-form-item>
            <div class="detail-summary">
              <el-tag size="mini" type="info">可比月份 {{ comparableMonths }}</el-tag>
              <el-tag size="mini" :type="governanceActionTagType(latestDecision.governanceAction)">
                最近治理 {{ latestDecision.governanceAction || '暂无记录' }}
              </el-tag>
            </div>
            <el-form-item>
              <el-button type="primary" :loading="loadingDecision" @click="submitDecision">提交治理决策</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-collapse v-model="shadowSecondaryPanels" class="mt16 shadow-secondary-collapse">
      <el-collapse-item name="evidence">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>更多治理证据</span>
            <span class="section-meta">图表 / 月度明细 / 复盘跳转</span>
          </div>
        </template>
        <el-row :gutter="16">
          <el-col :xs="24" :xl="12">
            <el-card shadow="never" class="box-card" v-loading="loadingCharts">
              <div slot="header" class="section-header">
                <span>治理证据图</span>
                <span class="section-meta">月度差值对比</span>
              </div>
              <shadow-evidence-chart :option="deltaChartOption" height="320px" />
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="12">
            <el-card shadow="never" class="box-card" v-loading="loadingCharts">
              <div slot="header" class="section-header">
                <span>收益趋势图</span>
                <span class="section-meta">候选 vs 基线月度年化</span>
              </div>
              <shadow-evidence-chart :option="annualTrendChartOption" height="320px" />
            </el-card>
          </el-col>
        </el-row>

        <el-card shadow="never" class="box-card mt16">
          <div slot="header" class="section-header">
            <span>月度对比明细</span>
            <span class="section-meta">{{ compareTargetText }}</span>
          </div>
          <el-table v-loading="loadingCompare" :data="monthsData" border>
            <el-table-column label="月份" prop="month" width="100" />
            <el-table-column label="基线年化(%)" min-width="120">
              <template slot-scope="scope">{{ formatMetric(scope.row.baseline, 'avg_annual_return') }}</template>
            </el-table-column>
            <el-table-column label="候选年化(%)" min-width="120">
              <template slot-scope="scope">{{ formatMetric(scope.row.candidate, 'avg_annual_return') }}</template>
            </el-table-column>
            <el-table-column label="年化差值(%)" min-width="120">
              <template slot-scope="scope">
                <span :class="metricClass(scope.row.delta, 'avg_annual_return', true)">{{ formatDelta(scope.row.delta, 'avg_annual_return') }}</span>
              </template>
            </el-table-column>
            <el-table-column label="回撤差值(%)" min-width="120">
              <template slot-scope="scope">
                <span :class="metricClass(scope.row.delta, 'avg_max_drawdown', false)">{{ formatDelta(scope.row.delta, 'avg_max_drawdown') }}</span>
              </template>
            </el-table-column>
            <el-table-column label="胜率差值(%)" min-width="120">
              <template slot-scope="scope">
                <span :class="metricClass(scope.row.delta, 'avg_win_rate', true)">{{ formatDelta(scope.row.delta, 'avg_win_rate') }}</span>
              </template>
            </el-table-column>
            <el-table-column label="失效率差值(%)" min-width="130">
              <template slot-scope="scope">
                <span :class="metricClass(scope.row.delta, 'invalid_rate', false)">{{ formatDelta(scope.row.delta, 'invalid_rate') }}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!monthsData.length && !loadingCompare" description="暂无对比数据，请先执行查询" />
        </el-card>

        <el-card shadow="never" class="box-card mt16">
          <div slot="header" class="section-header">
            <span>复盘跳转</span>
            <span class="section-meta">月份跳转</span>
          </div>
          <el-table v-loading="loadingReviewLinks" :data="reviewLinks" border height="320">
            <el-table-column label="标题" prop="title" min-width="180" />
            <el-table-column label="说明" prop="summary" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="100" fixed="right">
              <template slot-scope="scope">
                <el-button type="text" size="mini" @click="goReviewLink(scope.row)">去复盘</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!reviewLinks.length && !loadingReviewLinks" description="暂无复盘对象" />
        </el-card>
      </el-collapse-item>

      <el-collapse-item name="audit">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>审计与补充信息</span>
            <span class="section-meta">能力清单 / 历史留痕 / 补充说明</span>
          </div>
        </template>
        <el-row :gutter="16">
          <el-col :xs="24" :xl="10">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>策略能力清单</span>
                <span class="section-meta">策略能力</span>
              </div>
              <el-table v-loading="loadingCapabilities" :data="capabilities" border height="320">
                <el-table-column label="策略类型" prop="strategy_type" width="160" />
                <el-table-column label="描述" prop="description" min-width="220" />
                <el-table-column label="必填参数" min-width="220">
                  <template slot-scope="scope">{{ joinParams(scope.row.required_params) }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!capabilities.length && !loadingCapabilities" description="暂无策略能力数据" />
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="14">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>治理历史</span>
                <span class="section-meta">历史留痕</span>
              </div>
              <el-table v-loading="loadingHistory" :data="historyRows" border height="320">
                <el-table-column label="时间" prop="createTime" min-width="170" />
                <el-table-column label="动作" width="110">
                  <template slot-scope="scope">
                    <el-tag size="mini" :type="governanceActionTagType(scope.row.governanceAction)">
                      {{ scope.row.governanceAction || '-' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="审批" prop="approvalStatus" width="110" />
                <el-table-column label="置信度" prop="confidenceLevel" width="100" />
                <el-table-column label="执行人" prop="actor" width="120" />
                <el-table-column label="说明" prop="remark" min-width="220" show-overflow-tooltip />
              </el-table>
              <el-empty v-if="!historyRows.length && !loadingHistory" description="暂无治理历史记录" />
            </el-card>
          </el-col>
        </el-row>

        <el-card shadow="never" class="box-card mt16">
          <div slot="header" class="section-header">
            <span>补充证据</span>
            <span class="section-meta">提交前可调整</span>
          </div>
          <el-form :model="decisionForm" label-width="108px" size="small">
            <el-form-item label="系统建议">
              <el-input v-model="decisionForm.systemRecommendation" readonly />
            </el-form-item>
            <el-form-item label="核心证据">
              <el-input v-model="decisionForm.coreEvidenceText" type="textarea" :rows="3" placeholder="每行一条核心证据" />
            </el-form-item>
            <el-form-item label="风险备注">
              <el-input v-model="decisionForm.riskNoteText" type="textarea" :rows="3" placeholder="每行一条风险提示" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script>
import ElCollapse from 'element-ui/lib/collapse'
import ElCollapseItem from 'element-ui/lib/collapse-item'
import 'element-ui/lib/theme-chalk/collapse.css'
import {
  getDashboardActionItems,
  getGovernanceHistory,
  getShadowCompare,
  getShadowCompareApplicability,
  getShadowCompareCharts,
  getShadowCompareSummary,
  getShadowReviewLinks,
  getStrategyCapabilities,
  listStrategies,
  runShadowCompare,
  submitGovernanceDecision
} from '@/api/quant'
import { buildReviewRouteQuery } from '@/views/quant/review/review-context'
const { buildShadowActionPlan } = require('./shadow-explain')
const {
  applyShadowRouteQuery,
  createShadowRefreshKey,
  getShadowBootstrapCriticalKeys,
  getShadowBootstrapDeferredKeys,
  didShadowQueryChange,
  didShadowRouteContextChange,
  getShadowDeferredRefreshKeys,
  getShadowPrimaryRefreshKeys
} = require('./shadow-page-state')

const SHADOW_ACTION_TYPES = [
  'DATA_INTEGRITY_REVIEW',
  'PIPELINE_RECOVERY',
  'PIPELINE_WAIT',
  'REVIEW_CANDIDATE'
]

export default {
  name: 'QuantShadowCompare',
  components: {
    ElCollapse,
    ElCollapseItem,
    ShadowEvidenceChart: () => import(
      /* webpackChunkName: "quant-shadow-evidence-chart" */
      './components/ShadowEvidenceChart'
    )
  },
  data() {
    return {
      loadingCompare: false,
      loadingCharts: false,
      loadingCapabilities: false,
      loadingHistory: false,
      loadingApplicability: false,
      loadingDecision: false,
      loadingReport: false,
      loadingActionItems: false,
      loadingReviewLinks: false,
      strategyList: [],
      capabilities: [],
      dashboardActionItems: [],
      comparePayload: {},
      summaryPayload: {},
      chartsPayload: {},
      applicabilityPayload: {},
      reviewLinks: [],
      historyRows: [],
      reportMessage: '',
      shadowSecondaryPanels: [],
      shadowRefreshInFlightKey: '',
      shadowRefreshPromise: null,
      shadowRefreshRequestId: 0,
      queryParams: {
        baselineStrategyId: undefined,
        candidateStrategyId: undefined,
        months: 6
      },
      decisionForm: {
        systemRecommendation: '',
        governanceAction: 'OBSERVE',
        confidenceLevel: 'MEDIUM',
        approvalStatus: 'PENDING',
        decisionSource: 'shadow_compare',
        actor: 'ruoyi-ui',
        effectiveFrom: '',
        remark: '',
        coreEvidenceText: '',
        riskNoteText: ''
      }
    }
  },
  computed: {
    monthsData() {
      return Array.isArray(this.comparePayload.months_data) ? this.comparePayload.months_data : []
    },
    comparableMonths() {
      const summary = this.summaryPayload.summary || {}
      return summary.comparableMonths || 0
    },
    latestDecision() {
      return this.summaryPayload.latestDecision || {}
    },
    compareTargetText() {
      const baseline = this.summaryPayload.baseline || this.comparePayload.baseline || {}
      const candidate = this.summaryPayload.candidate || this.comparePayload.candidate || {}
      if (!baseline.strategy_id || !candidate.strategy_id) {
        return '请先选择基线策略与候选策略'
      }
      return `${baseline.strategy_name || baseline.strategy_id} vs ${candidate.strategy_name || candidate.strategy_id}`
    },
    shadowActionPlan() {
      return buildShadowActionPlan({
        actionItems: this.dashboardActionItems,
        queryParams: this.queryParams,
        summaryPayload: this.summaryPayload
      })
    },
    deltaChartOption() {
      const categories = Array.isArray(this.chartsPayload.categories) ? this.chartsPayload.categories : []
      return {
        tooltip: { trigger: 'axis' },
        legend: {
          data: ['年化差值', '回撤差值', '胜率差值', '失效率差值']
        },
        grid: {
          top: 40,
          left: 24,
          right: 24,
          bottom: 24,
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: categories
        },
        yAxis: {
          type: 'value'
        },
        series: [
          {
            name: '年化差值',
            type: 'bar',
            data: this.chartsPayload.annualDeltaSeries || []
          },
          {
            name: '回撤差值',
            type: 'bar',
            data: this.chartsPayload.drawdownDeltaSeries || []
          },
          {
            name: '胜率差值',
            type: 'line',
            smooth: true,
            data: this.chartsPayload.winRateDeltaSeries || []
          },
          {
            name: '失效率差值',
            type: 'line',
            smooth: true,
            data: this.chartsPayload.invalidRateDeltaSeries || []
          }
        ]
      }
    },
    annualTrendChartOption() {
      const categories = Array.isArray(this.chartsPayload.categories) ? this.chartsPayload.categories : []
      const baseline = this.summaryPayload.baseline || this.comparePayload.baseline || {}
      const candidate = this.summaryPayload.candidate || this.comparePayload.candidate || {}
      return {
        tooltip: { trigger: 'axis' },
        legend: {
          data: [
            baseline.strategy_name || '基线策略',
            candidate.strategy_name || '候选策略'
          ]
        },
        grid: {
          top: 40,
          left: 24,
          right: 24,
          bottom: 24,
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: categories
        },
        yAxis: {
          type: 'value'
        },
        series: [
          {
            name: baseline.strategy_name || '基线策略',
            type: 'line',
            smooth: true,
            data: this.chartsPayload.baselineAnnualSeries || []
          },
          {
            name: candidate.strategy_name || '候选策略',
            type: 'line',
            smooth: true,
            data: this.chartsPayload.candidateAnnualSeries || []
          }
        ]
      }
    }
  },
  created() {
    this.syncQueryFromRoute()
    this.loadAll()
  },
  watch: {
    '$route.query': {
      async handler(nextQuery, previousQuery) {
        if (!didShadowRouteContextChange(nextQuery, previousQuery)) {
          return
        }
        const changed = this.syncQueryFromRoute()
        if (changed && this.isQueryReady()) {
          await this.refreshGovernance()
        }
      },
      deep: true
    }
  },
  methods: {
    todayString() {
      const now = new Date()
      const year = now.getFullYear()
      const month = `${now.getMonth() + 1}`.padStart(2, '0')
      const day = `${now.getDate()}`.padStart(2, '0')
      return `${year}-${month}-${day}`
    },
    syncQueryFromRoute() {
      const query = this.$route && this.$route.query ? this.$route.query : {}
      const next = applyShadowRouteQuery(query, this.strategyList, this.queryParams)
      const changed = didShadowQueryChange(this.queryParams, next)
      this.queryParams = next
      return changed
    },
    async loadAll() {
      const taskMap = {
        strategies: () => this.loadStrategies(),
        capabilities: () => this.loadCapabilities()
      }
      await Promise.all(getShadowBootstrapCriticalKeys().map(key => taskMap[key]()))
      if (this.isQueryReady()) {
        await this.refreshGovernance()
      }
      this.scheduleDeferredWork(async () => {
        await Promise.all(getShadowBootstrapDeferredKeys().map(key => taskMap[key]()))
      })
    },
    async loadStrategies() {
      const response = await listStrategies()
      const items = Array.isArray(response.data) ? response.data : []
      this.strategyList = items
      this.queryParams = applyShadowRouteQuery(
        this.$route && this.$route.query ? this.$route.query : {},
        items,
        this.queryParams
      )
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
    async loadDashboardActionItems() {
      this.loadingActionItems = true
      try {
        const response = await getDashboardActionItems({ limit: 8 })
        const rows = Array.isArray(response.data) ? response.data : []
        this.dashboardActionItems = rows.filter(item => SHADOW_ACTION_TYPES.includes(String(item.actionType || '')))
      } finally {
        this.loadingActionItems = false
      }
    },
    isQueryReady() {
      return !!(this.queryParams.baselineStrategyId && this.queryParams.candidateStrategyId)
    },
    validateQuery() {
      if (!this.queryParams.baselineStrategyId || !this.queryParams.candidateStrategyId) {
        this.$modal.msgWarning('请先选择基线策略和候选策略')
        return false
      }
      if (this.queryParams.baselineStrategyId === this.queryParams.candidateStrategyId) {
        this.$modal.msgWarning('基线策略与候选策略不能相同')
        return false
      }
      return true
    },
    async refreshGovernance() {
      if (!this.validateQuery()) {
        return
      }
      const params = { ...this.queryParams }
      const refreshKey = createShadowRefreshKey(params)
      if (this.shadowRefreshPromise && this.shadowRefreshInFlightKey === refreshKey) {
        return this.shadowRefreshPromise
      }

      const requestId = this.shadowRefreshRequestId + 1
      this.shadowRefreshRequestId = requestId
      this.shadowRefreshInFlightKey = refreshKey
      this.loadingCompare = true
      this.reportMessage = ''
      this.chartsPayload = {}
      this.applicabilityPayload = {}
      this.reviewLinks = []
      this.historyRows = []

      let refreshPromise
      refreshPromise = (async () => {
        try {
          const primaryTaskMap = {
            compare: () => getShadowCompare(params),
            summary: () => getShadowCompareSummary(params),
            actionItems: () => this.loadDashboardActionItems()
          }
          const [compareResult, summaryResult] = await Promise.allSettled(
            getShadowPrimaryRefreshKeys().map(async key => {
              const response = await primaryTaskMap[key]()
              return { key, response }
            })
          )

          if (compareResult.status !== 'fulfilled') {
            throw compareResult.reason
          }
          if (requestId !== this.shadowRefreshRequestId) {
            return
          }

          const primaryResults = {}
          ;[compareResult, summaryResult].forEach(result => {
            if (result.status === 'fulfilled' && result.value && result.value.key) {
              primaryResults[result.value.key] = result.value.response
            }
          })

          this.comparePayload = primaryResults.compare && primaryResults.compare.data
            ? primaryResults.compare.data
            : {}
          this.summaryPayload = primaryResults.summary && primaryResults.summary.data
            ? primaryResults.summary.data
            : {}
          if (summaryResult.status !== 'fulfilled') {
            this.summaryPayload = this.buildSummaryFallback()
            this.$modal.msgWarning('治理摘要暂未完全就绪，已基于影子对比结果降级展示。')
          }
          this.syncDecisionFormFromSummary()
          this.scheduleDeferredGovernanceRefresh(params, requestId)
        } catch (error) {
          if (requestId === this.shadowRefreshRequestId) {
            this.$modal.msgError('影子对比数据加载失败')
          }
          throw error
        } finally {
          if (requestId === this.shadowRefreshRequestId) {
            this.loadingCompare = false
          }
          if (this.shadowRefreshPromise === refreshPromise) {
            this.shadowRefreshPromise = null
            this.shadowRefreshInFlightKey = ''
          }
        }
      })()
      this.shadowRefreshPromise = refreshPromise
      return refreshPromise
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
    scheduleDeferredGovernanceRefresh(params, requestId) {
      this.scheduleDeferredWork(async () => {
        if (requestId !== this.shadowRefreshRequestId) {
          return
        }
        this.loadingCharts = true
        this.loadingApplicability = true
        this.loadingReviewLinks = true
        this.loadingHistory = true
        try {
          const deferredTaskMap = {
            charts: () => getShadowCompareCharts(params),
            applicability: () => getShadowCompareApplicability(params),
            links: () => getShadowReviewLinks(params),
            history: () => getGovernanceHistory(params)
          }
          const results = await Promise.allSettled(
            getShadowDeferredRefreshKeys().map(async key => {
              const response = await deferredTaskMap[key]()
              return { key, response }
            })
          )
          if (requestId !== this.shadowRefreshRequestId) {
            return
          }
          const resultMap = {}
          results.forEach(result => {
            if (result.status === 'fulfilled' && result.value && result.value.key) {
              resultMap[result.value.key] = result.value.response
            }
          })
          this.chartsPayload = resultMap.charts && resultMap.charts.data
            ? resultMap.charts.data
            : this.buildChartsFallback()
          this.applicabilityPayload = resultMap.applicability && resultMap.applicability.data
            ? resultMap.applicability.data
            : this.buildApplicabilityFallback()
          this.reviewLinks = resultMap.links && Array.isArray(resultMap.links.data)
            ? resultMap.links.data
            : this.buildReviewLinksFallback()
          this.historyRows = resultMap.history && Array.isArray(resultMap.history.data)
            ? resultMap.history.data
            : []
          this.notifyGovernanceFallbacks({
            summaryResult: { status: 'fulfilled' },
            chartsResult: results[0] || { status: 'fulfilled' },
            applicabilityResult: results[1] || { status: 'fulfilled' },
            linksResult: results[2] || { status: 'fulfilled' },
            historyResult: results[3] || { status: 'fulfilled' }
          })
        } finally {
          if (requestId === this.shadowRefreshRequestId) {
            this.loadingCharts = false
            this.loadingApplicability = false
            this.loadingReviewLinks = false
            this.loadingHistory = false
          }
        }
      })
    },
    notifyGovernanceFallbacks(results) {
      const degradedItems = []
      if (results.summaryResult.status !== 'fulfilled') degradedItems.push('治理摘要')
      if (results.chartsResult.status !== 'fulfilled') degradedItems.push('图形证据')
      if (results.applicabilityResult.status !== 'fulfilled') degradedItems.push('适用性说明')
      if (results.linksResult.status !== 'fulfilled') degradedItems.push('复盘跳转')
      if (results.historyResult.status !== 'fulfilled') degradedItems.push('治理历史')
      if (degradedItems.length) {
        this.$modal.msgWarning(`治理增强数据暂未完全就绪，已降级展示：${degradedItems.join('、')}`)
      }
    },
    buildSummaryFallback() {
      const summary = this.comparePayload.summary || {}
      const recommendation = this.deriveRecommendationFromCompare(summary)
      return {
        baseline: this.comparePayload.baseline || {},
        candidate: this.comparePayload.candidate || {},
        summary: {
          comparableMonths: Number(summary.comparable_months || 0),
          candidateBetterAnnualMonths: Number(summary.candidate_better_annual_months || 0),
          candidateLowerDrawdownMonths: Number(summary.candidate_lower_drawdown_months || 0),
          candidateHigherWinRateMonths: Number(summary.candidate_higher_win_rate_months || 0),
          candidateLowerInvalidRateMonths: Number(summary.candidate_lower_invalid_rate_months || 0)
        },
        recommendation,
        governanceAction: recommendation === 'PROMOTE_CANDIDATE'
          ? 'REPLACE'
          : (recommendation === 'OBSERVE' ? 'OBSERVE' : 'KEEP'),
        confidenceLevel: 'MEDIUM',
        recommendationReason: '当前治理增强接口未返回完整摘要，先基于影子对比原始统计做降级展示。',
        coreEvidences: this.buildCoreEvidenceFallback(summary),
        riskNotes: ['当前治理增强接口未完全可用，建议以后端治理摘要为准。'],
        latestDecision: {}
      }
    },
    buildCoreEvidenceFallback(summary) {
      return [
        `可比月份 ${Number(summary.comparable_months || 0)} 个`,
        `候选策略年化更优 ${Number(summary.candidate_better_annual_months || 0)} 月`,
        `候选策略回撤更低 ${Number(summary.candidate_lower_drawdown_months || 0)} 月`
      ]
    },
    deriveRecommendationFromCompare(summary) {
      const comparable = Number(summary.comparable_months || 0)
      const annual = Number(summary.candidate_better_annual_months || 0)
      const drawdown = Number(summary.candidate_lower_drawdown_months || 0)
      const winRate = Number(summary.candidate_higher_win_rate_months || 0)
      const invalid = Number(summary.candidate_lower_invalid_rate_months || 0)
      if (comparable > 0 && annual >= Math.max(1, comparable - 1) && drawdown >= Math.max(1, comparable - 1) && (winRate > 0 || invalid > 0)) {
        return 'PROMOTE_CANDIDATE'
      }
      if (annual > 0 || drawdown > 0 || winRate > 0 || invalid > 0) {
        return 'OBSERVE'
      }
      if (!comparable) {
        return 'INSUFFICIENT_DATA'
      }
      return 'KEEP_BASELINE'
    },
    buildChartsFallback() {
      const categories = []
      const annualDeltaSeries = []
      const drawdownDeltaSeries = []
      const winRateDeltaSeries = []
      const invalidRateDeltaSeries = []
      const baselineAnnualSeries = []
      const candidateAnnualSeries = []
      this.monthsData.slice().reverse().forEach(item => {
        categories.push(item.month)
        annualDeltaSeries.push(Number(item.delta && item.delta.avg_annual_return !== undefined ? item.delta.avg_annual_return : 0))
        drawdownDeltaSeries.push(Number(item.delta && item.delta.avg_max_drawdown !== undefined ? item.delta.avg_max_drawdown : 0))
        winRateDeltaSeries.push(Number(item.delta && item.delta.avg_win_rate !== undefined ? item.delta.avg_win_rate : 0))
        invalidRateDeltaSeries.push(Number(item.delta && item.delta.invalid_rate !== undefined ? item.delta.invalid_rate : 0))
        baselineAnnualSeries.push(Number(item.baseline && item.baseline.avg_annual_return !== undefined ? item.baseline.avg_annual_return : 0))
        candidateAnnualSeries.push(Number(item.candidate && item.candidate.avg_annual_return !== undefined ? item.candidate.avg_annual_return : 0))
      })
      return {
        categories,
        annualDeltaSeries,
        drawdownDeltaSeries,
        winRateDeltaSeries,
        invalidRateDeltaSeries,
        baselineAnnualSeries,
        candidateAnnualSeries
      }
    },
    buildApplicabilityFallback() {
      return {
        applicabilityConclusion: '当前适用性说明接口未就绪，先基于影子对比统计判断候选策略是否值得继续观察。',
        strengths: ['可继续参考月度差值和回测分析页判断候选策略优势来源。'],
        riskNotes: ['请以后端治理摘要与复盘页证据作为最终决策依据。']
      }
    },
    buildReviewLinksFallback() {
      return this.monthsData.slice(0, 4).flatMap(item => ([
        {
          title: `${item.month} 候选策略复盘`,
          summary: '基于月度影子对比结果降级生成的复盘入口',
          reviewLevel: 'strategy',
          strategyId: this.queryParams.candidateStrategyId,
          dateRangeStart: `${item.month}-01`,
          dateRangeEnd: `${item.month}-31`,
          sourcePage: 'shadow',
          sourceAction: 'shadowCandidateFallback'
        },
        {
          title: `${item.month} 基线策略复盘`,
          summary: '基于月度影子对比结果降级生成的复盘入口',
          reviewLevel: 'strategy',
          strategyId: this.queryParams.baselineStrategyId,
          dateRangeStart: `${item.month}-01`,
          dateRangeEnd: `${item.month}-31`,
          sourcePage: 'shadow',
          sourceAction: 'shadowBaselineFallback'
        }
      ]))
    },
    syncDecisionFormFromSummary() {
      this.decisionForm.systemRecommendation = this.summaryPayload.recommendation || ''
      this.decisionForm.governanceAction = this.summaryPayload.governanceAction || 'OBSERVE'
      this.decisionForm.confidenceLevel = this.summaryPayload.confidenceLevel || 'MEDIUM'
      this.decisionForm.effectiveFrom = this.decisionForm.effectiveFrom || this.todayString()
      this.decisionForm.coreEvidenceText = Array.isArray(this.summaryPayload.coreEvidences)
        ? this.summaryPayload.coreEvidences.join('\n')
        : ''
      this.decisionForm.riskNoteText = Array.isArray(this.summaryPayload.riskNotes)
        ? this.summaryPayload.riskNotes.join('\n')
        : ''
    },
    splitLines(text) {
      return String(text || '')
        .split('\n')
        .map(item => item.trim())
        .filter(Boolean)
    },
    async submitDecision() {
      if (!this.validateQuery()) {
        return
      }
      this.loadingDecision = true
      try {
        const response = await submitGovernanceDecision({
          baselineStrategyId: this.queryParams.baselineStrategyId,
          candidateStrategyId: this.queryParams.candidateStrategyId,
          months: this.queryParams.months,
          systemRecommendation: this.decisionForm.systemRecommendation,
          governanceAction: this.decisionForm.governanceAction,
          confidenceLevel: this.decisionForm.confidenceLevel,
          approvalStatus: this.decisionForm.approvalStatus,
          decisionSource: this.decisionForm.decisionSource,
          actor: this.decisionForm.actor,
          effectiveFrom: this.decisionForm.effectiveFrom,
          remark: this.decisionForm.remark,
          coreEvidences: this.splitLines(this.decisionForm.coreEvidenceText),
          riskNotes: this.splitLines(this.decisionForm.riskNoteText)
        })
        const payload = response.data || {}
        this.historyRows = Array.isArray(payload.history) ? payload.history : []
        this.$modal.msgSuccess(`治理决策已提交 #${payload.decisionId || ''}`)
        await this.refreshGovernance()
      } finally {
        this.loadingDecision = false
      }
    },
    async handleGenerateReport() {
      if (!this.validateQuery()) {
        return
      }
      this.loadingReport = true
      try {
        const response = await runShadowCompare(this.queryParams)
        this.reportMessage = response.data || '报告已生成'
        this.$modal.msgSuccess('报告生成成功')
      } finally {
        this.loadingReport = false
      }
    },
    goReviewLink(row) {
      if (!row) {
        return
      }
      this.$router.push({
        path: '/quant/review',
        query: {
          ...buildReviewRouteQuery({
            reviewLevel: row.reviewLevel || 'strategy',
            strategyId: row.strategyId,
            sourcePage: row.sourcePage || 'shadow',
            sourceAction: row.sourceAction || 'shadow'
          }),
          dateRangeStart: row.dateRangeStart,
          dateRangeEnd: row.dateRangeEnd
        }
      }).catch(() => {})
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
      if (!item) {
        return
      }
      const path = item.targetPage || item.path
      if (!path) {
        return
      }
      if (path === '/quant/shadow') {
        return
      }
      this.$router.push({
        path,
        query: this.normalizeRouteQuery(item.targetQuery || item.query || {})
      }).catch(() => {})
    },
    handleActionItem(item) {
      if (item && item.isCurrentPage && item.currentSectionRef) {
        this.$nextTick(() => this.scrollToSection(item.currentSectionRef))
        return
      }
      this.goRouteLink(item)
    },
    scrollToSection(refName) {
      if (!refName || !this.$refs[refName]) {
        return
      }
      const target = this.$refs[refName].$el || this.$refs[refName]
      if (target && target.scrollIntoView) {
        target.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    },
    goBacktestAnalysis() {
      this.$router.push('/quant/backtest').catch(() => {})
    },
    goReviewOverview() {
      this.$router.push('/quant/review').catch(() => {})
    },
    strategyLabel(item) {
      const statusText = Number(item.status) === 1 ? '启用' : '停用'
      return `${item.id} - ${item.strategy_name} (${item.strategy_type}) [${statusText}]`
    },
    joinParams(params) {
      return Array.isArray(params) && params.length ? params.join(', ') : '-'
    },
    formatMetric(source, key) {
      if (!source || source[key] === null || source[key] === undefined) {
        return '-'
      }
      return Number(source[key]).toFixed(2)
    },
    formatDelta(source, key) {
      if (!source || source[key] === null || source[key] === undefined) {
        return '-'
      }
      const value = Number(source[key])
      const prefix = value > 0 ? '+' : ''
      return `${prefix}${value.toFixed(2)}`
    },
    metricClass(source, key, higherBetter) {
      if (!source || source[key] === null || source[key] === undefined) {
        return ''
      }
      const value = Number(source[key])
      if (value === 0) {
        return ''
      }
      const positive = higherBetter ? value > 0 : value < 0
      return positive ? 'is-better' : 'is-worse'
    },
    recommendationTagType(recommendation) {
      if (recommendation === 'PROMOTE_CANDIDATE') return 'success'
      if (recommendation === 'OBSERVE') return 'warning'
      if (recommendation === 'KEEP_BASELINE') return 'info'
      return 'danger'
    },
    governanceActionTagType(action) {
      if (action === 'REPLACE') return 'danger'
      if (action === 'OBSERVE') return 'warning'
      if (action === 'KEEP') return 'success'
      if (action === 'DISABLE') return 'danger'
      return 'info'
    },
    priorityTagType(priority) {
      if (priority === 'P0') return 'danger'
      if (priority === 'P1') return 'warning'
      return 'info'
    }
  }
}
</script>

<style scoped>
.mt16 {
  margin-top: 16px;
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

.header-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.summary-card {
  margin-bottom: 12px;
  min-height: 140px;
}

.shadow-conclusion-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.shadow-conclusion-card {
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid #ebeef5;
  background: rgba(255, 255, 255, 0.86);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.shadow-conclusion-card label {
  color: #909399;
  font-size: 12px;
}

.shadow-conclusion-card strong {
  color: #303133;
  font-size: 18px;
  line-height: 1.5;
}

.shadow-conclusion-card span {
  color: #606266;
  line-height: 1.6;
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

.summary-hint {
  margin-top: 10px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

.detail-summary {
  margin: 12px 0;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.evidence-list,
.plain-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ops-action-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ops-action-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid #ebeef5;
  background: rgba(255, 255, 255, 0.9);
}

.ops-action-main {
  flex: 1;
}

.ops-action-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: #303133;
  font-weight: 600;
}

.ops-action-reason {
  margin-top: 8px;
  color: #606266;
  line-height: 1.6;
}

.evidence-item,
.plain-list-item,
.risk-item {
  padding: 10px 12px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  background: #fafafa;
  color: #303133;
  line-height: 1.6;
}

.evidence-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.plain-list-item--risk,
.risk-item {
  background: #fff7e6;
  border-color: #f5dab1;
}

.sub-title {
  margin: 16px 0 10px;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.is-better {
  color: #67c23a;
  font-weight: 600;
}

.is-worse {
  color: #f56c6c;
  font-weight: 600;
}

.report-message {
  margin-top: 10px;
  color: #606266;
  font-size: 13px;
  word-break: break-all;
}

.shadow-secondary-collapse {
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

@media (max-width: 1200px) {
  .shadow-conclusion-grid {
    grid-template-columns: 1fr;
  }
}
</style>
