package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 海水养殖管理权限开关（单行 governance_code=AQUA_FEED_GOVERNANCE）。
 * OPEN 正常推进；RESTRICTED 收紧：饲料批号停止推进，配方版本保留已确认事实。
 */
@TableName("t_feed_governance")
public class FeedGovernance extends BaseEntity {

    public static final String CODE = "AQUA_FEED_GOVERNANCE";
    public static final String MODE_OPEN = "OPEN";
    public static final String MODE_RESTRICTED = "RESTRICTED";

    private String governanceCode;
    private String accessMode;
    private String reason;
    private String operator;

    public String getGovernanceCode() { return governanceCode; }
    public void setGovernanceCode(String governanceCode) { this.governanceCode = governanceCode; }
    public String getAccessMode() { return accessMode; }
    public void setAccessMode(String accessMode) { this.accessMode = accessMode; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
