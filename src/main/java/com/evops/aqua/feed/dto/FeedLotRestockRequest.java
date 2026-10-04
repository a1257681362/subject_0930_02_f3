package com.evops.aqua.feed.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 批号补货：向在库批号追加库存并写入库流水；requestId 幂等。
 */
public class FeedLotRestockRequest {

    @NotNull(message = "补货数量不能为空")
    @DecimalMin(value = "0.001", message = "补货数量必须大于0")
    private BigDecimal quantityKg;

    @NotBlank(message = "补货请求号不能为空")
    private String requestId;

    private String reason;

    public BigDecimal getQuantityKg() { return quantityKg; }
    public void setQuantityKg(BigDecimal quantityKg) { this.quantityKg = quantityKg; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
