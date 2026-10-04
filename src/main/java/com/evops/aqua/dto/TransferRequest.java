package com.evops.aqua.dto;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 换池请求：批次整批转移到目标池，生成转移记录。
 */
public class TransferRequest {

    @NotNull(message = "目标养殖池不能为空")
    private Long toPondId;

    @NotNull(message = "换池日期不能为空")
    private LocalDate transferDate;

    private String reason;

    public Long getToPondId() { return toPondId; }
    public void setToPondId(Long toPondId) { this.toPondId = toPondId; }
    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
