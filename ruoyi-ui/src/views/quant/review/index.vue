<template>
  <div class="app-container review-page">
    <el-card shadow="never" class="box-card review-hero">
      <div slot="header" class="section-header">
        <span>复盘分析</span>
        <span class="section-meta">证据链</span>
      </div>
      <el-form :model="queryParams" :inline="true" size="small" label-width="90px">
        <el-form-item label="复盘层级">
          <el-select v-model="queryParams.reviewLevel" style="width: 160px" @change="handleReviewLevelChange">
            <el-option label="trade" value="trade" />
            <el-option label="strategy" value="strategy" />
            <el-option label="governance" value="governance" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略">
          <el-select v-model="queryParams.strategyId" clearable filterable style="width: 220px">
            <el-option v-for="item in strategyList" :key="item.id" :label="strategyLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标的">
          <el-input v-model="queryParams.stockCode" placeholder="如 000001" style="width: 150px" clearable />
        </el-form-item>
        <el-form-item label="信号ID">
          <el-input-number v-model="queryParams.signalId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item v-if="queryParams.reviewLevel === 'governance'" label="基线策略">
          <el-select v-model="queryParams.baselineStrategyId" clearable filterable style="width: 220px">
            <el-option v-for="item in strategyList" :key="`base-${item.id}`" :label="strategyLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="queryParams.reviewLevel === 'governance'" label="候选策略">
          <el-select v-model="queryParams.candidateStrategyId" clearable filterable style="width: 220px">
            <el-option
              v-for="item in strategyList"
              :key="`candidate-${item.id}`"
              :label="strategyLabel(item)"
              :value="item.id"
              :disabled="item.id === queryParams.baselineStrategyId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="queryParams.dateRangeStart" type="date" value-format="yyyy-MM-dd" style="width: 148px" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="queryParams.dateRangeEnd" type="date" value-format="yyyy-MM-dd" style="width: 148px" />
        </el-form-item>
        <el-form-item v-if="queryParams.reviewLevel === 'governance'" label="月份">
          <el-input-number v-model="queryParams.months" :min="1" :max="24" controls-position="right" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" :loading="loadingReview" @click="refreshReview">查询</el-button>
          <el-button icon="el-icon-refresh" :loading="loadingReview" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="scope-bar">
        <div class="scope-bar__header">
          <span>复盘范围上下文</span>
          <div class="scope-bar__actions">
            <el-button size="mini" type="primary" plain @click="syncRouteQuery">同步当前条件到链接</el-button>
            <el-button size="mini" @click="goSymbols">去标的治理页</el-button>
          </div>
        </div>
        <div class="scope-bar__summary">
          <span>{{ scopeSummaryText }}</span>
          <span>{{ scopePreviewText }}</span>
        </div>
      </div>
      <div class="quick-actions">
        <el-button type="primary" plain size="small" @click="goExecutionFocus('abnormal')">处理异常反馈</el-button>
        <el-button plain size="small" @click="goExecutionFocus('unmatched')">处理未匹配成交</el-button>
        <el-button plain size="small" @click="goExecutionFocus('positionDiff')">核对持仓差异</el-button>
        <el-button plain size="small" @click="goShadowCompare">查看影子对比</el-button>
      </div>
    </el-card>

    <el-alert
      v-if="reviewContext.hasContext"
      class="mt16"
      type="info"
      :closable="false"
      show-icon
      :title="reviewContextTitle"
      :description="reviewContextDescription"
    />

    <el-card shadow="never" class="box-card mt16">
      <div slot="header" class="section-header">
        <span>当前结论</span>
        <span class="section-meta">先看结论，再看证据</span>
      </div>
      <div class="review-conclusion-grid">
        <div class="review-conclusion-card">
          <label>复盘对象</label>
          <strong>{{ summaryPayload.reviewTargetName || '待选择' }}</strong>
          <span>{{ summaryPayload.reviewLevel || queryParams.reviewLevel }}</span>
        </div>
        <div class="review-conclusion-card">
          <label>复盘结论</label>
          <strong>
            <el-tag size="mini" :type="reviewConclusionTagType(summaryPayload.reviewConclusion)">
              {{ summaryPayload.reviewConclusion || '待分析' }}
            </el-tag>
          </strong>
          <span>{{ summaryPayload.primaryReason || '先加载复盘摘要' }}</span>
        </div>
        <div class="review-conclusion-card">
          <label>建议动作</label>
          <strong>
            <el-tag size="mini" :type="reviewActionTagType(summaryPayload.suggestedAction)">
              {{ summaryPayload.suggestedAction || 'KEEP' }}
            </el-tag>
          </strong>
          <span>置信度 {{ summaryPayload.confidenceLevel || 'LOW' }}</span>
        </div>
      </div>
      <div class="review-conclusion-actions">
        <el-button type="primary" plain size="small" @click="goExecutionFocus('abnormal')">去执行闭环</el-button>
        <el-button plain size="small" @click="goShadowCompare">去影子治理</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="box-card mt16" v-loading="loadingActionItems">
      <div slot="header" class="section-header">
        <span>结论动作</span>
        <span class="section-meta">{{ reviewActionPlan.headline }}</span>
      </div>
      <div class="plain-list">
        <div v-for="line in reviewActionPlan.summaryLines" :key="line" class="plain-list-item">
          {{ line }}
        </div>
      </div>
      <div v-if="reviewActionPlan.nextActions.length" class="ops-action-list">
        <div
          v-for="item in reviewActionPlan.nextActions"
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
            {{ item.isCurrentPage ? '去沉淀' : '去处理' }}
          </el-button>
        </div>
      </div>
      <el-empty v-else description="当前没有额外运营动作，按证据链完成复盘即可" :image-size="60" />
    </el-card>

    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :xl="16">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>核心证据</span>
            <span class="section-meta">价格与买卖点</span>
          </div>
          <review-evidence-chart :option="klineChartOption" height="360px" />
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="8">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>执行闭环摘要</span>
            <span class="section-meta">只保留当前需要处理的异常</span>
          </div>
          <div class="feedback-summary">
            <el-tag type="success" size="mini">已执行 {{ reconciliationSummary.executedSignalCount || 0 }}</el-tag>
            <el-tag type="warning" size="mini">待执行 {{ reconciliationSummary.pendingSignalCount || 0 }}</el-tag>
            <el-tag type="danger" size="mini">漏执行 {{ reconciliationSummary.missedSignalCount || 0 }}</el-tag>
            <el-tag type="info" size="mini">未匹配 {{ reconciliationSummary.unmatchedExecutionCount || 0 }}</el-tag>
          </div>
          <div class="plain-list">
            <div class="plain-list-item">当前待核对异常 {{ summaryPayload.executionIssueCount || reconciliationSummary.pendingSignalCount || 0 }} 条。</div>
            <div class="plain-list-item">需要细看成交、反馈和时间链路时，再展开下方次级证据区。</div>
          </div>
          <div class="review-conclusion-actions">
            <el-button type="primary" plain size="small" @click="goExecutionFocus('abnormal')">去处理执行异常</el-button>
            <el-button plain size="small" @click="toggleReviewPanel('execution')">展开执行证据</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-collapse v-model="reviewSecondaryPanels" class="mt16 review-secondary-collapse">
      <el-collapse-item name="charts">
        <template slot="title">
          <span class="collapse-title">更多图表与案例</span>
          <span class="collapse-meta">历史图表、正式案例、治理证据</span>
        </template>
        <el-row :gutter="16">
          <el-col :xs="24" :xl="8">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>正式复盘案例</span>
                <span class="section-meta">正式案例</span>
              </div>
              <el-table :data="reviewCases" border height="320">
                <el-table-column label="Case" prop="caseId" width="84" />
                <el-table-column label="层级" prop="reviewLevel" width="96" />
                <el-table-column label="资产" prop="assetType" width="90" />
                <el-table-column label="对象" prop="reviewTargetName" min-width="180" show-overflow-tooltip />
                <el-table-column label="操作" width="90">
                  <template slot-scope="scope">
                    <el-button type="text" size="mini" @click="applyCase(scope.row)">去复盘</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!reviewCases.length" description="暂无正式复盘案例" :image-size="60" />
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="16">
            <el-row :gutter="16">
              <el-col :xs="24" :xl="12">
                <el-card shadow="never" class="box-card">
                  <div slot="header" class="section-header">
                    <span>持仓区间复盘图</span>
                    <span class="section-meta">持仓走势</span>
                  </div>
                  <review-evidence-chart :option="holdingChartOption" height="280px" />
                </el-card>
              </el-col>
              <el-col :xs="24" :xl="12">
                <el-card shadow="never" class="box-card">
                  <div slot="header" class="section-header">
                    <span>净值 + 基准 + 回撤图</span>
                    <span class="section-meta">净值走势</span>
                  </div>
                  <review-evidence-chart :option="navChartOption" height="280px" />
                </el-card>
              </el-col>
              <el-col :xs="24" class="mt16">
                <el-card shadow="never" class="box-card">
                  <div slot="header" class="section-header">
                    <span>市场状态叠加图</span>
                    <span class="section-meta">市场环境</span>
                  </div>
                  <review-evidence-chart :option="overlayChartOption" height="280px" />
                </el-card>
              </el-col>
            </el-row>
          </el-col>
        </el-row>
        <el-row :gutter="16" class="mt16">
          <el-col v-if="showGovernanceSection" :xs="24" :xl="14">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>治理证据图</span>
                <span class="section-meta">治理证据</span>
              </div>
              <review-evidence-chart :option="governanceChartOption" height="280px" />
              <div class="governance-caption">
                {{ governanceSummaryText }}
              </div>
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="showGovernanceSection ? 10 : 24">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>规则要点</span>
                <span class="section-meta">规则标签</span>
              </div>
              <div v-if="(ruleExplainPayload.explanations || []).length" class="plain-list">
                <div v-for="item in ruleExplainPayload.explanations" :key="item" class="plain-list-item">{{ item }}</div>
              </div>
              <el-empty v-else description="暂无规则解释" :image-size="60" />
            </el-card>
          </el-col>
        </el-row>
      </el-collapse-item>

      <el-collapse-item name="execution">
        <template slot="title">
          <span class="collapse-title">执行闭环证据</span>
          <span class="collapse-meta">成交反馈、异常摘要</span>
        </template>
        <el-card ref="conclusionSection" shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>执行闭环复盘</span>
            <span class="section-meta">异常摘要</span>
          </div>
          <el-table :data="executionFeedbackRows" border height="320">
            <el-table-column label="信号ID" prop="signal_id" width="90" />
            <el-table-column label="代码" prop="stock_code" width="100" />
            <el-table-column label="状态" prop="status" width="100" />
            <el-table-column label="检查日" prop="check_date" width="110" />
            <el-table-column label="动作" prop="feedback_action" min-width="160" />
          </el-table>
        </el-card>
      </el-collapse-item>

      <el-collapse-item name="conclusion">
        <template slot="title">
          <span class="collapse-title">提交复盘结论</span>
          <span class="collapse-meta">手工沉淀最终结论</span>
        </template>
        <el-card shadow="never" class="box-card">
          <el-form :model="conclusionForm" label-width="108px" size="small">
            <el-form-item label="复盘对象">
              <el-input v-model="conclusionForm.reviewTargetName" readonly />
            </el-form-item>
            <el-form-item label="复盘结论">
              <el-select v-model="conclusionForm.reviewConclusion" style="width: 100%">
                <el-option label="HEALTHY" value="HEALTHY" />
                <el-option label="OBSERVE" value="OBSERVE" />
                <el-option label="WARNING" value="WARNING" />
                <el-option label="INVALID" value="INVALID" />
              </el-select>
            </el-form-item>
            <el-form-item label="建议动作">
              <el-select v-model="conclusionForm.suggestedAction" style="width: 100%">
                <el-option label="KEEP" value="KEEP" />
                <el-option label="OBSERVE" value="OBSERVE" />
                <el-option label="REDUCE_WEIGHT" value="REDUCE_WEIGHT" />
                <el-option label="REPLACE" value="REPLACE" />
                <el-option label="DISABLE" value="DISABLE" />
              </el-select>
            </el-form-item>
            <el-form-item label="主要原因">
              <el-input v-model="conclusionForm.primaryReason" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="次要原因">
              <el-input v-model="conclusionForm.secondaryReason" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="置信度">
              <el-select v-model="conclusionForm.confidenceLevel" style="width: 100%">
                <el-option label="HIGH" value="HIGH" />
                <el-option label="MEDIUM" value="MEDIUM" />
                <el-option label="LOW" value="LOW" />
              </el-select>
            </el-form-item>
            <el-form-item label="补充备注">
              <el-input v-model="conclusionForm.remark" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loadingConclusion" @click="submitConclusion">提交复盘结论</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-collapse-item>

      <el-collapse-item name="timeline">
        <template slot="title">
          <span class="collapse-title">事件链路 / 调试证据</span>
          <span class="collapse-meta">{{ timelineSectionMeta }}</span>
        </template>
        <el-card shadow="never" class="box-card">
          <el-timeline v-if="timelineRows.length">
            <el-timeline-item
              v-for="item in timelineRows"
              :key="`${item.eventType}-${item.relatedObjectId}-${item.eventTime}`"
              :timestamp="String(item.eventTime || '')"
              :type="timelineItemType(item)"
              placement="top"
            >
              <div class="timeline-title">{{ item.eventTitle }}</div>
              <div class="timeline-detail">{{ item.eventDetail || '-' }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无时间线事件" :image-size="60" />
        </el-card>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script>
import ElAlert from 'element-ui/lib/alert'
import ElCollapse from 'element-ui/lib/collapse'
import ElCollapseItem from 'element-ui/lib/collapse-item'
import ElTimeline from 'element-ui/lib/timeline'
import ElTimelineItem from 'element-ui/lib/timeline-item'
import 'element-ui/lib/theme-chalk/alert.css'
import 'element-ui/lib/theme-chalk/collapse.css'
import 'element-ui/lib/theme-chalk/timeline.css'
import 'element-ui/lib/theme-chalk/timeline-item.css'
import {
  getCanaryLatest,
  getDashboardActionItems,
  getDashboardSummary,
  getExecutionReconciliationSummary,
  getReviewCaseDetail,
  getReviewCases,
  getReviewGovernanceEvidence,
  getReviewHoldingRange,
  getReviewKline,
  getReviewMarketOverlay,
  getReviewNavDrawdown,
  getReviewRuleExplain,
  getReviewSummary,
  getReviewTimeline,
  listExecutionFeedbackDetails,
  listStrategies,
  submitReviewConclusion
} from '@/api/quant'
import { buildReviewRouteQuery, filterExecutionFeedbackRows, normalizeReviewContext } from './review-context'
const { buildReviewActionPlan } = require('./review-explain')
const {
  clearCaseBoundQueryParams,
  createDefaultReviewQueryParams,
  createReviewRefreshKey,
  didReviewRouteContextChange,
  getReviewBootstrapCriticalKeys,
  getReviewBootstrapDeferredKeys,
  getReviewBootstrapPrefetchKeys,
  getReviewDeferredRefreshKeys,
  getReviewPrimaryRefreshKeys,
  getReviewTimelineLimit,
  getReviewTimelineSectionMeta,
  shouldLoadGovernanceEvidence
} = require('./review-page-state')

const REVIEW_ACTION_TYPES = [
  'DATA_INTEGRITY_REVIEW',
  'PIPELINE_RECOVERY',
  'PIPELINE_WAIT',
  'EXECUTION_RECONCILIATION',
  'PARTIAL_EXECUTION',
  'PENDING_SIGNAL_EXECUTION',
  'POSITION_RISK',
  'POSITION_SYNC_DIFF',
  'REVIEW_CANDIDATE'
]

export default {
  name: 'QuantReview',
  components: {
    ElAlert,
    ElCollapse,
    ElCollapseItem,
    ElTimeline,
    ElTimelineItem,
    ReviewEvidenceChart: () => import(
      /* webpackChunkName: "quant-review-evidence-chart" */
      './components/ReviewEvidenceChart'
    )
  },
  data() {
    return {
      loadingReview: false,
      loadingConclusion: false,
      loadingActionItems: false,
      strategyList: [],
      dashboardSummary: {},
      dashboardActionItems: [],
      reconciliationSummary: {},
      canaryLatest: {},
      reviewCases: [],
      reviewContext: normalizeReviewContext(),
      reviewRefreshInFlightKey: '',
      reviewRefreshPromise: null,
      reviewRefreshRequestId: 0,
      reviewSecondaryPanels: [],
      executionFeedbackRaw: [],
      summaryPayload: {},
      klinePayload: {},
      holdingPayload: {},
      navPayload: {},
      overlayPayload: {},
      governancePayload: {},
      ruleExplainPayload: {},
      timelineRows: [],
      queryParams: createDefaultReviewQueryParams(),
      conclusionForm: {
        reviewTargetName: '',
        reviewConclusion: 'OBSERVE',
        primaryReason: '',
        secondaryReason: '',
        suggestedAction: 'OBSERVE',
        confidenceLevel: 'MEDIUM',
        remark: '',
        actor: 'ruoyi-ui',
        sourcePage: 'review',
        sourceAction: 'manualReview'
      }
    }
  },
  computed: {
    reviewContextTitle() {
      return '当前复盘范围已按来源上下文自动收敛'
    },
    reviewContextDescription() {
      const segments = []
      if (this.reviewContext.stockCode) segments.push(`标的 ${this.reviewContext.stockCode}`)
      if (this.reviewContext.strategyId !== undefined) segments.push(`策略 ${this.reviewContext.strategyId}`)
      if (this.reviewContext.signalId !== undefined) segments.push(`信号 ${this.reviewContext.signalId}`)
      if (this.reviewContext.baselineStrategyId !== undefined && this.reviewContext.candidateStrategyId !== undefined) {
        segments.push(`治理对 ${this.reviewContext.baselineStrategyId} -> ${this.reviewContext.candidateStrategyId}`)
      }
      if (this.reviewContext.dateRangeStart || this.reviewContext.dateRangeEnd) {
        segments.push(`区间 ${this.reviewContext.dateRangeStart || '-'} ~ ${this.reviewContext.dateRangeEnd || '-'}`)
      }
      if (this.reviewContext.scopeType) {
        const scopeParts = [`范围 ${this.reviewContext.scopeType}`]
        if (this.reviewContext.scopePoolCode) {
          scopeParts.push(this.reviewContext.scopePoolCode)
        }
        if ((this.reviewContext.symbols || []).length) {
          scopeParts.push(`直接指定 ${this.reviewContext.symbols.join('/')}`)
        }
        if ((this.reviewContext.whitelist || []).length) {
          scopeParts.push(`白名单 ${this.reviewContext.whitelist.join('/')}`)
        }
        if ((this.reviewContext.blacklist || []).length) {
          scopeParts.push(`黑名单 ${this.reviewContext.blacklist.join('/')}`)
        }
        if ((this.reviewContext.adHocSymbols || []).length) {
          scopeParts.push(`临时补充 ${this.reviewContext.adHocSymbols.join('/')}`)
        }
        segments.push(scopeParts.join(' · '))
      }
      if (this.reviewContext.sourceActionLabel) segments.push(`来源 ${this.reviewContext.sourceActionLabel}`)
      return segments.join('，')
    },
    scopeSummaryText() {
      if (!this.queryParams.scopeType) {
        return '当前未指定统一范围模型，复盘以对象级筛选为主。'
      }
      const parts = [`范围模型 ${this.queryParams.scopeType}`]
      if (this.queryParams.scopePoolCode) {
        parts.push(`股票池 ${this.queryParams.scopePoolCode}`)
      }
      return parts.join(' · ')
    },
    scopePreviewText() {
      const parts = []
      if ((this.queryParams.symbols || []).length) {
        parts.push(`直接指定 ${this.queryParams.symbols.join(',')}`)
      }
      if ((this.queryParams.whitelist || []).length) {
        parts.push(`白名单 ${this.queryParams.whitelist.join(',')}`)
      }
      if ((this.queryParams.blacklist || []).length) {
        parts.push(`黑名单 ${this.queryParams.blacklist.join(',')}`)
      }
      if ((this.queryParams.adHocSymbols || []).length) {
        parts.push(`临时补充 ${this.queryParams.adHocSymbols.join(',')}`)
      }
      return parts.length ? parts.join(' | ') : '未叠加白名单 / 黑名单 / 临时补充。'
    },
    executionFeedbackRows() {
      return filterExecutionFeedbackRows(this.executionFeedbackRaw, this.queryParams).slice(0, 12)
    },
    reviewActionPlan() {
      return buildReviewActionPlan({
        actionItems: this.dashboardActionItems,
        reviewContext: this.reviewContext,
        summaryPayload: this.summaryPayload,
        reconciliationSummary: this.reconciliationSummary
      })
    },
    klineChartOption() {
      const categories = Array.isArray(this.klinePayload.categories) ? this.klinePayload.categories : []
      const closeSeries = (this.klinePayload.candles || []).map(item => (Array.isArray(item) ? item[1] : null))
      const signalSeries = (this.klinePayload.signalPoints || []).map(item => ({
        value: [String(item.signalDate), Number(item.suggestPrice || 0)],
        name: item.signalType
      }))
      const executionSeries = (this.klinePayload.executionPoints || []).map(item => ({
        value: [String(item.tradeDate), Number(item.price || 0)],
        name: item.side
      }))
      return {
        tooltip: { trigger: 'axis' },
        legend: { data: ['收盘价', 'MA5', 'MA10', 'MA20', '信号点', '成交点'] },
        grid: { top: 40, left: 24, right: 24, bottom: 24, containLabel: true },
        xAxis: { type: 'category', data: categories },
        yAxis: { type: 'value', scale: true },
        series: [
          { name: '收盘价', type: 'line', smooth: true, data: closeSeries },
          { name: 'MA5', type: 'line', smooth: true, data: this.klinePayload.ma5Series || [] },
          { name: 'MA10', type: 'line', smooth: true, data: this.klinePayload.ma10Series || [] },
          { name: 'MA20', type: 'line', smooth: true, data: this.klinePayload.ma20Series || [] },
          { name: '信号点', type: 'scatter', symbolSize: 10, data: signalSeries },
          { name: '成交点', type: 'scatter', symbolSize: 12, data: executionSeries }
        ]
      }
    },
    holdingChartOption() {
      const rows = Array.isArray(this.holdingPayload.ranges) ? this.holdingPayload.ranges : []
      return {
        tooltip: { trigger: 'axis' },
        legend: { data: ['最高浮盈', '区间回撤', '已实现收益'] },
        grid: { top: 40, left: 24, right: 24, bottom: 24, containLabel: true },
        xAxis: { type: 'category', data: rows.map(item => `${item.entryDate || '-'} -> ${item.exitDate || '持有中'}`) },
        yAxis: { type: 'value' },
        series: [
          { name: '最高浮盈', type: 'bar', data: rows.map(item => Number(item.maxFloatingProfit || 0)) },
          { name: '区间回撤', type: 'bar', data: rows.map(item => Number(item.maxDrawdownInHolding || 0)) },
          { name: '已实现收益', type: 'line', smooth: true, data: rows.map(item => Number(item.realizedProfit || 0)) }
        ]
      }
    },
    navChartOption() {
      return {
        tooltip: { trigger: 'axis' },
        legend: { data: ['策略净值', '基准净值', '回撤'] },
        grid: { top: 40, left: 24, right: 24, bottom: 24, containLabel: true },
        xAxis: { type: 'category', data: this.navPayload.categories || [] },
        yAxis: [{ type: 'value' }, { type: 'value' }],
        series: [
          { name: '策略净值', type: 'line', smooth: true, data: this.navPayload.strategyNavSeries || [] },
          { name: '基准净值', type: 'line', smooth: true, data: this.navPayload.benchmarkNavSeries || [] },
          { name: '回撤', type: 'bar', yAxisIndex: 1, data: this.navPayload.drawdownSeries || [] }
        ]
      }
    },
    overlayChartOption() {
      const points = Array.isArray(this.overlayPayload.points) ? this.overlayPayload.points : []
      return {
        tooltip: { trigger: 'axis' },
        legend: { data: ['基准指数', '预算比例', '策略启用'] },
        grid: { top: 40, left: 24, right: 24, bottom: 24, containLabel: true },
        xAxis: { type: 'category', data: points.map(item => item.date) },
        yAxis: [{ type: 'value' }, { type: 'value' }],
        series: [
          { name: '基准指数', type: 'line', smooth: true, data: points.map(item => Number(item.benchmarkClose || 0)) },
          { name: '预算比例', type: 'bar', yAxisIndex: 1, data: points.map(item => Number(item.budgetPct || 0)) },
          { name: '策略启用', type: 'line', yAxisIndex: 1, data: points.map(item => (item.strategyEnabled ? 1 : 0)) }
        ]
      }
    },
    governanceChartOption() {
      const charts = this.governancePayload.charts || {}
      return {
        tooltip: { trigger: 'axis' },
        legend: { data: ['年化差值', '回撤差值', '胜率差值', '失效率差值'] },
        grid: { top: 40, left: 24, right: 24, bottom: 24, containLabel: true },
        xAxis: { type: 'category', data: charts.categories || [] },
        yAxis: { type: 'value' },
        series: [
          { name: '年化差值', type: 'bar', data: charts.annualDeltaSeries || [] },
          { name: '回撤差值', type: 'bar', data: charts.drawdownDeltaSeries || [] },
          { name: '胜率差值', type: 'line', smooth: true, data: charts.winRateDeltaSeries || [] },
          { name: '失效率差值', type: 'line', smooth: true, data: charts.invalidRateDeltaSeries || [] }
        ]
      }
    },
    governanceSummaryText() {
      const summary = this.governancePayload.summary || {}
      if (!summary.recommendationReason) {
        return '当前复盘对象未落到治理对比时，这里会显示候选策略与基线策略的证据摘要。'
      }
      return summary.recommendationReason
    },
    timelineSectionMeta() {
      return getReviewTimelineSectionMeta()
    },
    showGovernanceSection() {
      return shouldLoadGovernanceEvidence(this.queryParams)
    }
  },
  created() {
    this.syncReviewContext()
    this.loadAll()
  },
  watch: {
    '$route.query': {
      async handler(nextQuery, previousQuery) {
        if (!didReviewRouteContextChange(nextQuery, previousQuery)) {
          return
        }
        this.syncReviewContext()
        await this.hydrateCaseContext()
        await this.refreshReview()
      },
      deep: true
    }
  },
  methods: {
    syncReviewContext() {
      const context = normalizeReviewContext(this.$route && this.$route.query ? this.$route.query : {})
      this.reviewContext = {
        ...context,
        sourceActionLabel: this.reviewSourceActionLabel(context.sourceAction)
      }
      this.queryParams = {
        ...createDefaultReviewQueryParams(),
        ...this.queryParams,
        ...context
      }
      this.ensureQueryDefaults()
    },
    async loadAll() {
      const taskMap = {
        strategies: () => this.loadStrategies(),
        dashboardSummary: () => this.loadDashboardSummary(),
        executionFeedback: () => this.loadExecutionFeedback(),
        canaryLatest: () => this.loadCanaryLatest()
      }
      await Promise.all(getReviewBootstrapCriticalKeys().map(key => taskMap[key]()))
      await this.hydrateCaseContext()
      await this.refreshReview()
      this.scheduleDeferredWork(async () => {
        await Promise.all(getReviewBootstrapDeferredKeys().map(key => taskMap[key]()))
      })
    },
    async loadStrategies() {
      const response = await listStrategies()
      this.strategyList = Array.isArray(response.data) ? response.data : []
      this.ensureQueryDefaults()
    },
    async loadDashboardSummary() {
      const [summaryResp, reconciliationResp] = await Promise.all([
        getDashboardSummary(),
        getExecutionReconciliationSummary()
      ])
      this.dashboardSummary = summaryResp.data || {}
      this.reconciliationSummary = reconciliationResp.data || {}
    },
    async loadDashboardActionItems() {
      this.loadingActionItems = true
      try {
        const response = await getDashboardActionItems({ limit: 8 })
        const rows = Array.isArray(response.data) ? response.data : []
        this.dashboardActionItems = rows.filter(item => REVIEW_ACTION_TYPES.includes(String(item.actionType || '')))
      } finally {
        this.loadingActionItems = false
      }
    },
    async loadExecutionFeedback() {
      const response = await listExecutionFeedbackDetails({ limit: 50 })
      this.executionFeedbackRaw = Array.isArray(response.data) ? response.data : []
    },
    async loadCanaryLatest() {
      const response = await getCanaryLatest()
      this.canaryLatest = response.data || {}
    },
    async loadReviewCases() {
      const response = await getReviewCases({ reviewLevel: this.queryParams.reviewLevel, limit: 12 })
      this.reviewCases = Array.isArray(response.data) ? response.data : []
    },
    async hydrateCaseContext() {
      if (!this.queryParams.caseId) {
        return
      }
      const response = await getReviewCaseDetail(this.queryParams.caseId)
      const routeQuery = response.data && response.data.routeQuery ? response.data.routeQuery : {}
      if (!routeQuery.caseId) {
        return
      }
      const context = normalizeReviewContext(routeQuery)
      this.reviewContext = {
        ...context,
        sourceActionLabel: this.reviewSourceActionLabel(context.sourceAction)
      }
      this.queryParams = {
        ...createDefaultReviewQueryParams(),
        ...this.queryParams,
        ...context
      }
      this.ensureQueryDefaults()
    },
    ensureQueryDefaults() {
      const activeItems = this.strategyList.filter(item => Number(item.status) === 1)
      const pool = activeItems.length ? activeItems : this.strategyList
      if (!pool.length) {
        return
      }
      if ((this.queryParams.reviewLevel === 'trade' || this.queryParams.reviewLevel === 'strategy') && !this.queryParams.strategyId) {
        this.queryParams.strategyId = pool[0].id
      }
      if (this.queryParams.reviewLevel === 'governance') {
        if (!this.queryParams.baselineStrategyId) {
          this.queryParams.baselineStrategyId = pool[0].id
        }
        if (!this.queryParams.candidateStrategyId || this.queryParams.candidateStrategyId === this.queryParams.baselineStrategyId) {
          const candidate = pool.find(item => item.id !== this.queryParams.baselineStrategyId)
          this.queryParams.candidateStrategyId = candidate ? candidate.id : undefined
        }
      }
    },
    handleReviewLevelChange(value) {
      if (this.queryParams.caseId) {
        this.queryParams = clearCaseBoundQueryParams(this.queryParams, {
          reviewLevel: value
        })
        const context = normalizeReviewContext({
          ...this.queryParams,
          sourcePage: 'review',
          sourceAction: 'manualReview'
        })
        this.reviewContext = {
          ...context,
          sourceActionLabel: this.reviewSourceActionLabel(context.sourceAction)
        }
      }
      this.ensureQueryDefaults()
    },
    toggleReviewPanel(name) {
      if (this.reviewSecondaryPanels.includes(name)) {
        this.reviewSecondaryPanels = this.reviewSecondaryPanels.filter(item => item !== name)
        return
      }
      this.reviewSecondaryPanels = [...this.reviewSecondaryPanels, name]
    },
    buildReviewParams() {
      return {
        caseId: this.queryParams.caseId,
        reviewLevel: this.queryParams.reviewLevel,
        strategyId: this.queryParams.strategyId,
        stockCode: this.queryParams.stockCode || undefined,
        signalId: this.queryParams.signalId,
        baselineStrategyId: this.queryParams.baselineStrategyId,
        candidateStrategyId: this.queryParams.candidateStrategyId,
        months: this.queryParams.months,
        dateRangeStart: this.queryParams.dateRangeStart || undefined,
        dateRangeEnd: this.queryParams.dateRangeEnd || undefined,
        scopeType: this.queryParams.scopeType || undefined,
        scopePoolCode: this.queryParams.scopePoolCode || undefined,
        symbols: Array.isArray(this.queryParams.symbols) && this.queryParams.symbols.length ? this.queryParams.symbols : undefined,
        whitelist: Array.isArray(this.queryParams.whitelist) && this.queryParams.whitelist.length ? this.queryParams.whitelist : undefined,
        blacklist: Array.isArray(this.queryParams.blacklist) && this.queryParams.blacklist.length ? this.queryParams.blacklist : undefined,
        adHocSymbols: Array.isArray(this.queryParams.adHocSymbols) && this.queryParams.adHocSymbols.length ? this.queryParams.adHocSymbols : undefined
      }
    },
    async refreshReview() {
      const params = this.buildReviewParams()
      const refreshKey = createReviewRefreshKey(params)
      if (this.reviewRefreshPromise && this.reviewRefreshInFlightKey === refreshKey) {
        return this.reviewRefreshPromise
      }

      const requestId = this.reviewRefreshRequestId + 1
      this.reviewRefreshRequestId = requestId
      this.reviewRefreshInFlightKey = refreshKey
      this.loadingReview = true
      this.timelineRows = []
      this.reviewCases = []

      let refreshPromise
      refreshPromise = (async () => {
        try {
          const governanceEnabled = shouldLoadGovernanceEvidence(params)
          const primaryTaskMap = {
            summary: () => getReviewSummary(params),
            kline: () => getReviewKline(params),
            holding: () => getReviewHoldingRange(params),
            nav: () => getReviewNavDrawdown({
              strategyId: params.strategyId,
              dateRangeStart: params.dateRangeStart,
              dateRangeEnd: params.dateRangeEnd
            }),
            overlay: () => getReviewMarketOverlay({
              strategyId: params.strategyId,
              dateRangeStart: params.dateRangeStart,
              dateRangeEnd: params.dateRangeEnd
            }),
            governance: () => (governanceEnabled
              ? getReviewGovernanceEvidence({
                baselineStrategyId: params.baselineStrategyId,
                candidateStrategyId: params.candidateStrategyId,
                months: params.months
              })
              : Promise.resolve({ data: {} })),
            ruleExplain: () => getReviewRuleExplain({
              reviewLevel: params.reviewLevel,
              strategyId: params.strategyId,
              stockCode: params.stockCode,
              baselineStrategyId: params.baselineStrategyId,
              candidateStrategyId: params.candidateStrategyId
            })
          }
          const [summaryResp, klineResp, holdingResp, navResp, overlayResp, governanceResp, ruleResp] = await Promise.all(
            getReviewPrimaryRefreshKeys().map(key => primaryTaskMap[key]())
          )
          if (requestId !== this.reviewRefreshRequestId) {
            return
          }
          this.summaryPayload = summaryResp.data || {}
          this.klinePayload = klineResp.data || {}
          this.holdingPayload = holdingResp.data || {}
          this.navPayload = navResp.data || {}
          this.overlayPayload = overlayResp.data || {}
          this.governancePayload = governanceEnabled ? (governanceResp.data || {}) : {}
          this.ruleExplainPayload = ruleResp.data || {}
          this.syncConclusionForm()
          this.scheduleDeferredReviewRefresh(params, requestId)
        } finally {
          if (requestId === this.reviewRefreshRequestId) {
            this.loadingReview = false
          }
          if (this.reviewRefreshPromise === refreshPromise) {
            this.reviewRefreshPromise = null
            this.reviewRefreshInFlightKey = ''
          }
        }
      })()
      this.reviewRefreshPromise = refreshPromise
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
    scheduleDeferredReviewRefresh(params, requestId) {
      this.scheduleDeferredWork(async () => {
        if (requestId !== this.reviewRefreshRequestId) {
          return
        }
        const deferredTaskMap = {
          timeline: async () => {
            const response = await getReviewTimeline({
              ...params,
              limit: getReviewTimelineLimit()
            })
            if (requestId === this.reviewRefreshRequestId) {
              this.timelineRows = Array.isArray(response.data) ? response.data : []
            }
          },
          cases: async () => {
            const response = await getReviewCases({ reviewLevel: params.reviewLevel, limit: 12 })
            if (requestId === this.reviewRefreshRequestId) {
              this.reviewCases = Array.isArray(response.data) ? response.data : []
            }
          },
          actionItems: async () => {
            await this.loadDashboardActionItems()
          }
        }
        await Promise.all(getReviewDeferredRefreshKeys().map(key => deferredTaskMap[key]()))
      })
    },
    syncConclusionForm() {
      this.conclusionForm.reviewTargetName = this.summaryPayload.reviewTargetName || ''
      this.conclusionForm.reviewConclusion = this.summaryPayload.reviewConclusion || 'OBSERVE'
      this.conclusionForm.primaryReason = this.summaryPayload.primaryReason || ''
      this.conclusionForm.secondaryReason = this.summaryPayload.secondaryReason || ''
      this.conclusionForm.suggestedAction = this.summaryPayload.suggestedAction || 'OBSERVE'
      this.conclusionForm.confidenceLevel = this.summaryPayload.confidenceLevel || 'MEDIUM'
    },
    async submitConclusion() {
      this.loadingConclusion = true
      try {
        const params = this.buildReviewParams()
        const response = await submitReviewConclusion({
          ...params,
          caseId: this.queryParams.caseId,
          reviewTargetName: this.conclusionForm.reviewTargetName,
          reviewConclusion: this.conclusionForm.reviewConclusion,
          primaryReason: this.conclusionForm.primaryReason,
          secondaryReason: this.conclusionForm.secondaryReason,
          suggestedAction: this.conclusionForm.suggestedAction,
          confidenceLevel: this.conclusionForm.confidenceLevel,
          remark: this.conclusionForm.remark,
          actor: this.conclusionForm.actor,
          sourcePage: this.conclusionForm.sourcePage,
          sourceAction: this.conclusionForm.sourceAction,
          evidenceSnapshot: {
            summary: this.summaryPayload,
            governance: this.governancePayload.summary || {}
          }
        })
        this.$modal.msgSuccess(`复盘结论已提交 #${response.data && response.data.reviewId ? response.data.reviewId : ''}`)
        await this.refreshReview()
      } finally {
        this.loadingConclusion = false
      }
    },
    resetQuery() {
      this.queryParams = createDefaultReviewQueryParams()
      this.reviewContext = normalizeReviewContext()
      this.ensureQueryDefaults()
      const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
      if (Object.keys(routeQuery).length) {
        this.$router.replace({ path: '/quant/review', query: {} }).catch(() => {})
        return
      }
      this.refreshReview()
    },
    syncRouteQuery() {
      const query = buildReviewRouteQuery({
        ...this.buildReviewParams(),
        sourcePage: this.reviewContext.sourcePage || 'review',
        sourceAction: this.reviewContext.sourceAction || 'manualReview'
      })
      this.$router.replace({ path: '/quant/review', query }).catch(() => {})
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
    applyCase(row) {
      if (!row) {
        return
      }
      const query = buildReviewRouteQuery({
        caseId: row.caseId,
        reviewLevel: row.reviewLevel || 'trade',
        sourcePage: 'review',
        sourceAction: 'case'
      })
      this.$router.push({ path: '/quant/review', query }).catch(() => {})
    },
    strategyLabel(item) {
      return `${item.id} - ${item.strategy_name} (${item.strategy_type})`
    },
    reviewConclusionTagType(value) {
      if (value === 'HEALTHY') return 'success'
      if (value === 'OBSERVE') return 'warning'
      if (value === 'WARNING') return 'danger'
      if (value === 'INVALID') return 'danger'
      return 'info'
    },
    reviewActionTagType(value) {
      if (value === 'KEEP') return 'success'
      if (value === 'OBSERVE') return 'warning'
      if (value === 'REDUCE_WEIGHT') return 'warning'
      if (value === 'REPLACE') return 'danger'
      if (value === 'DISABLE') return 'danger'
      return 'info'
    },
    priorityTagType(priority) {
      if (priority === 'P0') return 'danger'
      if (priority === 'P1') return 'warning'
      return 'info'
    },
    reviewCaseTypeLabel(value) {
      if (value === 'ETF_REVIEW') return 'ETF复盘'
      if (value === 'GOVERNANCE_REVIEW') return '治理复盘'
      if (value === 'STRATEGY_REVIEW') return '策略复盘'
      return '交易复盘'
    },
    timelineItemType(item) {
      const eventType = String(item.eventType || '').toUpperCase()
      if (eventType === 'GOVERNANCE' || eventType === 'REVIEW') return 'warning'
      if (eventType === 'EXECUTION') return 'success'
      if (eventType === 'SIGNAL') return 'primary'
      return 'info'
    },
    reviewSourceActionLabel(action) {
      const labels = {
        signal: '待执行信号',
        executionRecord: '成交记录',
        unmatchedExecution: '未匹配成交',
        feedback: '执行反馈',
        RECORD_EXECUTION: '待补录成交',
        COMPLETE_PARTIAL_EXECUTION: '部分成交待补齐',
        CANCELLED_CONFIRMED: '已确认取消',
        MISSED_CONFIRMED: '已确认漏执行',
        CHECK_EXCEPTION: '执行异常核对',
        MANUAL_REVIEW: '人工复核',
        manualReview: '人工复核',
        case: '正式复盘案例',
        reviewCandidate: '待复盘对象',
        governanceCandidate: '治理候选对象',
        etfOverview: 'ETF 专题对象',
        etfGovernance: 'ETF 治理对象',
        shadowCandidateStrength: '候选策略优势月份',
        shadowCandidateWeakness: '候选策略劣势月份',
        shadowBaselineReference: '基线策略对照月份'
      }
      return labels[action] || (action ? String(action) : '')
    },
    goExecutionFocus(focus) {
      const query = {
        focus,
        ...buildReviewRouteQuery({
          ...this.buildReviewParams(),
          sourcePage: 'review',
          sourceAction: focus
        })
      }
      this.$router.push({ path: '/quant/execution', query }).catch(() => {})
    },
    goShadowCompare() {
      const query = {
        baselineStrategyId: this.queryParams.baselineStrategyId,
        candidateStrategyId: this.queryParams.candidateStrategyId,
        months: this.queryParams.months,
        scopeType: this.queryParams.scopeType || undefined,
        scopePoolCode: this.queryParams.scopePoolCode || undefined
      }
      this.$router.push({ path: '/quant/shadow', query }).catch(() => {})
    },
    goSymbols() {
      this.$router.push('/quant/symbols').catch(() => {})
    }
  }
}
</script>

<style scoped>
.review-page {
  background:
    radial-gradient(circle at top right, rgba(245, 214, 135, 0.22), transparent 32%),
    linear-gradient(180deg, #f7f4ec 0%, #f5f7fa 40%, #eef2f7 100%);
}

.mt16 {
  margin-top: 16px;
}

.review-hero {
  border: 1px solid #e6dcc8;
  background: linear-gradient(135deg, rgba(255, 248, 232, 0.94), rgba(247, 250, 255, 0.98));
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

.quick-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.scope-bar {
  margin-top: 10px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #e6dcc8;
  background: rgba(255, 255, 255, 0.72);
}

.scope-bar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-weight: 600;
  color: #303133;
}

.scope-bar__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.scope-bar__summary {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #606266;
  line-height: 1.6;
}

.summary-card {
  min-height: 144px;
  margin-bottom: 12px;
}

.review-conclusion-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.review-conclusion-card {
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid #e6dcc8;
  background: rgba(255, 255, 255, 0.84);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.review-conclusion-card label {
  color: #909399;
  font-size: 12px;
}

.review-conclusion-card strong {
  color: #303133;
  font-size: 18px;
  line-height: 1.5;
}

.review-conclusion-card span {
  color: #606266;
  line-height: 1.6;
}

.review-conclusion-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.review-secondary-collapse {
  border-top: 0;
}

.collapse-title {
  color: #303133;
  font-weight: 600;
}

.collapse-meta {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.summary-title {
  font-size: 13px;
  color: #606266;
}

.summary-value {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
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

.is-warning {
  color: #c57d2a;
}

.plain-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.plain-list-item {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  background: rgba(255, 255, 255, 0.78);
  line-height: 1.7;
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
  border-radius: 12px;
  border: 1px solid #ebeef5;
  background: rgba(255, 255, 255, 0.88);
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

.feedback-summary {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.governance-caption {
  margin-top: 12px;
  color: #606266;
  line-height: 1.7;
}

.timeline-title {
  font-weight: 600;
  color: #303133;
}

.timeline-detail {
  margin-top: 6px;
  color: #606266;
  line-height: 1.6;
}

@media (max-width: 1200px) {
  .review-conclusion-grid {
    grid-template-columns: 1fr;
  }
}
</style>
