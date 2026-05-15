<template>
  <el-card shadow="never" class="today-status-card">
    <div class="card-head">
      <div>
        <div class="eyebrow">今日结论</div>
        <div class="headline">{{ headlineAction }}</div>
        <div class="headline-meta">
          <span class="headline-meta__label">建议动作</span>
          <strong>{{ continueLabel }}</strong>
        </div>
      </div>
      <el-tag size="mini" :type="statusType">{{ statusLabel }}</el-tag>
    </div>

    <div class="decision-grid">
      <div class="decision-card" :class="{ 'decision-card--blocked': !canContinue }">
        <div class="decision-card__value">{{ statusReason }}</div>
      </div>

      <div class="decision-card decision-card--secondary" v-if="statusSuggestion">
        <div class="decision-card__value">{{ statusSuggestion }}</div>
      </div>
    </div>

    <div class="action-row">
      <el-button type="primary" size="small" @click="$emit('run-primary')">
        {{ primaryActionLabel }}
      </el-button>
      <el-button
        v-if="secondaryActionLabel"
        plain
        size="small"
        :disabled="!canContinue"
        @click="$emit('run-secondary')"
      >
        {{ secondaryActionLabel }}
      </el-button>
    </div>
  </el-card>
</template>

<script>
export default {
  name: 'TodayStatusCard',
  props: {
    summary: {
      type: Object,
      default: () => ({})
    },
    primaryAction: {
      type: Object,
      default: () => ({})
    },
    secondaryAction: {
      type: Object,
      default: null
    }
  },
  computed: {
    todayStatus() {
      return (this.summary && (this.summary.todayStatus || this.summary)) || {}
    },
    headlineAction() {
      return this.todayStatus.headlineAction || this.todayStatus.todayStatusHeadlineAction || this.primaryActionLabel || this.statusLabel || '请先确认状态'
    },
    statusLabel() {
      return this.todayStatus.statusLabel || this.todayStatus.todayStatusLabel || this.todayStatus.label || this.todayStatus.statusCode || this.todayStatus.code || '警告'
    },
    canContinue() {
      if (Object.prototype.hasOwnProperty.call(this.todayStatus, 'todayStatusCanContinue')) {
        return this.todayStatus.todayStatusCanContinue !== false
      }
      return this.todayStatus.canContinue !== false
    },
    continueLabel() {
      return this.todayStatus.continueLabel || this.todayStatus.todayStatusContinueLabel || '可以继续查看'
    },
    statusReason() {
      return this.todayStatus.reason || this.todayStatus.todayStatusReason || '暂无今日状态说明'
    },
    statusSuggestion() {
      return this.todayStatus.suggestion || this.todayStatus.todayStatusSuggestion || ''
    },
    primaryActionLabel() {
      return this.primaryAction && this.primaryAction.label ? this.primaryAction.label : '查看建议动作'
    },
    secondaryActionLabel() {
      return this.secondaryAction && this.secondaryAction.label ? this.secondaryAction.label : ''
    },
    statusType() {
      const code = this.todayStatus.statusCode || this.todayStatus.todayStatusCode || this.todayStatus.code
      if (code === 'OPERABLE') return 'success'
      if (code === 'BLOCKED') return 'danger'
      if (code === 'RUNNING') return 'info'
      return 'warning'
    }
  }
}
</script>

<style scoped>
.today-status-card {
  border-radius: 18px;
  border: 1px solid #dfe7ef;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.eyebrow {
  color: #6b7280;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.headline {
  margin-top: 8px;
  font-size: 30px;
  font-weight: 700;
  color: #111827;
  line-height: 1.4;
}

.headline-meta {
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, #ecfdf5, #f0fdf4);
  border: 1px solid #86efac;
}

.headline-meta__label {
  display: block;
  font-size: 12px;
  color: #4b5563;
}

.headline-meta strong {
  display: block;
  margin-top: 6px;
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}

.decision-grid {
  margin-top: 16px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.decision-card {
  padding: 14px 16px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #e6edf5;
}

.decision-card--blocked {
  background: #fff7ed;
  border-color: #fdba74;
}

.decision-card--secondary {
  background: linear-gradient(135deg, #f8fafc, #eef6ff);
}

.decision-card__value {
  color: #1f2937;
  line-height: 1.8;
}

.action-row {
  margin-top: 16px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
