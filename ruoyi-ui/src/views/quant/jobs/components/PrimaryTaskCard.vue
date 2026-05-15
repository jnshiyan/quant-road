<template>
  <el-card shadow="never" class="primary-task-card">
    <div v-if="statusLine" class="primary-task-card__status-line">
      <el-tag size="mini" :type="statusType">{{ statusLabel }}</el-tag>
      <span>{{ statusLine }}</span>
    </div>

    <div class="card-head">
      <div class="card-head__main">
        <div class="eyebrow">当前调度摘要</div>
        <div class="task-name">{{ task.taskName || '当前无运行任务' }}</div>
        <div v-if="primaryHint" class="primary-hint">
          {{ primaryHint }}
        </div>
      </div>
      <div class="card-head__side">
        <el-tag size="mini" :type="statusType">{{ statusLabel }}</el-tag>
        <div class="progress-strip">
          <span>步骤进度</span>
          <strong>{{ task.progressSummary || '0 / 0' }}</strong>
        </div>
      </div>
    </div>

    <div v-if="isIdleState" class="idle-summary-strip">
      <div class="idle-summary-pill">
        <label>下一步</label>
        <strong>直接发起手工调度</strong>
      </div>
      <div class="idle-summary-pill">
        <label>历史</label>
        <strong>最近 3 条结果在下方</strong>
      </div>
      <div class="idle-summary-pill">
        <label>自动</label>
        <strong>自动计划收进折叠区</strong>
      </div>
    </div>

    <div v-else class="fact-grid">
      <div class="fact-item">
        <label>当前步骤</label>
        <strong>{{ task.currentStep || '-' }}</strong>
      </div>
      <div class="fact-item">
        <label>下一步</label>
        <strong>{{ task.nextStep || '-' }}</strong>
      </div>
      <div class="fact-item">
        <label>当前范围</label>
        <strong>{{ task.scopeSummary || '-' }}</strong>
      </div>
      <div class="fact-item">
        <label>时间范围</label>
        <strong>{{ task.timeRangeSummary || '-' }}</strong>
      </div>
      <div class="fact-item">
        <label>触发方式</label>
        <strong>{{ task.triggerModeLabel || '-' }}</strong>
      </div>
      <div class="fact-item">
        <label>当前状态</label>
        <strong>{{ statusLabel }}</strong>
      </div>
    </div>

    <div class="primary-task-card__actions">
      <el-button
        type="primary"
        size="small"
        :loading="pendingActionKey === (primaryAction.code || '')"
        @click="$emit('run-primary', primaryAction)"
      >
        {{ primaryAction.label || '刷新状态' }}
      </el-button>
      <el-button
        v-for="item in secondaryActions"
        :key="item.code"
        type="text"
        size="small"
        :disabled="pendingActionKey === item.code"
        @click="$emit('run-secondary', item)"
      >
        {{ item.label }}
      </el-button>
    </div>

    <div v-if="actionFeedback" class="feedback-box">
      <div class="feedback-title">{{ actionFeedback.message }}</div>
      <div v-if="actionFeedback.nextStep" class="feedback-next">{{ actionFeedback.nextStep }}</div>
    </div>

    <div v-if="task.requiresManualIntervention" class="task-warning">
      当前任务需要人工介入，建议进入调度详情或运维中心继续处理。
    </div>
  </el-card>
</template>

<script>
export default {
  name: 'PrimaryTaskCard',
  props: {
    primaryTask: {
      type: Object,
      default: () => ({})
    },
    statusLine: {
      type: String,
      default: ''
    },
    primaryHint: {
      type: String,
      default: ''
    },
    primaryAction: {
      type: Object,
      default: () => ({})
    },
    secondaryActions: {
      type: Array,
      default: () => []
    },
    pendingActionKey: {
      type: String,
      default: ''
    },
    actionFeedback: {
      type: Object,
      default: null
    }
  },
  computed: {
    task() {
      return this.primaryTask || {}
    },
    isIdleState() {
      return this.task.status === 'IDLE'
    },
    statusType() {
      const status = this.task.status || ''
      if (status === 'SUCCESS') return 'success'
      if (status === 'FAILED' || status === 'PARTIAL_FAILED') return 'danger'
      if (status === 'RUNNING' || status === 'QUEUED' || status === 'PENDING') return 'warning'
      return 'info'
    },
    statusLabel() {
      const status = this.task.status || ''
      if (status === 'SUCCESS') return '已完成'
      if (status === 'FAILED') return '失败'
      if (status === 'PARTIAL_FAILED') return '部分失败'
      if (status === 'RUNNING') return '运行中'
      if (status === 'QUEUED') return '排队中'
      if (status === 'PENDING') return '待开始'
      if (status === 'IDLE') return '空闲'
      return status || '未知'
    }
  }
}
</script>

<style scoped>
.primary-task-card {
  border-radius: 22px;
  border: 1px solid #dce7ef;
  background: linear-gradient(180deg, #ffffff, #f8fbff);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.05);
}

.primary-task-card__status-line {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f8fffd, #eef6ff);
  border: 1px solid #d9e7ef;
  color: #334155;
  line-height: 1.6;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-top: 16px;
}

.card-head__main {
  flex: 1;
  min-width: 0;
}

.card-head__side {
  width: 240px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.eyebrow {
  color: #64748b;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 700;
}

.task-name {
  margin-top: 8px;
  font-size: 32px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.4;
}

.primary-hint {
  margin-top: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.95);
  border: 1px solid #e2e8f0;
  color: #475569;
  line-height: 1.7;
}

.progress-strip {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 14px 16px;
  border-radius: 16px;
  background: #0f172a;
  border: 1px solid #0f172a;
  color: #cbd5e1;
}

.progress-strip strong {
  color: #ffffff;
  font-size: 20px;
}

.fact-grid {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.idle-summary-strip {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.idle-summary-pill {
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  border: 1px solid #e2e8f0;
}

.idle-summary-pill label {
  display: block;
  color: #94a3b8;
  font-size: 12px;
}

.idle-summary-pill strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  line-height: 1.6;
}

.fact-item {
  padding: 14px 16px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.fact-item label {
  display: block;
  color: #94a3b8;
  font-size: 12px;
}

.fact-item strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  line-height: 1.6;
}

.primary-task-card__actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding-top: 4px;
}

.feedback-box {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f8fafc, #eef6ff);
  border: 1px solid #dbe7f3;
}

.feedback-title {
  color: #0f172a;
  font-weight: 600;
}

.feedback-next {
  margin-top: 8px;
  color: #334155;
  line-height: 1.7;
}

.task-warning {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  color: #c2410c;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .fact-grid {
    grid-template-columns: 1fr;
  }

  .idle-summary-strip {
    grid-template-columns: 1fr;
  }

  .card-head {
    flex-direction: column;
  }

  .card-head__side {
    width: 100%;
    align-items: stretch;
  }
}
</style>
