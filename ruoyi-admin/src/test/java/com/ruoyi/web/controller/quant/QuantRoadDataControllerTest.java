package com.ruoyi.web.controller.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ruoyi.web.service.quant.QuantDispatchDetailService;
import com.ruoyi.web.service.quant.QuantOperationsCenterService;
import com.ruoyi.web.service.quant.QuantRoadPythonService;
import com.ruoyi.web.service.quant.QuantRoadGovernanceService;
import com.ruoyi.web.service.quant.QuantRoadQueryService;
import com.ruoyi.web.service.quant.QuantRoadSymbolScopeService;
import com.ruoyi.web.service.quant.QuantTaskCenterService;

class QuantRoadDataControllerTest
{
    private static final class LegacySignalsQueryService extends QuantRoadQueryService
    {
        private List<Map<String, Object>> legacyResponse;
        private LocalDate actualDate;

        private LegacySignalsQueryService()
        {
            super(mock(JdbcTemplate.class));
        }

        @Override
        public List<Map<String, Object>> signals(LocalDate signalDate)
        {
            actualDate = signalDate;
            return legacyResponse;
        }

        @Override
        public Map<String, Object> signals(LocalDate signalDate, int pageNum, int pageSize)
        {
            throw new AssertionError("signals(LocalDate,int,int) should not be called");
        }
    }

    private static final class PagingSignalsQueryService extends QuantRoadQueryService
    {
        private Map<String, Object> pagedResponse;
        private LocalDate actualDate;
        private Integer actualPageNum;
        private Integer actualPageSize;

        private PagingSignalsQueryService()
        {
            super(mock(JdbcTemplate.class));
        }

        @Override
        public List<Map<String, Object>> signals(LocalDate signalDate)
        {
            throw new AssertionError("signals(LocalDate) should not be called");
        }

        @Override
        public Map<String, Object> signals(LocalDate signalDate, int pageNum, int pageSize)
        {
            actualDate = signalDate;
            actualPageNum = pageNum;
            actualPageSize = pageSize;
            return pagedResponse;
        }
    }

    private MockMvc buildMockMvc(
            QuantRoadQueryService queryService,
            QuantRoadSymbolScopeService symbolScopeService)
    {
        return buildMockMvc(
                queryService,
                symbolScopeService,
                mock(QuantTaskCenterService.class),
                mock(QuantOperationsCenterService.class),
                mock(QuantDispatchDetailService.class));
    }

    private MockMvc buildMockMvc(
            QuantRoadQueryService queryService,
            QuantRoadSymbolScopeService symbolScopeService,
            QuantTaskCenterService taskCenterService,
            QuantOperationsCenterService operationsCenterService,
            QuantDispatchDetailService dispatchDetailService)
    {
        return MockMvcBuilders
                .standaloneSetup(new QuantRoadDataController(
                        queryService,
                        mock(QuantRoadPythonService.class),
                        mock(QuantRoadGovernanceService.class),
                        symbolScopeService,
                        taskCenterService,
                        operationsCenterService,
                        dispatchDetailService))
                .build();
    }

    @Test
    void signalsReturnsDerivedFields() throws Exception
    {
        LegacySignalsQueryService queryService = new LegacySignalsQueryService();
        queryService.legacyResponse = List.of(Map.of(
                "id", 5001L,
                "stock_code", "000001",
                "execution_due_date", "2026-05-07",
                "match_hint", "pending_record_execution"));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/signals").param("signalDate", "2026-05-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].execution_due_date").value("2026-05-07"))
                .andExpect(jsonPath("$.data[0].match_hint").value("pending_record_execution"));

