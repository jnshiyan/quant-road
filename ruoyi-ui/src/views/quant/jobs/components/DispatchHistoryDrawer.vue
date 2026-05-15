<template>
  <el-drawer :visible.sync="visibleProxy" size="72%" direction="rtl" append-to-body>
    <div slot="title" class="drawer-title">
      <div class="eyebrow">调度详情</div>
      <div class="headline">{{ record.taskName || '调度记录' }}</div>
    </div>
    <div class="drawer-body">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="触发时间">{{ record.startedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ record.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="触发方式">{{ record.triggerModeLabel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="触发源">{{ record.triggerSource || '-' }}</el-descriptions-item>
        <el-descriptions-item label="范围">{{ record.scopeSummary || '-' }}</el-descriptions-item>
        <el-descriptions-item label="时间范围">{{ record.timeRangeSummary || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Job ID">{{ record.jobId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次 ID">{{ record.batchId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果摘要" :span="2">{{ record.resultSummary || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误摘要" :span="2">{{ record.errorSummary || '无' }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="!record.jobId"
        class="mt16"
        title="当前记录来自自动调度日志，暂无异步分片明细。"
        type="info"
        :closable="false"
        show-icon
      />

      <job-shard-table v-if="record.jobId" class="mt16" :rows="shards" />
      <job-result-table v-if="record.jobId" class="mt16" :rows="results" />
    </div>
  </el-drawer>
</template>

<script>
import ElAlert from 'element-ui/lib/alert'
import ElDrawer from 'element-ui/lib/drawer'
import 'element-ui/lib/theme-chalk/alert.css'
import 'element-ui/lib/theme-chalk/drawer.css'
import JobShardTable from './JobShardTable'
import JobResultTable from './JobResultTable'

export default {
  name: 'DispatchHistoryDrawer',
  components: {
    ElAlert,
    ElDrawer,
    JobShardTable,
    JobResultTable
  },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    record: {
      type: Object,
      default: () => ({})
    },
    shards: {
      type: Array,
      default: () => []
    },
    results: {
      type: Array,
      default: () => []
    }
  },
  computed: {
    visibleProxy: {
      get() {
        return this.visible
      },
      set(value) {
        this.$emit('update:visible', value)
      }
    }
  }
}
</script>

<style scoped>
.drawer-title .eyebrow {
  color: #6b7280;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.drawer-title .headline {
  margin-top: 8px;
  color: #0f172a;
  font-size: 22px;
  font-weight: 700;
}

.drawer-body {
  padding: 0 12px 24px;
}

.mt16 {
  margin-top: 16px;
}
</style>
