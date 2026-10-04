package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 饲料模块变更证据：批号推进、配方发布、投喂单创建/锁定/执行、治理收紧，全部只追加。
 */
@TableName("t_feed_change_evidence")
public class FeedChangeEvidence extends BaseEntity {

    public static final String ENTITY_LOT = "FEED_LOT";
    public static final String ENTITY_FORMULA_VERSION = "FEED_FORMULA_VERSION";
    public static final String ENTITY_FEED_ORDER = "FEED_ORDER";
    public static final String ENTITY_GOVERNANCE = "FEED_GOVERNANCE";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_LOCK = "LOCK";
    public static final String ACTION_EXECUTE = "EXECUTE";
    public static final String ACTION_TIGHTEN = "TIGHTEN";
    public static final String ACTION_REOPEN = "REOPEN";
    public static final String ACTION_BLOCK = "BLOCK";

    private String entityType;
    private Long entityId;
    private String action;
    private String requestId;
    private String beforeJson;
    private String afterJson;
    private String detail;

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
