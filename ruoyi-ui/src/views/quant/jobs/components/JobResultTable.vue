<template>
  <el-card shadow="never" class="job-result-table-card" :body-style="cardBodyStyle">
    <div v-if="showHeader" slot="header" class="clearfix">
      <span>结果明细</span>
    </div>
    <el-table v-if="rows.length" :data="rows" border :size="tableSize" stripe>
      <el-table-column label="股票代码" prop="stock_code" width="110" />
      <el-table-column label="策略ID" prop="strategy_id" width="100" />
      <el-table-column label="信号" prop="signal_type" width="90" />
      <el-table-column label="年化收益" prop="annual_return" width="110" />
      <el-table-column label="最大回撤" prop="max_drawdown" width="110" />
      <el-table-column label="胜率" prop="win_rate" width="90" />
      <el-table-column label="交易次数" prop="trade_count" width="90" />
      <el-table-column label="总成本" prop="total_cost" width="100" />
      <el-table-column label="备注" prop="remark" min-width="180" show-overflow-tooltip />
    </el-table>
    <div v-else class="table-empty">当前任务还没有结果明细</div>
  </el-card>
</template>

<script>
export default {
  name: 'JobResultTable',
  props: {
    rows: {
      type: Array,
      default: () => []
    },
    compact: {
      type: Boolean,
      default: false
    },
    showHeader: {
      type: Boolean,
      default: true
    }
  },
  computed: {
    tableSize() {
      return this.compact ? 'mini' : 'medium'
    },
    cardBodyStyle() {
      return this.compact ? { padding: '0' } : null
    }
  }
}
</script>

<style scoped>
.table-empty {
  padding: 14px 16px;
  color: #606266;
  line-height: 1.6;
}

.job-result-table-card ::v-deep .el-table td,
.job-result-table-card ::v-deep .el-table th {
  padding: 8px 0;
}
</style>
