package com.evops.aqua.feed.dto;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建投喂单（草稿）：按批次/日期/水温用当前生效配方自动匹配投喂量。
 * 日龄由批次投苗日与投喂日期推算；TRIAL 为试喂，FORMAL 为正式投喂，两者不混在日汇总。
 */
public class FeedOrderCreateRequest {

    @NotNull(message = "养殖池不能为空")
    private Long pondId;

    @NotNull(message = "投苗批次不能为空")
    private Long batchId;

    @NotNull(message = "投喂日期不能为空")
    private LocalDate feedDate;

    /** TRIAL 试喂 / FORMAL 正式投喂，默认 FORMAL */
    private String feedType;

    @NotNull(message = "投喂时水温不能为空")
    private BigDecimal waterTempC;

    private String requestId;

    private String remark;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getFeedDate() { return feedDate; }
    public void setFeedDate(LocalDate feedDate) { this.feedDate = feedDate; }
    public String getFeedType() { return feedType; }
    public void setFeedType(String feedType) { this.feedType = feedType; }
    public BigDecimal getWaterTempC() { return waterTempC; }
    public void setWaterTempC(BigDecimal waterTempC) { this.waterTempC = waterTempC; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
