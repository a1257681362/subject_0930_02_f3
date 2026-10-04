package com.evops.aqua.feed.controller;

import com.evops.aqua.feed.dto.FeedLotCreateRequest;
import com.evops.aqua.feed.dto.FeedLotRestockRequest;
import com.evops.aqua.feed.entity.FeedLot;
import com.evops.aqua.feed.entity.FeedLotTxn;
import com.evops.aqua.feed.service.FeedLotService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 饲料批号：登记、补货、查询与流水。
 * 收紧期登记/补货被拦截；批号不可删除（表上无删除标记，无 DELETE 接口）。
 */
@RestController
@RequestMapping("/api/aqua/feeding/lots")
public class FeedLotController {

    private final FeedLotService lotService;

    public FeedLotController(FeedLotService lotService) {
        this.lotService = lotService;
    }

    @PostMapping
    public ApiResponse<FeedLot> register(@Validated @RequestBody FeedLotCreateRequest request) {
        return ApiResponse.ok(lotService.register(request));
    }

    @GetMapping
    public ApiResponse<List<FeedLot>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(lotService.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("lot", lotService.getOrThrow(id));
        detail.put("txns", lotService.listTxns(id));
        return ApiResponse.ok(detail);
    }

    /** 补货入库：requestId 幂等，重复请求不产生第二条流水 */
    @PostMapping("/{id}/restock")
    public ApiResponse<FeedLot> restock(@PathVariable Long id,
                                        @Validated @RequestBody FeedLotRestockRequest request) {
        return ApiResponse.ok(lotService.restock(id, request));
    }
}
