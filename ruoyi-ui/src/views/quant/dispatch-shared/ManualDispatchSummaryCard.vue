<template>
  <el-card shadow="never" class="summary-card">
    <div class="eyebrow">本次提交摘要</div>
    <div class="headline">{{ strategyName || '请选择策略后提交' }}</div>

    <div class="summary-primary-action">
      <el-button class="submit-btn" type="primary" :loading="loading" @click="$emit('submit')">
        {{ loading ? '正在提交调度任务...' : '执行任务' }}
      </el-button>
      <div class="summary-primary-action__hint">右侧直接提交，提交后自动进入调度详情。</div>
    </div>

    <div class="submit-state" :class="`is-${submitView.tone || 'neutral'}`">
      <div class="submit-state__headline">
        <span>{{ submitView.phaseLabel }}</span>
        <strong v-if="submitView.elapsedSeconds > 0">{{ submitView.elapsedSeconds }}s</strong>
      </div>
      <div class="submit-state__detail">{{ submitView.detail }}</div>
      <div class="submit-state__expectation">{{ submitView.expectation }}</div>
    </div>

    <div class="summary-grid">
      <div class="summary-item">
        <label>执行范围</label>
        <span>{{ scopeSummary || '-' }}</span>
      </div>
      <div class="summary-item">
        <label>标的数量</label>
        <span>{{ symbolCount }}</span>
      </div>
      <div class="summary-item wide" v-if="symbolsSummary">
        <label>标的预览</label>
        <span>{{ symbolsSummary }}</span>
      </div>
      <div class="summary-item wide">
        <label>行情时间范围</label>
        <span>{{ marketRange || '-' }}</span>
      </div>
      <div class="summary-item">
        <label>策略起算日</label>
        <span>{{ backtestStartDate || '-' }}</span>
      </div>
      <div class="summary-item">
        <label>预计执行模式</label>
        <span>{{ resolvedMode || '提交后由系统判定' }}</span>
      </div>
      <div class="summary-item">
        <label>预计分片</label>
        <span>{{ plannedShards }}</span>
      </div>
      <div class="summary-item wide">
        <label>提交契约</label>
        <span>先创建调度任务，再拿任务回执并自动进入调度详情页。</span>
      </div>
    </div>

    <div class="note">
      真正提交的是这里展示的最终范围与时间范围，不是左侧原始输入文本；提交阶段如果变慢，请先看上面的状态区，它会告诉你当前在等什么。
    </div>
  </el-card>
</template>

<script>
export default {
  name: 'ManualDispatchSummaryCard',
  props: {
    strategyName: {
      type: String,
      default: ''
    },
    scopeSummary: {
      type: String,
      default: ''
    },
    symbols: {
      type: Array,
      default: () => []
    },
    symbolCount: {
      type: Number,
      default: 0
    },
    marketRange: {
      type: String,
      default: ''
    },
    backtestStartDate: {
      type: String,
      default: ''
    },
    resolvedMode: {
      type: String,
      default: ''
    },
    plannedShards: {
      type: String,
      default: '提交后判定'
    },
    submitView: {
      type: Object,
      default: () => ({
        status: 'idle',
        elapsedSeconds: 0,
        phaseLabel: '提交后先创建调度任务',
        detail: '手工调度不会在当前页面阻塞执行，会先创建调度任务并等待服务端返回任务回执。',
        expectation: '任务回执生成后自动进入调度详情页，继续查看当前阶段与执行日志。',
        tone: 'neutral'
      })
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    symbolsSummary() {
      return Array.isArray(this.symbols) ? this.symbols.join(', ') : ''
    }
  }
}
</script>

<style scoped>
.summary-card {
  position: sticky;
  top: 20px;
  border-radius: 20px;
}

.eyebrow {
  color: #6b7280;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.headline {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.summary-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 14px;
}

.summary-primary-action {
  margin-top: 16px;
}

.summary-primary-action__hint {
  margin-top: 8px;
  color: #64748b;
  line-height: 1.6;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary-item label {
  color: #94a3b8;
  font-size: 12px;
}

.summary-item span {
  color: #1f2937;
  line-height: 1.7;
  word-break: break-word;
}

.wide {
  grid-column: 1 / -1;
}

.note {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f8fafc, #eef6ff);
  color: #334155;
  line-height: 1.7;
}

.submit-state {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid #dbe7f3;
  background: linear-gradient(180deg, #f8fbff, #eef6ff);
}

.submit-state.is-success {
  border-color: #cdebd8;
  background: linear-gradient(180deg, #f7fff8, #edf9f0);
}

.submit-state.is-danger {
  border-color: #f3d0d0;
  background: linear-gradient(180deg, #fff8f8, #fff0f0);
}

.submit-state__headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #0f172a;
  font-weight: 700;
}

.submit-state__headline strong {
  color: #0f766e;
}

.submit-state__detail,
.submit-state__expectation {
  margin-top: 8px;
  color: #334155;
  line-height: 1.7;
}

.submit-btn {
  width: 100%;
}
</style>
