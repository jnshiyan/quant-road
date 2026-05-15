<template>
  <el-card shadow="never" class="job-shard-table-card" :body-style="cardBodyStyle">
    <div v-if="showHeader" slot="header" class="clearfix">
      <span>分片进度</span>
    </div>
    <el-table v-if="rows.length" :data="rows" border :size="tableSize" stripe>
      <el-table-column label="分片ID" min-width="90">
        <template slot-scope="scope">
          {{ scope.row.id || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="分片标识" min-width="200">
        <template slot-scope="scope">
          {{ scope.row.shardKey || scope.row.shard_key || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="策略ID" width="100">
        <template slot-scope="scope">
          {{ fallbackValue(scope.row.strategyId, scope.row.strategy_id, '-') }}
        </template>
      </el-table-column>
      <el-table-column label="分片序号" width="100">
        <template slot-scope="scope">
          {{ fallbackValue(scope.row.shardIndex, scope.row.shard_index, '-') }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="股票数" width="90">
        <template slot-scope="scope">
          {{ fallbackValue(scope.row.symbolCount, scope.row.symbol_count, 0) }}
        </template>
      </el-table-column>
      <el-table-column label="标的预览" min-width="180" show-overflow-tooltip>
        <template slot-scope="scope">
          {{ scope.row.symbolsText || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="尝试次数" width="90">
        <template slot-scope="scope">
          {{ fallbackValue(scope.row.attemptCount, scope.row.attempt_count, 0) }}
        </template>
      </el-table-column>
      <el-table-column label="Worker" min-width="120">
        <template slot-scope="scope">
          {{ scope.row.leaseOwner || scope.row.lease_owner || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="最后错误" min-width="200" show-overflow-tooltip>
        <template slot-scope="scope">
          {{ scope.row.lastError || scope.row.last_error || '-' }}
        </template>
      </el-table-column>
    </el-table>
    <div v-else class="table-empty">当前任务还没有分片明细</div>
  </el-card>
</template>

<script>
export default {
  name: 'JobShardTable',
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
  },
  methods: {
    fallbackValue() {
      for (let index = 0; index < arguments.length; index += 1) {
        const value = arguments[index]
        if (value !== undefined && value !== null && value !== '') {
          return value
        }
      }
      return ''
    },
    statusType(status) {
      if (status === 'SUCCESS') return 'success'
      if (status === 'FAILED' || status === 'PARTIAL_FAILED') return 'danger'
      if (status === 'RUNNING') return 'warning'
      return 'info'
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

.job-shard-table-card ::v-deep .el-table td,
.job-shard-table-card ::v-deep .el-table th {
  padding: 8px 0;
}
</style>
