package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 饲料批号流水：入库 IN / 出库 OUT / 冻结 FREEZE，只追加。
 * 库存余额以流水为事实来源，重复出库请求不得产生第二条流水。
 */
@TableName("t_feed_lot_txn")
public class FeedLotTxn extends BaseEntity {

    public static final String DIRECTION_IN = "IN";
    public static final String DIRECTION_OUT = "OUT";
    public static final String DIRECTION_FREEZE = "FREEZE";

    private Long lotId;
    private Long feedOrderId;
    private String direction;
    private BigDecimal quantityKg;
    private BigDecimal balanceKg;
    private String reason;
    private String requestId;

    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public Long getFeedOrderId() { return feedOrderId; }
    public void setFeedOrderId(Long feedOrderId) { this.feedOrderId = feedOrderId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public BigDecimal getQuantityKg() { return quantityKg; }
    public void setQuantityKg(BigDecimal quantityKg) { this.quantityKg = quantityKg; }
    public BigDecimal getBalanceKg() { return balanceKg; }
    public void setBalanceKg(BigDecimal balanceKg) { this.balanceKg = balanceKg; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
