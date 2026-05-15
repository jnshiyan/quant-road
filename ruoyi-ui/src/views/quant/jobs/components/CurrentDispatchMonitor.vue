<template>
  <el-card shadow="never" class="current-dispatch-monitor">
    <div slot="header" class="monitor-header">
      <div>
        <div class="eyebrow">当前运行明细</div>
        <div class="headline">等待 / 分片 / 结果</div>
      </div>
      <div class="monitor-actions">
        <span class="refresh-meta">最近刷新 {{ refreshedAt || '-' }}</span>
        <el-switch
          v-model="autoRefreshProxy"
          active-text="自动刷新"
          inactive-text="手动刷新"
        />
        <el-button size="mini" icon="el-icon-refresh" @click="$emit('refresh')">立即刷新</el-button>
      </div>
    </div>

    <el-empty
      v-if="!jobId"
      description="当前没有可追踪的运行中调度。系统一旦进入排队或运行，这里会自动显示分片与结果反馈。"
    />

    <div v-else>
      <div class="summary-grid">
        <div class="summary-item">
          <label>任务 ID</label>
          <span>{{ jobId }}</span>
        </div>
        <div class="summary-item">
          <label>当前状态</label>
          <span>
            <el-tag size="mini" :type="statusType">{{ statusText }}</el-tag>
          </span>
        </div>
        <div class="summary-item">
          <label>系统判定</label>
          <span>{{ dispatchText }}</span>
        </div>
        <div class="summary-item">
          <label>分片进度</label>
          <span>{{ progressText }}</span>
        </div>
        <div class="summary-item">
          <label>当前等待</label>
          <span>{{ waitingFor || '-' }}</span>
        </div>
        <div class="summary-item">
          <label>重点提醒</label>
          <span>{{ hintText }}</span>
        </div>
      </div>

      <el-alert
        v-if="showSyncHint"
        class="mt16"
        title="当前任务是同步执行模式，不会产生异步分片表；请结合上方运行反馈和当前状态理解进展。"
        type="info"
        :closable="false"
        show-icon
      />

      <div class="insight-grid mt16">
        <div class="insight-card">
          <div class="insight-title">状态变化</div>
          <div class="insight-caption">最近一次变化 {{ lastChangeAt || '暂无' }}</div>
          <div v-if="changeHighlights.length" class="insight-list mt12">
            <div v-for="(item, index) in changeHighlights" :key="`${item}-${index}`" class="insight-item insight-item--accent">
              <div class="insight-main">{{ item }}</div>
            </div>
          </div>
          <el-empty v-else :image-size="48" description="当前还没有检测到新的状态变化" />
        </div>

        <div class="insight-card">
          <div class="insight-title">运行线索</div>
          <div v-if="focusEvents.length" class="insight-list">
            <div v-for="(item, index) in focusEvents" :key="`${item.stepName || 'step'}-${index}`" class="insight-item">
              <div class="insight-main">{{ item.stepName || item.message || '系统事件' }}</div>
              <div class="insight-meta">
                {{ item.status || '-' }}
                <span v-if="item.endTime"> · {{ item.endTime }}</span>
                <span v-else-if="item.startTime"> · {{ item.startTime }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else :image-size="48" description="暂无步骤线索" />
        </div>

        <div class="insight-card">
          <div class="insight-title">错误焦点</div>
          <div v-if="focusErrors.length" class="insight-list">
            <div v-for="(item, index) in focusErrors" :key="`${item.category || 'error'}-${index}`" class="insight-item">
              <div class="insight-main">{{ item.category || '未知错误' }}</div>
              <div class="insight-meta">出现 {{ item.count || 0 }} 次</div>
            </div>
          </div>
          <el-empty v-else :image-size="48" description="当前没有聚合错误" />
        </div>

        <div class="insight-card">
          <div class="insight-title">活跃 Worker</div>
          <div v-if="activeWorkers.length" class="insight-list">
            <div v-for="item in activeWorkers" :key="item.workerId" class="insight-item">
              <div class="insight-main">{{ item.workerId }}</div>
              <div class="insight-meta">运行中分片 {{ item.runningShardCount }}</div>
            </div>
          </div>
          <el-empty v-else :image-size="48" description="当前没有活跃 worker" />
        </div>
      </div>

      <job-shard-table v-if="!showSyncHint" class="mt16" :rows="focusShards" />
      <job-result-table class="mt16" :rows="focusResults" />
    </div>
  </el-card>
</template>

<script>
import ElAlert from 'element-ui/lib/alert'
import 'element-ui/lib/theme-chalk/alert.css'
import JobResultTable from './JobResultTable'
import JobShardTable from './JobShardTable'

export default {
  name: 'CurrentDispatchMonitor',
  components: {
    ElAlert,
    JobResultTable,
    JobShardTable
  },
  data() {
    return {
      changeHighlights: [],
      lastChangeAt: '',
      previousSnapshot: null
    }
  },
  props: {
    jobId: {
      type: [Number, String],
      default: undefined
    },
    jobStatus: {
      type: Object,
      default: null
    },
    shards: {
      type: Array,
      default: () => []
    },
    results: {
      type: Array,
      default: () => []
    },
    events: {
      type: Array,
      default: () => []
    },
    errorCategories: {
      type: Array,
      default: () => []
    },
    autoRefresh: {
      type: Boolean,
      default: true
    },
    refreshedAt: {
      type: String,
      default: ''
    },
    waitingFor: {
      type: String,
      default: ''
    }
  },
  computed: {
    autoRefreshProxy: {
      get() {
        return this.autoRefresh
      },
      set(value) {
        this.$emit('update:auto-refresh', value)
      }
    },
    statusText() {
      return this.jobStatus && this.jobStatus.status ? this.jobStatus.status : 'IDLE'
    },
    statusType() {
      if (this.statusText === 'SUCCESS') return 'success'
      if (this.statusText === 'FAILED' || this.statusText === 'PARTIAL_FAILED') return 'danger'
      if (['RUNNING', 'QUEUED', 'PENDING'].includes(this.statusText)) return 'warning'
      return 'info'
    },
    progressText() {
      const completed = this.jobStatus && this.jobStatus.completedShardCount ? this.jobStatus.completedShardCount : 0
      const planned = this.jobStatus && this.jobStatus.plannedShardCount ? this.jobStatus.plannedShardCount : 0
      return `${completed}/${planned}`
    },
    dispatchText() {
      const resolved = this.jobStatus && (this.jobStatus.resolvedMode || this.jobStatus.resolvedExecutionMode)
      if (!resolved) {
        return '等待系统判定'
      }
      if (resolved === 'async') {
        return '异步队列执行'
      }
      if (resolved === 'sync') {
        return '同步直接执行'
      }
      return resolved
    },
    showSyncHint() {
      const resolved = this.jobStatus && (this.jobStatus.resolvedMode || this.jobStatus.resolvedExecutionMode)
      return resolved === 'sync'
    },
    hintText() {
      if (this.statusText === 'FAILED' || this.statusText === 'PARTIAL_FAILED') {
        return this.jobStatus && this.jobStatus.errorMessage ? this.jobStatus.errorMessage : '存在失败分片，请查看下方明细。'
      }
      if (this.statusText === 'SUCCESS') {
        return '当前调度已完成，可继续看调度历史或量化看板。'
      }
      return '保持页面停留即可，系统会持续刷新当前执行反馈。'
    },
    focusShards() {
      const rows = Array.isArray(this.shards) ? this.shards.slice() : []
      const priority = {
        RUNNING: 0,
        PENDING: 1,
        QUEUED: 2,
        FAILED: 3,
        PARTIAL_FAILED: 4,
        SUCCESS: 5
      }
      return rows
        .sort((left, right) => {
          const leftKey = priority[left.status] ?? 99
          const rightKey = priority[right.status] ?? 99
          if (leftKey !== rightKey) {
            return leftKey - rightKey
          }
          return Number(left.shard_index || 0) - Number(right.shard_index || 0)
        })
        .slice(0, 12)
    },
    focusResults() {
      const rows = Array.isArray(this.results) ? this.results : []
      return rows.slice(0, 20)
    },
    focusEvents() {
      const rows = Array.isArray(this.events) ? this.events : []
      return rows.slice(0, 6)
    },
    focusErrors() {
      const rows = Array.isArray(this.errorCategories) ? this.errorCategories : []
      return rows.slice(0, 6)
    },
    activeWorkers() {
      const counters = new Map()
      for (const row of Array.isArray(this.shards) ? this.shards : []) {
        if (row.status !== 'RUNNING' || !row.lease_owner) {
          continue
        }
        const current = counters.get(row.lease_owner) || 0
        counters.set(row.lease_owner, current + 1)
      }
      return Array.from(counters.entries()).map(([workerId, runningShardCount]) => ({
        workerId,
        runningShardCount
      }))
    },
    snapshotSignature() {
      return JSON.stringify({
        jobId: this.jobId || null,
        status: this.jobStatus && this.jobStatus.status,
        completedShardCount: this.jobStatus && this.jobStatus.completedShardCount,
        failedShardCount: this.jobStatus && this.jobStatus.failedShardCount,
        plannedShardCount: this.jobStatus && this.jobStatus.plannedShardCount,
        runningWorkers: this.activeWorkers,
        resultCount: Array.isArray(this.results) ? this.results.length : 0,
        errorCategories: this.focusErrors
      })
    }
  },
  watch: {
    jobId(newValue, oldValue) {
      if (String(newValue || '') === String(oldValue || '')) {
        return
      }
      this.previousSnapshot = null
      this.changeHighlights = []
      this.lastChangeAt = ''
    },
    snapshotSignature() {
      this.reconcileSnapshot()
    }
  },
  methods: {
    buildSnapshot() {
      return {
        status: this.jobStatus && this.jobStatus.status ? this.jobStatus.status : 'IDLE',
        completedShardCount: Number(this.jobStatus && this.jobStatus.completedShardCount ? this.jobStatus.completedShardCount : 0),
        failedShardCount: Number(this.jobStatus && this.jobStatus.failedShardCount ? this.jobStatus.failedShardCount : 0),
        plannedShardCount: Number(this.jobStatus && this.jobStatus.plannedShardCount ? this.jobStatus.plannedShardCount : 0),
        resultCount: Array.isArray(this.results) ? this.results.length : 0,
        runningWorkers: this.activeWorkers.map(item => item.workerId),
        topError: this.focusErrors.length ? this.focusErrors[0].category || '' : ''
      }
    },
    reconcileSnapshot() {
      if (!this.jobId) {
        this.previousSnapshot = null
        this.changeHighlights = []
        this.lastChangeAt = ''
        return
      }
      const nextSnapshot = this.buildSnapshot()
      if (!this.previousSnapshot) {
        this.previousSnapshot = nextSnapshot
        return
      }
      const highlights = []
      if (this.previousSnapshot.status !== nextSnapshot.status) {
        highlights.push(`状态从 ${this.previousSnapshot.status} 变为 ${nextSnapshot.status}`)
      }
      if (nextSnapshot.completedShardCount > this.previousSnapshot.completedShardCount) {
        highlights.push(`新增完成分片 ${nextSnapshot.completedShardCount - this.previousSnapshot.completedShardCount} 个`)
      }
      if (nextSnapshot.failedShardCount > this.previousSnapshot.failedShardCount) {
        highlights.push(`新增失败分片 ${nextSnapshot.failedShardCount - this.previousSnapshot.failedShardCount} 个`)
      }
      if (nextSnapshot.resultCount > this.previousSnapshot.resultCount) {
        highlights.push(`新增结果 ${nextSnapshot.resultCount - this.previousSnapshot.resultCount} 条`)
      }
      const newWorkers = nextSnapshot.runningWorkers.filter(item => !this.previousSnapshot.runningWorkers.includes(item))
      if (newWorkers.length) {
        highlights.push(`新增活跃 Worker：${newWorkers.join(', ')}`)
      }
      if (nextSnapshot.topError && nextSnapshot.topError !== this.previousSnapshot.topError) {
        highlights.push(`错误焦点变为：${nextSnapshot.topError}`)
      }
      if (highlights.length) {
        this.changeHighlights = highlights.slice(0, 6)
        this.lastChangeAt = new Date().toLocaleString()
      }
      this.previousSnapshot = nextSnapshot
    }
  }
}
</script>

<style scoped>
.monitor-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.monitor-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.refresh-meta {
  color: #64748b;
  font-size: 12px;
}

.eyebrow {
  color: #6b7280;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.headline {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.insight-card {
  padding: 14px;
  border-radius: 14px;
  background: #f8fafc;
}

.insight-title {
  margin-bottom: 10px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.insight-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.insight-caption {
  color: #64748b;
  font-size: 12px;
}

.insight-item {
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
}

.insight-item--accent {
  border: 1px solid #c7e0ff;
  background: linear-gradient(180deg, #f8fbff, #eef6ff);
}

.insight-main {
  color: #0f172a;
  line-height: 1.5;
}

.insight-meta {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.summary-item {
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-item label {
  color: #64748b;
  font-size: 12px;
}

.summary-item span {
  color: #0f172a;
  line-height: 1.6;
  word-break: break-word;
}

.mt16 {
  margin-top: 16px;
}

.mt12 {
  margin-top: 12px;
}

@media (max-width: 1200px) {
  .monitor-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-grid {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }

  .insight-grid {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }
}
</style>
