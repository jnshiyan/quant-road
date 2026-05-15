<template>
  <el-card shadow="never" class="task-action-panel">
    <div class="panel-head">
      <div>
        <div class="eyebrow">主操作</div>
        <div class="headline">{{ primaryAction.label || '刷新状态' }}</div>
      </div>
      <el-button
        type="primary"
        :loading="pendingActionKey === (primaryAction.code || '')"
        @click="$emit('run-primary', primaryAction)"
      >
        {{ primaryAction.label || '刷新状态' }}
      </el-button>
    </div>
    <div class="secondary-actions">
      <el-button
        v-for="item in secondaryActions"
        :key="item.code"
        plain
        :loading="pendingActionKey === item.code"
        @click="$emit('run-secondary', item)"
      >
        {{ item.label }}
      </el-button>
    </div>
    <div v-if="actionFeedback" class="feedback-box">
      <div class="feedback-title">{{ actionFeedback.message }}</div>
      <div class="feedback-meta">
        <span v-if="actionFeedback.jobId">任务 ID {{ actionFeedback.jobId }}</span>
        <span v-if="actionFeedback.mode">模式 {{ actionFeedback.mode }}</span>
        <span v-if="actionFeedback.startedAt">开始于 {{ actionFeedback.startedAt }}</span>
      </div>
      <div v-if="actionFeedback.nextStep" class="feedback-next">{{ actionFeedback.nextStep }}</div>
    </div>
  </el-card>
</template>

<script>
export default {
  name: 'TaskActionPanel',
  props: {
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
  }
}
</script>

<style scoped>
.task-action-panel {
  border-radius: 18px;
  border: 1px solid #dfe7ef;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.eyebrow {
  color: #6b7280;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.headline {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 700;
  color: #111827;
}

.secondary-actions {
  margin-top: 14px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.feedback-box {
  margin-top: 16px;
  padding: 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f8fafc, #eef6ff);
}

.feedback-title {
  color: #0f172a;
  font-weight: 600;
}

.feedback-meta {
  margin-top: 8px;
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  color: #475569;
  font-size: 12px;
}

.feedback-next {
  margin-top: 10px;
  color: #334155;
}
</style>
