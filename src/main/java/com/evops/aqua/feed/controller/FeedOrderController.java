package com.evops.aqua.feed.controller;

import com.evops.aqua.feed.dto.FeedOrderCreateRequest;
import com.evops.aqua.feed.dto.FeedOrderExecuteRequest;
import com.evops.aqua.feed.dto.FeedOrderLockRequest;
import com.evops.aqua.feed.entity.FeedOrder;
import com.evops.aqua.feed.service.FeedOrderService;
import com.evops.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 投喂单：按配方开单（草稿）→ 锁定批号与配方快照 → 执行出库。
 * 未锁定不允许执行；临时减量须登记原因与审批人；执行 requestId 幂等。
 */
@RestController
@RequestMapping("/api/aqua/feeding/orders")
public class FeedOrderController {

    private final FeedOrderService orderService;

    public FeedOrderController(FeedOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<FeedOrder> create(@Validated @RequestBody FeedOrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @GetMapping
    public ApiResponse<List<FeedOrder>> list(@RequestParam(required = false) Long pondId,
                                             @RequestParam(required = false) Long batchId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String feedType,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate feedDate) {
        return ApiResponse.ok(orderService.listOrders(pondId, batchId, status, feedType, feedDate));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.detail(id));
    }

    /** 锁定：校验批号效期与库存、冻结配方快照；减量必须带原因与审批人 */
    @PostMapping("/{id}/lock")
    public ApiResponse<FeedOrder> lock(@PathVariable Long id,
                                       @Validated @RequestBody FeedOrderLockRequest request) {
        return ApiResponse.ok(orderService.lock(id, request));
    }

    /** 执行：仅锁定单可执行；requestId 幂等，重复执行不二次扣库存 */
    @PostMapping("/{id}/execute")
    public ApiResponse<FeedOrder> execute(@PathVariable Long id,
                                          @Validated @RequestBody FeedOrderExecuteRequest request) {
        return ApiResponse.ok(orderService.execute(id, request));
    }

    /**
     * 日汇总：按池按日分试喂/正式两个桶统计已执行投喂单，两类不混在一起。
     */
    @GetMapping("/daily-summary")
    public ApiResponse<Map<String, Object>> dailySummary(
            @RequestParam Long pondId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate feedDate) {
        return ApiResponse.ok(orderService.dailySummary(pondId, feedDate));
    }
}
