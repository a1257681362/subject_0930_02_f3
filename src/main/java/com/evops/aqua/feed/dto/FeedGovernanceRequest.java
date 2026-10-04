package com.evops.aqua.feed.dto;

import javax.validation.constraints.NotBlank;

/**
 * 海水养殖管理权限开关：
 * RESTRICTED 收紧——饲料批号停止推进，配方版本保留已确认事实；
 * OPEN 放开——恢复推进，收紧期冻结的批号经效期复检后可继续使用。
 */
public class FeedGovernanceRequest {

    @NotBlank(message = "权限模式不能为空（OPEN/RESTRICTED）")
    private String accessMode;

    private String reason;

    private String operator;

    public String getAccessMode() { return accessMode; }
    public void setAccessMode(String accessMode) { this.accessMode = accessMode; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
