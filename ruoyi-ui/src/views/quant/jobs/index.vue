<template>
  <div class="task-center-page">
    <div class="dispatch-center-header">
      <div class="dispatch-center-header__main">
        <div class="dispatch-center-header__chips">
          <div class="page-eyebrow">量化调度中心</div>
          <div v-if="showWorkbenchMarker" class="workbench-marker">工作树调试页</div>
        </div>
        <h1>调度中心</h1>
        <div class="dispatch-center-status-line">{{ taskCenterUi.pageStatusLine }}</div>
        <div class="dispatch-center-guide">
          <span>先看当前调度</span>
          <span>再决定是否发起新任务</span>
          <span>历史与定义下沉到折叠区</span>
        </div>
      </div>
      <el-button size="small" plain icon="el-icon-refresh" class="dispatch-center-header__refresh" @click="refreshHome">刷新</el-button>
    </div>

    <primary-task-card
      :primary-task="taskCenterUi.primaryTaskView || taskCenterSummary.primaryTask"
      :status-line="taskCenterUi.pageStatusLine"
      :primary-hint="taskCenterUi.primaryTaskHint"
      :primary-action="taskCenterUi.primaryAction"
      :secondary-actions="cardSecondaryActions"
      :pending-action-key="pendingActionKey"
      :action-feedback="actionFeedback"
      @run-primary="handlePrimaryAction"
      @run-secondary="handleSecondaryAction"
    />

    <div class="section-title section-title--inline mt16">
      <div>
        <span>最近 3 条调度结果</span>
        <span class="section-meta section-meta--inline">只保留最近结果，不在首屏堆完整历史</span>
      </div>
      <el-tag size="mini" type="info">最近 {{ recentDispatchCards.length }} 条</el-tag>
    </div>
    <div class="recent-history-grid">
      <el-card
        v-for="row in recentDispatchCards"
        :key="row.jobId || row.startedAt || row.taskName"
        shadow="never"
        class="recent-history-card"
      >
        <div class="recent-history-card__head">
          <div class="recent-history-card__title">{{ row.taskName || '未命名任务' }}</div>
          <el-tag size="mini" :type="historyStatusType(row.status)">{{ row.status || '-' }}</el-tag>
        </div>
        <div class="recent-history-card__meta">触发时间 {{ row.startedAt || '-' }}</div>
        <div class="recent-history-card__meta">范围 {{ row.scopeSummary || '-' }}</div>
        <div class="recent-history-card__meta">时间范围 {{ row.timeRangeSummary || '-' }}</div>
        <div class="recent-history-card__result">{{ row.resultSummary || '等待系统产出结果摘要' }}</div>
        <el-button size="small" type="primary" plain @click="openDispatchDetail(row)">查看详情</el-button>
      </el-card>
      <div v-if="!recentDispatchCards.length" class="recent-history-empty">最近没有可展示的调度结果。</div>
    </div>

    <el-collapse v-model="jobsSecondaryPanels" class="mt16 jobs-secondary-collapse">
      <el-collapse-item name="progress">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>最近进展与技术摘要</span>
            <span class="section-meta">展开后看步骤、技术状态和辅助事实</span>
          </div>
        </template>
        <task-progress-timeline
          :events="taskCenterUi.progressEvents"
          :technical-summary="taskCenterUi.technicalSummaryRows"
          :primary-task="taskCenterUi.primaryTaskView"
        />
      </el-collapse-item>

      <el-collapse-item name="definitions">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>调度定义</span>
            <span class="section-meta">查看自动计划与固定调度入口</span>
          </div>
        </template>
        <div class="auto-plan-summary">
          <div class="auto-plan-summary__title">{{ nextScheduledDispatch.taskName || '暂无启用的自动调度' }}</div>
          <div class="auto-plan-summary__meta">下一次 {{ nextScheduledDispatch.nextFireTime || '-' }}</div>
          <div class="auto-plan-summary__meta">范围 {{ nextScheduledDispatch.defaultScope || '-' }}</div>
          <div class="auto-plan-summary__meta">时间范围 {{ nextScheduledDispatch.defaultTimeRange || '-' }}</div>
          <el-button type="text" @click="$router.push('/quant/dispatch-auto')">查看自动计划</el-button>
        </div>
        <dispatch-definition-table :items="dispatchDefinitions" @filter-history="handleFilterHistory" />
      </el-collapse-item>

      <el-collapse-item name="history">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>全部调度历史</span>
            <span class="section-meta">筛选和回看完整记录</span>
          </div>
        </template>
        <dispatch-history-table
          :rows="dispatchHistoryRows"
          :total="dispatchHistoryTotal"
          :page="dispatchHistoryData.pageNum"
          :limit="dispatchHistoryData.pageSize"
          @change="loadDispatchHistory"
          @view-detail="openDispatchDetail"
        />
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script>
import ElCollapse from 'element-ui/lib/collapse'
import ElCollapseItem from 'element-ui/lib/collapse-item'
import 'element-ui/lib/theme-chalk/collapse.css'
import {
  getDispatchDefinitions,
  getDispatchHistory,
  getTaskCenterSummary
} from '@/api/quant'
import PrimaryTaskCard from './components/PrimaryTaskCard'
import TaskProgressTimeline from './components/TaskProgressTimeline'
import DispatchDefinitionTable from './components/DispatchDefinitionTable'
import DispatchHistoryTable from './components/DispatchHistoryTable'
import { buildTaskCenterState } from './jobs-task-center-state'
const { pickCurrentJobId, readDispatchHandoff } = require('./dispatch-handoff')

