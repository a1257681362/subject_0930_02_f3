package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投喂单。
 * DRAFT 可编辑/重选配方 → LOCKED 冻结配方版本与行快照、批号后才能执行 → EXECUTED 出库扣库存。
 * 临时减量（实际量小于计划量）必须登记减量原因与审批人。
 */
@TableName("t_feed_order")
public class FeedOrder extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_LOCKED = "LOCKED";
    public static final String STATUS_EXECUTED = "EXECUTED";

    public static final String TYPE_TRIAL = "TRIAL";
    public static final String TYPE_FORMAL = "FORMAL";

    private String orderNo;
    private Long pondId;
    private Long batchId;
    private LocalDate feedDate;
    private String feedType;
    private String species;
    private Integer dayAge;
    private BigDecimal waterTempC;
    private Long formulaVersionId;
    private String status;
    private BigDecimal plannedQtyKg;
    private BigDecimal actualQtyKg;
    private String reductionReason;
    private String reductionApprover;
    private Long lotId;
    private String lotNoSnapshot;
    private String feedNameSnapshot;
    private String formulaSnapshot;
    private LocalDateTime lockedTime;
    private Long lockedBy;
    private LocalDateTime executedTime;
    private Long executedBy;
    private String execRequestId;
    private String createRequestId;
    private String remark;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getFeedDate() { return feedDate; }
    public void setFeedDate(LocalDate feedDate) { this.feedDate = feedDate; }
    public String getFeedType() { return feedType; }
    public void setFeedType(String feedType) { this.feedType = feedType; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public Integer getDayAge() { return dayAge; }
    public void setDayAge(Integer dayAge) { this.dayAge = dayAge; }
    public BigDecimal getWaterTempC() { return waterTempC; }
    public void setWaterTempC(BigDecimal waterTempC) { this.waterTempC = waterTempC; }
    public Long getFormulaVersionId() { return formulaVersionId; }
    public void setFormulaVersionId(Long formulaVersionId) { this.formulaVersionId = formulaVersionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getPlannedQtyKg() { return plannedQtyKg; }
    public void setPlannedQtyKg(BigDecimal plannedQtyKg) { this.plannedQtyKg = plannedQtyKg; }
    public BigDecimal getActualQtyKg() { return actualQtyKg; }
    public void setActualQtyKg(BigDecimal actualQtyKg) { this.actualQtyKg = actualQtyKg; }
    public String getReductionReason() { return reductionReason; }
    public void setReductionReason(String reductionReason) { this.reductionReason = reductionReason; }
    public String getReductionApprover() { return reductionApprover; }
    public void setReductionApprover(String reductionApprover) { this.reductionApprover = reductionApprover; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public String getLotNoSnapshot() { return lotNoSnapshot; }
    public void setLotNoSnapshot(String lotNoSnapshot) { this.lotNoSnapshot = lotNoSnapshot; }
    public String getFeedNameSnapshot() { return feedNameSnapshot; }
    public void setFeedNameSnapshot(String feedNameSnapshot) { this.feedNameSnapshot = feedNameSnapshot; }
    public String getFormulaSnapshot() { return formulaSnapshot; }
    public void setFormulaSnapshot(String formulaSnapshot) { this.formulaSnapshot = formulaSnapshot; }
    public LocalDateTime getLockedTime() { return lockedTime; }
    public void setLockedTime(LocalDateTime lockedTime) { this.lockedTime = lockedTime; }
    public Long getLockedBy() { return lockedBy; }
    public void setLockedBy(Long lockedBy) { this.lockedBy = lockedBy; }
    public LocalDateTime getExecutedTime() { return executedTime; }
    public void setExecutedTime(LocalDateTime executedTime) { this.executedTime = executedTime; }
    public Long getExecutedBy() { return executedBy; }
    public void setExecutedBy(Long executedBy) { this.executedBy = executedBy; }
    public String getExecRequestId() { return execRequestId; }
    public void setExecRequestId(String execRequestId) { this.execRequestId = execRequestId; }
    public String getCreateRequestId() { return createRequestId; }
    public void setCreateRequestId(String createRequestId) { this.createRequestId = createRequestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
