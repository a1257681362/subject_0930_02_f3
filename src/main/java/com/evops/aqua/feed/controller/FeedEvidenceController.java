package com.evops.aqua.feed.controller;

import com.evops.aqua.feed.entity.FeedChangeEvidence;
import com.evops.aqua.feed.service.FeedEvidenceService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 饲料模块变更证据：可按对象、动作、requestId 追溯所有推进动作（含收紧期拦截 BLOCK）。
 */
@RestController
@RequestMapping("/api/aqua/feeding/evidence")
public class FeedEvidenceController {

    private final FeedEvidenceService evidenceService;

    public FeedEvidenceController(FeedEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping
    public ApiResponse<List<FeedChangeEvidence>> list(@RequestParam(required = false) String entityType,
                                                      @RequestParam(required = false) Long entityId,
                                                      @RequestParam(required = false) String requestId,
                                                      @RequestParam(required = false) String action) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId, action));
    }
}
