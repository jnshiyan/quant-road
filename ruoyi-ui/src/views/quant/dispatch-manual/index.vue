<template>
  <div class="manual-dispatch-page">
    <div class="manual-dispatch-header">
      <div class="manual-dispatch-header__main">
        <div class="manual-dispatch-header__chips">
          <div class="page-eyebrow">手工调度</div>
          <div class="manual-dispatch-header__badge">提交前操作台</div>
        </div>
        <h1>手工调度</h1>
        <div class="manual-dispatch-topline">提交前先看提交契约，再确认策略、时间范围和执行范围。</div>
        <div class="manual-dispatch-guide">
          <span>先确认提交契约</span>
          <span>再填写 3 步</span>
          <span>右侧直接执行任务</span>
        </div>
      </div>
      <div class="manual-dispatch-header__actions">
        <el-button plain @click="$router.push('/quant/jobs')">返回调度中心</el-button>
        <el-button plain @click="$router.push('/quant/dispatch-auto')">查看自动计划</el-button>
      </div>
    </div>

    <div class="manual-dispatch-contract-strip">
      <div class="manual-dispatch-contract-strip__main">
        <div class="manual-dispatch-contract-strip__title">{{ submitView.phaseLabel }}</div>
        <div class="manual-dispatch-contract-strip__detail">{{ submitView.detail }}</div>
      </div>
      <div class="manual-dispatch-contract-strip__facts">
        <div class="contract-fact">
          <label>当前策略</label>
          <strong>{{ selectedStrategyName }}</strong>
        </div>
        <div class="contract-fact">
          <label>执行范围</label>
          <strong>{{ scopeSummary }}</strong>
        </div>
        <div class="contract-fact">
          <label>行情时间范围</label>
          <strong>{{ timeRangeSummary }}</strong>
        </div>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never" class="step-card">
          <div class="step-title">第 1 步：选择策略</div>
          <el-form class="mt16" :inline="true" :model="jobForm" size="small" label-width="92px">
            <el-form-item label="策略">
              <el-select v-model="jobForm.strategyId" clearable filterable style="width: 260px" placeholder="请选择策略">
                <el-option
                  v-for="item in strategyList"
                  :key="item.id"
                  :label="`${item.id} - ${item.strategy_name || item.strategyName || ''}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="执行人">
              <el-input v-model="jobForm.actor" style="width: 160px" />
            </el-form-item>
            <el-form-item label="总资金">
              <el-input-number v-model="jobForm.portfolioTotalCapital" :min="0" :step="10000" controls-position="right" />
            </el-form-item>
            <el-form-item label="通知">
              <el-switch v-model="jobForm.notify" />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="step-card mt16">
          <div class="step-title">第 2 步：确定数据范围</div>
          <el-form class="mt16" :inline="true" size="small" label-width="104px">
            <el-form-item label="行情时间范围">
              <el-date-picker
                v-model="timeRange"
                type="daterange"
                value-format="yyyy-MM-dd"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
              />
            </el-form-item>
            <el-form-item label="策略起算日">
              <el-date-picker
                v-model="jobForm.strategyBacktestStartDate"
                type="date"
                value-format="yyyy-MM-dd"
                placeholder="策略起算日"
              />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="step-card mt16">
          <div class="step-title">第 3 步：确认执行范围</div>
          <el-row :gutter="16" class="mt16">
            <el-col :xs="24" :lg="12">
              <el-form :model="scopeForm" label-width="92px" size="small">
                <el-form-item label="预设范围">
                  <el-select v-model="scopeForm.scopeType" style="width: 100%" @change="handleScopeTypeChange">
                    <el-option
                      v-for="item in presetScopes"
                      :key="item.scopeType"
                      :label="item.label"
                      :value="item.scopeType"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item v-if="currentScopeNeedsPool" label="正式股票池">
                  <el-select v-model="scopeForm.scopePoolCode" style="width: 100%" placeholder="请选择正式股票池">
                    <el-option
                      v-for="item in availablePools"
                      :key="item.poolCode"
                      :label="`${item.poolName} (${item.poolCode})`"
                      :value="item.poolCode"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="直接指定">
                  <el-input
                    v-model="scopeForm.symbolsText"
                    type="textarea"
                    :rows="3"
                    placeholder="逗号或换行分隔，例如 510300,159915"
                  />
                </el-form-item>
                <el-form-item label="白名单">
                  <el-input v-model="scopeForm.whitelistText" type="textarea" :rows="2" />
                </el-form-item>
                <el-form-item label="黑名单">
                  <el-input v-model="scopeForm.blacklistText" type="textarea" :rows="2" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" size="small" :loading="loadingScopePreview" @click="loadScopePreview">刷新范围预览</el-button>
                  <el-button size="small" @click="$router.push('/quant/symbols')">去标的治理页</el-button>
                </el-form-item>
              </el-form>
            </el-col>
            <el-col :xs="24" :lg="12">
              <div class="scope-preview-card">
                <div class="scope-preview-card__title">提交口径预览</div>
                <div class="scope-preview-card__summary">
                  <div class="scope-preview-card__count">{{ scopePreview.resolvedCount || 0 }}</div>
                  <div class="scope-preview-card__copy">
                    <div class="scope-preview-card__label">{{ scopePreview.scopeType || '当前范围' }}</div>
                    <div class="scope-preview-card__caption">{{ previewCaption }}</div>
                  </div>
                </div>
                <div class="scope-preview-card__meta">
                  <el-tag size="mini" type="success">白名单 {{ scopePreview.whitelistCount || 0 }}</el-tag>
                  <el-tag size="mini" type="warning">黑名单 {{ scopePreview.blacklistCount || 0 }}</el-tag>
                  <el-tag size="mini" type="info">临时补充 {{ scopePreview.adHocCount || 0 }}</el-tag>
                </div>
                <div class="scope-preview-card__facts">
                  <div class="scope-preview-card__fact">
                    <label>行情时间范围</label>
                    <strong>{{ timeRangeSummary }}</strong>
                  </div>
                  <div class="scope-preview-card__fact">
                    <label>策略起算日</label>
                    <strong>{{ jobForm.strategyBacktestStartDate || '-' }}</strong>
                  </div>
                </div>
                <div v-if="previewSymbols.length" class="scope-preview-card__symbols">
                  {{ previewSymbols.join(', ') }}
                </div>
                <div class="scope-preview-card__tip">
                  真正提交的是这里解析出的最终标的结果，不是左侧原始输入文本。
                </div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="9">
        <manual-dispatch-summary-card
          ref="summaryCard"
          :strategy-name="selectedStrategyName"
          :scope-summary="scopeSummary"
          :symbols="previewSymbols"
          :symbol-count="Number(scopePreview.resolvedCount || 0)"
          :market-range="timeRangeSummary"
          :backtest-start-date="jobForm.strategyBacktestStartDate"
          :resolved-mode="resolvedModeSummary"
          :planned-shards="plannedShardsSummary"
          :submit-view="submitView"
          :loading="submitting"
          @submit="confirmAndSubmitExecution"
        />
      </el-col>
    </el-row>
  </div>
