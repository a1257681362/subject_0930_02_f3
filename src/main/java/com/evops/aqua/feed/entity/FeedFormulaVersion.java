package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 投喂配方版本。同一时刻仅一个 ACTIVE；
 * 发布新版本时旧版本置 SUPERSEDED，已锁定投喂单冻结旧版本快照，历史投喂不随改版变化。
 * 权限收紧时已发布版本作为已确认事实保留，不删除不改写。
 */
@TableName("t_feed_formula_version")
public class FeedFormulaVersion extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private String versionNo;
    private String status;
    private String remark;
    private LocalDateTime publishedTime;

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getPublishedTime() { return publishedTime; }
    public void setPublishedTime(LocalDateTime publishedTime) { this.publishedTime = publishedTime; }
}
