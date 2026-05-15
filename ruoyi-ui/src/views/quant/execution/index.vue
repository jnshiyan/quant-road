<template>
  <div class="app-container">
    <div class="execution-page-header mb16">
      <div>
        <div class="page-title">执行回写</div>
        <div class="page-subtitle">执行闭环 / 先处理阻断，再决定是否需要人工回写</div>
      </div>
      <div class="execution-page-header__actions">
        <span class="focus-label">当前视图</span>
        <span class="execution-page-header__value">{{ focusPresetLabel }}</span>
      </div>
    </div>

    <el-card shadow="never" class="box-card mb16 summary-strip-card">
      <div class="summary-strip execution-topline">
        <el-tag size="mini" type="warning">待执行 {{ reconciliationSummary.pendingSignalCount || 0 }}</el-tag>
        <el-tag size="mini" type="success">已执行 {{ reconciliationSummary.executedSignalCount || 0 }}</el-tag>
        <el-tag size="mini" type="danger">漏执行 {{ reconciliationSummary.missedSignalCount || 0 }}</el-tag>
        <el-tag size="mini" type="warning">部分成交 {{ reconciliationSummary.partialExecutionCount || 0 }}</el-tag>
        <el-tag size="mini" type="info">未匹配 {{ reconciliationSummary.unmatchedExecutionCount || 0 }}</el-tag>
        <el-tag size="mini" :type="reconciliationSummary.todayWritebackComplete ? 'success' : 'warning'">
          回写完成 {{ reconciliationSummary.todayWritebackComplete ? '是' : '否' }}
        </el-tag>
      </div>
    </el-card>

    <el-row :gutter="16" class="mb16 execution-first-screen-grid">
      <el-col :xs="24" :xl="10">
        <el-card shadow="never" class="box-card execution-primary-card">
          <div class="execution-primary-card__label">今日闭环状态</div>
          <div class="execution-primary-card__title">{{ executionActionPlan.headline }}</div>
          <div class="execution-primary-card__desc">{{ executionFirstScreen.primaryAction.description }}</div>
          <div class="execution-primary-card__summary">
            <span
              v-for="line in executionActionPlan.summaryLines"
              :key="line"
              class="execution-primary-card__summary-line"
            >
              {{ line }}
            </span>
          </div>
          <div class="execution-primary-card__footer">
            <div class="execution-primary-card__footer-meta">
              <span class="focus-label">首要动作</span>
              <span class="focus-current">{{ executionFirstScreen.primaryAction.isCrossPage ? '需先跨页处理' : '可直接在本页处理' }}</span>
            </div>
            <div class="execution-primary-card__actions">
              <el-button type="primary" @click="handleExecutionScreenAction(executionFirstScreen.primaryAction)">
                {{ executionFirstScreen.primaryAction.title }}
              </el-button>
              <el-button plain @click="applyFocusPreset('all')">查看全部异常</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="7">
        <el-card shadow="never" class="box-card execution-summary-card execution-summary-card--anomaly">
          <div class="execution-summary-card__label">异常优先级</div>
          <div class="execution-summary-card__title">{{ executionFirstScreen.summaryCards[0].title }}</div>
          <div class="execution-summary-card__emphasis">{{ executionFirstScreen.summaryCards[0].emphasis }}</div>
          <div class="execution-summary-card__metric-list">
            <span
              v-for="line in executionFirstScreen.summaryCards[0].lines"
              :key="line"
              class="execution-summary-card__metric"
            >
              {{ line }}
            </span>
          </div>
          <div class="execution-summary-card__actions">
            <el-button size="mini" type="warning" plain @click="applyFocusPreset('unmatched')">处理未匹配成交</el-button>
            <el-button size="mini" type="danger" plain @click="applyFocusPreset('abnormal')">查看异常反馈</el-button>
            <el-button size="mini" plain @click="applyFocusPreset('partial')">查看部分成交</el-button>
            <el-button size="mini" type="info" plain @click="applyFocusPreset('positionDiff')">核对持仓差异</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="7">
        <el-card shadow="never" class="box-card execution-summary-card execution-summary-card--manual">
          <div class="execution-summary-card__label">{{ executionFirstScreen.summaryCards[1].label }}</div>
          <div class="execution-summary-card__title">手工触发</div>
          <div class="execution-summary-card__emphasis">{{ executionFirstScreen.summaryCards[1].emphasis }}</div>
          <div class="execution-summary-card__plain-list">
            <div
              v-for="line in executionFirstScreen.summaryCards[1].lines"
              :key="line"
              class="plain-list-item"
            >
              {{ line }}
            </div>
          </div>
          <div class="execution-summary-card__actions execution-summary-card__actions--stacked">
            <el-button
              v-for="item in executionFirstScreen.manualActions"
              :key="item.action"
              size="mini"
              type="primary"
              plain
              @click="handleExecutionScreenAction(item)"
            >
              {{ item.action === 'section:recordEntrySection' ? '批量导入回写' : '手工成交回写' }}
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card ref="recordSection" shadow="never" class="box-card mb16">
      <div slot="header" class="focus-toolbar">
        <span class="focus-label">当前异常列表</span>
        <span class="focus-current">当前视图：{{ focusPresetLabel }}</span>
      </div>
      <div class="execution-focus-bar">
        <el-button
          size="mini"
          :type="focusPreset === 'all' ? 'primary' : 'default'"
          @click="applyFocusPreset('all')"
        >
          全部
        </el-button>
        <el-button
          size="mini"
          :type="focusPreset === 'unmatched' ? 'warning' : 'default'"
          @click="applyFocusPreset('unmatched')"
        >
          未匹配成交
        </el-button>
        <el-button
          size="mini"
          :type="focusPreset === 'abnormal' ? 'danger' : 'default'"
          @click="applyFocusPreset('abnormal')"
        >
          异常反馈
        </el-button>
        <el-button
          size="mini"
          :type="focusPreset === 'partial' ? 'warning' : 'default'"
          @click="applyFocusPreset('partial')"
        >
          部分成交
        </el-button>
        <el-button
          size="mini"
          :type="focusPreset === 'positionDiff' ? 'info' : 'default'"
          @click="applyFocusPreset('positionDiff')"
        >
          持仓差异
        </el-button>
      </div>
      <el-form :inline="true" :model="recordQuery" size="small" class="mt16">
        <el-form-item label="股票代码">
          <el-input v-model="recordQuery.stockCode" placeholder="000001" style="width: 120px" />
        </el-form-item>
        <el-form-item label="条数">
          <el-input-number v-model="recordQuery.limit" :min="1" :max="500" controls-position="right" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="loadExecutionRecords">查询</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="loadingRecords" :data="filteredExecutionRecords" border :row-class-name="executionRecordRowClassName">
        <el-table-column label="ID" prop="id" width="80" />
        <el-table-column label="日期" prop="trade_date" width="110" />
        <el-table-column label="代码" prop="stock_code" width="90" />
        <el-table-column label="方向" prop="side" width="80">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.side === 'BUY' ? 'success' : 'danger'">{{ scope.row.side }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数量" prop="quantity" width="90" />
        <el-table-column label="价格" prop="price" width="90" />
        <el-table-column label="策略" min-width="160">
          <template slot-scope="scope">{{ scope.row.strategy_id }} - {{ scope.row.strategy_name || '-' }}</template>
        </el-table-column>
        <el-table-column label="匹配状态" width="120">
          <template slot-scope="scope">
            <el-tag size="mini" :type="matchStatusTagType(scope.row.match_status)">
              {{ matchStatusLabel(scope.row.match_status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="持仓同步" width="120">
          <template slot-scope="scope">
            <el-tag size="mini" :type="positionSyncStatusTagType(scope.row.position_sync_status)">
              {{ positionSyncStatusLabel(scope.row.position_sync_status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template slot-scope="scope">
            <el-button v-if="isUnmatchedExecution(scope.row)" type="text" size="mini" @click="openMatchDialog(scope.row)">人工匹配</el-button>
            <el-button type="text" size="mini" @click="goReviewFromExecutionRecord(scope.row)">去复盘</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!filteredExecutionRecords.length && !loadingRecords" :description="executionRecordEmptyText" />
    </el-card>

    <el-collapse v-model="executionSecondaryPanels" class="secondary-section-collapse">
      <el-collapse-item name="chain">
        <template slot="title">
          <span class="collapse-title">闭环链路与处理建议</span>
          <span class="collapse-meta">{{ executionChainSummary.headline }}</span>
        </template>
        <el-row :gutter="16">
          <el-col :xs="24" :xl="14">
            <el-card shadow="never" class="box-card">
              <div class="chain-grid">
                <div
                  v-for="item in executionChainSummary.stages"
                  :key="item.key"
                  class="chain-card"
                  :class="`chain-card--${item.status}`"
                >
                  <div class="chain-card__title">{{ item.title }}</div>
                  <div class="chain-card__value">{{ item.value }}</div>
                  <div class="chain-card__summary">{{ item.summary }}</div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="10">
            <el-card shadow="never" class="box-card" v-loading="loadingActionItems">
              <div class="plain-list">
                <div v-for="line in executionActionPlan.summaryLines" :key="line" class="plain-list-item">
                  {{ line }}
                </div>
              </div>
              <div v-if="executionActionPlan.nextActions.length" class="ops-action-list">
                <div
                  v-for="item in executionActionPlan.nextActions"
                  :key="item.renderKey"
                  class="ops-action-item"
                >
                  <div class="ops-action-main">
                    <div class="ops-action-title">
                      <el-tag size="mini" :type="priorityTagType(item.priority)">{{ item.priority }}</el-tag>
                      <span>{{ item.title }}</span>
                      <el-tag v-if="item.isCrossPage" size="mini" type="info">跨页</el-tag>
                    </div>
                    <div class="ops-action-reason">{{ item.reason }}</div>
                  </div>
                  <el-button type="text" size="mini" @click="goRouteLink(item)">去处理</el-button>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-collapse-item>

      <el-collapse-item name="tools">
        <template slot="title">
          <span class="collapse-title">手工处理工具</span>
          <span class="collapse-meta">低频使用，默认收起</span>
        </template>
        <el-row :gutter="16">
          <el-col :xs="24" :xl="12">
            <el-card ref="importSection" shadow="never" class="box-card">
              <div slot="header">
                <span>手工成交回写（仅在券商回流缺失时使用）</span>
              </div>
          <el-form ref="recordFormRef" :model="recordForm" :rules="recordRules" label-width="100px" size="small">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="股票代码" prop="stockCode">
                  <el-input v-model="recordForm.stockCode" placeholder="000001" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="买卖方向" prop="side">
                  <el-select v-model="recordForm.side" style="width: 100%">
                    <el-option label="BUY" value="BUY" />
                    <el-option label="SELL" value="SELL" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="数量" prop="quantity">
                  <el-input-number v-model="recordForm.quantity" :min="1" :step="100" controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="价格" prop="price">
                  <el-input-number v-model="recordForm.price" :min="0.01" :precision="3" :step="0.01" controls-position="right" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="成交日期" prop="tradeDate">
                  <el-date-picker v-model="recordForm.tradeDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="策略ID" prop="strategyId">
                  <el-select v-model="recordForm.strategyId" clearable filterable style="width: 100%">
                    <el-option
                      v-for="item in strategyList"
                      :key="item.id"
                      :label="`${item.id} - ${item.strategy_name}`"
                      :value="item.id"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="信号ID">
                  <el-input-number v-model="recordForm.signalId" :min="1" controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="外部订单号">
                  <el-input v-model="recordForm.externalOrderId" placeholder="券商订单号(可选)" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="佣金">
                  <el-input-number v-model="recordForm.commission" :min="0" :precision="4" :step="0.0001" controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="印花税">
                  <el-input-number v-model="recordForm.tax" :min="0" :precision="4" :step="0.0001" controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="滑点">
                  <el-input-number v-model="recordForm.slippage" :min="0" :precision="4" :step="0.0001" controls-position="right" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item>
              <el-button type="primary" :loading="loadingRecord" @click="submitRecord">提交成交回写</el-button>
              <el-button @click="resetRecordForm">重置</el-button>
            </el-form-item>
          </el-form>
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="12">
            <el-card ref="recordEntrySection" shadow="never" class="box-card">
              <div slot="header" class="clearfix">
                <span>批量导入</span>
                <el-link
              style="float:right;"
              type="primary"
              :underline="false"
              href="/templates/execution-import-template.csv"
              target="_blank"
            >
              下载 CSV 模板
            </el-link>
          </div>
          <el-tabs v-model="importMode">
            <el-tab-pane label="浏览器上传（推荐）" name="upload">
              <el-form :inline="true" label-width="80px" size="small">
                <el-form-item label="CSV 文件">
                  <el-upload
                    ref="uploadRef"
                    action="#"
                    :auto-upload="false"
                    :file-list="uploadFileList"
                    :limit="1"
                    accept=".csv"
                    :on-change="handleUploadChange"
                    :on-remove="handleUploadRemove"
                  >
                    <el-button size="small">选择 CSV</el-button>
                  </el-upload>
                </el-form-item>
                <el-form-item label="策略ID">
                  <el-input-number v-model="importForm.strategyId" :min="1" controls-position="right" />
                </el-form-item>
                <el-form-item>
                  <el-button :loading="loadingValidateImportUpload" @click="previewUploadImport">预校验</el-button>
                  <el-button
                    type="primary"
                    :loading="loadingImportUpload"
                    :disabled="!canConfirmUploadImport"
                    @click="submitUploadImport"
                  >
                    确认导入
                  </el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>
            <el-tab-pane label="服务端路径导入" name="path">
              <el-form :inline="true" :model="importForm" label-width="100px" size="small">
                <el-form-item label="CSV 文件路径">
                  <el-input v-model="importForm.file" placeholder="D:\\data\\executions.csv" style="width: 360px" />
                </el-form-item>
                <el-form-item label="策略ID">
                  <el-input-number v-model="importForm.strategyId" :min="1" controls-position="right" />
                </el-form-item>
                <el-form-item>
                  <el-button :loading="loadingValidateImportPath" @click="previewImportByPath">预校验</el-button>
                  <el-button
                    type="primary"
                    :loading="loadingImportPath"
                    :disabled="!canConfirmPathImport"
                    @click="submitImportByPath"
                  >
                    确认导入
                  </el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
          <div v-if="importValidation" class="validation-block">
            <div class="validation-summary">
              <el-tag size="mini" :type="importValidation.canImport ? 'success' : 'warning'">
                {{ importValidation.canImport ? '可导入' : '需处理后再导入' }}
              </el-tag>
              <span>总行数 {{ importValidation.totalRows || 0 }}</span>
              <span>有效 {{ importValidation.validRows || 0 }}</span>
              <span>无效 {{ importValidation.invalidRows || 0 }}</span>
              <span>重复 {{ importValidation.duplicateRows || 0 }}</span>
              <span>未匹配信号 {{ importValidation.unmatchedSignalRows || 0 }}</span>
            </div>
            <el-table
              v-if="Array.isArray(importValidation.previewRows) && importValidation.previewRows.length"
              :data="importValidation.previewRows.slice(0, 12)"
              border
              size="mini"
              class="validation-table"
            >
              <el-table-column label="行号" prop="rowNo" width="70" />
              <el-table-column label="状态" width="120">
                <template slot-scope="scope">
                  <el-tag size="mini" :type="validationStatusType(scope.row.status)">{{ scope.row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="代码" prop="stockCode" width="100" />
              <el-table-column label="方向" prop="side" width="90" />
              <el-table-column label="策略ID" prop="strategyId" width="90" />
              <el-table-column label="信号ID" prop="signalId" width="90" />
              <el-table-column label="建议动作" width="120">
                <template slot-scope="scope">
                  <el-tag size="mini" :type="previewActionTagType(scope.row.recommendedAction)">
                    {{ scope.row.actionLabel || previewActionLabel(scope.row.recommendedAction) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="说明" prop="message" min-width="260" show-overflow-tooltip />
              <el-table-column label="处理" width="90" fixed="right">
                <template slot-scope="scope">
                  <el-button
                    v-if="scope.row.recommendedAction !== 'NO_ACTION'"
                    type="text"
                    size="mini"
                    @click="handlePreviewRowAction(scope.row)"
                  >
                    去处理
                  </el-button>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
              <div class="output-title">最近执行输出</div>
              <el-input
                v-model="lastOutput"
                type="textarea"
                :rows="10"
                placeholder="回写/导入输出内容"
              />
            </el-card>
          </el-col>
        </el-row>
      </el-collapse-item>

      <el-collapse-item name="assist">
        <template slot="title">
          <span class="collapse-title">辅助核对与持仓同步</span>
          <span class="collapse-meta">信号、反馈、持仓差异</span>
        </template>
        <el-row :gutter="16">
          <el-col :xs="24" :xl="12">
            <el-card ref="signalSection" shadow="never" class="box-card">
          <div slot="header">
            <span>交易信号（辅助回写）</span>
          </div>
          <el-form :inline="true" size="small">
            <el-form-item label="信号日期">
              <el-date-picker
                v-model="signalDate"
                type="date"
                value-format="yyyy-MM-dd"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="代码">
              <el-input v-model="signalFilter.stockCode" placeholder="000001" style="width: 120px" />
            </el-form-item>
            <el-form-item label="方向">
              <el-select v-model="signalFilter.side" clearable style="width: 100px">
                <el-option label="BUY" value="BUY" />
                <el-option label="SELL" value="SELL" />
              </el-select>
            </el-form-item>
            <el-form-item label="策略">
              <el-select v-model="signalFilter.strategyId" clearable filterable style="width: 140px">
                <el-option
                  v-for="item in strategyList"
                  :key="item.id"
                  :label="`${item.id} - ${item.strategy_name}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadSignals">查询信号</el-button>
            </el-form-item>
            <el-form-item>
              <el-button @click="resetSignalFilter">清空筛选</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="loadingSignals" :data="filteredSignals" border height="300">
            <el-table-column label="信号ID" prop="id" width="90" />
            <el-table-column label="代码" prop="stock_code" width="100" />
            <el-table-column label="名称" prop="stock_name" min-width="120" />
            <el-table-column label="方向" prop="signal_type" width="90" />
            <el-table-column label="价格" prop="suggest_price" width="90" />
            <el-table-column label="策略ID" prop="strategy_id" width="90" />
            <el-table-column label="应执行日" prop="execution_due_date" width="110" />
            <el-table-column label="处理提示" width="120">
              <template slot-scope="scope">
                <el-tag size="mini" :type="signalHintTagType(scope.row.match_hint)">
                  {{ signalHintLabel(scope.row.match_hint) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="已执行" width="90">
              <template slot-scope="scope">
                <el-tag size="mini" :type="Number(scope.row.is_execute) === 1 ? 'success' : 'warning'">
                  {{ Number(scope.row.is_execute) === 1 ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
              <template slot-scope="scope">
                <el-button
                  v-if="Number(scope.row.is_execute) !== 1"
                  type="text"
                  size="mini"
                  @click="handleSignalRecord(scope.row)"
                >
                  带入回写
                </el-button>
                <el-button
                  type="text"
                  size="mini"
                  @click="handleSignalViewRecords(scope.row)"
                >
                  查看成交
                </el-button>
                <el-button
                  type="text"
                  size="mini"
                  @click="goReviewFromSignal(scope.row)"
                >
                  去复盘
                </el-button>
              </template>
            </el-table-column>
              </el-table>
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="12">
            <el-card ref="feedbackSection" shadow="never" class="box-card">
          <div slot="header">
            <span>{{ feedbackTitle }}</span>
          </div>
          <el-form :inline="true" size="small">
            <el-form-item label="条数">
              <el-input-number v-model="feedbackLimit" :min="1" :max="200" controls-position="right" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadFeedback">刷新反馈</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="loadingFeedback" :data="filteredFeedbackRows" border height="300" :row-class-name="feedbackRowClassName">
            <el-table-column label="信号ID" prop="signal_id" width="90" />
            <el-table-column label="代码" prop="stock_code" width="100" />
            <el-table-column label="状态" width="100">
              <template slot-scope="scope">
                <el-tag size="mini" :type="feedbackTagType(scope.row)">
                  {{ feedbackTagLabel(scope.row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="检查日" prop="check_date" width="110" />
            <el-table-column label="已执行数量" prop="executed_quantity" width="100" />
            <el-table-column label="逾期天数" prop="overdue_days" width="90" />
            <el-table-column label="关联成交" min-width="130">
              <template slot-scope="scope">
                <span v-if="matchedExecutionText(scope.row)">{{ matchedExecutionText(scope.row) }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="建议动作" width="140">
              <template slot-scope="scope">
                <el-tag size="mini" :type="feedbackActionTagType(scope.row.feedback_action)">
                  {{ feedbackActionLabel(scope.row.feedback_action) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="备注" prop="remark" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="170">
              <template slot-scope="scope">
                <el-button
                  v-if="canMarkExecutionException(scope.row)"
                  type="text"
                  size="mini"
                  @click="openExceptionDialog(scope.row)"
                >
                  标记异常
                </el-button>
                <el-button type="text" size="mini" @click="goReviewFromFeedback(scope.row)">去复盘</el-button>
              </template>
            </el-table-column>
              </el-table>
              <el-empty v-if="!filteredFeedbackRows.length && !loadingFeedback" :description="feedbackEmptyText" />
            </el-card>
          </el-col>
        </el-row>

        <el-card ref="positionSection" shadow="never" class="box-card mt16">
          <div slot="header" class="clearfix">
            <span>持仓同步结果</span>
            <span class="header-meta">
              <el-tag size="mini" :type="positionSyncTagType(positionSyncResult.syncStatus)">
                {{ positionSyncResult.syncStatus || 'EMPTY' }}
              </el-tag>
            </span>
          </div>
          <div class="sync-summary">
            <span>差异项：{{ positionSyncResult.differenceCount || 0 }}</span>
            <span>真实持仓：{{ positionBeforeCount }}</span>
            <span>推导持仓：{{ positionAfterCount }}</span>
          </div>
          <el-table v-loading="loadingPositionSync" :data="positionSyncResult.differenceItems || []" border>
            <el-table-column label="代码" prop="stockCode" width="100" />
            <el-table-column label="差异类型" prop="differenceType" width="110" />
            <el-table-column label="真实数量" prop="actualQuantity" width="100" />
            <el-table-column label="推导数量" prop="derivedQuantity" width="100" />
            <el-table-column label="真实成本" prop="actualCostPrice" width="100" />
            <el-table-column label="推导成本" prop="derivedCostPrice" width="100" />
          </el-table>
          <el-empty
            v-if="!loadingPositionSync && (!positionSyncResult.differenceItems || !positionSyncResult.differenceItems.length)"
            :description="positionSyncResult.syncStatus === 'MATCH' ? '当前持仓与已匹配成交一致' : '暂无持仓同步差异数据'"
          />
        </el-card>
      </el-collapse-item>
    </el-collapse>

    <el-dialog title="人工确认成交匹配" :visible.sync="matchDialogVisible" width="760px" append-to-body>
      <div v-if="selectedExecutionRecord" class="match-dialog-meta">
        <span>成交ID：{{ selectedExecutionRecord.id }}</span>
        <span>代码：{{ selectedExecutionRecord.stock_code }}</span>
        <span>方向：{{ selectedExecutionRecord.side }}</span>
        <span>策略：{{ selectedExecutionRecord.strategy_id }}</span>
      </div>
      <el-table v-loading="loadingMatchCandidates" :data="matchCandidates" border max-height="360">
        <el-table-column label="选择" width="70">
          <template slot-scope="scope">
            <el-radio v-model="selectedCandidateSignalId" :label="scope.row.signalId">{{ '' }}</el-radio>
          </template>
        </el-table-column>
        <el-table-column label="信号ID" prop="signalId" width="90" />
        <el-table-column label="代码" prop="stockCode" width="100" />
        <el-table-column label="方向" prop="signalType" width="90" />
        <el-table-column label="日期" prop="signalDate" width="110" />
        <el-table-column label="匹配分" prop="matchScore" width="90" />
        <el-table-column label="原因" prop="matchReason" min-width="180" show-overflow-tooltip />
        <el-table-column label="已执行" width="90">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.alreadyExecuted ? 'info' : 'success'">
              {{ scope.row.alreadyExecuted ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingMatchCandidates && !matchCandidates.length" description="暂无可用候选，请检查方向、策略或信号日期" />
      <span slot="footer" class="dialog-footer">
        <el-button @click="closeMatchDialog">取消</el-button>
        <el-button type="primary" :loading="confirmingMatch" @click="submitConfirmMatch">确认匹配</el-button>
      </span>
    </el-dialog>

    <el-dialog title="标记执行异常" :visible.sync="exceptionDialogVisible" width="520px" append-to-body>
      <div v-if="selectedFeedbackRow" class="match-dialog-meta">
        <span>信号ID：{{ selectedFeedbackRow.signal_id }}</span>
        <span>代码：{{ selectedFeedbackRow.stock_code }}</span>
        <span>当前状态：{{ selectedFeedbackRow.status }}</span>
      </div>
      <el-form :model="exceptionForm" label-width="100px" size="small">
        <el-form-item label="异常类型">
          <el-select v-model="exceptionForm.exceptionType" style="width: 100%">
            <el-option label="MISSED" value="MISSED" />
            <el-option label="CANCELLED" value="CANCELLED" />
            <el-option label="MANUAL_REVIEW" value="MANUAL_REVIEW" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="exceptionForm.remark"
            type="textarea"
            :rows="4"
            placeholder="例如：券商拒单、盘后撤单、人工确认无需执行"
          />
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button @click="closeExceptionDialog">取消</el-button>
        <el-button type="primary" :loading="markingException" @click="submitMarkException">确认标记</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import ElCollapse from 'element-ui/lib/collapse'
import ElCollapseItem from 'element-ui/lib/collapse-item'
import ElLink from 'element-ui/lib/link'
import ElUpload from 'element-ui/lib/upload'
import 'element-ui/lib/theme-chalk/collapse.css'
import 'element-ui/lib/theme-chalk/link.css'
import 'element-ui/lib/theme-chalk/upload.css'
import {
  confirmExecutionMatch,
  getDashboardActionItems,
  getExecutionReconciliationSummary,
  getPositionSyncResult,
  listExecutionFeedbackDetails,
  listExecutionMatchCandidates,
  listExecutionRecords,
  listSignals,
  listStrategies,
  markExecutionException,
  runImportExecutions,
  runImportExecutionsUpload,
  runRecordExecution,
  validateExecutionImport,
  validateExecutionImportUpload
} from '@/api/quant'
import { extractSignalRows } from '@/views/quant/signal-response'
import { buildReviewRouteQuery } from '@/views/quant/review/review-context'
const { buildExecutionActionPlan, buildExecutionChainSummary } = require('./execution-explain')
const {
  applyExecutionRouteContext,
  didExecutionRouteDataChange,
  normalizeExecutionRouteContext
} = require('./execution-route')
const {
  getExecutionClosureCriticalKeys,
  getExecutionClosureDeferredKeys,
  resolveExecutionFirstScreenState
} = require('./execution-page-state')

const EXECUTION_ACTION_TYPES = [
  'DATA_INTEGRITY_REVIEW',
  'PIPELINE_RECOVERY',
  'PIPELINE_WAIT',
  'EXECUTION_RECONCILIATION',
  'PARTIAL_EXECUTION',
  'PENDING_SIGNAL_EXECUTION',
  'POSITION_SYNC_DIFF'
]

export default {
  name: 'QuantExecutionCenter',
  components: {
    ElCollapse,
    ElCollapseItem,
    ElLink,
    ElUpload
  },
  data() {
    return {
      strategyList: [],
      loadingRecord: false,
      loadingValidateImportPath: false,
      loadingValidateImportUpload: false,
      loadingImportPath: false,
      loadingImportUpload: false,
      loadingRecords: false,
      loadingSignals: false,
      loadingFeedback: false,
      loadingPositionSync: false,
      loadingActionItems: false,
      loadingMatchCandidates: false,
      confirmingMatch: false,
      markingException: false,
      importMode: 'upload',
      uploadFileList: [],
      uploadRawFile: null,
      signalDate: this.todayString(),
      feedbackLimit: 50,
      lastOutput: '',
      importValidation: null,
      reconciliationSummary: {},
      positionSyncResult: {},
      executionActionItems: [],
      executionRecords: [],
      signals: [],
      feedbackRows: [],
      matchDialogVisible: false,
      exceptionDialogVisible: false,
      selectedExecutionRecord: null,
      selectedFeedbackRow: null,
      matchCandidates: [],
      selectedCandidateSignalId: undefined,
      executionRefreshRequestId: 0,
      executionSecondaryPanels: [],
      routeContext: normalizeExecutionRouteContext(),
      focusPreset: 'all',
      signalFilter: {
        stockCode: '',
        side: '',
        strategyId: undefined
      },
      recordQuery: {
        stockCode: '',
        limit: 50
      },
      recordForm: {
        stockCode: '',
        side: 'BUY',
        quantity: 100,
        price: 10,
        tradeDate: this.todayString(),
        strategyId: undefined,
        signalId: undefined,
        commission: 0,
        tax: 0,
        slippage: 0,
        externalOrderId: ''
      },
      importForm: {
        file: '',
        strategyId: undefined
      },
      exceptionForm: {
        exceptionType: 'MISSED',
        remark: ''
      },
      recordRules: {
        stockCode: [{ required: true, message: '请输入股票代码', trigger: 'blur' }],
        side: [{ required: true, message: '请选择买卖方向', trigger: 'change' }],
        quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
        price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
        tradeDate: [{ required: true, message: '请选择成交日期', trigger: 'change' }],
        strategyId: [{ required: true, message: '请选择策略', trigger: 'change' }]
      }
    }
  },
  computed: {
    abnormalFeedbackCount() {
      return this.feedbackRows.filter(row => this.isAbnormalFeedback(row)).length
    },
    focusPresetLabel() {
      const labels = {
        all: '全部',
        unmatched: '只看未匹配成交',
        abnormal: '只看异常反馈',
        partial: '只看部分成交',
        positionDiff: '查看持仓差异'
      }
      return labels[this.focusPreset] || '全部'
    },
    filteredExecutionRecords() {
      if (this.focusPreset === 'unmatched') {
        return this.executionRecords.filter(row => this.isUnmatchedExecution(row))
      }
      return this.executionRecords
    },
    filteredFeedbackRows() {
      if (this.focusPreset === 'abnormal') {
        return this.feedbackRows.filter(row => this.isAbnormalFeedback(row))
      }
      if (this.focusPreset === 'partial') {
        return this.feedbackRows.filter(row => this.isPartialFeedback(row))
      }
      return this.feedbackRows
    },
    filteredSignals() {
      return this.signals.filter(row => {
        const stockCode = (this.signalFilter.stockCode || '').trim()
        const side = (this.signalFilter.side || '').trim()
        const strategyId = this.signalFilter.strategyId
        if (stockCode && String(row.stock_code || '').indexOf(stockCode) < 0) {
          return false
        }
        if (side && String(row.signal_type || '') !== side) {
          return false
        }
        if (strategyId && Number(row.strategy_id) !== Number(strategyId)) {
          return false
        }
        return true
      })
    },
    executionRecordTitle() {
      return this.focusPreset === 'unmatched' ? '执行记录（只看未匹配成交）' : '执行记录'
    },
    executionRecordEmptyText() {
      return this.focusPreset === 'unmatched' ? '最近加载的执行记录中暂无未匹配成交' : '暂无执行记录'
    },
    feedbackTitle() {
      if (this.focusPreset === 'abnormal') {
        return 'T+1 执行反馈（只看异常反馈）'
      }
      if (this.focusPreset === 'partial') {
        return 'T+1 执行反馈（只看部分成交）'
      }
      return 'T+1 执行反馈'
    },
    feedbackEmptyText() {
      if (this.focusPreset === 'abnormal') {
        return '最近加载的反馈中暂无异常项'
      }
      if (this.focusPreset === 'partial') {
        return '最近加载的反馈中暂无部分成交项'
      }
      return '暂无执行反馈数据'
    },
    positionBeforeCount() {
      return Array.isArray(this.positionSyncResult.positionBefore) ? this.positionSyncResult.positionBefore.length : 0
    },
    positionAfterCount() {
      return Array.isArray(this.positionSyncResult.positionAfter) ? this.positionSyncResult.positionAfter.length : 0
    },
    canConfirmPathImport() {
      return this.isImportValidationUsable('path')
    },
    canConfirmUploadImport() {
      return this.isImportValidationUsable('upload')
    },
    executionChainSummary() {
      return buildExecutionChainSummary({
        reconciliationSummary: this.reconciliationSummary,
        positionSyncResult: this.positionSyncResult,
        abnormalFeedbackCount: this.abnormalFeedbackCount,
        focusPreset: this.focusPreset
      })
    },
    topExecutionAction() {
      return Array.isArray(this.executionActionItems) && this.executionActionItems.length
        ? this.executionActionItems[0]
        : null
    },
    executionActionPlan() {
      return buildExecutionActionPlan({
        actionItems: this.executionActionItems,
        reconciliationSummary: this.reconciliationSummary,
        positionSyncResult: this.positionSyncResult,
        abnormalFeedbackCount: this.abnormalFeedbackCount,
        focusPreset: this.focusPreset
      })
    },
    executionFirstScreen() {
      return resolveExecutionFirstScreenState({
        topExecutionAction: this.topExecutionAction,
        reconciliationSummary: this.reconciliationSummary,
        positionSyncResult: this.positionSyncResult,
        abnormalFeedbackCount: this.abnormalFeedbackCount
      })
    }
  },
  created() {
    this.syncRouteContext()
    this.loadStrategies()
    this.refreshExecutionClosure()
  },
  watch: {
    '$route.query': {
      handler() {
        const changed = this.syncRouteContext()
        if (changed) {
          this.refreshExecutionClosure()
        }
      },
      deep: true
    },
    'importForm.file'() {
      if (this.importMode === 'path') {
        this.clearImportValidation()
      }
    },
    'importForm.strategyId'() {
      this.clearImportValidation()
    }
  },
  methods: {
    todayString() {
      const now = new Date()
      const year = now.getFullYear()
      const month = `${now.getMonth() + 1}`.padStart(2, '0')
      const day = `${now.getDate()}`.padStart(2, '0')
      return `${year}-${month}-${day}`
    },
    normalizeRouteQuery(query) {
      const normalized = {}
      Object.keys(query || {}).forEach(key => {
        const value = query[key]
        if (value !== undefined && value !== null && value !== '') {
          normalized[key] = value
        }
      })
      return normalized
    },
    goRouteLink(item) {
      if (!item) {
        return
      }
      const path = item.targetPage || item.path
      if (!path) {
        return
      }
      this.$router.push({
        path,
        query: this.normalizeRouteQuery(item.targetQuery || item.query || {})
      }).catch(() => {})
    },
    handleExecutionScreenAction(item) {
      if (!item) {
        return
      }
      if (item.action === 'route' || item.isCrossPage) {
        this.goRouteLink(item)
        return
      }
      if (String(item.action || '').indexOf('focus:') === 0) {
        this.applyFocusPreset(String(item.action).slice('focus:'.length))
        return
      }
      if (String(item.action || '').indexOf('section:') === 0) {
        this.scrollToSection(String(item.action).slice('section:'.length))
        return
      }
      if (item.targetPage || item.path) {
        this.goRouteLink(item)
      }
    },
    async loadStrategies() {
      const response = await listStrategies()
      this.strategyList = Array.isArray(response.data) ? response.data : []
      if (!this.recordForm.strategyId && this.strategyList.length) {
        this.recordForm.strategyId = this.strategyList[0].id
      }
      if (!this.importForm.strategyId && this.strategyList.length) {
        this.importForm.strategyId = this.strategyList[0].id
      }
    },
    submitRecord() {
      this.$refs.recordFormRef.validate(valid => {
        if (!valid) {
          return
        }
        this.doSubmitRecord()
      })
    },
    async doSubmitRecord() {
      this.loadingRecord = true
      try {
        const payload = { ...this.recordForm }
        if (!payload.signalId) {
          delete payload.signalId
        }
        const response = await runRecordExecution(payload)
        this.lastOutput = this.stringifyOutput(response.data)
        this.$modal.msgSuccess('成交回写成功')
        await this.refreshExecutionClosure()
      } finally {
        this.loadingRecord = false
      }
    },
    validateImportPath(path) {
      const normalized = (path || '').trim()
      if (!normalized) {
        return '请输入导入文件路径'
      }
      const isWindowsAbs = /^[a-zA-Z]:[\\/]/.test(normalized)
      const isLinuxAbs = normalized.startsWith('/')
      if (!isWindowsAbs && !isLinuxAbs) {
        return '请输入绝对路径（Windows 如 D:\\\\data\\\\executions.csv）'
      }
      if (!normalized.toLowerCase().endsWith('.csv')) {
        return '仅支持 .csv 文件'
      }
      return ''
    },
    currentImportValidationKey(mode) {
      if (mode === 'upload') {
        if (!this.uploadRawFile) {
          return ''
        }
        return [
          mode,
          this.uploadRawFile.name,
          this.uploadRawFile.size,
          this.uploadRawFile.lastModified,
          this.importForm.strategyId || ''
        ].join('|')
      }
      return [
        mode,
        (this.importForm.file || '').trim(),
        this.importForm.strategyId || ''
      ].join('|')
    },
    clearImportValidation() {
      this.importValidation = null
    },
    isImportValidationUsable(mode) {
      return !!(
        this.importValidation &&
        this.importValidation.canImport &&
        this.importValidation.validationMode === mode &&
        this.importValidation.validationKey === this.currentImportValidationKey(mode)
      )
    },
    applyImportValidation(payload, mode) {
      this.importValidation = {
        ...(payload || {}),
        validationMode: mode,
        validationKey: this.currentImportValidationKey(mode)
      }
    },
    validationStatusType(status) {
      if (status === 'VALID') return 'success'
      if (status === 'UNMATCHED_SIGNAL') return 'warning'
      return 'danger'
    },
    previewActionTagType(action) {
      if (action === 'NO_ACTION') return 'success'
      if (action === 'CHECK_SIGNAL_MATCH' || action === 'REVIEW_DUPLICATE_EXECUTION') return 'warning'
      return 'danger'
    },
    previewActionLabel(action) {
      const labels = {
        NO_ACTION: '无需处理',
        CHECK_SIGNAL_MATCH: '核对信号',
        REVIEW_DUPLICATE_EXECUTION: '核重成交',
        FIX_SOURCE_FILE: '修正文件'
      }
      return labels[action] || '去处理'
    },
    handleUploadChange(file, fileList) {
      const latestList = fileList.slice(-1)
      this.uploadFileList = latestList
      this.uploadRawFile = latestList.length ? latestList[0].raw : null
      this.clearImportValidation()
    },
    handleUploadRemove() {
      this.uploadFileList = []
      this.uploadRawFile = null
      this.clearImportValidation()
    },
    async previewImportByPath() {
      const pathError = this.validateImportPath(this.importForm.file)
      if (pathError) {
        this.$modal.msgWarning(pathError)
        return
      }
      this.loadingValidateImportPath = true
      try {
        const response = await validateExecutionImport(this.importForm)
        this.applyImportValidation(response.data, 'path')
        this.lastOutput = this.stringifyOutput(response.data)
        this.$modal.msgSuccess('导入预校验完成')
      } finally {
        this.loadingValidateImportPath = false
      }
    },
    async previewUploadImport() {
      if (!this.uploadRawFile) {
        this.$modal.msgWarning('请先选择 CSV 文件')
        return
      }
      this.loadingValidateImportUpload = true
      try {
        const formData = new FormData()
        formData.append('file', this.uploadRawFile)
        if (this.importForm.strategyId) {
          formData.append('strategyId', String(this.importForm.strategyId))
        }
        const response = await validateExecutionImportUpload(formData)
        this.applyImportValidation(response.data, 'upload')
        this.lastOutput = this.stringifyOutput(response.data)
        this.$modal.msgSuccess('导入预校验完成')
      } finally {
        this.loadingValidateImportUpload = false
      }
    },
    async submitImportByPath() {
      if (!this.isImportValidationUsable('path')) {
        this.$modal.msgWarning('请先完成当前文件的预校验')
        return
      }
      const pathError = this.validateImportPath(this.importForm.file)
      if (pathError) {
        this.$modal.msgWarning(pathError)
        return
      }
      this.loadingImportPath = true
      try {
        const response = await runImportExecutions(this.importForm)
        this.lastOutput = this.stringifyOutput(response.data)
        this.clearImportValidation()
        await this.refreshExecutionClosure()
        await this.handleImportResult(response.data, '按路径导入成功')
      } finally {
        this.loadingImportPath = false
      }
    },
    async submitUploadImport() {
      if (!this.isImportValidationUsable('upload')) {
        this.$modal.msgWarning('请先完成当前文件的预校验')
        return
      }
      if (!this.uploadRawFile) {
        this.$modal.msgWarning('请先选择 CSV 文件')
        return
      }
      this.loadingImportUpload = true
      try {
        const formData = new FormData()
        formData.append('file', this.uploadRawFile)
        if (this.importForm.strategyId) {
          formData.append('strategyId', String(this.importForm.strategyId))
        }
        const response = await runImportExecutionsUpload(formData)
        this.lastOutput = this.stringifyOutput(response.data)
        this.uploadFileList = []
        this.uploadRawFile = null
        this.clearImportValidation()
        this.$refs.uploadRef && this.$refs.uploadRef.clearFiles()
        await this.refreshExecutionClosure()
        await this.handleImportResult(response.data, '上传导入成功')
      } finally {
        this.loadingImportUpload = false
      }
    },
    resetRecordForm() {
      this.recordForm = {
        stockCode: '',
        side: 'BUY',
        quantity: 100,
        price: 10,
        tradeDate: this.todayString(),
        strategyId: this.strategyList.length ? this.strategyList[0].id : undefined,
        signalId: undefined,
        commission: 0,
        tax: 0,
        slippage: 0,
        externalOrderId: ''
      }
      this.$nextTick(() => this.$refs.recordFormRef && this.$refs.recordFormRef.clearValidate())
    },
    resetSignalFilter() {
      this.signalFilter = {
        stockCode: '',
        side: '',
        strategyId: undefined
      }
    },
    prefillRecordFormFromSignal(row) {
      if (!row) {
        return
      }
      this.recordForm = {
        ...this.recordForm,
        stockCode: row.stock_code || '',
        side: row.signal_type || 'BUY',
        price: Number(row.suggest_price || this.recordForm.price || 10),
        tradeDate: row.execution_due_date || this.todayString(),
        strategyId: row.strategy_id || this.recordForm.strategyId,
        signalId: row.id || undefined
      }
    },
    handleSignalRecord(row) {
      this.prefillRecordFormFromSignal(row)
      this.$nextTick(() => {
        this.scrollToSection('recordEntrySection')
        this.$refs.recordFormRef && this.$refs.recordFormRef.clearValidate()
      })
      this.$modal.msgSuccess('已带入回写表单，请补充真实成交数量与费用后提交。')
    },
    async handleSignalViewRecords(row) {
      this.recordQuery.stockCode = row && row.stock_code ? row.stock_code : ''
      await this.loadExecutionRecords()
      this.$nextTick(() => this.scrollToSection('recordSection'))
      this.$modal.msgSuccess('已定位到执行记录区。')
    },
    buildReviewQuery(payload) {
      return buildReviewRouteQuery({
        reviewLevel: 'trade',
        sourcePage: 'execution',
        ...payload
      })
    },
    goReview(query) {
      this.$router.push({
        path: '/quant/review',
        query: this.buildReviewQuery(query)
      })
    },
    goReviewFromSignal(row) {
      if (!row) {
        return
      }
      this.goReview({
        stockCode: row.stock_code,
        strategyId: row.strategy_id,
        signalId: row.id,
        sourceAction: 'signal'
      })
    },
    goReviewFromExecutionRecord(row) {
      if (!row) {
        return
      }
      this.goReview({
        stockCode: row.stock_code,
        strategyId: row.strategy_id,
        signalId: row.signal_id,
        sourceAction: this.isUnmatchedExecution(row) ? 'unmatchedExecution' : 'executionRecord'
      })
    },
    goReviewFromFeedback(row) {
      if (!row) {
        return
      }
      this.goReview({
        stockCode: row.stock_code,
        strategyId: row.strategy_id,
        signalId: row.signal_id,
        sourceAction: row.feedback_action || 'feedback'
      })
    },
    async loadExecutionRecords() {
      this.loadingRecords = true
      try {
        const response = await listExecutionRecords(this.recordQuery)
        this.executionRecords = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingRecords = false
      }
    },
    async handleImportResult(payload, successMessage) {
      const result = payload || {}
      const unmatchedRows = Array.isArray(result.unmatchedPreviewRows) ? result.unmatchedPreviewRows : []
      if (!unmatchedRows.length) {
        this.$modal.msgSuccess(successMessage)
        return
      }
      this.applyFocusPreset('unmatched')
      const firstRow = unmatchedRows[0]
      const matchedRecord = this.findImportedUnmatchedExecution(firstRow)
      if (unmatchedRows.length === 1 && matchedRecord) {
        this.$modal.msgWarning('导入已完成，但仍有 1 条成交未自动匹配，已为你打开人工匹配。')
        await this.openMatchDialog(matchedRecord)
        return
      }
      this.$nextTick(() => this.scrollToSection('recordSection'))
      this.$modal.msgWarning(`导入已完成，但仍有 ${unmatchedRows.length} 条成交需要人工匹配。`)
    },
    findImportedUnmatchedExecution(previewRow) {
      if (!previewRow) {
        return null
      }
      return this.executionRecords.find(row => (
        this.isUnmatchedExecution(row) &&
        String(row.stock_code || '') === String(previewRow.stockCode || '') &&
        String(row.side || '') === String(previewRow.side || '') &&
        String(row.trade_date || '') === String(previewRow.tradeDate || '') &&
        Number(row.strategy_id || 0) === Number(previewRow.strategyId || 0) &&
        Number(row.quantity || 0) === Number(previewRow.quantity || 0) &&
        Number(row.price || 0) === Number(previewRow.price || 0)
      )) || null
    },
    async loadSignals() {
      this.loadingSignals = true
      try {
        const response = await listSignals({ signalDate: this.signalDate })
        this.signals = extractSignalRows(response.data)
      } finally {
        this.loadingSignals = false
      }
    },
    async loadFeedback(requestId = this.executionRefreshRequestId) {
      this.loadingFeedback = true
      try {
        const response = await listExecutionFeedbackDetails({ limit: this.feedbackLimit })
        if (requestId === this.executionRefreshRequestId) {
          this.feedbackRows = Array.isArray(response.data) ? response.data : []
        }
      } finally {
        if (requestId === this.executionRefreshRequestId) {
          this.loadingFeedback = false
        }
      }
    },
    async loadReconciliationSummary() {
      const response = await getExecutionReconciliationSummary()
      this.reconciliationSummary = response.data || {}
    },
    async loadPositionSyncResult(params) {
      this.loadingPositionSync = true
      try {
        const response = await getPositionSyncResult(params)
        this.positionSyncResult = response.data || {}
      } finally {
        this.loadingPositionSync = false
      }
    },
    async loadExecutionActionItems() {
      this.loadingActionItems = true
      try {
        const response = await getDashboardActionItems({ limit: 8 })
        const rows = Array.isArray(response.data) ? response.data : []
        this.executionActionItems = rows.filter(item => EXECUTION_ACTION_TYPES.includes(String(item.actionType || '')))
      } finally {
        this.loadingActionItems = false
      }
    },
    async refreshExecutionClosure() {
      const requestId = this.executionRefreshRequestId + 1
      this.executionRefreshRequestId = requestId
      this.feedbackRows = []
      const taskMap = {
        records: () => this.loadExecutionRecords(),
        signals: () => this.loadSignals(),
        reconciliationSummary: () => this.loadReconciliationSummary(),
        positionSyncResult: () => this.loadPositionSyncResult(),
        actionItems: () => this.loadExecutionActionItems(),
        feedback: () => this.loadFeedback(requestId)
      }
      await Promise.all(getExecutionClosureCriticalKeys().map(key => taskMap[key]()))
      this.$nextTick(() => this.scrollToFocusTarget())
      this.scheduleDeferredWork(async () => {
        if (requestId !== this.executionRefreshRequestId) {
          return
        }
        await Promise.all(getExecutionClosureDeferredKeys().map(key => taskMap[key]()))
      })
    },
    scheduleDeferredWork(task) {
      const runner = () => Promise.resolve()
        .then(task)
        .catch(() => {})
      if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
        window.requestAnimationFrame(() => runner())
        return
      }
      setTimeout(() => runner(), 0)
    },
    normalizeFocusPreset(focus) {
      const normalized = String(focus || '').trim()
      const supported = ['all', 'unmatched', 'abnormal', 'partial', 'positionDiff']
      return supported.indexOf(normalized) >= 0 ? normalized : 'all'
    },
    syncRouteContext() {
      const routeContext = normalizeExecutionRouteContext(this.$route && this.$route.query ? this.$route.query : {})
      const changed = didExecutionRouteDataChange(this.routeContext, routeContext)
      this.routeContext = routeContext
      this.focusPreset = this.normalizeFocusPreset(routeContext.focusPreset)
      if (routeContext.hasTradeContext) {
        const nextState = applyExecutionRouteContext(routeContext, {
          signalFilter: this.signalFilter,
          recordQuery: this.recordQuery,
          recordForm: this.recordForm
        })
        this.signalFilter = nextState.signalFilter
        this.recordQuery = nextState.recordQuery
        this.recordForm = nextState.recordForm
      }
      this.$nextTick(() => this.scrollToRouteTarget(routeContext))
      return changed
    },
    applyFocusPreset(preset) {
      const normalized = this.normalizeFocusPreset(preset)
      if (this.$router && this.$route) {
        const query = { ...this.$route.query }
        if (normalized === 'all') {
          delete query.focus
        } else {
          query.focus = normalized
        }
        this.$router.replace({ path: this.$route.path, query }).catch(() => {})
      }
      this.focusPreset = normalized
      this.$nextTick(() => this.scrollToFocusTarget())
    },
    scrollToFocusTarget() {
      const targetMap = {
        unmatched: 'recordSection',
        abnormal: 'feedbackSection',
        partial: 'feedbackSection',
        positionDiff: 'positionSection'
      }
      const refName = targetMap[this.focusPreset]
      this.scrollToSection(refName)
    },
    scrollToRouteTarget(routeContext = {}) {
      if (this.focusPreset !== 'all') {
        this.scrollToFocusTarget()
        return
      }
      if (routeContext.hasTradeContext) {
        this.scrollToSection('importSection')
      }
    },
    scrollToSection(refName) {
      if (!refName || !this.$refs[refName]) {
        return
      }
      const target = this.$refs[refName].$el || this.$refs[refName]
      if (target && target.scrollIntoView) {
        target.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    },
    async handlePreviewRowAction(row) {
      if (!row || row.recommendedAction === 'NO_ACTION') {
        return
      }
      if (row.recommendedAction === 'CHECK_SIGNAL_MATCH') {
        this.signalDate = row.tradeDate || this.signalDate
        this.signalFilter = {
          stockCode: row.stockCode || '',
          side: row.side || '',
          strategyId: row.strategyId || undefined
        }
        this.prefillRecordFormFromPreview(row)
        await this.loadSignals()
        this.$nextTick(() => this.scrollToSection('signalSection'))
        this.$modal.msgWarning('已定位到辅助信号区并预填回写表单；若当日未找到信号，可切换前一交易日继续核对。')
        return
      }
      if (row.recommendedAction === 'REVIEW_DUPLICATE_EXECUTION') {
        this.recordQuery.stockCode = row.stockCode || ''
        await this.loadExecutionRecords()
        this.$nextTick(() => this.scrollToSection('recordSection'))
        this.$modal.msgWarning('已定位到执行记录区，请先核对是否重复导入或重复录入。')
        return
      }
      this.$nextTick(() => this.scrollToSection('importSection'))
      this.$modal.msgWarning('请先修正源文件中的错误行后再重新预校验。')
    },
    prefillRecordFormFromPreview(row) {
      if (!row) {
        return
      }
      this.recordForm = {
        ...this.recordForm,
        stockCode: row.stockCode || '',
        side: row.side || 'BUY',
        quantity: Number(row.quantity || 100),
        price: Number(row.price || 10),
        tradeDate: row.tradeDate || this.todayString(),
        strategyId: row.strategyId || this.recordForm.strategyId,
        signalId: row.signalId || undefined,
        externalOrderId: row.externalOrderId || ''
      }
    },
    isUnmatchedExecution(row) {
      return !!row && (row.match_status === 'UNMATCHED' || !row.signal_id)
    },
    isPartialExecution(row) {
      return !!row && row.match_status === 'PARTIAL'
    },
    isPartialFeedback(row) {
      return !!row && row.feedback_action === 'COMPLETE_PARTIAL_EXECUTION'
    },
    isAbnormalFeedback(row) {
      return !!row && row.feedback_action !== 'NO_ACTION'
    },
    executionRecordRowClassName({ row }) {
      if (this.isUnmatchedExecution(row)) {
        return 'row-unmatched'
      }
      if (this.isPartialExecution(row)) {
        return 'row-partial'
      }
      return ''
    },
    feedbackRowClassName({ row }) {
      if (this.isPartialFeedback(row)) {
        return 'row-partial'
      }
      if (row && row.status === 'MISSED') {
        return 'row-missed'
      }
      return ''
    },
    async openMatchDialog(row) {
      this.selectedExecutionRecord = row
      this.selectedCandidateSignalId = undefined
      this.matchCandidates = []
      this.matchDialogVisible = true
      this.loadingMatchCandidates = true
      try {
        const response = await listExecutionMatchCandidates({ executionRecordId: row.id, limit: 5 })
        this.matchCandidates = Array.isArray(response.data) ? response.data : []
        if (this.matchCandidates.length) {
          this.selectedCandidateSignalId = this.matchCandidates[0].signalId
        }
      } finally {
        this.loadingMatchCandidates = false
      }
    },
    closeMatchDialog() {
      this.matchDialogVisible = false
      this.selectedExecutionRecord = null
      this.selectedCandidateSignalId = undefined
      this.matchCandidates = []
    },
    openExceptionDialog(row) {
      this.selectedFeedbackRow = row
      this.exceptionForm = {
        exceptionType: row && row.status === 'MISSED' ? 'MISSED' : 'MANUAL_REVIEW',
        remark: row && row.remark ? row.remark : ''
      }
      this.exceptionDialogVisible = true
    },
    closeExceptionDialog() {
      this.exceptionDialogVisible = false
      this.selectedFeedbackRow = null
      this.exceptionForm = {
        exceptionType: 'MISSED',
        remark: ''
      }
    },
    async submitConfirmMatch() {
      if (!this.selectedExecutionRecord || !this.selectedCandidateSignalId) {
        this.$modal.msgWarning('请先选择一个候选信号')
        return
      }
      this.confirmingMatch = true
      try {
        const response = await confirmExecutionMatch({
          signalId: this.selectedCandidateSignalId,
          executionRecordId: this.selectedExecutionRecord.id,
          actor: 'ruoyi-ui',
          remark: 'manual_match_from_execution_page'
        })
        this.lastOutput = this.stringifyOutput(response.data)
        this.$modal.msgSuccess('成交与信号已完成匹配')
        this.closeMatchDialog()
        await this.refreshExecutionClosure()
      } finally {
        this.confirmingMatch = false
      }
    },
    async submitMarkException() {
      if (!this.selectedFeedbackRow || !this.selectedFeedbackRow.signal_id) {
        this.$modal.msgWarning('缺少可标记的信号')
        return
      }
      this.markingException = true
      try {
        const response = await markExecutionException({
          signalId: this.selectedFeedbackRow.signal_id,
          exceptionType: this.exceptionForm.exceptionType,
          remark: this.exceptionForm.remark,
          actor: 'ruoyi-ui'
        })
        this.lastOutput = this.stringifyOutput(response.data)
        this.$modal.msgSuccess('执行异常已标记')
        this.closeExceptionDialog()
        await this.refreshExecutionClosure()
      } finally {
        this.markingException = false
      }
    },
    matchStatusTagType(status) {
      if (status === 'EXECUTED') return 'success'
      if (status === 'PARTIAL') return 'warning'
      if (status === 'MISSED') return 'danger'
      if (status === 'UNMATCHED') return 'warning'
      return 'info'
    },
    signalHintTagType(hint) {
      if (hint === 'already_recorded_execution') return 'success'
      if (hint === 'pending_record_execution') return 'warning'
      return 'info'
    },
    signalHintLabel(hint) {
      const labels = {
        pending_record_execution: '待回写',
        already_recorded_execution: '已回写'
      }
      return labels[hint] || '待处理'
    },
    matchStatusLabel(status) {
      const labels = {
        UNMATCHED: '未匹配',
        PARTIAL: '部分闭环',
        EXECUTED: '已闭环',
        MISSED: '异常漏执行',
        PENDING: '待确认'
      }
      return labels[status] || '-'
    },
    feedbackTagType(row) {
      if (!row) return 'info'
      if (row.feedback_action === 'COMPLETE_PARTIAL_EXECUTION') return 'warning'
      if (row.status === 'EXECUTED') return 'success'
      if (row.status === 'MISSED') return 'danger'
      if (row.status === 'PENDING') return 'info'
      return 'info'
    },
    feedbackTagLabel(row) {
      if (row.feedback_action === 'COMPLETE_PARTIAL_EXECUTION') {
        return 'PARTIAL'
      }
      return row.status || '-'
    },
    feedbackActionTagType(action) {
      if (action === 'NO_ACTION') return 'success'
      if (action === 'RECORD_EXECUTION') return 'warning'
      if (action === 'COMPLETE_PARTIAL_EXECUTION') return 'warning'
      if (action === 'CANCELLED_CONFIRMED' || action === 'MISSED_CONFIRMED') return 'info'
      if (action === 'CHECK_EXCEPTION') return 'danger'
      return 'info'
    },
    feedbackActionLabel(action) {
      const labels = {
        NO_ACTION: '无需处理',
        RECORD_EXECUTION: '补录成交',
        COMPLETE_PARTIAL_EXECUTION: '继续补齐',
        CANCELLED_CONFIRMED: '已确认取消',
        MISSED_CONFIRMED: '已确认漏执行',
        CHECK_EXCEPTION: '核对异常',
        MANUAL_REVIEW: '人工复核'
      }
      return labels[action] || '人工复核'
    },
    matchedExecutionText(row) {
      const ids = Array.isArray(row.matched_execution_ids) ? row.matched_execution_ids : []
      return ids.length ? ids.join(', ') : ''
    },
    canMarkExecutionException(row) {
      if (!row) {
        return false
      }
      return !['NO_ACTION', 'CANCELLED_CONFIRMED', 'MISSED_CONFIRMED'].includes(row.feedback_action)
    },
    positionSyncStatusTagType(status) {
      if (status === 'MATCH') return 'success'
      if (status === 'DIFF') return 'warning'
      if (status === 'PENDING_MATCH') return 'info'
      return 'info'
    },
    positionSyncStatusLabel(status) {
      const labels = {
        MATCH: '已同步',
        DIFF: '有差异',
        PENDING_MATCH: '待匹配后校验',
        UNKNOWN: '待确认'
      }
      return labels[status] || '待确认'
    },
    positionSyncTagType(status) {
      if (status === 'MATCH') return 'success'
      if (status === 'DIFF') return 'warning'
      return 'info'
    },
    priorityTagType(priority) {
      if (priority === 'P0') return 'danger'
      if (priority === 'P1') return 'warning'
      return 'info'
    },
    stringifyOutput(payload) {
      if (payload === null || payload === undefined) {
        return ''
      }
      if (typeof payload === 'string') {
        return payload
      }
      try {
        return JSON.stringify(payload, null, 2)
      } catch (error) {
        return String(payload)
      }
    }
  }
}
</script>

<style scoped>
.mb16 {
  margin-bottom: 16px;
}

.mt12 {
  margin-top: 12px;
}

.mt16 {
  margin-top: 16px;
}

.summary-card {
  min-height: 108px;
}

.summary-strip-card {
  border-radius: 16px;
}

.summary-strip {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.plain-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.plain-list-item {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #ebeef5;
  background: rgba(250, 250, 250, 0.92);
  color: #606266;
  line-height: 1.7;
}

.chain-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.chain-card {
  padding: 14px;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  background: linear-gradient(180deg, #ffffff, #fafcff);
}

.chain-card--warning {
  border-color: #f3d19e;
  background: linear-gradient(180deg, #fffaf2, #fffdf8);
}

.chain-card--healthy {
  border-color: #c2e7b0;
  background: linear-gradient(180deg, #f7fff4, #fcfffb);
}

.chain-card--active {
  border-color: #b8d8ff;
  background: linear-gradient(180deg, #f5faff, #fbfdff);
}

.chain-card__title {
  color: #606266;
  font-size: 13px;
}

.chain-card__value {
  margin-top: 10px;
  color: #303133;
  font-size: 28px;
  font-weight: 600;
}

.chain-card__summary {
  margin-top: 8px;
  color: #606266;
  line-height: 1.6;
}

.ops-action-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ops-action-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  background: rgba(255, 255, 255, 0.9);
}

.ops-action-main {
  flex: 1;
}

.ops-action-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: #303133;
  font-weight: 600;
}

.ops-action-reason {
  margin-top: 8px;
  color: #606266;
  line-height: 1.6;
}

.execution-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.execution-page-header__actions {
  display: inline-flex;
  gap: 10px;
  flex-wrap: wrap;
}

.execution-page-header__value {
  color: #303133;
  font-weight: 600;
}

.execution-topline {
  align-items: center;
}

.execution-first-screen-grid {
  align-items: stretch;
}

.execution-primary-card,
.execution-summary-card {
  height: 100%;
}

.execution-primary-card {
  border-radius: 20px;
  background: linear-gradient(180deg, #ffffff, #f8fbff);
}

.execution-primary-card__label,
.execution-summary-card__label {
  color: #6b7280;
  font-size: 12px;
  letter-spacing: 0.04em;
}

.execution-primary-card__title,
.execution-summary-card__title {
  margin-top: 8px;
  color: #0f172a;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.5;
}

.execution-primary-card__desc {
  margin-top: 8px;
  color: #475569;
  line-height: 1.7;
}

.execution-primary-card__summary {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.execution-primary-card__summary-line,
.execution-summary-card__metric {
  padding: 8px 10px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #475569;
  font-size: 12px;
}

.execution-primary-card__footer {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #ebeef5;
}

.execution-primary-card__footer-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.execution-primary-card__actions,
.execution-summary-card__actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.execution-summary-card {
  border-radius: 18px;
}

.execution-summary-card--anomaly {
  background: linear-gradient(180deg, #fffaf3, #ffffff);
}

.execution-summary-card--manual {
  background: linear-gradient(180deg, #f7fbff, #ffffff);
}

.execution-summary-card__emphasis {
  margin-top: 10px;
  color: #303133;
  font-size: 24px;
  font-weight: 700;
}

.execution-summary-card__metric-list,
.execution-summary-card__plain-list {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.execution-summary-card__plain-list {
  flex-direction: column;
}

.execution-summary-card__actions--stacked .el-button {
  flex: 1 1 100%;
}

.summary-title {
  color: #909399;
  font-size: 13px;
}

.summary-value {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.summary-value.is-success {
  color: #67c23a;
}

.summary-value.is-danger {
  color: #f56c6c;
}

.summary-value.is-warning {
  color: #e6a23c;
}

.output-title {
  margin: 12px 0 8px;
  color: #606266;
}

.validation-block {
  margin-top: 12px;
}

.validation-summary {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 10px;
  color: #606266;
  font-size: 12px;
}

.validation-table {
  margin-bottom: 8px;
}

.header-meta {
  float: right;
  color: #909399;
  font-size: 12px;
}

.sync-summary {
  margin-bottom: 12px;
  color: #606266;
}

.sync-summary span {
  margin-right: 16px;
}

.match-dialog-meta {
  margin-bottom: 12px;
  color: #606266;
}

.match-dialog-meta span {
  margin-right: 16px;
}

.focus-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.focus-label {
  color: #606266;
  font-weight: 600;
}

.focus-current {
  margin-left: auto;
  color: #909399;
  font-size: 13px;
}

.focus-count {
  margin-left: 4px;
  color: #909399;
}

.execution-focus-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.secondary-section-collapse {
  border-top: 0;
}

.collapse-title {
  color: #303133;
  font-weight: 600;
}

.collapse-meta {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 1200px) {
  .execution-page-header {
    flex-direction: column;
  }
}

/deep/ .row-unmatched {
  background: #fff7e6;
}

/deep/ .row-partial {
  background: #fffbe6;
}

/deep/ .row-missed {
  background: #fff1f0;
}
</style>