</template>

<script>
import {
  executeQuantTask,
  getSymbolScopeOptions,
  getSymbolScopePreview,
  listStrategies,
  listSymbolPools
} from '@/api/quant'
import ManualDispatchSummaryCard from '@/views/quant/dispatch-shared/ManualDispatchSummaryCard'
const { saveDispatchHandoff } = require('@/views/quant/jobs/dispatch-handoff')
const { buildManualDispatchSubmitView } = require('@/views/quant/dispatch-manual/manual-dispatch-submit-state')

function normalizeTextList(value) {
  return String(value || '')
    .split(/[\n,，]/)
    .map(item => item.trim())
    .filter(Boolean)
}

function isoDateYearsAgo(years) {
  const today = new Date()
  today.setFullYear(today.getFullYear() - years)
  return today.toISOString().slice(0, 10)
}

function isoToday() {
  return new Date().toISOString().slice(0, 10)
}

export default {
  name: 'QuantDispatchManual',
  components: {
    ManualDispatchSummaryCard
  },
  data() {
    return {
      loadingScopePreview: false,
      submitting: false,
      submitState: {
        status: 'idle',
        startedAt: 0,
        jobId: null,
        errorMessage: ''
      },
      submitNow: 0,
      submitTicker: null,
      strategyList: [],
      presetScopes: [{ scopeType: 'all_stocks', label: '全市场' }],
      availablePools: [],
      jobForm: {
        strategyId: undefined,
        actor: 'ruoyi-ui',
        portfolioTotalCapital: 100000,
        notify: false,
        strategyBacktestStartDate: isoDateYearsAgo(5)
      },
      timeRange: [isoDateYearsAgo(5), isoToday()],
      scopeForm: {
        scopeType: 'all_stocks',
        scopePoolCode: '',
        symbolsText: '',
        whitelistText: '',
        blacklistText: '',
        adHocSymbolsText: ''
      },
      scopePreview: {}
    }
  },
  computed: {
    currentScopeNeedsPool() {
      return ['stock_pool', 'etf_pool'].includes(this.scopeForm.scopeType)
    },
    previewSymbols() {
      const symbols = Array.isArray(this.scopePreview.symbols) ? this.scopePreview.symbols : []
      return symbols.slice(0, 18)
    },
    previewCaption() {
      if (!this.scopePreview.resolvedCount) {
        return '还没有生成本次执行范围，请先刷新预览。'
      }
      return `本次会以 ${this.scopePreview.resolvedCount} 个标的作为统一执行范围。`
    },
    timeRangeSummary() {
      const start = Array.isArray(this.timeRange) ? this.timeRange[0] : ''
      const end = Array.isArray(this.timeRange) ? this.timeRange[1] : ''
      if (!start && !end) {
        return '未设置'
      }
      return `${start || '-'} ~ ${end || '-'}`
    },
    selectedStrategyName() {
      const match = this.strategyList.find(item => String(item.id) === String(this.jobForm.strategyId))
      if (!match) {
        return '请选择策略'
      }
      return match.strategy_name || match.strategyName || String(match.id)
    },
    scopeSummary() {
      const label = this.scopePreview.scopeType || this.scopeForm.scopeType || '未设置'
      const count = Number(this.scopePreview.resolvedCount || 0)
      return count > 0 ? `${label} / ${count} 个标的` : label
    },
    resolvedModeSummary() {
      const count = Number(this.scopePreview.resolvedCount || 0)
      if (!count) {
        return '提交后由系统判定'
      }
      return count <= 2 ? '轻量任务，倾向直接执行' : '系统按规模自动判定'
    },
    plannedShardsSummary() {
      const count = Number(this.scopePreview.resolvedCount || 0)
      if (!count) {
        return '提交后判定'
      }
      return count <= 2 ? '预估 1 个分片' : `预估 ${count} 个以内分片`
    },
    submitView() {
      return buildManualDispatchSubmitView(this.submitState, {
        now: this.submitNow || Date.now()
      })
    }
  },
  async created() {
    await this.loadReferenceData()
    await this.loadScopePreview()
  },
  beforeDestroy() {
    this.stopSubmitTicker()
  },
  methods: {
    startSubmitTicker() {
      this.submitNow = Date.now()
      this.stopSubmitTicker()
      this.submitTicker = window.setInterval(() => {
        this.submitNow = Date.now()
      }, 1000)
    },
    stopSubmitTicker() {
      if (this.submitTicker) {
        window.clearInterval(this.submitTicker)
        this.submitTicker = null
      }
    },
    setSubmitState(status, extra = {}) {
      this.submitNow = Date.now()
      this.submitState = {
        ...this.submitState,
        status,
        ...extra
      }
    },
    resolveSubmitErrorMessage(error) {
      const backendMessage = error && error.response && error.response.data && error.response.data.msg
      if (backendMessage) {
        return backendMessage
      }
      if (error && error.message) {
        return error.message
      }
      return '提交失败，请检查接口响应或后台日志。'
    },
    async loadReferenceData() {
      const [strategyResponse, scopeOptionsResponse, poolsResponse] = await Promise.all([
        listStrategies(),
        getSymbolScopeOptions(),
        listSymbolPools()
      ])
      this.strategyList = Array.isArray(strategyResponse.data) ? strategyResponse.data : []
      const scopeOptions = scopeOptionsResponse.data || {}
      this.presetScopes = Array.isArray(scopeOptions.presetScopes) && scopeOptions.presetScopes.length
        ? scopeOptions.presetScopes
        : this.presetScopes
      this.availablePools = Array.isArray(poolsResponse.data) ? poolsResponse.data : []
    },
    handleScopeTypeChange() {
      if (!this.currentScopeNeedsPool) {
        this.scopeForm.scopePoolCode = ''
      }
    },
    buildScopePayload() {
      const payload = {
        scopeType: this.scopeForm.scopeType || 'all_stocks'
      }
      const symbols = normalizeTextList(this.scopeForm.symbolsText)
      const whitelist = normalizeTextList(this.scopeForm.whitelistText)
      const blacklist = normalizeTextList(this.scopeForm.blacklistText)
      const adHocSymbols = normalizeTextList(this.scopeForm.adHocSymbolsText)
      if (this.scopeForm.scopePoolCode) payload.scopePoolCode = this.scopeForm.scopePoolCode
      if (symbols.length) payload.symbols = symbols
      if (whitelist.length) payload.whitelist = whitelist
      if (blacklist.length) payload.blacklist = blacklist
      if (adHocSymbols.length) payload.adHocSymbols = adHocSymbols
      return payload
    },
    async loadScopePreview() {
      this.loadingScopePreview = true
      try {
        const response = await getSymbolScopePreview(this.buildScopePayload())
        this.scopePreview = response.data || {}
      } catch (error) {
        this.$modal.msgError('读取范围预览失败')
      } finally {
        this.loadingScopePreview = false
      }
    },
    async confirmAndSubmitExecution() {
      if (!this.scopePreview.resolvedCount) {
        await this.loadScopePreview()
      }
      const resolvedCount = Number(this.scopePreview.resolvedCount || 0)
      if (resolvedCount <= 0) {
        this.$modal.msgWarning('当前没有可执行标的，请先刷新范围预览')
        return
      }
      await this.$confirm(
        `本次将提交 ${resolvedCount} 个标的，行情时间范围 ${this.timeRangeSummary}，策略起算日 ${this.jobForm.strategyBacktestStartDate || '-'}。提交成功后将自动进入调度详情页。`,
        '执行任务',
        {
          confirmButtonText: '确认提交',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
      this.submitting = true
      this.setSubmitState('submitting', {
        startedAt: Date.now(),
        jobId: null,
        errorMessage: ''
      })
      this.startSubmitTicker()
      try {
        const response = await executeQuantTask({
          strategyId: this.jobForm.strategyId,
          actor: this.jobForm.actor,
          notify: this.jobForm.notify,
          requestedMode: 'async',
          portfolioTotalCapital: this.jobForm.portfolioTotalCapital,
          startDate: this.timeRange[0],
          endDate: this.timeRange[1],
          strategyBacktestStartDate: this.jobForm.strategyBacktestStartDate,
          ...this.buildScopePayload()
        })
        const payload = response.data || {}
        const jobId = payload.jobId || payload.executionId
        if (!jobId) {
          throw new Error('服务端未返回任务 ID，暂时无法进入调度详情页。')
        }
        this.setSubmitState('redirecting', { jobId })
        this.$modal.msgSuccess(`已创建任务 #${jobId}，正在进入调度详情页`)
        saveDispatchHandoff(window.sessionStorage, {
          jobId,
          taskName: this.selectedStrategyName,
          scopeSummary: this.scopeSummary,
          submittedAt: new Date().toLocaleString()
        })
        this.$router.push({ path: `/quant/dispatch-detail/${jobId}` }).catch(() => {})
      } catch (error) {
        const errorMessage = this.resolveSubmitErrorMessage(error)
        this.setSubmitState('failed', { errorMessage })
        this.$modal.msgError(errorMessage)
      } finally {
        this.stopSubmitTicker()
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.manual-dispatch-page {
  padding: 4px;
}

.manual-dispatch-header {
  margin-bottom: 16px;
  padding: 18px 20px;
  border-radius: 24px;
  background: linear-gradient(135deg, #f8fffd, #eef6ff);
  border: 1px solid #dfeee8;
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
}

.manual-dispatch-header__main {
  min-width: 0;
}

.manual-dispatch-header__chips {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.page-eyebrow {
  color: #0f766e;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  font-weight: 700;
}

.manual-dispatch-header__badge {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.12);
  color: #115e59;
  font-size: 12px;
  font-weight: 700;
}

.manual-dispatch-header h1 {
  margin: 10px 0 8px;
  font-size: 30px;
  line-height: 1.35;
  color: #0f172a;
}

.manual-dispatch-topline {
  color: #475569;
  line-height: 1.7;
}

.manual-dispatch-guide {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.manual-dispatch-guide span {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid #d9e7ef;
  color: #334155;
  font-size: 12px;
}

.manual-dispatch-header__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.manual-dispatch-contract-strip {
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #dbe7f3;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.manual-dispatch-contract-strip__main {
  min-width: 0;
}

.manual-dispatch-contract-strip__title {
  color: #0f172a;
  font-weight: 600;
}

.manual-dispatch-contract-strip__detail {
  margin-top: 8px;
  color: #475569;
  line-height: 1.7;
}

.manual-dispatch-contract-strip__facts {
  min-width: 420px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.contract-fact {
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.contract-fact label {
  display: block;
  color: #94a3b8;
  font-size: 12px;
}

.contract-fact strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  line-height: 1.6;
}

.step-card {
  border-radius: 20px;
}

.step-title {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.scope-preview-card {
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(180deg, #ffffff, #f8fbff);
  border: 1px solid #dbe7f3;
}

.scope-preview-card__title {
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
}

.scope-preview-card__summary {
  margin-top: 14px;
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.scope-preview-card__count {
  min-width: 72px;
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
  color: #0f766e;
}

.scope-preview-card__copy {
  min-width: 0;
}

.scope-preview-card__label {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.scope-preview-card__caption {
  margin-top: 8px;
  color: #64748b;
  line-height: 1.7;
}

.scope-preview-card__meta,
.scope-preview-card__facts {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.scope-preview-card__fact {
  flex: 1 1 180px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.scope-preview-card__fact label {
  display: block;
  color: #94a3b8;
  font-size: 12px;
}

.scope-preview-card__fact strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  line-height: 1.6;
}

.scope-preview-card__symbols {
  margin-top: 16px;
  color: #334155;
  line-height: 1.7;
  word-break: break-word;
}

.scope-preview-card__tip {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #334155;
  line-height: 1.7;
}

.mt16 {
  margin-top: 16px;
}

@media (max-width: 1200px) {
  .manual-dispatch-header,
  .manual-dispatch-contract-strip {
    flex-direction: column;
  }

  .manual-dispatch-contract-strip__facts {
    min-width: 0;
    width: 100%;
  }
}
</style>
