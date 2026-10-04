package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 饲料批号：登记入库、按投喂出库；过期或被治理收紧时冻结。
 * 状态单向推进：IN_STOCK → FROZEN（过期/收紧冻结，可在放开后解冻回 IN_STOCK）。
 */
@TableName("t_feed_lot")
public class FeedLot extends BaseEntity {

    public static final String STATUS_IN_STOCK = "IN_STOCK";
    public static final String STATUS_FROZEN = "FROZEN";

    private String lotNo;
    private String feedName;
    private String manufacturer;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private BigDecimal stockKg;
    private String status;
    private Boolean frozenFlag;
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getFrozenFlag() { return frozenFlag; }
    public void setFrozenFlag(Boolean frozenFlag) { this.frozenFlag = frozenFlag; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
