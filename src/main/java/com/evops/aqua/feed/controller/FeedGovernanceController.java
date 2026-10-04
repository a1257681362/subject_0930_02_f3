package com.evops.aqua.feed.controller;

import com.evops.aqua.feed.dto.FeedGovernanceRequest;
import com.evops.aqua.feed.entity.FeedGovernance;
import com.evops.aqua.feed.service.FeedGovernanceService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 海水养殖管理权限开关。
 * RESTRICTED：饲料批号停止推进（在库批号冻结，登记/补货/锁定/执行拦截），配方版本保留已确认事实。
 */
@RestController
@RequestMapping("/api/aqua/feeding/governance")
public class FeedGovernanceController {

    private final FeedGovernanceService governanceService;

    public FeedGovernanceController(FeedGovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @GetMapping
    public ApiResponse<FeedGovernance> current() {
        return ApiResponse.ok(governanceService.current());
    }

    /** 收紧权限：批号停止推进并冻结在库批号，配方版本事实保留 */
    @PostMapping("/tighten")
    public ApiResponse<FeedGovernance> tighten(@Validated @RequestBody FeedGovernanceRequest request) {
        return ApiResponse.ok(governanceService.tighten(request.getReason(), request.getOperator()));
    }

    /** 放开权限：恢复推进，收紧期冻结批号经效期复检后解冻，过期批号保持冻结 */
    @PostMapping("/reopen")
    public ApiResponse<FeedGovernance> reopen(@Validated @RequestBody FeedGovernanceRequest request) {
        return ApiResponse.ok(governanceService.reopen(request.getReason(), request.getOperator()));
    }
}
