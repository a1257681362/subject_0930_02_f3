package com.evops.aqua.feed.dto;

import javax.validation.constraints.NotBlank;

/**
 * 执行投喂单：仅 LOCKED 单可执行；requestId 强制幂等，重复执行不二次扣库存。
 */
public class FeedOrderExecuteRequest {

    @NotBlank(message = "执行请求号不能为空")
    private String requestId;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
