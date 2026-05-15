package com.ruoyi.web.controller.quant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ruoyi.web.service.quant.QuantRoadGovernanceService;

class QuantRoadGovernanceControllerTest
{
    @Test
    void governanceHistoryReturnsRows() throws Exception
    {
        QuantRoadGovernanceService governanceService = mock(QuantRoadGovernanceService.class);
        when(governanceService.governanceHistory(1L, 2L, 5)).thenReturn(List.of(Map.of(
                "id", 1001L,
                "governanceAction", "REPLACE",
                "approvalStatus", "APPROVED")));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadGovernanceController(governanceService))
                .build();

        mockMvc.perform(get("/quant/governance/history")
                .param("baselineStrategyId", "1")
                .param("candidateStrategyId", "2")
                .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1001))
                .andExpect(jsonPath("$.data[0].governanceAction").value("REPLACE"));
    }

    @Test
    void governanceDecisionReturnsStoredPayload() throws Exception
    {
        QuantRoadGovernanceService governanceService = mock(QuantRoadGovernanceService.class);
        when(governanceService.submitGovernanceDecision(org.mockito.ArgumentMatchers.any())).thenReturn(Map.of(
                "decisionId", 1001L,
                "governanceAction", "REPLACE",
                "approvalStatus", "APPROVED"));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuantRoadGovernanceController(governanceService))
                .build();

        String body = """
                {
                  "baselineStrategyId": 1,
                  "candidateStrategyId": 2,
                  "months": 6,
                  "systemRecommendation": "PROMOTE_CANDIDATE",
                  "governanceAction": "REPLACE",
                  "confidenceLevel": "HIGH",
                  "approvalStatus": "APPROVED",
                  "decisionSource": "shadow_compare",
                  "actor": "tester",
                  "effectiveFrom": "2026-05-08",
                  "remark": "candidate stable enough"
                }
                """;

        mockMvc.perform(post("/quant/governance/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decisionId").value(1001))
                .andExpect(jsonPath("$.data.governanceAction").value("REPLACE"))
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));
    }
}
