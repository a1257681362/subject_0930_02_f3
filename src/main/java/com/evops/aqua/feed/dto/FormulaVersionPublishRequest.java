package com.evops.aqua.feed.dto;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 发布投喂配方新版本：配方行按 苗种 + 日龄区间 + 水温区间 匹配投喂量。
 * 新版本发布后旧版本自动停用；已锁定投喂单冻结旧版本快照，历史投喂继续引用旧快照。
 */
public class FormulaVersionPublishRequest {

    @NotBlank(message = "配方版本号不能为空")
    private String versionNo;

    private String remark;

    @NotEmpty(message = "至少配置一条配方行")
    @Valid
    private List<FormulaItemDto> items;

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<FormulaItemDto> getItems() { return items; }
    public void setItems(List<FormulaItemDto> items) { this.items = items; }

    /**
     * 配方行：日龄/水温均为闭区间，区间端点必须合法；同苗种区间不得重叠（应用层校验）。
     */
    public static class FormulaItemDto {
        @NotBlank(message = "苗种不能为空")
        private String species;
        @NotNull(message = "日龄下限不能为空")
        private Integer dayAgeFrom;
        @NotNull(message = "日龄上限不能为空")
        private Integer dayAgeTo;
        @NotNull(message = "水温下限不能为空")
        private BigDecimal waterTempFromC;
        @NotNull(message = "水温上限不能为空")
        private BigDecimal waterTempToC;
        @NotBlank(message = "饲料名称不能为空")
        private String feedName;
        @NotNull(message = "投喂量不能为空")
        @DecimalMin(value = "0.001", message = "投喂量必须大于0")
        private BigDecimal doseKg;
        private Integer mealCount;

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
}
