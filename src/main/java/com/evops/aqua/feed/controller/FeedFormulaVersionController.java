package com.evops.aqua.feed.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.feed.dto.FormulaVersionPublishRequest;
import com.evops.aqua.feed.entity.FeedFormulaItem;
import com.evops.aqua.feed.entity.FeedFormulaVersion;
import com.evops.aqua.feed.mapper.FeedFormulaVersionMapper;
import com.evops.aqua.feed.service.FeedFormulaVersionService;
import com.evops.aqua.feed.service.FeedOrderService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投喂配方版本：按苗种/日龄/水温发布配方行。
 * 发布新版本后旧版本停用但永不删改；未锁定草稿投喂单在同事务内重绑新版本。
 */
@RestController
@RequestMapping("/api/aqua/feeding/formula-versions")
public class FeedFormulaVersionController {

    private final FeedFormulaVersionService formulaVersionService;
    private final FeedFormulaVersionMapper versionMapper;
    private final FeedOrderService orderService;

    public FeedFormulaVersionController(FeedFormulaVersionService formulaVersionService,
                                        FeedFormulaVersionMapper versionMapper,
                                        FeedOrderService orderService) {
        this.formulaVersionService = formulaVersionService;
        this.versionMapper = versionMapper;
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<FeedFormulaVersion> publish(
            @Validated @RequestBody FormulaVersionPublishRequest request) {
        // 发布与未锁定草稿重绑在同一事务内完成
        return ApiResponse.ok(formulaVersionService.publish(request, orderService::rebindDraftOrders));
    }

    @GetMapping
    public ApiResponse<List<FeedFormulaVersion>> list() {
        return ApiResponse.ok(versionMapper.selectList(
                new QueryWrapper<FeedFormulaVersion>().orderByDesc("id")));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("version", formulaVersionService.getOrThrow(id));
        detail.put("items", formulaVersionService.listItems(id));
        return ApiResponse.ok(detail);
    }
}
