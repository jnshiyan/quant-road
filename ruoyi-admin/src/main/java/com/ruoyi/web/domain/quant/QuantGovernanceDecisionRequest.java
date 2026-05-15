package com.ruoyi.web.domain.quant;

import java.util.List;

public class QuantGovernanceDecisionRequest
{
    private Long baselineStrategyId;
    private Long candidateStrategyId;
    private Integer months;
    private String systemRecommendation;
    private String governanceAction;
    private String confidenceLevel;
    private String approvalStatus;
    private String decisionSource;
    private String actor;
    private String effectiveFrom;
    private String remark;
    private List<String> coreEvidences;
    private List<String> riskNotes;

    public Long getBaselineStrategyId()
    {
        return baselineStrategyId;
    }

    public void setBaselineStrategyId(Long baselineStrategyId)
    {
        this.baselineStrategyId = baselineStrategyId;
    }

    public Long getCandidateStrategyId()
    {
        return candidateStrategyId;
    }

    public void setCandidateStrategyId(Long candidateStrategyId)
    {
        this.candidateStrategyId = candidateStrategyId;
    }

    public Integer getMonths()
    {
        return months;
    }

    public void setMonths(Integer months)
    {
        this.months = months;
    }

    public String getSystemRecommendation()
    {
        return systemRecommendation;
    }

    public void setSystemRecommendation(String systemRecommendation)
    {
        this.systemRecommendation = systemRecommendation;
    }

    public String getGovernanceAction()
    {
        return governanceAction;
    }

    public void setGovernanceAction(String governanceAction)
    {
        this.governanceAction = governanceAction;
    }

    public String getConfidenceLevel()
    {
        return confidenceLevel;
    }

    public void setConfidenceLevel(String confidenceLevel)
    {
        this.confidenceLevel = confidenceLevel;
    }

    public String getApprovalStatus()
    {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus)
    {
        this.approvalStatus = approvalStatus;
    }

    public String getDecisionSource()
    {
        return decisionSource;
    }

    public void setDecisionSource(String decisionSource)
    {
        this.decisionSource = decisionSource;
    }

    public String getActor()
    {
        return actor;
    }

    public void setActor(String actor)
    {
        this.actor = actor;
    }

    public String getEffectiveFrom()
    {
        return effectiveFrom;
    }

    public void setEffectiveFrom(String effectiveFrom)
    {
        this.effectiveFrom = effectiveFrom;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public List<String> getCoreEvidences()
    {
        return coreEvidences;
    }

    public void setCoreEvidences(List<String> coreEvidences)
    {
        this.coreEvidences = coreEvidences;
    }

    public List<String> getRiskNotes()
    {
        return riskNotes;
    }

    public void setRiskNotes(List<String> riskNotes)
    {
        this.riskNotes = riskNotes;
    }
}
