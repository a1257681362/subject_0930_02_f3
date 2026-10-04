package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 配方行：按 苗种 + 日龄区间（天，闭区间）+ 水温区间（℃，闭区间）确定投喂量。
 * 行随版本快照，发布后不改写；同版本同苗种的日龄/水温覆盖区间不得重叠。
 */
@TableName("t_feed_formula_item")
public class FeedFormulaItem extends BaseEntity {

    private Long versionId;
    private String species;
    private Integer dayAgeFrom;
    private Integer dayAgeTo;
    private BigDecimal waterTempFromC;
    private BigDecimal waterTempToC;
    private String feedName;
    private BigDecimal doseKg;
    private Integer mealCount;

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public Integer getDayAgeFrom() { return dayAgeFrom; }
    public void setDayAgeFrom(Integer dayAgeFrom) { this.dayAgeFrom = dayAgeFrom; }
    public Integer getDayAgeTo() { return dayAgeTo; }
    public void setDayAgeTo(Integer dayAgeTo) { this.dayAgeTo = dayAgeTo; }
    public BigDecimal getWaterTempFromC() { return waterTempFromC; }
    public void setWaterTempFromC(BigDecimal waterTempFromC) { this.waterTempFromC = waterTempFromC; }
    public BigDecimal getWaterTempToC() { return waterTempToC; }
    public void setWaterTempToC(BigDecimal waterTempToC) { this.waterTempToC = waterTempToC; }
    public String getFeedName() { return feedName; }
    public void setFeedName(String feedName) { this.feedName = feedName; }
    public BigDecimal getDoseKg() { return doseKg; }
    public void setDoseKg(BigDecimal doseKg) { this.doseKg = doseKg; }
    public Integer getMealCount() { return mealCount; }
    public void setMealCount(Integer mealCount) { this.mealCount = mealCount; }
}
