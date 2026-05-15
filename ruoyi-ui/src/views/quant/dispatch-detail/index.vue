<template>
  <div class="dispatch-detail-page">
    <div class="page-header">
      <div>
        <div class="page-eyebrow">调度详情</div>
        <h1>{{ detailState.pageTitle }}</h1>
        <div class="page-summary">{{ detailState.resultSummary }}</div>
        <div class="page-guide-chips">
          <span>{{ detailState.firstScreenMode === 'outcome-first' ? '先看结果与异常' : '先看当前执行进度' }}</span>
          <span>结果明细在首屏下方</span>
          <span>日志与分片收进折叠区</span>
        </div>
      </div>
      <div class="page-header__side">
        <div class="page-status">
          <el-tag size="small" :type="detailState.statusType">{{ detailState.statusLabel }}</el-tag>
          <span>{{ detailState.refreshHint }}</span>
          <span>最近刷新：{{ refreshedAt || '-' }}</span>
        </div>
        <div class="page-actions">
          <el-button size="small" type="primary" @click="loadDetail">刷新</el-button>
          <el-button size="small" plain @click="$router.push('/quant/jobs')">返回调度中心</el-button>
          <el-button size="small" type="text" @click="toggleAutoRefresh">{{ autoRefresh ? '关闭自动刷新' : '开启自动刷新' }}</el-button>
        </div>
      </div>
    </div>

    <el-row v-if="detailState.firstScreenMode === 'progress-first'" :gutter="12">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="summary-card">
          <div class="card-title">本次执行了什么</div>
          <div class="fact-grid">
            <div v-for="item in detailState.factRows" :key="item.label" class="fact-item">
              <label>{{ item.label }}</label>
              <span>{{ item.value }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="summary-card">
          <div class="card-title">结果与异常</div>
          <div class="outcome-list">
            <div v-for="item in detailState.outcomeStats" :key="item.label" class="outcome-item">
              <label>{{ item.label }}</label>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
          <div class="latest-log-card">
            <label>最近一条日志</label>
            <div>{{ detailState.latestLogLabel }}</div>
            <small>{{ detailState.latestLogTime }}</small>
          </div>
          <div class="result-preview-block">
            <div class="error-block__title">这次产出了什么</div>
            <div v-if="detailState.resultBreakdown.length" class="result-chip-row">
              <span v-for="item in detailState.resultBreakdown" :key="item.label" class="result-chip">
                {{ item.label }} {{ item.value }}
              </span>
            </div>
            <div v-if="detailState.resultPreviewRows.length" class="result-preview-list">
              <div v-for="item in detailState.resultPreviewRows" :key="item.title" class="result-preview-item">
                <div class="result-preview-title">{{ item.title }}</div>
                <div class="result-preview-meta">{{ item.subtitle }}</div>
              </div>
            </div>
            <div v-else class="compact-empty">当前还没有代表结果。</div>
          </div>
          <div class="error-block">
            <div class="error-block__title">异常分类</div>
            <div v-if="errorCategories.length" class="error-list">
              <div v-for="(item, index) in errorCategories" :key="`${item.category}-${index}`" class="error-item">
                <div class="error-name">{{ item.category || '未知异常' }}</div>
                <div class="error-meta">出现 {{ item.count || 0 }} 次</div>
              </div>
            </div>
            <div v-else class="empty-copy compact-empty">当前没有聚合异常。</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row v-else :gutter="12">
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="summary-card">
          <div class="card-title">结果与异常</div>
          <div class="outcome-list">
            <div v-for="item in detailState.outcomeStats" :key="item.label" class="outcome-item">
              <label>{{ item.label }}</label>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
          <div class="latest-log-card">
            <label>最近一条日志</label>
            <div>{{ detailState.latestLogLabel }}</div>
            <small>{{ detailState.latestLogTime }}</small>
          </div>
          <div class="error-block">
            <div class="error-block__title">异常分类</div>
            <div v-if="errorCategories.length" class="error-list">
              <div v-for="(item, index) in errorCategories" :key="`${item.category}-${index}`" class="error-item">
                <div class="error-name">{{ item.category || '未知异常' }}</div>
                <div class="error-meta">出现 {{ item.count || 0 }} 次</div>
              </div>
            </div>
            <div v-else class="empty-copy compact-empty">当前没有聚合异常。</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="summary-card">
          <div class="card-title">本次产出了什么</div>
          <div class="result-preview-block result-preview-block--top">
            <div v-if="detailState.resultBreakdown.length" class="result-chip-row">
              <span v-for="item in detailState.resultBreakdown" :key="item.label" class="result-chip">
                {{ item.label }} {{ item.value }}
              </span>
            </div>
            <div v-if="detailState.resultPreviewRows.length" class="result-preview-list">
              <div v-for="item in detailState.resultPreviewRows" :key="item.title" class="result-preview-item">
                <div class="result-preview-title">{{ item.title }}</div>
                <div class="result-preview-meta">{{ item.subtitle }}</div>
              </div>
            </div>
            <div v-else class="compact-empty">当前还没有代表结果。</div>
          </div>
          <div class="card-title card-title--sub">本次执行了什么</div>
          <div class="fact-grid">
            <div v-for="item in detailState.factRows" :key="item.label" class="fact-item">
              <label>{{ item.label }}</label>
              <span>{{ item.value }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="section-title mt12">结果明细</div>
    <job-result-table :rows="results" compact :show-header="false" />

    <el-collapse v-model="detailSecondaryPanels" class="mt16 detail-secondary-collapse">
      <el-collapse-item name="shards">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>分片明细</span>
            <span class="section-meta">任务被拆成了哪些执行单元</span>
          </div>
        </template>
        <job-shard-table :rows="shards" compact :show-header="false" />
      </el-collapse-item>

      <el-collapse-item name="logs">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>结构化日志</span>
            <span class="section-meta">系统实际记录了哪些步骤</span>
          </div>
        </template>
        <el-card shadow="never" class="compact-log-card">
          <div v-if="logRows.length" class="log-stream">
            <div v-for="(item, index) in logRows" :key="`${item.stepName || item.message}-${index}`" class="log-row">
              <div class="log-time">{{ item.endTime || item.startTime || '-' }}</div>
              <div class="log-level">{{ item.status || 'INFO' }}</div>
              <div class="log-message">{{ item.stepName || item.message || '系统事件' }}</div>
            </div>
          </div>
          <div v-else class="compact-empty-panel">当前还没有可展示的结构化日志。</div>
        </el-card>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script>
import ElCollapse from 'element-ui/lib/collapse'
import ElCollapseItem from 'element-ui/lib/collapse-item'
import 'element-ui/lib/theme-chalk/collapse.css'
import { getDispatchDetail } from '@/api/quant'
import JobResultTable from '@/views/quant/jobs/components/JobResultTable'
import JobShardTable from '@/views/quant/jobs/components/JobShardTable'
const { buildDispatchDetailState } = require('./dispatch-detail-state')

export default {
  name: 'QuantDispatchDetail',
  components: {
    ElCollapse,
    ElCollapseItem,
    JobResultTable,
    JobShardTable
  },
  data() {
    return {
      autoRefresh: true,
      refreshTimer: null,
      refreshCountdownTimer: null,
      nextRefreshInSeconds: 0,
      detailPayload: {},
      historyRecord: {},
      jobStatus: null,
      shards: [],
      results: [],
      events: [],
      errorCategories: [],
      refreshedAt: '',
      activeBatchId: null,
      detailSecondaryPanels: []
    }
  },
  computed: {
    jobId() {
      return this.$route.params.jobId
    },
    detailState() {
      return buildDispatchDetailState({
        detailPayload: this.detailPayload,
        historyRecord: this.historyRecord,
        jobStatus: this.jobStatus,
        shards: this.shards,
        results: this.results,
        events: this.events,
        errorCategories: this.errorCategories,
        autoRefresh: this.autoRefresh,
        nextRefreshInSeconds: this.nextRefreshInSeconds
      })
    },
    statusText() {
      return this.detailState.statusText
    },
    runningShard() {
      return (this.shards || []).find(item => item.status === 'RUNNING') || null
    },
    logRows() {
      if (Array.isArray(this.events) && this.events.length) {
        return this.events.slice(0, 20)
      }
      const rows = []
      if (this.historyRecord.startedAt) {
        rows.push({
          startTime: this.historyRecord.startedAt,
          status: 'INFO',
          stepName: `调度已提交：${this.historyRecord.scopeSummary || '未记录范围'}`
        })
      }
      if (this.runningShard) {
        rows.push({
          startTime: this.refreshedAt,
          status: 'RUNNING',
          stepName: `当前正在处理 ${this.runningShard.shard_key || '运行中分片'}`
        })
      }
      return rows
    }
  },
  watch: {
    autoRefresh() {
      this.scheduleRefresh()
    },
    '$route.params.jobId': {
      immediate: false,
      async handler() {
        await this.loadDetail()
      }
    }
  },
  async created() {
    await this.loadDetail()
  },
  beforeDestroy() {
    this.clearRefreshTimer()
  },
  methods: {
    clearRefreshTimer() {
      if (this.refreshTimer) {
        clearTimeout(this.refreshTimer)
        this.refreshTimer = null
      }
      if (this.refreshCountdownTimer) {
        clearInterval(this.refreshCountdownTimer)
        this.refreshCountdownTimer = null
      }
      this.nextRefreshInSeconds = 0
    },
    scheduleRefresh() {
      this.clearRefreshTimer()
      if (!this.autoRefresh || !['QUEUED', 'PENDING', 'RUNNING'].includes(this.statusText)) {
        return
      }
      this.nextRefreshInSeconds = 8
      this.refreshCountdownTimer = setInterval(() => {
        if (this.nextRefreshInSeconds > 0) {
          this.nextRefreshInSeconds -= 1
        }
      }, 1000)
      this.refreshTimer = setTimeout(async () => {
        await this.loadDetail()
      }, 8000)
    },
    async loadDetail() {
      if (!this.jobId) {
        return
      }
      try {
        const response = await getDispatchDetail(this.jobId)
        const detail = response.data || {}
        this.detailPayload = detail
        this.jobStatus = detail.status || null
        this.shards = Array.isArray(detail.shards) ? detail.shards : []
        this.results = Array.isArray(detail.results) ? detail.results : []
        this.historyRecord = detail.overview || {}
        this.activeBatchId = detail.batchId || null
        this.events = Array.isArray(detail.events) ? detail.events : []
        this.errorCategories = Array.isArray(detail.errorCategories) ? detail.errorCategories : []
        this.refreshedAt = new Date().toLocaleString()
      } catch (error) {
        this.$modal.msgError('读取调度详情失败')
      } finally {
        this.scheduleRefresh()
      }
    },
    toggleAutoRefresh() {
      this.autoRefresh = !this.autoRefresh
    }
  }
}
</script>

<style scoped>
.dispatch-detail-page {
  padding: 4px;
}

.page-header {
  margin-bottom: 12px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e5eaf3;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.page-eyebrow {
  color: #909399;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.page-header h1 {
  margin: 8px 0 0;
  font-size: 28px;
  line-height: 1.35;
  color: #0f172a;
}

.page-summary {
  margin-top: 8px;
  color: #475569;
  line-height: 1.6;
}

.page-guide-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.page-guide-chips span {
  padding: 5px 10px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #334155;
  font-size: 12px;
}

.page-header__side {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 280px;
}

.page-status {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  color: #909399;
  font-size: 12px;
}

.page-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.summary-card {
  border-radius: 12px;
  border: 1px solid #e5eaf3;
}

.compact-log-card ::v-deep .el-card__body {
  padding: 12px;
}

.card-title,
.section-title {
  margin-bottom: 12px;
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
}

.card-title--sub {
  margin-top: 18px;
}

.fact-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
}

.fact-item,
.outcome-item {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.fact-item label,
.outcome-item label,
.latest-log-card label {
  color: #909399;
  font-size: 12px;
}

.fact-item span,
.outcome-item strong {
  color: #1f2937;
  line-height: 1.6;
  word-break: break-word;
}

.outcome-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.latest-log-card {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
  color: #1f2937;
  line-height: 1.6;
}

.latest-log-card small {
  display: block;
  margin-top: 6px;
  color: #909399;
}

.error-block {
  margin-top: 12px;
}

.result-preview-block {
  margin-top: 12px;
}

.result-preview-block--top {
  margin-top: 0;
}

.error-block__title {
  margin-bottom: 8px;
  color: #606266;
  font-size: 13px;
  font-weight: 600;
}

.result-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.result-chip {
  padding: 4px 10px;
  border-radius: 999px;
  background: #eef6ff;
  border: 1px solid #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
  line-height: 1.4;
}

.result-preview-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

.result-preview-item {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
}

.result-preview-title {
  color: #1f2937;
  font-weight: 600;
  line-height: 1.5;
}

.result-preview-meta {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.error-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.error-item {
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
}

.error-name {
  color: #9a3412;
  font-weight: 700;
}

.error-meta,
.empty-copy {
  margin-top: 6px;
  color: #7c2d12;
  line-height: 1.6;
}

.compact-empty {
  margin-top: 0;
  color: #606266;
  background: #f8fafc;
  border-radius: 10px;
  padding: 10px 12px;
}

.compact-empty-panel {
  padding: 12px;
  color: #606266;
  background: #f8fafc;
  border-radius: 10px;
  line-height: 1.6;
}

.log-stream {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.log-row {
  display: grid;
  grid-template-columns: 150px 100px 1fr;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
  color: #1f2937;
}

.log-time,
.log-level {
  color: #64748b;
  font-size: 12px;
}

.mt16 {
  margin-top: 16px;
}

.mt12 {
  margin-top: 12px;
}

.detail-secondary-collapse {
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
  .page-header {
    flex-direction: column;
  }

  .fact-grid {
    grid-template-columns: 1fr;
  }

  .log-row {
    grid-template-columns: 1fr;
  }
}
</style>
