package com.ruoyi.web.domain.quant;

import java.util.Map;

public class QuantReviewConclusionRequest
{
    private Long caseId;
    private String reviewLevel;
    private Long strategyId;
    private String stockCode;
    private Long signalId;
    private String dateRangeStart;
    private String dateRangeEnd;
    private String reviewTargetName;
    private String reviewConclusion;
    private String primaryReason;
    private String secondaryReason;
    private String suggestedAction;
    private String confidenceLevel;
    private String actor;
    private String remark;
    private String sourcePage;
    private String sourceAction;
    private Map<String, Object> evidenceSnapshot;

    public Long getCaseId()
    {
        return caseId;
    }

    public void setCaseId(Long caseId)
    {
        this.caseId = caseId;
    }

    public String getReviewLevel()
    {
        return reviewLevel;
    }

    public void setReviewLevel(String reviewLevel)
    {
        this.reviewLevel = reviewLevel;
    }

    public Long getStrategyId()
    {
        return strategyId;
    }

    public void setStrategyId(Long strategyId)
    {
        this.strategyId = strategyId;
    }

    public String getStockCode()
    {
        return stockCode;
    }

    public void setStockCode(String stockCode)
    {
        this.stockCode = stockCode;
    }

    public Long getSignalId()
    {
        return signalId;
    }

    public void setSignalId(Long signalId)
    {
        this.signalId = signalId;
    }

    public String getDateRangeStart()
    {
        return dateRangeStart;
    }

    public void setDateRangeStart(String dateRangeStart)
    {
        this.dateRangeStart = dateRangeStart;
    }

    public String getDateRangeEnd()
    {
        return dateRangeEnd;
    }

    public void setDateRangeEnd(String dateRangeEnd)
    {
        this.dateRangeEnd = dateRangeEnd;
    }

    public String getReviewTargetName()
    {
        return reviewTargetName;
    }

    public void setReviewTargetName(String reviewTargetName)
    {
        this.reviewTargetName = reviewTargetName;
    }

    public String getReviewConclusion()
    {
        return reviewConclusion;
    }

    public void setReviewConclusion(String reviewConclusion)
    {
        this.reviewConclusion = reviewConclusion;
    }

    public String getPrimaryReason()
    {
        return primaryReason;
    }

    public void setPrimaryReason(String primaryReason)
    {
        this.primaryReason = primaryReason;
    }

    public String getSecondaryReason()
    {
        return secondaryReason;
    }

    public void setSecondaryReason(String secondaryReason)
    {
        this.secondaryReason = secondaryReason;
    }

    public String getSuggestedAction()
    {
        return suggestedAction;
    }

    public void setSuggestedAction(String suggestedAction)
    {
        this.suggestedAction = suggestedAction;
    }

    public String getConfidenceLevel()
    {
        return confidenceLevel;
    }

    public void setConfidenceLevel(String confidenceLevel)
    {
        this.confidenceLevel = confidenceLevel;
    }

    public String getActor()
    {
        return actor;
    }

    public void setActor(String actor)
    {
        this.actor = actor;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getSourcePage()
    {
        return sourcePage;
    }

    public void setSourcePage(String sourcePage)
    {
        this.sourcePage = sourcePage;
    }

    public String getSourceAction()
    {
        return sourceAction;
    }

    public void setSourceAction(String sourceAction)
    {
        this.sourceAction = sourceAction;
    }

    public Map<String, Object> getEvidenceSnapshot()
    {
        return evidenceSnapshot;
    }

    public void setEvidenceSnapshot(Map<String, Object> evidenceSnapshot)
    {
        this.evidenceSnapshot = evidenceSnapshot;
    }
}
