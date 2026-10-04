package com.evops.aqua.feed.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.feed.entity.FeedChangeEvidence;
import com.evops.aqua.feed.entity.FeedGovernance;
import com.evops.aqua.feed.mapper.FeedGovernanceMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 海水养殖管理权限开关。
 * RESTRICTED：饲料批号停止推进（登记/补货/锁定/执行一律拦截，在库批号整批冻结）；
 * 配方版本作为已确认事实保留，只读可查、不删不改。
 * OPEN：恢复推进；收紧期冻结的批号经效期复检后解冻，已过期批号继续冻结。
 */
@Service
public class FeedGovernanceService {

    private final FeedGovernanceMapper governanceMapper;
    private final FeedLotService lotService;
    private final FeedEvidenceService evidenceService;

    public FeedGovernanceService(FeedGovernanceMapper governanceMapper,
                                 @Lazy FeedLotService lotService,
                                 FeedEvidenceService evidenceService) {
        this.governanceMapper = governanceMapper;
        this.lotService = lotService;
        this.evidenceService = evidenceService;
    }

    public FeedGovernance current() {
        List<FeedGovernance> rows = governanceMapper.selectList(
                new QueryWrapper<FeedGovernance>()
                        .eq("governance_code", FeedGovernance.CODE));
        if (rows.isEmpty()) {
            FeedGovernance init = new FeedGovernance();
            init.setGovernanceCode(FeedGovernance.CODE);
            init.setAccessMode(FeedGovernance.MODE_OPEN);
            init.setReason("缺省放开");
            governanceMapper.insert(init);
            return init;
        }
        return rows.get(0);
    }

    public boolean isRestricted() {
        return FeedGovernance.MODE_RESTRICTED.equals(current().getAccessMode());
    }

    /**
     * 批号推进类操作统一闸门：收紧期直接拒绝并留 BLOCK 证据。
     */
    public void requireLotAdvancementAllowed(String action, Long lotId, String detail) {
        if (isRestricted()) {
            block(action, lotId, detail);
        }
    }

    /**
     * 配方版本类操作闸门：收紧期只保留已确认事实（只读），不允许发布新版本等推进。
     */
    public void requireOpen(String action, String entityType, Long entityId, String detail) {
        if (isRestricted()) {
            FeedGovernance g = current();
            evidenceService.recordDetached(entityType == null ? FeedChangeEvidence.ENTITY_GOVERNANCE : entityType,
                    entityId == null ? g.getId() : entityId,
                    FeedChangeEvidence.ACTION_BLOCK, null, null, g,
                    "海水养殖管理权限已收紧，拦截操作[" + action + "]"
                            + (detail == null ? "" : "：" + detail));
            throw new IllegalArgumentException("海水养殖管理权限已收紧，配方版本仅保留已确认事实，不能" + action);
        }
    }

    private void block(String action, Long lotId, String detail) {
        FeedGovernance g = current();
        evidenceService.recordDetached(FeedChangeEvidence.ENTITY_GOVERNANCE, g.getId(),
                FeedChangeEvidence.ACTION_BLOCK, null, null, g,
                "海水养殖管理权限已收紧，拦截批号推进动作[" + action + "]"
                        + (lotId == null ? "" : "，批号id=" + lotId)
                        + (detail == null ? "" : "：" + detail));
        throw new IllegalArgumentException("海水养殖管理权限已收紧，饲料批号停止推进，不能" + action);
    }

    @Transactional(rollbackFor = Exception.class)
    public FeedGovernance tighten(String reason, String operator) {
        FeedGovernance before = copyOf(current());
        FeedGovernance g = current();
        if (FeedGovernance.MODE_RESTRICTED.equals(g.getAccessMode())) {
            return g; // 幂等：重复收紧不重复冻结
        }
        g.setAccessMode(FeedGovernance.MODE_RESTRICTED);
        g.setReason(reason);
        g.setOperator(operator);
        governanceMapper.updateById(g);
        // 权限收紧：在库批号一律停止推进（冻结），配方版本不做任何删改
        int frozenLots = lotService.freezeAllInStock("海水养殖管理权限收紧：" + reason, operator);
        evidenceService.record(FeedChangeEvidence.ENTITY_GOVERNANCE, g.getId(),
                FeedChangeEvidence.ACTION_TIGHTEN, null, before, g,
                "海水养殖管理权限收紧，冻结在库饲料批号" + frozenLots + "个；配方版本作为已确认事实保留");
        return g;
    }

    @Transactional(rollbackFor = Exception.class)
    public FeedGovernance reopen(String reason, String operator) {
        FeedGovernance before = copyOf(current());
        FeedGovernance g = current();
        if (FeedGovernance.MODE_OPEN.equals(g.getAccessMode())) {
            return g;
        }
        g.setAccessMode(FeedGovernance.MODE_OPEN);
        g.setReason(reason);
        g.setOperator(operator);
        governanceMapper.updateById(g);
        // 放开后效期复检：仍在保质期内的批号解冻恢复推进；已过期的继续冻结
        int reopened = lotService.reopenFrozenLots();
        evidenceService.record(FeedChangeEvidence.ENTITY_GOVERNANCE, g.getId(),
                FeedChangeEvidence.ACTION_REOPEN, null, before, g,
                "海水养殖管理权限放开，复检解冻有效批号" + reopened + "个，过期批号保持冻结");
        return g;
    }

    private FeedGovernance copyOf(FeedGovernance source) {
        FeedGovernance copy = new FeedGovernance();
        copy.setId(source.getId());
        copy.setGovernanceCode(source.getGovernanceCode());
        copy.setAccessMode(source.getAccessMode());
        copy.setReason(source.getReason());
        copy.setOperator(source.getOperator());
        return copy;
    }
}
