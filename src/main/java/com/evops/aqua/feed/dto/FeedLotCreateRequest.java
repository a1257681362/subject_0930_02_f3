package com.evops.aqua.feed.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 饲料批号登记：批号全局唯一，登记时写入首批库存。
 */
public class FeedLotCreateRequest {

    @NotBlank(message = "饲料批号不能为空")
    private String lotNo;

    @NotBlank(message = "饲料名称不能为空")
    private String feedName;

    private String manufacturer;

    private LocalDate productionDate;

    @NotNull(message = "保质期至不能为空")
    private LocalDate expiryDate;

    @NotNull(message = "入库数量不能为空")
    @DecimalMin(value = "0.001", message = "入库数量必须大于0")
    private BigDecimal stockKg;

    private String remark;

    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public String getFeedName() { return feedName; }
    public void setFeedName(String feedName) { this.feedName = feedName; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate productionDate) { this.productionDate = productionDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public BigDecimal getStockKg() { return stockKg; }
    public void setStockKg(BigDecimal stockKg) { this.stockKg = stockKg; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