export default {
  name: 'QuantDispatchCenter',
  components: {
    ElCollapse,
    ElCollapseItem,
    PrimaryTaskCard,
    TaskProgressTimeline,
    DispatchDefinitionTable,
    DispatchHistoryTable
  },
  data() {
    return {
      loadingSummary: false,
      pendingActionKey: '',
      historyTaskCodeFilter: '',
      taskCenterSummary: {},
      taskCenterUi: buildTaskCenterState(),
      actionFeedback: null,
      jobsSecondaryPanels: [],
      dispatchDefinitionsData: [],
      dispatchHistoryData: {
        rows: [],
        total: 0,
        pageNum: 1,
        pageSize: 10
      },
      currentJobId: undefined,
      currentJobSource: 'none',
      recentDispatchHandoff: null
    }
  },
  computed: {
    nextScheduledDispatch() {
      return this.taskCenterSummary.nextScheduledDispatch || {}
    },
    dispatchDefinitions() {
      return Array.isArray(this.dispatchDefinitionsData) && this.dispatchDefinitionsData.length
        ? this.dispatchDefinitionsData
        : (Array.isArray(this.taskCenterSummary.dispatchDefinitions) ? this.taskCenterSummary.dispatchDefinitions : [])
    },
    dispatchHistoryRows() {
      return Array.isArray(this.dispatchHistoryData.rows) ? this.dispatchHistoryData.rows : []
    },
    dispatchHistoryTotal() {
      return Number(this.dispatchHistoryData.total || 0)
    },
    showWorkbenchMarker() {
      return process.env.NODE_ENV !== 'production'
    },
    recentDispatchCards() {
      return this.dispatchHistoryRows.slice(0, 3)
    },
    cardSecondaryActions() {
      const actions = Array.isArray(this.taskCenterUi.secondaryActions) ? this.taskCenterUi.secondaryActions.slice() : []
      const hasAutoAction = actions.some(item => item && item.targetPage === '/quant/dispatch-auto')
      if (!hasAutoAction) {
        actions.push({
          code: 'VIEW_AUTO_PLAN',
          label: '查看自动计划',
          targetPage: '/quant/dispatch-auto'
        })
      }
      return actions.slice(0, 2)
    }
  },
  async created() {
    await this.refreshHome()
  },
  methods: {
    async refreshHome() {
      await Promise.all([
        this.loadTaskCenterSummary(),
        this.loadDispatchDefinitions(),
        this.loadDispatchHistory()
      ])
      this.syncCurrentJobIdFromDispatchHistory()
    },
    async loadTaskCenterSummary() {
      this.loadingSummary = true
      try {
        const response = await getTaskCenterSummary()
        this.taskCenterSummary = response.data || {}
        this.taskCenterUi = buildTaskCenterState(this.taskCenterSummary)
      } catch (error) {
        this.$modal.msgError('读取调度中心摘要失败')
      } finally {
        this.loadingSummary = false
      }
    },
    async loadDispatchDefinitions() {
      try {
        const response = await getDispatchDefinitions()
        this.dispatchDefinitionsData = Array.isArray(response.data) ? response.data : []
      } catch (error) {
        this.dispatchDefinitionsData = []
      }
    },
    async loadDispatchHistory(payload = {}) {
      const pageNum = payload.page || this.dispatchHistoryData.pageNum || 1
      const pageSize = payload.limit || this.dispatchHistoryData.pageSize || 10
      try {
        const response = await getDispatchHistory({
          pageNum,
          pageSize,
          taskCode: this.historyTaskCodeFilter || undefined
        })
        this.dispatchHistoryData = response.data || { rows: [], total: 0, pageNum, pageSize }
      } catch (error) {
        const fallback = this.taskCenterSummary.dispatchHistory || {}
        this.dispatchHistoryData = {
          rows: Array.isArray(fallback.rows) ? fallback.rows : [],
          total: Number(fallback.total || 0),
          pageNum,
          pageSize
        }
      } finally {
        this.syncCurrentJobIdFromDispatchHistory()
      }
    },
    syncCurrentJobIdFromDispatchHistory() {
      const picked = pickCurrentJobId({
        rows: this.dispatchHistoryRows,
        storage: window.sessionStorage
      })
      this.currentJobId = picked.jobId
      this.currentJobSource = picked.source
      this.recentDispatchHandoff = picked.handoff || readDispatchHandoff(window.sessionStorage)
    },
    openDispatchDetail(row) {
      if (!row || !row.jobId) {
        this.$modal.msgWarning('当前记录没有可查看的任务详情')
        return
      }
      this.$router.push({ path: `/quant/dispatch-detail/${row.jobId}` }).catch(() => {})
    },
    openCurrentDispatch() {
      const fallbackJobId = this.currentJobId || (this.taskCenterSummary.currentDispatch && this.taskCenterSummary.currentDispatch.jobId)
      if (!fallbackJobId) {
        this.$modal.msgWarning('当前没有可查看的调度详情')
        return
      }
      this.$router.push({ path: `/quant/dispatch-detail/${fallbackJobId}` }).catch(() => {})
    },
    historyStatusType(status) {
      if (status === 'SUCCESS') return 'success'
      if (status === 'FAILED' || status === 'PARTIAL_FAILED') return 'danger'
      if (status === 'RUNNING' || status === 'QUEUED' || status === 'PENDING') return 'warning'
      return 'info'
    },
    handleFilterHistory(taskCode) {
      this.historyTaskCodeFilter = taskCode || ''
      this.loadDispatchHistory({ page: 1, limit: this.dispatchHistoryData.pageSize || 10 })
    },
    async handlePrimaryAction(action) {
      await this.runAction(action)
    },
    async handleSecondaryAction(action) {
      await this.runAction(action)
    },
    async runAction(action = {}) {
      const code = action.code || 'REFRESH_STATUS'
      if (code === 'GO_OPERATIONS' || code === 'GO_DASHBOARD' || code === 'VIEW_AUTO_PLAN') {
        this.$router.push(action.targetPage).catch(() => {})
        return
      }
      if (code === 'RUN_EXECUTION') {
        this.$router.push('/quant/dispatch-manual').catch(() => {})
        return
      }
      if (code === 'VIEW_CURRENT_DISPATCH' || code === 'WAIT_CURRENT_TASK') {
        this.openCurrentDispatch()
        return
      }
      this.pendingActionKey = code
      this.actionFeedback = {
        message: '正在刷新调度中心首页',
        startedAt: new Date().toLocaleString(),
        nextStep: '系统会重新读取今日状态、当前调度和最近历史。'
      }
      try {
        await this.refreshHome()
        this.actionFeedback = {
          message: action.label || '首页状态已刷新',
          startedAt: new Date().toLocaleString(),
          nextStep: this.currentJobId
            ? (this.currentJobSource === 'handoff'
              ? '最近手工提交的任务已接力到首页，可先进入调度详情页继续观察。'
              : '如果存在运行中的任务，可直接进入调度详情页继续观察。')
            : '当前没有运行中的任务，可发起新的手工调度。'
        }
      } finally {
        this.pendingActionKey = ''
      }
    }
  }
}
</script>

