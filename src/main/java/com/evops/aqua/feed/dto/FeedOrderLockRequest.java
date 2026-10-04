package com.evops.aqua.feed.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 锁定投喂单：指定出料批号；只有锁定后才允许执行。
 * 实际投喂量默认等于计划量；小于计划量即临时减量，必须登记减量原因与审批人。
 */
public class FeedOrderLockRequest {

    @NotNull(message = "出料饲料批号不能为空")
    private Long lotId;

    /** 不填按计划量锁定；小于计划量视为临时减量 */
    @DecimalMin(value = "0.001", message = "实际投喂量必须大于0")
    private BigDecimal actualQtyKg;

    private String reductionReason;

    private String reductionApprover;

    private String requestId;

    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public BigDecimal getActualQtyKg() { return actualQtyKg; }
    public void setActualQtyKg(BigDecimal actualQtyKg) { this.actualQtyKg = actualQtyKg; }
    public String getReductionReason() { return reductionReason; }
    public void setReductionReason(String reductionReason) { this.reductionReason = reductionReason; }
    public String getReductionApprover() { return reductionApprover; }
    public void setReductionApprover(String reductionApprover) { this.reductionApprover = reductionApprover; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
