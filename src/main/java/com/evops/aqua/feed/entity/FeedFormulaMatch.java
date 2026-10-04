package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 配方选择留痕：每次按苗种/日龄/水温匹配都追加一行，命中或未命中均留痕。
 */
@TableName("t_feed_formula_match")
public class FeedFormulaMatch extends BaseEntity {

    private Long feedOrderId;
    private Long formulaVersionId;
    private Long formulaItemId;
    private String species;
    private Integer dayAge;
    private BigDecimal waterTempC;
    private Boolean matchedFlag;
    private String detail;

    public Long getFeedOrderId() { return feedOrderId; }
    public void setFeedOrderId(Long feedOrderId) { this.feedOrderId = feedOrderId; }
    public Long getFormulaVersionId() { return formulaVersionId; }
    public void setFormulaVersionId(Long formulaVersionId) { this.formulaVersionId = formulaVersionId; }
    public Long getFormulaItemId() { return formulaItemId; }
    public void setFormulaItemId(Long formulaItemId) { this.formulaItemId = formulaItemId; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public Integer getDayAge() { return dayAge; }
    public void setDayAge(Integer dayAge) { this.dayAge = dayAge; }
    public BigDecimal getWaterTempC() { return waterTempC; }
    public void setWaterTempC(BigDecimal waterTempC) { this.waterTempC = waterTempC; }
    public Boolean getMatchedFlag() { return matchedFlag; }
    public void setMatchedFlag(Boolean matchedFlag) { this.matchedFlag = matchedFlag; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