<style scoped>
.task-center-page {
  padding: 4px;
  background:
    radial-gradient(circle at top left, rgba(15, 118, 110, 0.08), transparent 28%),
    linear-gradient(180deg, #f8fbff 0, #ffffff 180px);
}

.dispatch-center-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
  padding: 18px 20px;
  border-radius: 22px;
  background: linear-gradient(135deg, #f8fffd, #eef6ff);
  border: 1px solid #d9e7ef;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.05);
}

.dispatch-center-header__main {
  min-width: 0;
}

.dispatch-center-header__chips {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.page-eyebrow {
  color: #0f766e;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  font-weight: 700;
}

.workbench-marker {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.12);
  color: #115e59;
  font-size: 12px;
  font-weight: 700;
}

.dispatch-center-header h1 {
  margin: 10px 0 0;
  color: #0f172a;
  font-size: 30px;
  line-height: 1.35;
}

.dispatch-center-status-line {
  margin-top: 8px;
  color: #475569;
  line-height: 1.7;
  max-width: 860px;
}

.dispatch-center-guide {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.dispatch-center-guide span {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid #dbe7ee;
  color: #334155;
  font-size: 12px;
}

.dispatch-center-header__refresh {
  border-color: #cbd5e1;
  background: rgba(255, 255, 255, 0.85);
}

.section-title {
  margin-bottom: 12px;
  color: #0f172a;
  font-size: 20px;
  font-weight: 700;
}

.section-title--inline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.recent-history-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.recent-history-card {
  border-radius: 18px;
  border: 1px solid #e4ebf5;
  background: linear-gradient(180deg, #ffffff, #fbfdff);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.recent-history-card__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.recent-history-card__title {
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.45;
}

.recent-history-card__meta {
  margin-top: 10px;
  color: #475569;
  line-height: 1.7;
}

.recent-history-card__result {
  margin: 14px 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  color: #334155;
  line-height: 1.7;
}

.recent-history-empty {
  grid-column: 1 / -1;
  padding: 12px 14px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px dashed #d6dde8;
  color: #64748b;
}

.jobs-secondary-collapse {
  border-radius: 16px;
  overflow: hidden;
}

.collapse-title-shell {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 600;
}

.section-meta {
  color: #64748b;
  font-size: 12px;
  font-weight: 400;
}

.section-meta--inline {
  margin-left: 10px;
}

.auto-plan-summary {
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.auto-plan-summary__title {
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
}

.auto-plan-summary__meta {
  margin-top: 8px;
  color: #475569;
  line-height: 1.7;
}

.mt16 {
  margin-top: 16px;
}

@media (max-width: 1200px) {
  .recent-history-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .dispatch-center-header {
    flex-direction: column;
  }

  .section-title--inline {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
