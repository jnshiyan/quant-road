<template>
  <div class="operations-page">
    <div class="operations-header">
      <div class="operations-header__main">
        <div class="ops-eyebrow">运维中心</div>
        <h1>运维中心</h1>
        <div class="operations-summary">
          {{ operationsUi.blockerTitle }} · {{ operationsUi.blockerReason }}
        </div>
        <div class="ops-guide-chips">
          <span>先判断阻断</span>
          <span>再决定恢复动作</span>
          <span>高级兜底工具下沉</span>
        </div>
      </div>
      <div class="operations-header__actions">
        <el-button plain @click="$router.push('/quant/jobs')">返回调度中心</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :xl="10">
        <el-card shadow="never" class="ops-card">
          <div class="section-eyebrow">当前阻断</div>
          <div class="ops-title">{{ operationsUi.blockerTitle }}</div>
          <div class="ops-badge">{{ operationsUi.blockerBadge }}</div>
          <div class="ops-reason">{{ operationsUi.blockerReason }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="14">
        <el-card shadow="never" class="ops-card">
          <div class="section-eyebrow">建议恢复</div>
          <div class="recovery-actions">
            <el-button
              v-if="operationsUi.primaryRecovery"
              type="primary"
              :loading="pendingActionKey === operationsUi.primaryRecovery.code"
              @click="handleRecovery(operationsUi.primaryRecovery)"
            >
              {{ operationsUi.primaryRecovery.label }}
            </el-button>
            <el-button
              v-for="item in operationsUi.secondaryRecoveries"
              :key="item.code"
              plain
              :loading="pendingActionKey === item.code"
              @click="handleRecovery(item)"
            >
              {{ item.label }}
            </el-button>
          </div>
          <div v-if="!operationsUi.primaryRecovery && !operationsUi.secondaryRecoveries.length" class="ops-empty-copy">
            当前没有可一键执行的恢复动作，说明阻断已解除或需要人工研判。
          </div>
          <div class="ops-inline-chips">
            <el-tag size="mini" type="warning">先恢复主链路</el-tag>
            <el-tag size="mini" type="info">兜底工具后置</el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="ops-card mt16">
      <div class="section-eyebrow">技术状态</div>
      <div class="tech-grid">
        <div class="tech-panel">
          <div class="tech-panel__title">异步执行器</div>
          <div class="worker-grid">
            <div class="worker-item"><label>状态</label><span>{{ workerHealth.status || '-' }}</span></div>
            <div class="worker-item"><label>待消费分片</label><span>{{ workerHealth.queuedShardCount || 0 }}</span></div>
            <div class="worker-item"><label>运行中分片</label><span>{{ workerHealth.runningShardCount || 0 }}</span></div>
            <div class="worker-item"><label>活跃 worker</label><span>{{ workerHealth.activeWorkerCount || 0 }}</span></div>
          </div>
        </div>
        <div class="tech-panel">
          <div class="tech-panel__title">数据完整性</div>
          <div class="ops-title ops-title--compact">{{ dataIntegrity.status || 'UNKNOWN' }}</div>
          <div class="ops-reason">{{ dataIntegrity.message || '暂无数据完整性说明。' }}</div>
          <div class="ops-tags">
            <el-tag size="mini">{{ dataIntegrity.category || '未分类' }}</el-tag>
            <el-tag size="mini" type="success">可进看板 {{ dataIntegrity.canEnterDashboard ? '是' : '否' }}</el-tag>
          </div>
        </div>
      </div>
    </el-card>

    <el-collapse v-model="operationsPanels" class="mt16 operations-secondary-collapse">
      <el-collapse-item name="toolbox">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>高级兜底工具</span>
            <span class="section-meta">只有标准路径失效时再展开</span>
          </div>
        </template>
        <div class="toolbox-grid">
          <div class="toolbox-item toolbox-item--manual">
            <div class="toolbox-title">手工消费分片</div>
            <div class="ops-inline-form">
              <el-input v-model="workerId" size="small" placeholder="worker-id" style="width: 180px" />
              <el-input-number v-model="recoverLimit" size="small" :min="1" :max="500" controls-position="right" />
              <el-button size="small" @click="handleManualConsume">手工消费分片</el-button>
            </div>
          </div>
          <div v-for="item in operationsUi.compatibilityActions" :key="item.code" class="toolbox-item">
            <div class="toolbox-title">{{ item.label }}</div>
            <el-button size="mini" plain @click="handleCompatibility(item)">查看用法</el-button>
          </div>
        </div>
      </el-collapse-item>

      <el-collapse-item v-if="operationsUi.navigation.length" name="navigation">
        <template slot="title">
          <div class="collapse-title-shell">
            <span>恢复后下一步</span>
            <span class="section-meta">阻断解除后再继续业务链路</span>
          </div>
        </template>
        <div class="navigation-grid">
          <el-button
            v-for="item in operationsUi.navigation"
            :key="item.code"
            plain
            @click="$router.push(item.targetPage)"
          >
            {{ item.label }}
          </el-button>
        </div>
      </el-collapse-item>
    </el-collapse>

    <div v-if="actionFeedback" class="feedback-box">
      <div class="feedback-title">{{ actionFeedback.message }}</div>
      <div class="feedback-meta">{{ actionFeedback.time }}</div>
    </div>
  </div>
</template>

<script>
import ElCollapse from 'element-ui/lib/collapse'
import ElCollapseItem from 'element-ui/lib/collapse-item'
import 'element-ui/lib/theme-chalk/collapse.css'
import {
  getOperationsCenterSummary,
  recoverAsyncShards,
  recoverBatch,
  runAsyncWorkerOnce
} from '@/api/quant'
import { buildOperationsCenterState } from './operations-center-state'

export default {
  name: 'QuantOperationsCenter',
  components: {
    ElCollapse,
    ElCollapseItem
  },
  data() {
    return {
      summary: {},
      operationsUi: buildOperationsCenterState(),
      pendingActionKey: '',
      actionFeedback: null,
      workerId: 'ruoyi-web-worker',
      recoverLimit: 100,
      operationsPanels: []
    }
  },
  computed: {
    workerHealth() {
      return this.summary.workerHealth || {}
    },
    dataIntegrity() {
      return this.summary.dataIntegrity || {}
    },
    recentBatches() {
      const toolbox = this.summary.toolbox || {}
      return Array.isArray(toolbox.recentBatches) ? toolbox.recentBatches : []
    }
  },
  async created() {
    await this.loadSummary()
  },
  methods: {
    async loadSummary() {
      const response = await getOperationsCenterSummary()
      this.summary = response.data || {}
      this.operationsUi = buildOperationsCenterState(this.summary)
    },
    async handleRecovery(action = {}) {
      this.pendingActionKey = action.code || ''
      try {
        if (action.code === 'recoverAsyncShards') {
          await recoverAsyncShards({ limit: this.recoverLimit })
        } else if (action.code === 'recoverBatch') {
          const batchId = (this.summary.topBlocker && this.summary.topBlocker.batchId) || (this.recentBatches[0] && this.recentBatches[0].id)
          if (!batchId) {
            this.$modal.msgWarning('当前没有可恢复的失败批次')
            return
          }
          await recoverBatch(batchId, { actor: 'ruoyi-ui' })
        } else {
          this.$modal.msgWarning('当前动作需要人工判断，系统未提供一键恢复。')
          return
        }
        this.actionFeedback = {
          message: `${action.label || '恢复动作'} 已提交`,
          time: new Date().toLocaleString()
        }
        await this.loadSummary()
      } finally {
        this.pendingActionKey = ''
      }
    },
    async handleManualConsume() {
      await runAsyncWorkerOnce({ workerId: this.workerId })
      this.actionFeedback = {
        message: '手工消费请求已提交',
        time: new Date().toLocaleString()
      }
      await this.loadSummary()
    },
    handleCompatibility(item) {
      this.$alert(
        `${item.label} 只作为兜底入口保留。建议优先回到调度中心使用统一执行任务；只有标准路径失效时再使用兼容入口。`,
        '高级工具箱',
        {
          confirmButtonText: '我知道了'
        }
      )
    }
  }
}
</script>

<style scoped>
.operations-page {
  padding: 4px;
}

.operations-header {
  margin-bottom: 16px;
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
}

.operations-header__main {
  min-width: 0;
}

.ops-eyebrow,
.section-eyebrow {
  color: #92400e;
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.operations-header h1 {
  margin: 10px 0 0;
  max-width: 900px;
  font-size: 28px;
  line-height: 1.4;
  color: #111827;
}

.operations-summary {
  margin-top: 10px;
  color: #475569;
  line-height: 1.7;
}

.operations-header__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.ops-guide-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.ops-guide-chips span {
  padding: 6px 10px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #334155;
  font-size: 12px;
}

.ops-inline-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ops-card {
  border-radius: 20px;
}

.ops-title {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 700;
  color: #111827;
}

.ops-title--compact {
  font-size: 20px;
}

.ops-badge {
  margin-top: 10px;
  color: #b45309;
  font-weight: 600;
}

.ops-reason {
  margin-top: 12px;
  color: #475569;
  line-height: 1.8;
}

.recovery-actions,
.ops-inline-form,
.ops-tags {
  margin-top: 14px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.ops-empty-copy {
  margin-top: 14px;
  color: #64748b;
  line-height: 1.7;
}

.tech-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.tech-panel {
  padding: 16px;
  border-radius: 18px;
  background: #fafaf9;
  border: 1px solid #ebe7dc;
}

.tech-panel__title {
  color: #111827;
  font-weight: 700;
}

.worker-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.worker-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.worker-item label {
  color: #94a3b8;
  font-size: 12px;
}

.toolbox-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.navigation-grid {
  margin-top: 14px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.operations-secondary-collapse {
  border-radius: 16px;
  overflow: hidden;
}

.collapse-title-shell {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
}

.section-meta {
  color: #64748b;
  font-size: 12px;
  font-weight: 400;
}

.toolbox-item {
  padding: 14px;
  border-radius: 16px;
  background: #fafaf9;
  border: 1px solid #ebe7dc;
}

.toolbox-item--manual {
  background: #fffdf6;
}

.toolbox-title {
  color: #111827;
  font-weight: 600;
}

.feedback-box {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 14px;
  background: #f8fafc;
}

.feedback-title {
  font-weight: 600;
  color: #0f172a;
}

.feedback-meta {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.mt16 {
  margin-top: 16px;
}

@media (max-width: 1200px) {
  .operations-header {
    flex-direction: column;
  }

  .tech-grid {
    grid-template-columns: 1fr;
  }
}
</style>
