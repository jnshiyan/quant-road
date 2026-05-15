<template>
  <el-card shadow="never" class="task-progress-timeline">
    <div class="card-head">
      <div class="eyebrow">系统进展</div>
      <div class="section-meta">最近步骤与当前运行事实</div>
    </div>

    <div class="columns">
      <div>
        <div class="section-title">最近步骤</div>
        <div v-if="events.length" class="event-list">
          <div v-for="(item, index) in events" :key="`${item.stepName || 'event'}-${index}`" class="event-item">
            <div class="event-step">{{ item.stepName || item.message || '事件' }}</div>
            <div class="event-meta">
              <span>{{ item.status || '-' }}</span>
              <span v-if="item.endTime">结束 {{ item.endTime }}</span>
              <span v-else-if="item.startTime">开始 {{ item.startTime }}</span>
            </div>
          </div>
        </div>
        <div v-else class="empty-note">暂无步骤进展，展开后再看系统更新。</div>
      </div>
      <div>
        <div class="section-title">当前运行事实</div>
        <div class="meta-list">
          <div v-for="item in primaryTaskFacts" :key="item.label" class="meta-item">
            <label>{{ item.label }}</label>
            <span>{{ item.value }}</span>
          </div>
        </div>
        <div class="section-title technical-title">技术摘要</div>
        <div v-if="technicalItems.length" class="meta-list">
          <div v-for="item in technicalItems" :key="item.label" class="meta-item">
            <label>{{ item.label }}</label>
            <span>{{ item.value }}</span>
          </div>
        </div>
        <div v-else class="empty-note">暂无技术摘要，展开后再看运行细节。</div>
      </div>
    </div>
  </el-card>
</template>

<script>
export default {
  name: 'TaskProgressTimeline',
  props: {
    events: {
      type: Array,
      default: () => []
    },
    technicalSummary: {
      type: Array,
      default: () => []
    },
    primaryTask: {
      type: Object,
      default: () => ({})
    }
  },
  computed: {
    technicalItems() {
      return Array.isArray(this.technicalSummary) ? this.technicalSummary : []
    },
    primaryTaskFacts() {
      const task = this.primaryTask || {}
      return [
        { label: '当前运行什么', value: task.taskName || '当前无运行任务' },
        { label: '当前步骤', value: task.currentStep || '等待开始' },
        { label: '当前范围', value: task.scopeSummary || '未指定范围' },
        { label: '时间范围', value: task.timeRangeSummary || '未指定时间范围' },
        { label: '下一步', value: task.nextStep || '等待系统更新' }
      ]
    }
  }
}
</script>

<style scoped>
.task-progress-timeline {
  border-radius: 16px;
  border: 1px solid #e5eaf3;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.eyebrow {
  color: #1f2d3d;
  font-size: 16px;
  font-weight: 600;
}

.section-meta {
  color: #909399;
  font-size: 12px;
}

.columns {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.section-title {
  margin-bottom: 10px;
  font-weight: 600;
  color: #303133;
}

.technical-title {
  margin-top: 18px;
}

.event-list,
.meta-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.event-item,
.meta-item {
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
}

.event-step,
.meta-item span {
  color: #303133;
}

.event-meta {
  margin-top: 6px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  color: #909399;
  font-size: 12px;
}

.meta-item label {
  display: block;
  color: #909399;
  font-size: 12px;
}

.empty-note {
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
  color: #64748b;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .columns {
    grid-template-columns: 1fr;
  }
}
</style>
