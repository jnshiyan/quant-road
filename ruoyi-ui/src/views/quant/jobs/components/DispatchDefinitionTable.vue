<template>
  <el-card shadow="never" class="dispatch-definition-card">
    <div slot="header" class="table-head">
      <div>
        <div class="eyebrow">调度定义</div>
        <div class="headline">系统会调度哪些任务模板</div>
      </div>
    </div>
    <el-table :data="items" size="small">
      <el-table-column prop="taskName" label="任务" min-width="160" />
      <el-table-column label="自动调度" width="100">
        <template slot-scope="{ row }">
          <el-tag size="mini" :type="row.autoEnabled ? 'success' : 'info'">{{ row.autoEnabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="触发方式" min-width="140">
        <template slot-scope="{ row }">
          <el-tag v-for="item in row.triggerModes || []" :key="`${row.taskCode}-${item}`" size="mini" class="mr6">{{ triggerModeLabel(item) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="cronExpression" label="Cron" min-width="140" />
      <el-table-column prop="defaultStrategy" label="默认策略" min-width="140" />
      <el-table-column prop="nextFireTime" label="下一次自动调度" min-width="160" />
      <el-table-column prop="defaultScope" label="默认范围" min-width="140" />
      <el-table-column prop="defaultTimeRange" label="默认时间范围" min-width="140" />
      <el-table-column prop="latestRunSummary" label="最近结果" min-width="160" />
      <el-table-column label="操作" width="110">
        <template slot-scope="{ row }">
          <el-button type="text" size="mini" @click="$emit('filter-history', row.taskCode)">查看历史</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script>
export default {
  name: 'DispatchDefinitionTable',
  props: {
    items: {
      type: Array,
      default: () => []
    }
  },
  methods: {
    triggerModeLabel(value) {
      if (value === 'auto') return '自动触发'
      if (value === 'recovery') return '补偿触发'
      return '手工触发'
    }
  }
}
</script>

<style scoped>
.table-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.mr6 {
  margin-right: 6px;
}
</style>
