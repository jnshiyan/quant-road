<template>
  <div class="dispatch-auto-page">
    <div class="dispatch-auto-header">
      <div class="dispatch-auto-header__main">
        <div class="dispatch-auto-header__chips">
          <div class="page-eyebrow">调度中心 / 自动触发视图</div>
          <div class="dispatch-auto-header__badge">自动触发操作台</div>
        </div>
        <h1>自动调度</h1>
        <div class="dispatch-auto-topline">{{ autoPageStatusLine }}</div>
        <div class="dispatch-auto-guide">
          <span>先看系统下一次会跑什么</span>
          <span>再看最近自动结果</span>
          <span>异常优先于完整历史</span>
        </div>
      </div>
      <div class="dispatch-auto-header__actions">
        <el-button plain @click="$router.push('/quant/jobs')">返回调度中心</el-button>
        <el-button plain @click="loadPage">刷新自动调度</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="auto-summary-card">
          <div class="auto-summary-card__eyebrow">当前自动计划</div>
          <div class="auto-summary-card__title">{{ nextAutoDispatch.taskName || '暂无启用的自动调度' }}</div>
          <div class="auto-summary-card__meta">下一次 {{ nextAutoDispatch.nextFireTime || '-' }}</div>
          <div class="auto-summary-card__meta">范围 {{ nextAutoDispatch.defaultScope || '-' }}</div>
          <div class="auto-summary-card__meta">时间范围 {{ nextAutoDispatch.defaultTimeRange || '-' }}</div>
          <div class="auto-summary-card__actions">
            <el-button type="primary" size="small" @click="handleAutoPrimaryAction">
              {{ autoPrimaryLabel }}
            </el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="auto-summary-card">
          <div class="auto-summary-card__eyebrow">最近自动结果</div>
          <div class="auto-summary-card__title">{{ latestHistoryRow ? (latestHistoryRow.taskName || '未命名调度') : '暂无自动结果' }}</div>
          <div class="auto-summary-card__meta">时间 {{ latestHistoryRow ? (latestHistoryRow.startedAt || '-') : '-' }}</div>
          <div class="auto-summary-card__meta">状态 {{ latestHistoryRow ? (latestHistoryRow.status || '-') : '-' }}</div>
          <div class="auto-summary-card__copy">{{ latestHistorySummary }}</div>
          <div class="auto-summary-card__actions">
            <el-button
              v-if="latestHistoryRow && latestHistoryRow.jobId"
              size="small"
              plain
              @click="openDispatchDetail(latestHistoryRow)"
            >
              查看最近自动结果
            </el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="auto-summary-card auto-summary-card--warn">
          <div class="auto-summary-card__eyebrow">当前异常</div>
          <div class="auto-summary-card__title">{{ latestFailureRow ? (latestFailureRow.taskName || '最近自动调度异常') : '当前没有自动异常' }}</div>
          <div class="auto-summary-card__meta">最近失败数 {{ failureRows.length }}</div>
          <div class="auto-summary-card__copy">{{ latestFailureSummary }}</div>
          <div class="auto-summary-card__actions">
            <el-button
              v-if="latestFailureRow && latestFailureRow.jobId"
              size="small"
              plain
              @click="openDispatchDetail(latestFailureRow)"
            >
              进入异常详情
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-collapse v-model="dispatchAutoPanels" class="mt16 dispatch-auto-secondary-collapse">
      <el-collapse-item name="definitions">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>调度定义</span>
            <span class="section-meta">展开后看系统会自动跑哪些定义</span>
          </div>
        </template>
        <dispatch-definition-table :items="definitions" @filter-history="handleFilterHistory" />
      </el-collapse-item>

      <el-collapse-item name="history">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>最近执行历史</span>
            <span class="section-meta">完整回看自动触发历史</span>
          </div>
        </template>
        <dispatch-history-table
          :rows="historyRows"
          :total="historyTotal"
          :page="pageNum"
          :limit="pageSize"
          @change="loadHistory"
          @view-detail="openDispatchDetail"
        />
      </el-collapse-item>

      <el-collapse-item name="exceptions">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>异常与处理</span>
            <span class="section-meta">集中看失败记录和使用边界</span>
          </div>
        </template>
        <el-row :gutter="16">
          <el-col :xs="24" :lg="12">
            <el-card shadow="never">
              <div class="panel-title">最近失败的自动调度</div>
              <div v-if="failureRows.length" class="panel-list">
                <div v-for="(item, index) in failureRows" :key="`${item.jobId}-${index}`" class="panel-item">
                  <div class="panel-main">{{ item.taskName || '未命名调度' }}</div>
                  <div class="panel-meta">{{ item.startedAt || '-' }} · {{ item.status || '-' }}</div>
                  <div class="panel-copy">{{ item.errorSummary || item.resultSummary || '请进入调度详情查看原因。' }}</div>
                </div>
              </div>
              <div v-else class="empty-copy">最近没有自动调度失败记录。</div>
            </el-card>
          </el-col>
          <el-col :xs="24" :lg="12">
            <el-card shadow="never">
              <div class="panel-title">使用边界</div>
              <div class="panel-list">
                <div class="panel-item">
                  <div class="panel-main">这里负责看系统会自动跑什么</div>
                  <div class="panel-copy">重点看默认范围、默认时间范围，以及最近自动结果是否稳定。</div>
                </div>
                <div class="panel-item">
                  <div class="panel-main">如果要立刻执行，请回调度中心</div>
                  <div class="panel-copy">自动调度页只负责看计划与历史，不承担手工触发主流程。</div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script>
