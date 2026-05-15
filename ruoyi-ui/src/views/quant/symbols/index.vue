<template>
  <div class="app-container quant-symbols-page">
    <el-card shadow="never" class="box-card hero-card">
      <div slot="header" class="page-header">
        <div>
          <div class="page-title">标的体系</div>
          <div class="page-subtitle">定义 / 治理 / 运行观测</div>
        </div>
        <div class="page-actions">
          <el-button size="small" icon="el-icon-data-analysis" @click="goBacktest">去回测分析</el-button>
          <el-button size="small" icon="el-icon-s-operation" @click="goExecutionCenter">去执行闭环</el-button>
          <el-button size="small" type="primary" icon="el-icon-data-line" @click="goDashboard">回量化看板</el-button>
        </div>
      </div>

      <el-row :gutter="16" class="summary-row">
        <el-col :xs="24" :sm="12" :xl="6">
          <div class="hero-metric">
            <div class="hero-metric__label">正式股票池</div>
            <div class="hero-metric__value">{{ activePoolCount }}</div>
            <div class="hero-metric__hint">个股池 / ETF池 / 指数映射ETF池</div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :xl="6">
          <div class="hero-metric">
            <div class="hero-metric__label">指数映射</div>
            <div class="hero-metric__value">{{ indexEtfMappings.length }}</div>
            <div class="hero-metric__hint">主ETF + 备选ETF 默认口径</div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :xl="6">
          <div class="hero-metric">
            <div class="hero-metric__label">预览覆盖标的</div>
            <div class="hero-metric__value">{{ scopePreview.resolvedCount || 0 }}</div>
            <div class="hero-metric__hint">{{ scopePreviewContext }}</div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :xl="6">
          <div class="hero-metric">
            <div class="hero-metric__label">运行观测标的</div>
            <div class="hero-metric__value">{{ filteredSymbols.length }}</div>
            <div class="hero-metric__hint">信号、成交、持仓、反馈统一观察</div>
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="16" class="mt16">
        <el-col :xs="24" :xl="16">
          <div class="hero-panel hero-panel--flat">
            <div class="hero-panel__title">当前正式范围</div>
            <div class="plain-list">
              <div class="plain-list-item">{{ selectedPoolSummary }}</div>
              <div class="plain-list-item">{{ selectedPoolNarrative }}</div>
            </div>
            <div class="detail-summary">
              <el-tag size="mini" type="success">正式股票池 {{ activePoolCount }}</el-tag>
              <el-tag size="mini" type="info">指数映射 {{ indexEtfMappings.length }}</el-tag>
              <el-tag size="mini" type="warning">预览标的 {{ scopePreview.resolvedCount || 0 }}</el-tag>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :xl="8">
          <div class="hero-panel">
            <div class="hero-panel__title">下一步</div>
            <div class="plain-list">
              <div class="plain-list-item">先在“定义层”确认正式范围，再进入治理或运行观测。</div>
            </div>
            <div class="page-actions mt16">
              <el-button size="small" type="primary" @click="activeTab = 'governance'">看治理层</el-button>
              <el-button size="small" @click="activeTab = 'runtime'">看运行观测</el-button>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-tabs v-model="activeTab" class="mt16">
      <el-tab-pane label="定义层" name="universe">
        <el-collapse v-model="symbolsSecondaryPanels" class="symbols-secondary-collapse">
          <el-collapse-item name="layers">
            <template slot="title">
              <div class="collapse-title-shell">
                <span>体系说明</span>
                <span class="section-meta">定义层 / 治理层 / 运行观测</span>
              </div>
            </template>
            <el-row :gutter="16">
              <el-col :xs="24" :xl="8" v-for="layer in subjectLayers" :key="layer.key">
                <el-card shadow="hover" class="box-card layer-card">
                  <div class="layer-card__header">
                    <span>{{ layer.title }}</span>
                    <el-tag size="mini" :type="layer.tagType">{{ layer.tag }}</el-tag>
                  </div>
                  <div class="layer-card__body">{{ layer.description }}</div>
                  <div class="layer-card__chips">
                    <el-tag v-for="item in layer.guideChips" :key="`${layer.key}-${item}`" size="mini" type="info">{{ item }}</el-tag>
                  </div>
                </el-card>
              </el-col>
            </el-row>
          </el-collapse-item>
        </el-collapse>

        <el-row :gutter="16" class="mt16">
          <el-col :xs="24" :xl="13">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>股票池总览</span>
                <span class="section-meta">正式范围</span>
              </div>
              <el-table
                v-loading="loadingScope"
                :data="symbolPools"
                border
                highlight-current-row
                row-key="poolCode"
                @current-change="handlePoolCurrentChange"
                @row-click="selectPool"
              >
                <el-table-column label="池编码" prop="poolCode" width="150" />
                <el-table-column label="名称" prop="poolName" min-width="140" />
                <el-table-column label="范围类型" min-width="130">
                  <template slot-scope="scope">
                    <el-tag size="mini" type="info">{{ scopeLabel(scope.row.scopeType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="纳入" prop="includedCount" width="80" />
                <el-table-column label="候选" prop="candidateCount" width="80" />
                <el-table-column label="剔除" prop="excludedCount" width="80" />
                <el-table-column label="样例标的" min-width="180">
                  <template slot-scope="scope">
                    <span>{{ joinItems(scope.row.sampleSymbols) }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>

          <el-col :xs="24" :xl="11">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>范围预览</span>
                <span class="section-meta">范围样例</span>
              </div>
              <el-form :model="scopeForm" label-width="110px" size="small">
                <el-form-item label="预设范围">
                  <el-select v-model="scopeForm.scopeType" style="width: 100%" @change="handleScopeTypeChange">
                    <el-option
                      v-for="item in presetScopes"
                      :key="item.scopeType"
                      :label="`${item.label} · ${item.description}`"
                      :value="item.scopeType"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="股票池" v-if="currentScopeNeedsPool">
                  <el-select v-model="scopeForm.scopePoolCode" style="width: 100%" placeholder="请选择正式股票池" @change="handleScopePoolChange">
                    <el-option
                      v-for="item in availablePools"
                      :key="item.poolCode"
                      :label="`${item.poolName} (${item.poolCode})`"
                      :value="item.poolCode"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="直接指定标的">
                  <el-input
                    v-model="scopeForm.symbolsText"
                    type="textarea"
                    :rows="2"
                    placeholder="输入 000001, 510300；填写后会优先覆盖预设范围"
                  />
                </el-form-item>
                <el-form-item label="白名单">
                  <el-input
                    v-model="scopeForm.whitelistText"
                    type="textarea"
                    :rows="2"
                    placeholder="在预设范围上强制保留的标的，逗号或换行分隔"
                  />
                </el-form-item>
                <el-form-item label="黑名单">
                  <el-input
                    v-model="scopeForm.blacklistText"
                    type="textarea"
                    :rows="2"
                    placeholder="在预设范围上强制排除的标的"
                  />
                </el-form-item>
                <el-form-item label="临时补充">
                  <el-input
                    v-model="scopeForm.adHocSymbolsText"
                    type="textarea"
                    :rows="2"
                    placeholder="用于专题研究、候选池验证或临时复盘"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="loadingScopePreview" @click="loadScopePreview">刷新预览</el-button>
                  <el-button @click="applyPoolToRuntimeFilter">用当前池查看运行观测</el-button>
                </el-form-item>
              </el-form>

              <div class="scope-preview-panel">
                <div class="scope-preview-panel__header">
                  <span>{{ scopePreview.resolvedCount || 0 }} 个标的</span>
                  <span>{{ scopePreview.hasMore ? '仅展示前 30 个样例' : '已展示完整样例' }}</span>
                </div>
                <div class="scope-preview-panel__meta">
                  <el-tag size="mini" type="success">白名单 {{ scopePreview.whitelistCount || 0 }}</el-tag>
                  <el-tag size="mini" type="warning">黑名单 {{ scopePreview.blacklistCount || 0 }}</el-tag>
                  <el-tag size="mini" type="info">临时补充 {{ scopePreview.adHocCount || 0 }}</el-tag>
                </div>
                <div class="scope-preview-panel__symbols">
                  <el-tag v-for="item in previewSymbols" :key="item" size="mini" class="symbol-pill">{{ item }}</el-tag>
                  <el-empty v-if="!previewSymbols.length && !loadingScopePreview" description="点击“刷新预览”查看当前范围样例" :image-size="72" />
                </div>
                <div class="scope-preview-panel__note">{{ selectedPoolNarrative }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-collapse v-model="symbolsSecondaryPanels" class="mt16 symbols-secondary-collapse">
          <el-collapse-item name="details">
            <template slot="title">
              <div class="collapse-title-shell">
                <span>详细结构</span>
                <span class="section-meta">股票池明细 / 指数到 ETF 映射</span>
              </div>
            </template>
            <el-row :gutter="16">
              <el-col :xs="24" :xl="14">
                <el-card shadow="never" class="box-card">
                  <div slot="header" class="section-header">
                    <span>股票池明细</span>
                    <span class="section-meta">{{ selectedPoolSummary }}</span>
                  </div>
                  <div class="detail-summary">
                    <el-tag size="mini" type="success">纳入 {{ selectedPoolDetail.summary ? selectedPoolDetail.summary.includedCount : 0 }}</el-tag>
                    <el-tag size="mini" type="warning">候选 {{ selectedPoolDetail.summary ? selectedPoolDetail.summary.candidateCount : 0 }}</el-tag>
                    <el-tag size="mini" type="danger">剔除 {{ selectedPoolDetail.summary ? selectedPoolDetail.summary.excludedCount : 0 }}</el-tag>
                    <el-tag size="mini" type="info">个股 {{ selectedPoolDetail.summary ? selectedPoolDetail.summary.stockCount : 0 }}</el-tag>
                    <el-tag size="mini">ETF {{ selectedPoolDetail.summary ? selectedPoolDetail.summary.etfCount : 0 }}</el-tag>
                  </div>
                  <div class="pool-rule-box">{{ selectedPoolRuleText }}</div>
                  <el-table v-loading="loadingPoolDetail" :data="selectedPoolMembers" border height="360">
                    <el-table-column label="代码" prop="stockCode" width="100" />
                    <el-table-column label="名称" prop="stockName" min-width="140" />
                    <el-table-column label="资产类型" prop="assetType" width="100" />
                    <el-table-column label="来源" prop="sourceType" min-width="130" />
                    <el-table-column label="纳入状态" width="100">
                      <template slot-scope="scope">
                        <el-tag size="mini" :type="memberStatusTagType(scope.row.inclusionStatus)">
                          {{ scope.row.inclusionStatus }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="映射指数" prop="mappedIndexCode" width="110" />
                    <el-table-column label="映射角色" prop="mappedRole" width="110" />
                    <el-table-column label="备注" prop="note" min-width="160" show-overflow-tooltip />
                  </el-table>
                  <el-empty v-if="!selectedPoolMembers.length && !loadingPoolDetail" description="请选择股票池查看成员明细" />
                </el-card>
              </el-col>

              <el-col :xs="24" :xl="10">
                <el-card shadow="never" class="box-card">
                  <div slot="header" class="section-header">
                    <span>指数到 ETF 映射</span>
                    <span class="section-meta">指数 -> ETF</span>
                  </div>
                  <el-table v-loading="loadingScope" :data="indexEtfMappings" border height="430">
                    <el-table-column label="指数" min-width="150">
                      <template slot-scope="scope">
                        <div class="mapping-primary">{{ scope.row.indexName }}</div>
                        <div class="mapping-secondary">{{ scope.row.indexCode }}</div>
                      </template>
                    </el-table-column>
                    <el-table-column label="主ETF" min-width="140">
                      <template slot-scope="scope">
                        <div class="mapping-primary">{{ scope.row.primaryEtfCode }}</div>
                        <div class="mapping-secondary">{{ scope.row.primaryEtfName || '-' }}</div>
                      </template>
                    </el-table-column>
                    <el-table-column label="备选ETF" min-width="180">
                      <template slot-scope="scope">
                        <div class="mapping-candidates">
                          <el-tag
                            v-for="item in mappingCandidates(scope.row)"
                            :key="`${scope.row.indexCode}-${item.code}`"
                            size="mini"
                            class="symbol-pill"
                          >
                            {{ item.code }} {{ item.name }}
                          </el-tag>
                        </div>
                      </template>
                    </el-table-column>
                  </el-table>
                </el-card>
              </el-col>
            </el-row>
          </el-collapse-item>
        </el-collapse>
      </el-tab-pane>

      <el-tab-pane label="治理层" name="governance">
        <el-row :gutter="16">
          <el-col :xs="24" :xl="10">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>ETF 治理摘要</span>
                <span class="section-meta">承接 / 风险 / 动作</span>
              </div>
              <div v-loading="loadingEtfGovernance">
                <div class="governance-headline">
                  {{ etfGovernanceView.headline }}
                </div>
                <div class="detail-summary">
                  <el-tag size="mini" type="info">映射 {{ etfGovernanceStats.indexMappingCount || 0 }}</el-tag>
                  <el-tag size="mini" type="success">持仓 {{ etfGovernanceStats.holdingCount || 0 }}</el-tag>
                  <el-tag size="mini" type="warning">活跃信号 {{ etfGovernanceStats.activeSignalCount || 0 }}</el-tag>
                  <el-tag size="mini" type="danger">风险预警 {{ etfGovernanceStats.riskWarningCount || 0 }}</el-tag>
                  <el-tag size="mini" type="warning">待处理 {{ etfGovernanceStats.pendingExecutionCount || 0 }}</el-tag>
                </div>
                <div class="plain-list">
                  <div v-for="item in etfGovernanceView.summaryLines" :key="item" class="plain-list-item">
                    {{ item }}
                  </div>
                </div>
                <div class="sub-title">治理动作分布</div>
                <div class="detail-summary">
                  <el-tag
                    v-for="item in etfGovernanceView.actionStats"
                    :key="item.action"
                    size="mini"
                    :type="item.type"
                  >
                    {{ item.label }} {{ item.count }}
                  </el-tag>
                </div>
                <review-evidence-chart
                  v-if="etfGovernanceRows.length"
                  :option="etfGovernanceView.chartOption"
                  height="220px"
                />
                <div class="plain-list">
                  <div v-for="item in etfGovernancePrinciples" :key="item" class="plain-list-item">
                    {{ item }}
                  </div>
                </div>
                <div class="sub-title">ETF / 个股分层治理</div>
                <div class="plain-list">
                  <div v-for="item in etfGovernanceMatrix" :key="item.assetType" class="plain-list-item governance-matrix-item">
                    <div class="mapping-primary">{{ item.assetType }}</div>
                    <div class="mapping-secondary">{{ item.focus }}</div>
                    <div class="governance-reason">{{ item.defaultAction }}</div>
                  </div>
                </div>
                <div class="sub-title">当前治理焦点</div>
                <div class="plain-list">
                  <div
                    v-for="item in etfGovernanceView.priorityQueue"
                    :key="`${item.title}-${item.action}`"
                    class="plain-list-item governance-priority-item"
                  >
                    <div class="mapping-primary">{{ item.title }}</div>
                    <div class="mapping-secondary">{{ item.summary }}</div>
                    <div class="governance-priority-actions">
                      <el-tag size="mini" :type="item.actionType">{{ item.actionLabel }}</el-tag>
                      <el-button type="text" size="mini" @click="goEtfGovernanceReview(item.source)">去复盘</el-button>
                      <el-button type="text" size="mini" @click="goEtfGovernanceBacktest(item.source)">去回测</el-button>
                    </div>
                    <div class="governance-reason">{{ item.reason }}</div>
                  </div>
                </div>
                <el-empty
                  v-if="!etfGovernancePrinciples.length && !etfGovernanceMatrix.length && !loadingEtfGovernance"
                  description="暂无 ETF 治理摘要"
                  :image-size="60"
                />
              </div>
            </el-card>
          </el-col>

          <el-col :xs="24" :xl="14">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>ETF 治理对象清单</span>
                <span class="section-meta">独立治理对象</span>
              </div>
              <el-table v-loading="loadingEtfGovernance" :data="etfGovernanceRows" border height="360">
                <el-table-column label="指数" min-width="140">
                  <template slot-scope="scope">
                    <div class="mapping-primary">{{ scope.row.indexName }}</div>
                    <div class="mapping-secondary">{{ scope.row.indexCode }}</div>
                  </template>
                </el-table-column>
                <el-table-column label="主 ETF" min-width="150">
                  <template slot-scope="scope">
                    <div class="mapping-primary">{{ scope.row.primaryEtfCode }}</div>
                    <div class="mapping-secondary">{{ scope.row.primaryEtfName || '-' }}</div>
                  </template>
                </el-table-column>
                <el-table-column label="承接状态" width="110">
                  <template slot-scope="scope">
                    <el-tag size="mini" :type="scope.row.hasTodaySignal ? 'success' : 'info'">
                      {{ scope.row.hasTodaySignal ? '有信号' : '无信号' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="持仓" width="90">
                  <template slot-scope="scope">{{ scope.row.holdingQuantity || 0 }}</template>
                </el-table-column>
                <el-table-column label="治理动作" width="130">
                  <template slot-scope="scope">
                    <el-tag size="mini" :type="etfGovernanceActionTagType(scope.row.governanceAction)">
                      {{ etfGovernanceActionLabel(scope.row.governanceAction) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="治理说明" min-width="220" prop="governanceReason" show-overflow-tooltip />
                <el-table-column label="操作" width="160" fixed="right">
                  <template slot-scope="scope">
                    <el-button type="text" size="mini" @click="goEtfGovernanceReview(scope.row)">去复盘</el-button>
                    <el-button type="text" size="mini" @click="goEtfGovernanceBacktest(scope.row)">去回测</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!etfGovernanceRows.length && !loadingEtfGovernance" description="暂无 ETF 治理对象" />
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16" class="mt16">
          <el-col :xs="24" :xl="10">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>ETF 治理历史趋势</span>
                <span class="section-meta">历史趋势</span>
              </div>
              <div class="governance-headline">
                {{ etfReviewHistoryView.headline }}
              </div>
              <div class="risk-summary-grid">
                <div
                  v-for="item in etfReviewHistoryView.summaryStats"
                  :key="item.label"
                  class="risk-metric"
                >
                  <span class="risk-label">{{ item.label }}</span>
                  <span class="risk-value">{{ item.value }}</span>
                </div>
              </div>
              <div class="sub-title">结论分布</div>
              <div class="detail-summary">
                <el-tag
                  v-for="item in etfReviewHistoryView.conclusionStats"
                  :key="item.key"
                  size="mini"
                  type="info"
                >
                  {{ item.label }} {{ item.count }}
                </el-tag>
              </div>
              <review-evidence-chart
                v-if="etfReviewCases.length"
                :option="etfReviewHistoryView.trendChartOption"
                height="220px"
              />
              <el-empty v-else-if="!loadingEtfReviewCases" description="暂无 ETF formal case 趋势数据" :image-size="60" />
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="14">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>最近 ETF 正式复盘记录</span>
                <span class="section-meta">历史回看</span>
              </div>
              <el-table v-loading="loadingEtfReviewCases" :data="etfReviewCases.slice(0, 8)" border height="320">
                <el-table-column label="Case ID" prop="caseId" width="90" />
                <el-table-column label="类型" width="100">
                  <template slot-scope="scope">
                    <el-tag size="mini" type="warning">{{ scope.row.caseType || 'ETF_REVIEW' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="对象" min-width="160" prop="reviewTargetName" />
                <el-table-column label="结论" width="110">
                  <template slot-scope="scope">
                    <el-tag size="mini" :type="scope.row.lastReviewConclusion ? 'success' : 'info'">
                      {{ etfReviewConclusionLabel(scope.row.lastReviewConclusion) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="状态" min-width="110">
                  <template slot-scope="scope">
                    <el-tag size="mini" :type="scope.row.resolutionStatus === 'OPEN' ? 'warning' : 'success'">
                      {{ scope.row.resolutionStatus || 'OPEN' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="最近发现" min-width="170" prop="lastDetectedTime" />
                <el-table-column label="操作" width="120" fixed="right">
                  <template slot-scope="scope">
                    <el-button type="text" size="mini" @click="goEtfReviewCase(scope.row)">打开复盘</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!etfReviewCases.length && !loadingEtfReviewCases" description="暂无 ETF 正式复盘记录" :image-size="60" />
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16" class="mt16">
          <el-col :xs="24" :xl="12">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>ETF 热点对象</span>
                <span class="section-meta">长期观察</span>
              </div>
              <div class="governance-headline">
                {{ etfObjectHotspotView.headline }}
              </div>
              <review-evidence-chart
                v-if="etfObjectHotspotView.hotspots.length"
                :option="etfObjectHotspotView.chartOption"
                height="240px"
              />
              <div class="plain-list">
                <div
                  v-for="item in etfObjectHotspotView.hotspots"
                  :key="item.key"
                  class="plain-list-item governance-priority-item"
                >
                  <div class="mapping-primary">{{ item.label }}</div>
                  <div class="mapping-secondary">历史 case {{ item.caseCount }} 条，OPEN {{ item.openCount }} 条</div>
                  <div class="governance-reason">最近发现：{{ item.latestDetectedTime || '-' }}</div>
                </div>
              </div>
              <el-empty v-if="!etfObjectHotspotView.hotspots.length && !loadingEtfReviewCases" description="暂无 ETF 热点对象" :image-size="60" />
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="12">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>ETF / 个股治理对比</span>
                <span class="section-meta">治理负载</span>
              </div>
              <div class="governance-headline">
                {{ assetGovernanceComparisonView.headline }}
              </div>
              <review-evidence-chart
                :option="assetGovernanceComparisonView.chartOption"
                height="240px"
              />
              <div class="plain-list">
                <div
                  v-for="item in assetGovernanceComparisonView.rows"
                  :key="item.assetType"
                  class="plain-list-item governance-matrix-item"
                >
                  <div class="mapping-primary">{{ item.assetType }}</div>
                  <div class="mapping-secondary">当前待治理 {{ item.currentIssues }}，历史 formal case {{ item.historyCases }}</div>
                  <div class="governance-reason">OPEN {{ item.openCases }}，已出结论 {{ item.reviewedCases }}</div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="运行观测" name="runtime">
        <el-card shadow="never" class="box-card">
          <div slot="header" class="section-header">
            <span>运行观测筛选</span>
            <span class="section-meta">统一观测</span>
          </div>
          <el-form :inline="true" :model="queryParams" size="small" label-width="88px">
            <el-form-item label="信号日期">
              <el-date-picker
                v-model="queryParams.signalDate"
                type="date"
                value-format="yyyy-MM-dd"
                style="width: 180px"
              />
            </el-form-item>
            <el-form-item label="股票代码">
              <el-input v-model="queryParams.stockCode" placeholder="000001 / 510300" clearable style="width: 180px" />
            </el-form-item>
            <el-form-item label="策略">
              <el-select v-model="queryParams.strategyId" clearable filterable style="width: 260px" placeholder="全部策略">
                <el-option
                  v-for="item in strategyList"
                  :key="item.id"
                  :label="`${item.id} - ${item.strategy_name} (${item.strategy_type})`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="成交条数">
              <el-input-number v-model="queryParams.executionLimit" :min="20" :max="300" controls-position="right" />
            </el-form-item>
            <el-form-item label="反馈条数">
              <el-input-number v-model="queryParams.feedbackLimit" :min="20" :max="300" controls-position="right" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="el-icon-search" :loading="loadingObservation" @click="loadObservationData">刷新观测</el-button>
              <el-button @click="resetRuntimeFilter">清空标的筛选</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-row :gutter="16" class="mt16">
          <el-col :xs="24" :sm="12" :xl="6">
            <el-card shadow="hover" class="summary-card">
              <div class="summary-title">观测标的</div>
              <div class="summary-value">{{ filteredSymbols.length }}</div>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="12" :xl="6">
            <el-card shadow="hover" class="summary-card">
              <div class="summary-title">今日有信号</div>
              <div class="summary-value is-success">{{ activeSignalSymbols }}</div>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="12" :xl="6">
            <el-card shadow="hover" class="summary-card">
              <div class="summary-title">当前持仓</div>
              <div class="summary-value">{{ holdingSymbols }}</div>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="12" :xl="6">
            <el-card shadow="hover" class="summary-card">
              <div class="summary-title">待处理标的</div>
              <div class="summary-value is-warning">{{ actionableSymbols }}</div>
            </el-card>
          </el-col>
        </el-row>

        <el-card shadow="never" class="box-card mt16">
          <div slot="header" class="section-header">
            <span>标的运行一览</span>
            <span class="section-meta">高亮“待执行、异常反馈、未匹配成交”的处理对象</span>
          </div>
          <el-table
            v-loading="loadingObservation"
            :data="filteredSymbols"
            border
            highlight-current-row
            :row-class-name="symbolRowClassName"
            @row-click="handleSymbolRowClick"
          >
            <el-table-column label="代码" prop="stockCode" width="100" />
            <el-table-column label="名称" prop="stockName" min-width="120" />
            <el-table-column label="策略" min-width="180">
              <template slot-scope="scope">
                {{ strategyNames(scope.row.strategyIds) }}
              </template>
            </el-table-column>
            <el-table-column label="今日信号" width="90">
              <template slot-scope="scope">
                <el-tag size="mini" :type="scope.row.signalCount > 0 ? 'success' : 'info'">
                  {{ scope.row.signalCount }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="待执行" width="90">
              <template slot-scope="scope">
                <el-tag size="mini" :type="scope.row.pendingSignalCount > 0 ? 'warning' : 'success'">
                  {{ scope.row.pendingSignalCount }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="持仓数量" prop="holdingQuantity" width="100" />
            <el-table-column label="浮盈亏(%)" min-width="100">
              <template slot-scope="scope">
                <span :class="profitClass(scope.row.floatProfit)">
                  {{ formatPercent(scope.row.floatProfit) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="成交数" prop="executionCount" width="90" />
            <el-table-column label="异常反馈" width="90">
              <template slot-scope="scope">
                <el-tag size="mini" :type="scope.row.abnormalFeedbackCount > 0 ? 'danger' : 'success'">
                  {{ scope.row.abnormalFeedbackCount }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="未匹配成交" width="100">
              <template slot-scope="scope">
                <el-tag size="mini" :type="scope.row.unmatchedExecutionCount > 0 ? 'warning' : 'info'">
                  {{ scope.row.unmatchedExecutionCount }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最近信号日" prop="latestSignalDate" min-width="110" />
            <el-table-column label="最近成交日" prop="latestTradeDate" min-width="110" />
          </el-table>
          <el-empty v-if="!filteredSymbols.length && !loadingObservation" description="暂无标的观测数据" />
        </el-card>

        <el-row :gutter="16" class="mt16">
          <el-col :xs="24" :xl="14">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>标的详情</span>
                <span class="section-meta">{{ selectedSymbolTitle }}</span>
              </div>
              <div class="detail-summary">
                <el-tag size="mini" type="info">信号 {{ selectedSignals.length }}</el-tag>
                <el-tag size="mini" type="success">成交 {{ selectedExecutions.length }}</el-tag>
                <el-tag size="mini" type="warning">反馈 {{ selectedFeedbacks.length }}</el-tag>
                <el-tag v-if="selectedSymbolRow && selectedSymbolRow.lossWarning" size="mini" type="danger">止损预警</el-tag>
              </div>
              <el-table v-loading="loadingSignals" :data="selectedSignals" border height="280">
                <el-table-column label="信号ID" prop="id" width="90" />
                <el-table-column label="日期" prop="signal_date" width="110" />
                <el-table-column label="方向" prop="signal_type" width="90">
                  <template slot-scope="scope">
                    <el-tag size="mini" :type="scope.row.signal_type === 'BUY' ? 'success' : 'danger'">
                      {{ scope.row.signal_type }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="策略ID" prop="strategy_id" width="90" />
                <el-table-column label="建议价" min-width="90">
                  <template slot-scope="scope">{{ formatNumber(scope.row.suggest_price) }}</template>
                </el-table-column>
                <el-table-column label="已执行" width="90">
                  <template slot-scope="scope">
                    <el-tag size="mini" :type="Number(scope.row.is_execute) === 1 ? 'info' : 'warning'">
                      {{ Number(scope.row.is_execute) === 1 ? '是' : '否' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!selectedSignals.length && !loadingSignals" description="该标的暂无信号记录" />
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="10">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>持仓同步结果</span>
                <span class="section-meta">聚焦当前选中标的</span>
              </div>
              <div v-loading="loadingPositionSync" class="sync-wrap">
                <template v-if="selectedSymbolRow">
                  <div class="sync-line">
                    <span>同步状态：</span>
                    <el-tag size="mini" :type="positionSyncTagType(positionSyncResult.syncStatus)">
                      {{ positionSyncResult.syncStatus || 'NO_DATA' }}
                    </el-tag>
                  </div>
                  <div class="sync-line">
                    <span>差异数量：{{ positionSyncResult.differenceCount || 0 }}</span>
                    <span>目标代码：{{ selectedSymbolRow.stockCode }}</span>
                  </div>
                  <div class="sync-line sync-remark">{{ positionSyncSummary }}</div>
                </template>
                <el-empty v-else description="请先选择一只标的" />
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16" class="mt16">
          <el-col :xs="24" :xl="14">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>成交记录</span>
                <span class="section-meta">用于核对 signal -> execution 的回写情况</span>
              </div>
              <el-table v-loading="loadingExecutions" :data="selectedExecutions" border height="320">
                <el-table-column label="成交ID" prop="id" width="90" />
                <el-table-column label="成交日" prop="trade_date" width="110" />
                <el-table-column label="方向" prop="side" width="90">
                  <template slot-scope="scope">
                    <el-tag size="mini" :type="scope.row.side === 'BUY' ? 'success' : 'danger'">
                      {{ scope.row.side }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="数量" prop="quantity" width="90" />
                <el-table-column label="价格" min-width="90">
                  <template slot-scope="scope">{{ formatNumber(scope.row.price) }}</template>
                </el-table-column>
                <el-table-column label="策略" min-width="160">
                  <template slot-scope="scope">
                    {{ scope.row.strategy_name || `策略 ${scope.row.strategy_id || '-'}` }}
                  </template>
                </el-table-column>
                <el-table-column label="匹配信号" width="90">
                  <template slot-scope="scope">
                    <el-tag size="mini" :type="scope.row.signal_id ? 'success' : 'warning'">
                      {{ scope.row.signal_id || '未匹配' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!selectedExecutions.length && !loadingExecutions" description="该标的暂无成交记录" />
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="10">
            <el-card shadow="never" class="box-card">
              <div slot="header" class="section-header">
                <span>执行反馈</span>
                <span class="section-meta">聚焦未执行、部分成交与漏执行</span>
              </div>
              <el-table v-loading="loadingFeedback" :data="selectedFeedbacks" border height="320">
                <el-table-column label="信号ID" prop="signal_id" width="90" />
                <el-table-column label="状态" width="90">
                  <template slot-scope="scope">
                    <el-tag size="mini" :type="feedbackTagType(scope.row.status, scope.row.executed_quantity)">
                      {{ feedbackLabel(scope.row) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="检查日" prop="check_date" width="110" />
                <el-table-column label="已执行量" prop="executed_quantity" width="90" />
                <el-table-column label="备注" prop="remark" min-width="180" show-overflow-tooltip />
              </el-table>
              <el-empty v-if="!selectedFeedbacks.length && !loadingFeedback" description="该标的暂无执行反馈记录" />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import ElCollapse from 'element-ui/lib/collapse'
import ElCollapseItem from 'element-ui/lib/collapse-item'
import 'element-ui/lib/theme-chalk/collapse.css'
import {
  getEtfGovernanceSummary,
  getPositionSyncResult,
  getReviewCases,
  getSymbolPoolDetail,
  getSymbolScopeOptions,
  getSymbolScopePreview,
  listExecutionFeedbackDetails,
  listExecutionRecords,
  listIndexEtfMappings,
  listPositions,
  listSignals,
  listStrategies,
  listSymbolPools
} from '@/api/quant'
import { extractSignalRows } from '@/views/quant/signal-response'
import ReviewEvidenceChart from '@/views/quant/review/components/ReviewEvidenceChart'
import { buildReviewRouteQuery } from '@/views/quant/review/review-context'
const {
  buildEtfGovernanceViewModel,
  buildEtfReviewHistoryViewModel,
  buildObjectHotspotViewModel,
  buildAssetGovernanceComparisonViewModel,
  reviewConclusionLabel
} = require('./symbols-etf-governance')

function toCode(value) {
  return value ? String(value).trim() : ''
}

function parseSymbolText(raw) {
  if (!raw) {
    return []
  }
  return Array.from(new Set(String(raw)
    .split(/[\s,，;；]+/)
    .map(item => item.trim())
    .filter(Boolean)))
}

export default {
  name: 'QuantSymbols',
  components: {
    ElCollapse,
    ElCollapseItem,
    ReviewEvidenceChart
  },
  data() {
    return {
      activeTab: 'universe',
      symbolsSecondaryPanels: [],
      loadingScope: false,
      loadingPoolDetail: false,
      loadingScopePreview: false,
      loadingObservation: false,
      loadingSignals: false,
      loadingPositions: false,
      loadingExecutions: false,
      loadingFeedback: false,
      loadingPositionSync: false,
      loadingEtfGovernance: false,
      loadingEtfReviewCases: false,
      loadingEquityReviewCases: false,
      strategyList: [],
      signals: [],
      positions: [],
      executions: [],
      feedbacks: [],
      symbolScopeOptions: {},
      symbolPools: [],
      indexEtfMappings: [],
      etfGovernance: {},
      etfReviewCases: [],
      equityReviewCases: [],
      selectedPoolCode: '',
      selectedPoolDetail: {},
      scopePreview: {},
      selectedStockCode: '',
      positionSyncResult: {},
      scopeForm: {
        scopeType: 'all_stocks',
        scopePoolCode: '',
        symbolsText: '',
        whitelistText: '',
        blacklistText: '',
        adHocSymbolsText: ''
      },
      queryParams: {
        signalDate: this.todayString(),
        stockCode: '',
        strategyId: undefined,
        executionLimit: 100,
        feedbackLimit: 100
      }
    }
  },
  computed: {
    presetScopes() {
      return Array.isArray(this.symbolScopeOptions.presetScopes) ? this.symbolScopeOptions.presetScopes : []
    },
    previewSymbols() {
      return Array.isArray(this.scopePreview.symbols) ? this.scopePreview.symbols : []
    },
    activePoolCount() {
      return this.symbolPools.filter(item => item.status === 'ACTIVE').length
    },
    subjectLayers() {
      return [
        {
          key: 'index',
          title: '指数层',
          tag: '分析对象',
          tagType: 'info',
          description: '市场判断与基准比较。',
          guideChips: ['市场状态', '估值观察', 'ETF 映射']
        },
        {
          key: 'etf',
          title: 'ETF层',
          tag: '交易承接',
          tagType: 'success',
          description: '把指数判断落成真实交易标的。',
          guideChips: ['主 ETF', '备选 ETF', '低频纪律']
        },
        {
          key: 'stock',
          title: '个股层',
          tag: '主动交易',
          tagType: 'warning',
          description: '承接主动选股与更严格风控。',
          guideChips: ['规则池', '人工调整', '统一口径']
        }
      ]
    },
    currentScopeOption() {
      return this.presetScopes.find(item => item.scopeType === this.scopeForm.scopeType) || {}
    },
    currentScopeNeedsPool() {
      return Boolean(this.currentScopeOption.needsPool)
    },
    availablePools() {
      return this.symbolPools.filter(item => item.scopeType === this.scopeForm.scopeType)
    },
    etfGovernanceStats() {
      return this.etfGovernance.summary || {}
    },
    etfGovernancePrinciples() {
      return Array.isArray(this.etfGovernance.governancePrinciples) ? this.etfGovernance.governancePrinciples : []
    },
    etfGovernanceMatrix() {
      return Array.isArray(this.etfGovernance.governanceMatrix) ? this.etfGovernance.governanceMatrix : []
    },
    etfGovernanceRows() {
      return Array.isArray(this.etfGovernance.mappingGovernanceRows) ? this.etfGovernance.mappingGovernanceRows : []
    },
    etfGovernanceView() {
      return buildEtfGovernanceViewModel(this.etfGovernance)
    },
    etfReviewHistoryView() {
      return buildEtfReviewHistoryViewModel(this.etfReviewCases)
    },
    etfObjectHotspotView() {
      return buildObjectHotspotViewModel(this.etfReviewCases)
    },
    assetGovernanceComparisonView() {
      return buildAssetGovernanceComparisonViewModel({
        etfCases: this.etfReviewCases,
        equityCases: this.equityReviewCases,
        etfGovernanceRows: this.etfGovernanceRows,
        symbolRows: this.symbolRows
      })
    },
    selectedPoolSummary() {
      if (!this.selectedPoolDetail.poolCode) {
        return '请选择股票池查看正式成员和规则'
      }
      return `${this.selectedPoolDetail.poolName} (${this.selectedPoolDetail.poolCode})`
    },
    selectedPoolMembers() {
      return Array.isArray(this.selectedPoolDetail.members) ? this.selectedPoolDetail.members : []
    },
    selectedPoolRuleText() {
      const ruleDefinition = this.selectedPoolDetail.ruleDefinition || {}
      const pairs = Object.keys(ruleDefinition).map(key => `${key}: ${ruleDefinition[key]}`)
      if (!pairs.length) {
        return this.selectedPoolDetail.description || '当前股票池暂无额外规则说明'
      }
      return `${this.selectedPoolDetail.description || ''} ${pairs.join(' | ')}`.trim()
    },
    selectedPoolNarrative() {
      if (!this.scopePreview.scopeType) {
        return '预设范围用于解释研究范围，白名单 / 黑名单 / 临时补充用于表达一次具体回测或专题验证。'
      }
      if (this.scopeForm.symbolsText) {
        return '当前预览使用了“直接指定标的”，这会优先覆盖预设范围，适合小样本专题验证。'
      }
      if (this.currentScopeNeedsPool && this.scopeForm.scopePoolCode) {
        return `当前使用 ${this.scopeLabel(this.scopeForm.scopeType)} · ${this.scopeForm.scopePoolCode}，可直接复用于回测页和调度中心。`
      }
      return '当前预览使用预设范围作为底座，再叠加白名单、黑名单和临时补充标的。'
    },
    scopePreviewContext() {
      if (!this.scopePreview.scopeType) {
        return '等待范围预览'
      }
      return `${this.scopeLabel(this.scopePreview.scopeType)}${this.scopePreview.scopePoolCode ? ` · ${this.scopePreview.scopePoolCode}` : ''}`
    },
    symbolRows() {
      const merged = new Map()
      const ensureRow = (code, fallbackName = '') => {
        if (!code) {
          return null
        }
        if (!merged.has(code)) {
          merged.set(code, {
            stockCode: code,
            stockName: fallbackName || '-',
            strategyIds: [],
            signalCount: 0,
            pendingSignalCount: 0,
            holdingQuantity: 0,
            floatProfit: null,
            lossWarning: false,
            executionCount: 0,
            unmatchedExecutionCount: 0,
            abnormalFeedbackCount: 0,
            latestSignalDate: '',
            latestTradeDate: ''
          })
        }
        const row = merged.get(code)
        if ((!row.stockName || row.stockName === '-') && fallbackName) {
          row.stockName = fallbackName
        }
        return row
      }

      this.signals.forEach((item) => {
        const row = ensureRow(toCode(item.stock_code), item.stock_name)
        if (!row) return
        row.signalCount += 1
        if (Number(item.is_execute) !== 1) {
          row.pendingSignalCount += 1
        }
        if (item.strategy_id && !row.strategyIds.includes(item.strategy_id)) {
          row.strategyIds.push(item.strategy_id)
        }
        row.latestSignalDate = row.latestSignalDate || item.signal_date || ''
      })

      this.positions.forEach((item) => {
        const row = ensureRow(toCode(item.stock_code), item.stock_name)
        if (!row) return
        row.holdingQuantity = Number(item.quantity || 0)
        row.floatProfit = item.float_profit
        row.lossWarning = Number(item.loss_warning) === 1
      })

      this.executions.forEach((item) => {
        const row = ensureRow(toCode(item.stock_code))
        if (!row) return
        row.executionCount += 1
        if (item.strategy_id && !row.strategyIds.includes(item.strategy_id)) {
          row.strategyIds.push(item.strategy_id)
        }
        if (!item.signal_id) {
          row.unmatchedExecutionCount += 1
        }
        row.latestTradeDate = row.latestTradeDate || item.trade_date || ''
      })

      this.feedbacks.forEach((item) => {
        const row = ensureRow(toCode(item.stock_code), item.stock_name)
        if (!row) return
        if (item.strategy_id && !row.strategyIds.includes(item.strategy_id)) {
          row.strategyIds.push(item.strategy_id)
        }
        if (item.status !== 'EXECUTED' || Number(item.executed_quantity || 0) > 0) {
          row.abnormalFeedbackCount += 1
        }
      })

      return Array.from(merged.values()).sort((a, b) => {
        if (b.pendingSignalCount !== a.pendingSignalCount) {
          return b.pendingSignalCount - a.pendingSignalCount
        }
        if (b.abnormalFeedbackCount !== a.abnormalFeedbackCount) {
          return b.abnormalFeedbackCount - a.abnormalFeedbackCount
        }
        return a.stockCode.localeCompare(b.stockCode)
      })
    },
    filteredSymbols() {
      const codeKeyword = toCode(this.queryParams.stockCode)
      const strategyId = this.queryParams.strategyId
      return this.symbolRows.filter((item) => {
        if (codeKeyword && !item.stockCode.includes(codeKeyword) && !String(item.stockName || '').includes(codeKeyword)) {
          return false
        }
        if (strategyId && !item.strategyIds.includes(strategyId)) {
          return false
        }
        return true
      })
    },
    activeSignalSymbols() {
      return this.filteredSymbols.filter(item => item.signalCount > 0).length
    },
    holdingSymbols() {
      return this.filteredSymbols.filter(item => item.holdingQuantity > 0).length
    },
    actionableSymbols() {
      return this.filteredSymbols.filter(item => item.pendingSignalCount > 0 || item.abnormalFeedbackCount > 0 || item.unmatchedExecutionCount > 0).length
    },
    selectedSymbolRow() {
      return this.filteredSymbols.find(item => item.stockCode === this.selectedStockCode) || this.filteredSymbols[0] || null
    },
    selectedSymbolTitle() {
      if (!this.selectedSymbolRow) {
        return '请选择一只标的'
      }
      return `${this.selectedSymbolRow.stockCode} ${this.selectedSymbolRow.stockName || ''}`.trim()
    },
    selectedSignals() {
      if (!this.selectedSymbolRow) {
        return []
      }
      return this.signals.filter(item => toCode(item.stock_code) === this.selectedSymbolRow.stockCode)
    },
    selectedExecutions() {
      if (!this.selectedSymbolRow) {
        return []
      }
      return this.executions.filter(item => toCode(item.stock_code) === this.selectedSymbolRow.stockCode)
    },
    selectedFeedbacks() {
      if (!this.selectedSymbolRow) {
        return []
      }
      return this.feedbacks.filter(item => toCode(item.stock_code) === this.selectedSymbolRow.stockCode)
    },
    positionSyncSummary() {
      if (!this.selectedSymbolRow) {
        return '-'
      }
      const status = this.positionSyncResult.syncStatus || 'NO_DATA'
      if (status === 'MATCH') {
        return '当前持仓与已匹配成交一致。'
      }
      if (status === 'DIFF') {
        return `存在 ${this.positionSyncResult.differenceCount || 0} 条差异，建议进入执行闭环页继续核对。`
      }
      return '当前没有可用于同步校验的匹配结果。'
    }
  },
  watch: {
    filteredSymbols(rows) {
      if (!rows.length) {
        this.selectedStockCode = ''
        this.positionSyncResult = {}
        return
      }
      if (!rows.find(item => item.stockCode === this.selectedStockCode)) {
        this.selectedStockCode = rows[0].stockCode
        this.loadPositionSync()
      }
    },
    '$route.query': {
      handler() {
        this.syncScopeFromRoute()
        this.loadScopePreview()
      },
      deep: true
    }
  },
  created() {
    this.syncScopeFromRoute()
    this.loadAllData()
  },
  methods: {
    todayString() {
      const now = new Date()
      const year = now.getFullYear()
      const month = `${now.getMonth() + 1}`.padStart(2, '0')
      const day = `${now.getDate()}`.padStart(2, '0')
      return `${year}-${month}-${day}`
    },
    syncScopeFromRoute() {
      const query = this.$route && this.$route.query ? this.$route.query : {}
      if (query.scopeType) {
        this.scopeForm.scopeType = String(query.scopeType)
      }
      if (query.scopePoolCode !== undefined) {
        this.scopeForm.scopePoolCode = String(query.scopePoolCode || '')
      }
      if (query.symbols !== undefined) {
        this.scopeForm.symbolsText = Array.isArray(query.symbols) ? query.symbols.join(',') : String(query.symbols || '')
      }
      if (query.whitelist !== undefined) {
        this.scopeForm.whitelistText = Array.isArray(query.whitelist) ? query.whitelist.join(',') : String(query.whitelist || '')
      }
      if (query.blacklist !== undefined) {
        this.scopeForm.blacklistText = Array.isArray(query.blacklist) ? query.blacklist.join(',') : String(query.blacklist || '')
      }
      if (query.adHocSymbols !== undefined) {
        this.scopeForm.adHocSymbolsText = Array.isArray(query.adHocSymbols) ? query.adHocSymbols.join(',') : String(query.adHocSymbols || '')
      }
    },
    async loadAllData() {
      await Promise.all([
        this.loadScopeData(),
        this.loadObservationData()
      ])
    },
    async loadScopeData() {
      this.loadingScope = true
      try {
        const [optionsResp, poolsResp, mappingsResp] = await Promise.all([
          getSymbolScopeOptions(),
          listSymbolPools(),
          listIndexEtfMappings()
        ])
        this.symbolScopeOptions = optionsResp.data || {}
        this.symbolPools = Array.isArray(poolsResp.data) ? poolsResp.data : []
        this.indexEtfMappings = Array.isArray(mappingsResp.data) ? mappingsResp.data : []

        if (!this.scopeForm.scopeType && this.presetScopes.length) {
          this.scopeForm.scopeType = this.presetScopes[0].scopeType
        }
        if (!this.selectedPoolCode && this.symbolPools.length) {
          this.selectedPoolCode = this.symbolPools[0].poolCode
        }
        this.alignScopePoolWithType()
        if (this.selectedPoolCode) {
          await this.loadPoolDetail(this.selectedPoolCode)
        }
        await Promise.all([
          this.loadScopePreview(),
          this.loadEtfGovernanceSummary(),
          this.loadEtfReviewCases(),
          this.loadEquityReviewCases()
        ])
      } finally {
        this.loadingScope = false
      }
    },
    async loadEtfGovernanceSummary() {
      this.loadingEtfGovernance = true
      try {
        const response = await getEtfGovernanceSummary()
        this.etfGovernance = response.data || {}
      } catch (error) {
        this.etfGovernance = {}
        this.$modal.msgWarning('ETF 治理摘要暂不可用，已降级展示标的定义与映射信息')
      } finally {
        this.loadingEtfGovernance = false
      }
    },
    async loadEtfReviewCases() {
      this.loadingEtfReviewCases = true
      try {
        const response = await getReviewCases({
          reviewLevel: 'trade',
          caseType: 'ETF_REVIEW',
          assetType: 'ETF',
          limit: 24
        })
        this.etfReviewCases = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingEtfReviewCases = false
      }
    },
    async loadEquityReviewCases() {
      this.loadingEquityReviewCases = true
      try {
        const response = await getReviewCases({
          reviewLevel: 'trade',
          caseType: 'TRADE_REVIEW',
          assetType: 'EQUITY',
          limit: 24
        })
        this.equityReviewCases = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingEquityReviewCases = false
      }
    },
    async loadPoolDetail(poolCode) {
      if (!poolCode) {
        this.selectedPoolDetail = {}
        return
      }
      this.loadingPoolDetail = true
      try {
        const response = await getSymbolPoolDetail({ poolCode })
        this.selectedPoolDetail = response.data || {}
      } finally {
        this.loadingPoolDetail = false
      }
    },
    async loadScopePreview() {
      this.loadingScopePreview = true
      try {
        const response = await getSymbolScopePreview(this.buildScopePreviewParams())
        this.scopePreview = response.data || {}
      } finally {
        this.loadingScopePreview = false
      }
    },
    buildScopePreviewParams() {
      return {
        scopeType: this.scopeForm.scopeType,
        scopePoolCode: this.currentScopeNeedsPool ? this.scopeForm.scopePoolCode : undefined,
        symbols: parseSymbolText(this.scopeForm.symbolsText),
        whitelist: parseSymbolText(this.scopeForm.whitelistText),
        blacklist: parseSymbolText(this.scopeForm.blacklistText),
        adHocSymbols: parseSymbolText(this.scopeForm.adHocSymbolsText)
      }
    },
    alignScopePoolWithType() {
      if (!this.currentScopeNeedsPool) {
        this.scopeForm.scopePoolCode = ''
        return
      }
      const candidate = this.availablePools[0]
      if (!this.scopeForm.scopePoolCode || !this.availablePools.find(item => item.poolCode === this.scopeForm.scopePoolCode)) {
        this.scopeForm.scopePoolCode = candidate ? candidate.poolCode : ''
      }
    },
    handleScopeTypeChange() {
      this.alignScopePoolWithType()
      if (this.scopeForm.scopePoolCode) {
        this.loadPoolDetail(this.scopeForm.scopePoolCode)
      }
      this.loadScopePreview()
    },
    handleScopePoolChange(value) {
      this.selectedPoolCode = value
      this.loadPoolDetail(value)
      this.loadScopePreview()
    },
    handlePoolCurrentChange(row) {
      if (row && row.poolCode) {
        this.selectPool(row)
      }
    },
    selectPool(row) {
      if (!row || !row.poolCode) {
        return
      }
      this.selectedPoolCode = row.poolCode
      this.loadPoolDetail(row.poolCode)
      this.scopeForm.scopeType = row.scopeType
      this.scopeForm.scopePoolCode = row.poolCode
      this.loadScopePreview()
    },
    async loadObservationData() {
      this.loadingObservation = true
      try {
        await Promise.all([
          this.loadStrategies(),
          this.loadSignals(),
          this.loadPositions(),
          this.loadExecutions(),
          this.loadFeedbacks()
        ])
        if (!this.selectedStockCode && this.filteredSymbols.length) {
          this.selectedStockCode = this.filteredSymbols[0].stockCode
        }
        await this.loadPositionSync()
      } finally {
        this.loadingObservation = false
      }
    },
    async loadStrategies() {
      const response = await listStrategies()
      this.strategyList = Array.isArray(response.data) ? response.data : []
    },
    async loadSignals() {
      this.loadingSignals = true
      try {
        const response = await listSignals({ signalDate: this.queryParams.signalDate })
        this.signals = extractSignalRows(response.data)
      } finally {
        this.loadingSignals = false
      }
    },
    async loadPositions() {
      this.loadingPositions = true
      try {
        const response = await listPositions()
        this.positions = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingPositions = false
      }
    },
    async loadExecutions() {
      this.loadingExecutions = true
      try {
        const response = await listExecutionRecords({
          limit: this.queryParams.executionLimit,
          stockCode: toCode(this.queryParams.stockCode) || undefined
        })
        this.executions = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingExecutions = false
      }
    },
    async loadFeedbacks() {
      this.loadingFeedback = true
      try {
        const response = await listExecutionFeedbackDetails({ limit: this.queryParams.feedbackLimit })
        this.feedbacks = Array.isArray(response.data) ? response.data : []
      } finally {
        this.loadingFeedback = false
      }
    },
    async loadPositionSync() {
      if (!this.selectedSymbolRow) {
        this.positionSyncResult = {}
        return
      }
      this.loadingPositionSync = true
      try {
        const strategyId = this.queryParams.strategyId || this.selectedSymbolRow.strategyIds[0]
        const response = await getPositionSyncResult({
          strategyId,
          stockCode: this.selectedSymbolRow.stockCode
        })
        this.positionSyncResult = response.data || {}
      } finally {
        this.loadingPositionSync = false
      }
    },
    applyPoolToRuntimeFilter() {
      if (!this.scopePreview || !Array.isArray(this.scopePreview.symbols) || !this.scopePreview.symbols.length) {
        this.$modal.msgWarning('请先刷新范围预览')
        return
      }
      this.activeTab = 'runtime'
      this.queryParams.stockCode = this.scopePreview.symbols[0]
      this.selectedStockCode = this.scopePreview.symbols[0]
      this.loadObservationData()
    },
    resetRuntimeFilter() {
      this.queryParams.stockCode = ''
      this.queryParams.strategyId = undefined
      this.loadObservationData()
    },
    handleSymbolRowClick(row) {
      this.selectedStockCode = row.stockCode
      this.loadPositionSync()
    },
    scopeLabel(scopeType) {
      const matched = this.presetScopes.find(item => item.scopeType === scopeType)
      return matched ? matched.label : (scopeType || '-')
    },
    memberStatusTagType(status) {
      if (status === 'INCLUDED') return 'success'
      if (status === 'CANDIDATE') return 'warning'
      if (status === 'EXCLUDED') return 'danger'
      return 'info'
    },
    mappingCandidates(row) {
      const codes = Array.isArray(row.candidateEtfCodes) ? row.candidateEtfCodes : []
      const names = Array.isArray(row.candidateEtfNames) ? row.candidateEtfNames : []
      return codes.map((code, index) => ({
        code,
        name: names[index] || ''
      }))
    },
    etfGovernanceActionTagType(action) {
      if (action === 'REVIEW') return 'danger'
      if (action === 'BUILD_POSITION') return 'warning'
      if (action === 'KEEP_PRIMARY') return 'success'
      return 'info'
    },
    etfGovernanceActionLabel(action) {
      if (action === 'REVIEW') return '优先复盘'
      if (action === 'BUILD_POSITION') return '待建仓'
      if (action === 'KEEP_PRIMARY') return '保持主ETF'
      if (action === 'OBSERVE_MAPPING') return '观察映射'
      return action || '-'
    },
    etfReviewConclusionLabel(value) {
      return reviewConclusionLabel(value)
    },
    joinItems(items) {
      return Array.isArray(items) && items.length ? items.join(', ') : '-'
    },
    strategyNames(strategyIds) {
      if (!Array.isArray(strategyIds) || !strategyIds.length) {
        return '-'
      }
      return strategyIds
        .map((id) => {
          const matched = this.strategyList.find(item => item.id === id)
          return matched ? `${id}-${matched.strategy_name}` : `策略 ${id}`
        })
        .join(', ')
    },
    formatNumber(value) {
      if (value === null || value === undefined || value === '') {
        return '-'
      }
      return Number(value).toFixed(2)
    },
    formatPercent(value) {
      if (value === null || value === undefined || value === '') {
        return '-'
      }
      return `${Number(value).toFixed(2)}%`
    },
    profitClass(value) {
      if (value === null || value === undefined || value === '') {
        return ''
      }
      const numeric = Number(value)
      if (numeric > 0) {
        return 'profit-up'
      }
      if (numeric < 0) {
        return 'profit-down'
      }
      return ''
    },
    feedbackTagType(status, executedQuantity) {
      if (status === 'EXECUTED') return 'success'
      if (status === 'MISSED') return 'danger'
      if (status === 'PENDING' && Number(executedQuantity || 0) > 0) return 'warning'
      if (status === 'PENDING') return 'info'
      return 'info'
    },
    feedbackLabel(row) {
      if (row.status === 'PENDING' && Number(row.executed_quantity || 0) > 0) {
        return 'PARTIAL'
      }
      return row.status || '-'
    },
    positionSyncTagType(status) {
      if (status === 'MATCH') return 'success'
      if (status === 'DIFF') return 'warning'
      return 'info'
    },
    symbolRowClassName({ row }) {
      if (row && this.selectedSymbolRow && row.stockCode === this.selectedSymbolRow.stockCode) {
        return 'symbol-row-active'
      }
      if (row && (row.pendingSignalCount > 0 || row.abnormalFeedbackCount > 0 || row.unmatchedExecutionCount > 0)) {
        return 'symbol-row-warning'
      }
      return ''
    },
    goEtfGovernanceReview(row) {
      if (!row || !row.reviewQuery) {
        return
      }
      this.$router.push({
        path: '/quant/review',
        query: buildReviewRouteQuery(row.reviewQuery)
      }).catch(() => {})
    },
    goEtfGovernanceBacktest(row) {
      if (!row || !row.backtestQuery) {
        return
      }
      this.$router.push({
        path: '/quant/backtest',
        query: row.backtestQuery
      }).catch(() => {})
    },
    goEtfReviewCase(row) {
      if (!row || !row.caseId) {
        return
      }
      this.$router.push({
        path: '/quant/review',
        query: buildReviewRouteQuery({
          caseId: row.caseId,
          reviewLevel: row.reviewLevel,
          stockCode: row.stockCode,
          strategyId: row.strategyId,
          signalId: row.signalId,
          scopeType: row.scopeType,
          scopePoolCode: row.scopePoolCode,
          sourceAction: row.sourceAction || 'etfReviewCase'
        })
      }).catch(() => {})
    },
    goExecutionCenter() {
      this.$router.push('/quant/execution')
    },
    goDashboard() {
      this.$router.push('/quant/dashboard')
    },
    goBacktest() {
      this.$router.push('/quant/backtest')
    }
  }
}
</script>

<style scoped>
.mt16 {
  margin-top: 16px;
}

.hero-card {
  background:
    linear-gradient(135deg, rgba(24, 144, 255, 0.08), rgba(54, 207, 201, 0.08)),
    linear-gradient(180deg, #ffffff, #f8fbff);
}

.page-header,
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
}

.page-subtitle,
.section-meta {
  color: #606266;
  font-size: 13px;
}

.page-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.summary-row {
  margin-top: 12px;
}

.hero-metric {
  min-height: 118px;
  padding: 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: inset 0 0 0 1px rgba(24, 144, 255, 0.08);
}

.hero-metric__label {
  font-size: 13px;
  color: #606266;
}

.hero-metric__value {
  margin-top: 12px;
  font-size: 34px;
  line-height: 1;
  font-weight: 700;
  color: #1f2d3d;
}

.hero-metric__hint {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.layer-card {
  min-height: 180px;
}

.layer-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.layer-card__body {
  margin-top: 14px;
  line-height: 1.8;
  color: #606266;
}

.layer-card__chips {
  margin-top: 14px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.scope-preview-panel {
  padding: 14px 16px;
  border-radius: 14px;
  background: #f7fbff;
  border: 1px solid #d9ecff;
}

.scope-preview-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-weight: 600;
  color: #303133;
}

.scope-preview-panel__meta {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.scope-preview-panel__symbols {
  margin-top: 12px;
  min-height: 72px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-content: flex-start;
}

.scope-preview-panel__note {
  margin-top: 12px;
  color: #606266;
  line-height: 1.7;
}

.hero-panel {
  height: 100%;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid rgba(221, 228, 239, 0.96);
  background: rgba(255, 255, 255, 0.86);
}

.hero-panel--flat {
  background: linear-gradient(135deg, rgba(247, 251, 255, 0.96), rgba(255, 248, 230, 0.84));
}

.hero-panel__title {
  color: #303133;
  font-size: 15px;
  font-weight: 700;
}

.symbol-pill {
  margin-right: 0;
}

.detail-summary {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.risk-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.risk-metric {
  padding: 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(235, 238, 245, 0.92);
}

.risk-label {
  display: block;
  color: #909399;
  font-size: 12px;
}

.risk-value {
  display: block;
  margin-top: 8px;
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}

.pool-rule-box {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #fafafa;
  color: #606266;
  line-height: 1.7;
}

.mapping-primary {
  font-weight: 600;
  color: #303133;
}

.mapping-secondary {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.mapping-candidates {
  display: flex;
  gap: 6px;
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
  border: 1px solid rgba(235, 238, 245, 0.92);
  background: rgba(255, 255, 255, 0.82);
  color: #606266;
  line-height: 1.7;
}

.sub-title {
  margin: 16px 0 10px;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.governance-headline {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(255, 243, 224, 0.92), rgba(236, 245, 255, 0.96));
  color: #303133;
  line-height: 1.7;
  font-weight: 600;
}

.governance-matrix-item {
  background: linear-gradient(135deg, rgba(236, 245, 255, 0.88), rgba(255, 248, 230, 0.84));
}

.governance-reason {
  margin-top: 8px;
  color: #303133;
}

.governance-priority-item {
  background: linear-gradient(135deg, rgba(250, 250, 250, 0.96), rgba(247, 251, 255, 0.96));
}

.governance-priority-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.summary-card {
  margin-bottom: 12px;
}

.summary-title {
  font-size: 13px;
  color: #606266;
}

.summary-value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.symbols-secondary-collapse {
  border-radius: 12px;
  overflow: hidden;
}

.collapse-title-shell {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 600;
}

.is-success {
  color: #67c23a;
}

.is-warning {
  color: #e6a23c;
}

.sync-wrap {
  min-height: 180px;
}

.sync-line {
  margin-bottom: 10px;
  color: #606266;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.sync-remark {
  color: #909399;
}

.profit-up {
  color: #67c23a;
  font-weight: 600;
}

.profit-down {
  color: #f56c6c;
  font-weight: 600;
}

::v-deep .el-table .symbol-row-active {
  background-color: #ecf5ff;
}

::v-deep .el-table .symbol-row-warning {
  background-color: #fff7e6;
}
</style>
