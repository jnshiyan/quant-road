<template>
  <el-card shadow="never" class="job-status-card">
    <div slot="header" class="clearfix">
      <span>当前任务</span>
      <div class="header-actions">
        <el-tag size="mini" :type="statusType">{{ statusText }}</el-tag>
        <el-button size="mini" icon="el-icon-refresh" @click="$emit('refresh')">刷新</el-button>
        <el-button
          v-if="jobId"
          size="mini"
          type="warning"
          plain
          @click="$emit('retry')"
        >重试失败分片</el-button>
        <el-button
          v-if="jobId && canCancel"
          size="mini"
          type="danger"
          plain
          @click="$emit('cancel')"
        >取消任务</el-button>
      </div>
    </div>

    <el-empty v-if="!jobStatus" description="提交执行任务后会展示进度" />
    <div v-else class="job-grid">
      <div class="job-item">
        <label>任务 ID</label>
        <span>{{ jobId || '-' }}</span>
      </div>
      <div class="job-item">
        <label>状态</label>
        <span>{{ jobStatus.status || '-' }}</span>
      </div>
      <div class="job-item">
        <label>系统调度</label>
        <span>{{ dispatchText }}</span>
      </div>
      <div class="job-item">
        <label>进度</label>
        <span>{{ progressText }}</span>
      </div>
      <div class="job-item">
        <label>失败分片</label>
        <span>{{ jobStatus.failedShardCount || 0 }}</span>
      </div>
      <div class="job-item wide">
        <label>当前对象</label>
        <span>{{ jobStatus.currentObjectLabel || '-' }}</span>
      </div>
      <div class="job-item wide">
        <label>下一步</label>
        <span>{{ jobStatus.nextStepLabel || '-' }}</span>
      </div>
      <div class="job-item wide">
        <label>错误</label>
        <span>{{ jobStatus.errorMessage || '-' }}</span>
      </div>
    </div>
  </el-card>
</template>

<script>
export default {
  name: 'JobStatusCard',
  props: {
    jobId: {
      type: [Number, String],
      default: undefined
    },
    jobStatus: {
      type: Object,
      default: null
    }
  },
  computed: {
    statusText() {
      return this.jobStatus && this.jobStatus.status ? this.jobStatus.status : 'IDLE'
    },
    statusType() {
      const status = this.statusText
      if (status === 'SUCCESS') return 'success'
      if (status === 'FAILED' || status === 'PARTIAL_FAILED') return 'danger'
      if (status === 'RUNNING' || status === 'QUEUED' || status === 'PENDING') return 'warning'
      return 'info'
    },
    progressText() {
      if (!this.jobStatus) {
        return '-'
      }
      const completed = this.jobStatus.completedShardCount || 0
      const planned = this.jobStatus.plannedShardCount || 0
      return `${completed}/${planned}`
    },
    dispatchText() {
      if (!this.jobStatus) {
        return '-'
      }
      const resolved = this.jobStatus.resolvedMode || this.jobStatus.resolvedExecutionMode
      if (!resolved) {
        return '等待系统判定'
      }
      if (resolved === 'async') {
        return '系统判定为异步队列执行'
      }
      if (resolved === 'sync') {
        return '系统判定为直接执行'
      }
      return resolved
    },
    canCancel() {
      const status = this.jobStatus && this.jobStatus.status
      return ['QUEUED', 'PENDING', 'RUNNING'].includes(status)
    }
  }
}
</script>

<style scoped>
.header-actions {
  float: right;
  display: flex;
  gap: 8px;
  align-items: center;
}

.job-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 18px;
}

.job-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.job-item label {
  color: #909399;
  font-size: 12px;
}

.job-item span {
  color: #303133;
  word-break: break-word;
}

.wide {
  grid-column: 1 / -1;
}
</style>
