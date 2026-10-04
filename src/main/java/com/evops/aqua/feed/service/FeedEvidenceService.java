package com.evops.aqua.feed.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.feed.entity.FeedChangeEvidence;
import com.evops.aqua.feed.mapper.FeedChangeEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 饲料模块变更证据：批号推进、配方发布、投喂单创建/锁定/执行、治理收紧，统一只追加落库。
 */
@Service
public class FeedEvidenceService {

    private final FeedChangeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    public FeedEvidenceService(FeedChangeEvidenceMapper evidenceMapper, ObjectMapper objectMapper) {
        this.evidenceMapper = evidenceMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, String requestId,
                       Object before, Object after, String detail) {
        FeedChangeEvidence evidence = new FeedChangeEvidence();
        evidence.setEntityType(entityType);
        evidence.setEntityId(entityId);
        evidence.setAction(action);
        evidence.setRequestId(requestId);
        evidence.setBeforeJson(toJson(before));
        evidence.setAfterJson(toJson(after));
        evidence.setDetail(detail);
        evidenceMapper.insert(evidence);
    }

    /**
     * 拦截类证据：随后业务会抛异常回滚，证据必须在独立事务里提交，保证“被拦过”也留痕。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordDetached(String entityType, Long entityId, String action, String requestId,
                               Object before, Object after, String detail) {
        record(entityType, entityId, action, requestId, before, after, detail);
    }

    public List<FeedChangeEvidence> list(String entityType, Long entityId, String requestId, String action) {
        QueryWrapper<FeedChangeEvidence> wrapper = new QueryWrapper<>();
        wrapper.eq(entityType != null && !entityType.trim().isEmpty(), "entity_type", entityType);
        wrapper.eq(entityId != null, "entity_id", entityId);
        wrapper.eq(requestId != null && !requestId.trim().isEmpty(), "request_id", requestId);
        wrapper.eq(action != null && !action.trim().isEmpty(), "action", action);
        wrapper.orderByAsc("id");
        return evidenceMapper.selectList(wrapper);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    public String snapshotJson(Object value) {
        return toJson(value);
    }
}