        assertEquals(LocalDate.of(2026, 5, 6), queryService.actualDate);
    }

    @Test
    void signalsSupportsPageAndPageSize() throws Exception
    {
        PagingSignalsQueryService queryService = new PagingSignalsQueryService();
        queryService.pagedResponse = Map.of(
                "rows", List.of(Map.of("stock_code", "510300", "signal_type", "BUY")),
                "total", 3,
                "pageNum", 2,
                "pageSize", 2);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/signals")
                .param("signalDate", "2026-05-12")
                .param("pageNum", "2")
                .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.pageNum").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.rows.length()").value(1))
                .andExpect(jsonPath("$.data.rows[0].stock_code").value("510300"));

        assertEquals(LocalDate.of(2026, 5, 12), queryService.actualDate);
        assertEquals(2, queryService.actualPageNum);
        assertEquals(2, queryService.actualPageSize);
    }

    @Test
    void signalsWithOnlyPageNumKeepsLegacyArrayShape() throws Exception
    {
        LegacySignalsQueryService queryService = new LegacySignalsQueryService();
        queryService.legacyResponse = List.of(Map.of(
                "id", 5001L,
                "stock_code", "000001",
                "match_hint", "pending_record_execution"));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/signals")
                .param("signalDate", "2026-05-12")
                .param("pageNum", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stock_code").value("000001"))
                .andExpect(jsonPath("$.data[0].match_hint").value("pending_record_execution"));

        assertEquals(LocalDate.of(2026, 5, 12), queryService.actualDate);
    }

    @Test
    void signalsWithOnlyPageSizeKeepsLegacyArrayShape() throws Exception
    {
        LegacySignalsQueryService queryService = new LegacySignalsQueryService();
        queryService.legacyResponse = List.of(Map.of(
                "id", 5001L,
                "stock_code", "000001",
                "match_hint", "pending_record_execution"));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/signals")
                .param("signalDate", "2026-05-12")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stock_code").value("000001"))
                .andExpect(jsonPath("$.data[0].match_hint").value("pending_record_execution"));

        assertEquals(LocalDate.of(2026, 5, 12), queryService.actualDate);
    }

    @Test
    void executionReconciliationSummaryReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.executionReconciliationSummary()).thenReturn(Map.of(
                "pendingSignalCount", 2,
                "executedSignalCount", 6,
                "unmatchedExecutionCount", 1));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/executionReconciliationSummary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingSignalCount").value(2))
                .andExpect(jsonPath("$.data.unmatchedExecutionCount").value(1));
    }

    @Test
    void executionMatchCandidatesReturnsRows() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.executionMatchCandidates(9001L, 5)).thenReturn(List.of(Map.of(
                "executionRecordId", 9001L,
                "signalId", 3001L,
                "matchScore", 97)));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/executionMatchCandidates")
                .param("executionRecordId", "9001")
                .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].signalId").value(3001))
                .andExpect(jsonPath("$.data[0].matchScore").value(97));
    }

    @Test
    void positionSyncResultReturnsStatus() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.positionSyncResult(1L, "000001")).thenReturn(Map.of(
                "syncStatus", "MATCH",
                "differenceCount", 0));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/positionSyncResult")
                .param("strategyId", "1")
                .param("stockCode", "000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syncStatus").value("MATCH"))
                .andExpect(jsonPath("$.data.differenceCount").value(0));
    }

    @Test
    void dashboardActionItemsReturnsRows() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.dashboardActionItems(8)).thenReturn(List.of(Map.of(
                "actionType", "EXECUTION_RECONCILIATION",
                "priority", "P0",
                "title", "处理未匹配成交")));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/dashboardActionItems").param("limit", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].actionType").value("EXECUTION_RECONCILIATION"))
                .andExpect(jsonPath("$.data[0].priority").value("P0"));
    }

    @Test
    void positionRiskSummaryReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.positionRiskSummary()).thenReturn(Map.of(
                "totalPositionPct", 68.5,
                "riskLevel", "MEDIUM",
                "stopLossWarningCount", 2));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/positionRiskSummary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.data.stopLossWarningCount").value(2));
    }

    @Test
    void dashboardDeepLinksReturnsRows() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.dashboardDeepLinks()).thenReturn(List.of(Map.of(
                "title", "进入执行回写",
                "path", "/quant/execution",
                "badge", 3)));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/dashboardDeepLinks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("进入执行回写"))
                .andExpect(jsonPath("$.data[0].badge").value(3));
    }

    @Test
    void reviewCandidatesReturnsRows() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.reviewCandidates(6)).thenReturn(List.of(Map.of(
                "reviewLevel", "trade",
                "reviewTargetName", "000001",
                "status", "MISSED")));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/reviewCandidates").param("limit", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reviewLevel").value("trade"))
                .andExpect(jsonPath("$.data[0].status").value("MISSED"));
    }

    @Test
    void etfOverviewReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.etfOverview()).thenReturn(Map.of(
                "etfUniverseCount", 12,
                "indexMappingCount", 4,
                "recommendedScopeType", "etf_pool"));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/etfOverview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.etfUniverseCount").value(12))
                .andExpect(jsonPath("$.data.recommendedScopeType").value("etf_pool"));
    }

    @Test
    void etfGovernanceSummaryReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.etfGovernanceSummary()).thenReturn(Map.of(
                "summary", Map.of(
                        "indexMappingCount", 4,
                        "holdingCount", 2),
                "mappingGovernanceRows", List.of(Map.of(
                        "indexCode", "000300",
                        "primaryEtfCode", "510300",
                        "governanceAction", "REVIEW"))));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/etfGovernanceSummary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.indexMappingCount").value(4))
                .andExpect(jsonPath("$.data.mappingGovernanceRows[0].primaryEtfCode").value("510300"))
                .andExpect(jsonPath("$.data.mappingGovernanceRows[0].governanceAction").value("REVIEW"));
    }

    @Test
    void signalExplainReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.signalExplain(5001L)).thenReturn(Map.of(
                "signalId", 5001L,
                "headline", "BUY 信号待执行",
                "executionStatus", "PENDING"));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/signalExplain/5001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signalId").value(5001))
                .andExpect(jsonPath("$.data.executionStatus").value("PENDING"));
    }

    @Test
    void jobReadinessReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.jobReadiness(12L)).thenReturn(Map.of(
                "status", "READY_WITH_WARNINGS",
                "batchId", 12L,
                "canEnterDashboard", true));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/jobReadiness").param("batchId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY_WITH_WARNINGS"))
                .andExpect(jsonPath("$.data.canEnterDashboard").value(true));
    }

    @Test
    void jobSopHintsReturnsRows() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.jobSopHints(12L)).thenReturn(List.of(Map.of(
                "code", "recoverBatch",
                "title", "恢复失败批次")));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/jobSopHints").param("batchId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("recoverBatch"))
                .andExpect(jsonPath("$.data[0].title").value("恢复失败批次"));
    }

    @Test
    void asyncWorkerSummaryReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        when(queryService.asyncWorkerSummary()).thenReturn(Map.of(
                "status", "BLOCKED",
                "queuedShardCount", 4,
                "activeWorkerCount", 0));
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/asyncWorkerSummary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.queuedShardCount").value(4))
                .andExpect(jsonPath("$.data.activeWorkerCount").value(0));
    }

    @Test
    void symbolScopeOptionsReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.symbolScopeOptions()).thenReturn(Map.of(
                "presetScopes", List.of(Map.of("scopeType", "stock_pool", "label", "个股池")),
                "constraintOptions", List.of(Map.of("field", "whitelist", "label", "白名单"))));

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/symbolScopeOptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presetScopes[0].scopeType").value("stock_pool"))
                .andExpect(jsonPath("$.data.constraintOptions[0].field").value("whitelist"));
    }

    @Test
    void symbolPoolsReturnsRows() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.symbolPools()).thenReturn(List.of(Map.of(
                "poolCode", "STOCK_CORE",
                "poolName", "个股规则池",
                "includedCount", 80)));

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/symbolPools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].poolCode").value("STOCK_CORE"))
                .andExpect(jsonPath("$.data[0].includedCount").value(80));
    }

    @Test
    void symbolPoolDetailReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.symbolPoolDetail("STOCK_CORE")).thenReturn(Map.of(
                "poolCode", "STOCK_CORE",
                "members", List.of(Map.of("stockCode", "000001", "inclusionStatus", "INCLUDED")),
                "summary", Map.of("includedCount", 1)));

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/symbolPoolDetail").param("poolCode", "STOCK_CORE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.poolCode").value("STOCK_CORE"))
                .andExpect(jsonPath("$.data.members[0].stockCode").value("000001"))
                .andExpect(jsonPath("$.data.summary.includedCount").value(1));
    }

    @Test
    void indexEtfMappingsReturnsRows() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.indexEtfMappings()).thenReturn(List.of(Map.of(
                "indexCode", "000300",
                "primaryEtfCode", "510300")));

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/indexEtfMappings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].indexCode").value("000300"))
                .andExpect(jsonPath("$.data[0].primaryEtfCode").value("510300"));
    }

    @Test
    void symbolScopePreviewReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        when(symbolScopeService.symbolScopePreview(
                "stock_pool",
                "STOCK_CORE",
                List.of("000001"),
                List.of("000002"),
                List.of("000003"),
                List.of("510300"))).thenReturn(Map.of(
                        "scopeType", "stock_pool",
                        "scopePoolCode", "STOCK_CORE",
                        "resolvedCount", 2,
                        "symbols", List.of("000001", "510300")));

        MockMvc mockMvc = buildMockMvc(queryService, symbolScopeService);

        mockMvc.perform(get("/quant/data/symbolScopePreview")
                .param("scopeType", "stock_pool")
                .param("scopePoolCode", "STOCK_CORE")
                .param("symbols", "000001")
                .param("whitelist", "000002")
                .param("blacklist", "000003")
                .param("adHocSymbols", "510300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scopeType").value("stock_pool"))
                .andExpect(jsonPath("$.data.resolvedCount").value(2))
                .andExpect(jsonPath("$.data.symbols[1]").value("510300"));
    }

    @Test
    void taskCenterSummaryReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        QuantTaskCenterService taskCenterService = mock(QuantTaskCenterService.class);
        when(taskCenterService.summary()).thenReturn(Map.of(
                "todayStatus", Map.of(
                        "code", "WARNING",
                        "label", "警告"),
                "primaryTask", Map.of(
                        "taskName", "盘后主流程",
                        "waitingFor", "run-strategy"),
                "nextAction", Map.of(
                        "code", "GO_OPERATIONS",
                        "label", "先处理警告")));

        MockMvc mockMvc = buildMockMvc(
                queryService,
                symbolScopeService,
                taskCenterService,
                mock(QuantOperationsCenterService.class),
                mock(QuantDispatchDetailService.class));

        mockMvc.perform(get("/quant/data/taskCenterSummary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayStatus.code").value("WARNING"))
                .andExpect(jsonPath("$.data.primaryTask.waitingFor").value("run-strategy"))
                .andExpect(jsonPath("$.data.nextAction.code").value("GO_OPERATIONS"));
    }

    @Test
    void dispatchDetailReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        QuantDispatchDetailService dispatchDetailService = mock(QuantDispatchDetailService.class);
        when(dispatchDetailService.detailByJobId(88L)).thenReturn(Map.of(
                "jobId", 88L,
                "phaseCode", "RUN_STRATEGY",
                "waitingKind", "COMPUTING",
                "status", Map.of("status", "RUNNING"),
                "currentSymbols", List.of("510300"),
                "shards", List.of(Map.of("shardIndex", 0))));

        MockMvc mockMvc = buildMockMvc(
                queryService,
                symbolScopeService,
                mock(QuantTaskCenterService.class),
                mock(QuantOperationsCenterService.class),
                dispatchDetailService);

        mockMvc.perform(get("/quant/data/dispatchDetail/88"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(88))
                .andExpect(jsonPath("$.data.phaseCode").value("RUN_STRATEGY"))
                .andExpect(jsonPath("$.data.waitingKind").value("COMPUTING"))
                .andExpect(jsonPath("$.data.status.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.currentSymbols[0]").value("510300"));
    }

    @Test
    void operationsCenterSummaryReturnsPayload() throws Exception
    {
        QuantRoadQueryService queryService = mock(QuantRoadQueryService.class);
        QuantRoadSymbolScopeService symbolScopeService = mock(QuantRoadSymbolScopeService.class);
        QuantOperationsCenterService operationsCenterService = mock(QuantOperationsCenterService.class);
        when(operationsCenterService.summary()).thenReturn(Map.of(
                "topBlocker", Map.of(
                        "layer", "worker",
                        "title", "无活跃 worker"),
                "toolbox", Map.of(
                        "compatibilityActions", List.of(Map.of(
                                "code", "legacyFullDaily",
                                "label", "兼容 fullDaily")))));

        MockMvc mockMvc = buildMockMvc(
                queryService,
                symbolScopeService,
                mock(QuantTaskCenterService.class),
                operationsCenterService,
                mock(QuantDispatchDetailService.class));

        mockMvc.perform(get("/quant/data/operationsCenterSummary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topBlocker.layer").value("worker"))
                .andExpect(jsonPath("$.data.toolbox.compatibilityActions[0].code").value("legacyFullDaily"));
    }
}
