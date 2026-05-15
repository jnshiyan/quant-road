<template>
  <el-card shadow="never" class="dispatch-history-card">
    <div slot="header" class="table-head">
      <div>
        <div class="eyebrow">调度历史</div>
        <div class="headline">全部调度历史</div>
      </div>
      <el-tag size="mini" type="info">共 {{ total || 0 }} 条</el-tag>
    </div>
    <el-table :data="rows" size="small">
      <el-table-column prop="startedAt" label="触发时间" min-width="160" />
      <el-table-column prop="taskName" label="任务" min-width="140" />
      <el-table-column prop="triggerModeLabel" label="触发方式" width="110" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="scopeSummary" label="范围" min-width="150" />
      <el-table-column prop="timeRangeSummary" label="时间范围" min-width="150" />
      <el-table-column prop="resultSummary" label="结果摘要" min-width="180" />
      <el-table-column label="操作" width="100">
        <template slot-scope="{ row }">
          <el-button type="text" size="mini" @click="$emit('view-detail', row)">查看详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="localPage"
      :limit.sync="localLimit"
      @pagination="handlePagination"
    />
  </el-card>
</template>

<script>
export default {
  name: 'DispatchHistoryTable',
  props: {
    rows: {
      type: Array,
      default: () => []
    },
    total: {
      type: Number,
      default: 0
    },
    page: {
      type: Number,
      default: 1
    },
    limit: {
      type: Number,
      default: 10
    }
  },
  data() {
    return {
      localPage: this.page,
      localLimit: this.limit
    }
  },
  watch: {
    page(value) {
      this.localPage = value
    },
    limit(value) {
      this.localLimit = value
    }
  },
  methods: {
    handlePagination(payload) {
      this.$emit('change', payload)
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
</style>
