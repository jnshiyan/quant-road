package com.ruoyi.web.controller.quant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ruoyi.web.service.quant.QuantRoadReviewService;

class QuantRoadReviewControllerTest
{
    @Test
    void reviewCasesReturnsPayload() throws Exception
    {
        QuantRoadReviewService reviewService = mock(QuantRoadReviewService.class);
        when(reviewService.reviewCases("trade", "ETF_REVIEW", "ETF", 12)).thenReturn(java.util.List.of(Map.of(
                "caseId", 301L,
                "reviewLevel", "trade",
                "reviewTargetName", "ETF 风险复盘")));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadReviewController(reviewService))
                .build();

        mockMvc.perform(get("/quant/review/cases")
                .param("reviewLevel", "trade")
                .param("caseType", "ETF_REVIEW")
                .param("assetType", "ETF")
                .param("limit", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].caseId").value(301))
                .andExpect(jsonPath("$.data[0].reviewTargetName").value("ETF 风险复盘"));
    }

    @Test
    void reviewCaseDetailReturnsPayload() throws Exception
    {
        QuantRoadReviewService reviewService = mock(QuantRoadReviewService.class);
        when(reviewService.reviewCaseDetail(301L)).thenReturn(Map.of(
                "caseId", 301L,
                "reviewLevel", "trade",
                "routeQuery", Map.of("caseId", 301L, "reviewLevel", "trade")));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadReviewController(reviewService))
                .build();

        mockMvc.perform(get("/quant/review/caseDetail")
                .param("caseId", "301"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.caseId").value(301))
                .andExpect(jsonPath("$.data.routeQuery.caseId").value(301));
    }

    @Test
    void reviewSummaryReturnsPayload() throws Exception
    {
        QuantRoadReviewService reviewService = mock(QuantRoadReviewService.class);
        when(reviewService.reviewSummary("strategy", 1L, "000001", 101L, "2026-05-01", "2026-05-31", null, null, 6, 301L))
                .thenReturn(Map.of(
                        "reviewConclusion", "OBSERVE",
                        "suggestedAction", "observe"));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadReviewController(reviewService))
                .build();

        mockMvc.perform(get("/quant/review/summary")
                .param("reviewLevel", "strategy")
                .param("strategyId", "1")
                .param("stockCode", "000001")
                .param("signalId", "101")
                .param("caseId", "301")
                .param("dateRangeStart", "2026-05-01")
                .param("dateRangeEnd", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewConclusion").value("OBSERVE"))
                .andExpect(jsonPath("$.data.suggestedAction").value("observe"));
    }

    @Test
    void reviewConclusionReturnsStoredPayload() throws Exception
    {
        QuantRoadReviewService reviewService = mock(QuantRoadReviewService.class);
        when(reviewService.submitConclusion(any())).thenReturn(Map.of(
                "reviewId", 2001L,
                "reviewConclusion", "OBSERVE"));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadReviewController(reviewService))
                .build();

        String body = """
                {
                  "caseId": 301,
                  "reviewLevel": "strategy",
                  "strategyId": 1,
                  "stockCode": "000001",
                  "signalId": 101,
                  "dateRangeStart": "2026-05-01",
                  "dateRangeEnd": "2026-05-31",
                  "reviewConclusion": "OBSERVE",
                  "primaryReason": "执行偏差仍需观察",
                  "suggestedAction": "observe",
                  "confidenceLevel": "MEDIUM",
                  "actor": "tester"
                }
                """;

        mockMvc.perform(post("/quant/review/conclusion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewId").value(2001))
                .andExpect(jsonPath("$.data.reviewConclusion").value("OBSERVE"));
    }
}