import ElCollapse from 'element-ui/lib/collapse'
import ElCollapseItem from 'element-ui/lib/collapse-item'
import 'element-ui/lib/theme-chalk/collapse.css'
import { getDispatchDefinitions, getDispatchHistory } from '@/api/quant'
import DispatchDefinitionTable from '@/views/quant/jobs/components/DispatchDefinitionTable'
import DispatchHistoryTable from '@/views/quant/jobs/components/DispatchHistoryTable'

export default {
  name: 'QuantDispatchAuto',
  components: {
    ElCollapse,
    ElCollapseItem,
    DispatchDefinitionTable,
    DispatchHistoryTable
  },
  data() {
    return {
      definitions: [],
      historyRows: [],
      historyTotal: 0,
      pageNum: 1,
      pageSize: 10,
      taskCodeFilter: '',
      dispatchAutoPanels: []
    }
  },
  computed: {
    nextAutoDispatch() {
      return this.definitions[0] || {}
    },
    latestHistoryRow() {
      return this.historyRows[0] || null
    },
    latestFailureRow() {
      return this.failureRows[0] || null
    },
    failureRows() {
      return this.historyRows.filter(item => ['FAILED', 'PARTIAL_FAILED'].includes(item.status)).slice(0, 5)
    },
    autoPageStatusLine() {
      if (this.failureRows.length) {
        const latestFailure = this.latestFailureRow || {}
        return `${latestFailure.taskName || '最近自动调度'} 出现异常，建议先看失败结果，再决定是否需要补跑。`
      }
      if (!this.definitions.length) {
        return '当前没有启用中的自动触发计划，建议先检查调度定义与触发方式。'
      }
      return `系统下一次会按范围 ${this.nextAutoDispatch.defaultScope || '-'} 和时间范围 ${this.nextAutoDispatch.defaultTimeRange || '-'} 自动触发。`
    },
    latestHistorySummary() {
      if (!this.latestHistoryRow) {
        return '最近还没有自动触发结果，先刷新自动计划确认系统下一次会跑什么。'
      }
      return this.latestHistoryRow.resultSummary || this.latestHistoryRow.errorSummary || '进入调度详情查看最近自动结果。'
    },
    latestFailureSummary() {
      if (!this.latestFailureRow) {
        return '当前没有自动异常，异常记录会集中收在这里，避免首屏被完整历史挤满。'
      }
      return this.latestFailureRow.errorSummary || this.latestFailureRow.resultSummary || '进入调度详情查看失败原因。'
    },
    autoPrimaryLabel() {
      return this.latestHistoryRow && this.latestHistoryRow.jobId ? '查看最近自动结果' : '刷新自动计划'
    }
  },
  async created() {
    await this.loadPage()
  },
  methods: {
    async loadPage() {
      await Promise.all([this.loadDefinitions(), this.loadHistory()])
    },
    async loadDefinitions() {
      try {
        const response = await getDispatchDefinitions()
        this.definitions = Array.isArray(response.data)
          ? response.data.filter(item => Array.isArray(item.triggerModes) && item.triggerModes.includes('auto'))
          : []
      } catch (error) {
        this.$modal.msgError('读取自动调度定义失败')
      }
    },
    async loadHistory(payload = {}) {
      const pageNum = payload.page || this.pageNum || 1
      const pageSize = payload.limit || this.pageSize || 10
      try {
        const response = await getDispatchHistory({
          pageNum,
          pageSize,
          taskCode: this.taskCodeFilter || undefined,
          triggerMode: 'auto'
        })
        const data = response.data || {}
        this.historyRows = Array.isArray(data.rows) ? data.rows : []
        this.historyTotal = Number(data.total || 0)
        this.pageNum = pageNum
        this.pageSize = pageSize
      } catch (error) {
        this.$modal.msgError('读取自动调度历史失败')
      }
    },
    handleFilterHistory(taskCode) {
      this.taskCodeFilter = taskCode || ''
      this.loadHistory({ page: 1, limit: this.pageSize })
    },
    async handleAutoPrimaryAction() {
      if (this.latestHistoryRow && this.latestHistoryRow.jobId) {
        this.openDispatchDetail(this.latestHistoryRow)
        return
      }
      await this.loadPage()
    },
    openDispatchDetail(row) {
      if (!row || !row.jobId) {
        this.$modal.msgWarning('当前记录没有可查看的任务详情')
        return
      }
      this.$router.push({ path: `/quant/dispatch-detail/${row.jobId}` }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.dispatch-auto-page {
  padding: 4px;
  background:
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.08), transparent 28%),
    linear-gradient(180deg, #f8fbff 0, #ffffff 180px);
}

.dispatch-auto-header {
  margin-bottom: 16px;
  padding: 18px 20px;
  border-radius: 24px;
  background: linear-gradient(135deg, #fbfcff, #f8fffd);
  border: 1px solid #d9e7f7;
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
}

.dispatch-auto-header__main {
  min-width: 0;
}

.dispatch-auto-header__chips {
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

.dispatch-auto-header__badge {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.12);
  color: #115e59;
  font-size: 12px;
  font-weight: 700;
}

.dispatch-auto-header h1 {
  margin: 10px 0 8px;
  font-size: 30px;
  line-height: 1.35;
  color: #0f172a;
}

.dispatch-auto-topline {
  color: #475569;
  line-height: 1.7;
}

.dispatch-auto-guide {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.dispatch-auto-guide span {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid #d9e7ef;
  color: #334155;
  font-size: 12px;
}

.dispatch-auto-header__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.auto-summary-card {
  height: 100%;
  border-radius: 18px;
  border: 1px solid #dbe7f3;
  background: linear-gradient(180deg, #ffffff, #f8fbff);
}

.auto-summary-card--warn {
  background: linear-gradient(180deg, #fffdfa, #fff7ed);
}

.auto-summary-card__eyebrow {
  color: #64748b;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.auto-summary-card__title {
  margin-top: 8px;
  color: #0f172a;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.4;
}

.auto-summary-card__meta {
  margin-top: 8px;
  color: #475569;
  line-height: 1.7;
}

.auto-summary-card__copy {
  margin-top: 12px;
  color: #334155;
  line-height: 1.7;
}

.auto-summary-card__actions {
  margin-top: 14px;
}

.dispatch-auto-secondary-collapse {
  border-radius: 16px;
  overflow: hidden;
}

.collapse-title-shell {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
}

.section-meta {
  color: #64748b;
  font-size: 12px;
  font-weight: 400;
  flex-wrap: wrap;
}

.panel-title {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.panel-list {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel-item {
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fafc;
}

.panel-main {
  color: #0f172a;
  font-weight: 700;
}

.panel-meta {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.panel-copy,
.empty-copy {
  margin-top: 8px;
  color: #334155;
  line-height: 1.7;
}

.mt16 {
  margin-top: 16px;
}

@media (max-width: 1200px) {
  .dispatch-auto-header {
    flex-direction: column;
  }
}
</style>
