package com.evops.aqua.feed.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.feed.dto.FeedLotCreateRequest;
import com.evops.aqua.feed.dto.FeedLotRestockRequest;
import com.evops.aqua.feed.entity.FeedChangeEvidence;
import com.evops.aqua.feed.entity.FeedLot;
import com.evops.aqua.feed.entity.FeedLotTxn;
import com.evops.aqua.feed.entity.FeedOrder;
import com.evops.aqua.feed.mapper.FeedLotMapper;
import com.evops.aqua.feed.mapper.FeedLotTxnMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 饲料批号生命周期：登记入库 → 补货 → 投喂出库；过期或权限收紧时冻结。
 * 库存以只追加流水为事实来源；批号推进（登记/补货/出库/冻结）统一受治理开关闸门约束。
 */
@Service
public class FeedLotService {

    private final FeedLotMapper lotMapper;
    private final FeedLotTxnMapper txnMapper;
    private final FeedGovernanceService governanceService;
    private final FeedEvidenceService evidenceService;

    public FeedLotService(FeedLotMapper lotMapper,
                          FeedLotTxnMapper txnMapper,
                          FeedGovernanceService governanceService,
                          FeedEvidenceService evidenceService) {
        this.lotMapper = lotMapper;
        this.txnMapper = txnMapper;
        this.governanceService = governanceService;
        this.evidenceService = evidenceService;
    }

    /**
     * 登记批号与首批库存。收紧期拒绝推进。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedLot register(FeedLotCreateRequest request) {
        governanceService.requireLotAdvancementAllowed("登记饲料批号", null, request.getLotNo());
        Long duplicated = lotMapper.selectCount(
                new QueryWrapper<FeedLot>().eq("lot_no", request.getLotNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("饲料批号已存在：" + request.getLotNo());
        }
        if (request.getProductionDate() != null
                && request.getExpiryDate().isBefore(request.getProductionDate())) {
            throw new IllegalArgumentException("保质期不能早于生产日期");
        }

        FeedLot lot = new FeedLot();
        lot.setLotNo(request.getLotNo());
        lot.setFeedName(request.getFeedName());
        lot.setManufacturer(request.getManufacturer());
        lot.setProductionDate(request.getProductionDate());
        lot.setExpiryDate(request.getExpiryDate());
        lot.setStockKg(request.getStockKg());
        lot.setStatus(FeedLot.STATUS_IN_STOCK);
        lot.setFrozenFlag(Boolean.FALSE);
        lot.setRemark(request.getRemark());
        // 登记时即已过期的批号直接冻结，不能用于投喂
        if (lot.getExpiryDate().isBefore(LocalDate.now())) {
            lot.setStatus(FeedLot.STATUS_FROZEN);
            lot.setFrozenFlag(Boolean.TRUE);
        }
        lotMapper.insert(lot);

        appendTxn(lot, null, FeedLotTxn.DIRECTION_IN, lot.getStockKg(),
                lot.getStockKg(), "批号登记首批入库", null);
        evidenceService.record(FeedChangeEvidence.ENTITY_LOT, lot.getId(),
                FeedChangeEvidence.ACTION_CREATE, null, null, lot,
                "登记饲料批号" + lot.getLotNo() + "，入库" + lot.getStockKg() + "kg"
                        + (FeedLot.STATUS_FROZEN.equals(lot.getStatus()) ? "，登记时已过保质期，直接冻结" : ""));
        return lot;
    }

    /**
     * 补货：向在库批号追加库存并写入库流水；requestId 幂等，重复请求返回首次结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedLot restock(Long lotId, FeedLotRestockRequest request) {
        governanceService.requireLotAdvancementAllowed("饲料批号补货", lotId, null);
        FeedLot lot = getOrThrow(lotId);
        List<FeedLotTxn> sameRequest = txnMapper.selectList(
                new QueryWrapper<FeedLotTxn>().eq("request_id", request.getRequestId()));
        if (!sameRequest.isEmpty()) {
            return lot; // 重复补货请求不产生第二条流水
        }
        if (!FeedLot.STATUS_IN_STOCK.equals(lot.getStatus())) {
            throw new IllegalArgumentException("批号" + lot.getLotNo() + "已冻结，不能补货");
        }
        if (lot.getExpiryDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("批号" + lot.getLotNo() + "已过保质期，不能补货");
        }

        FeedLot before = copyOf(lot);
        lot.setStockKg(lot.getStockKg().add(request.getQuantityKg()));
        lotMapper.updateById(lot);
        appendTxn(lot, null, FeedLotTxn.DIRECTION_IN, request.getQuantityKg(),
                lot.getStockKg(), request.getReason(), request.getRequestId());
        evidenceService.record(FeedChangeEvidence.ENTITY_LOT, lot.getId(),
                FeedChangeEvidence.ACTION_UPDATE, request.getRequestId(), before, lot,
                "补货入库" + request.getQuantityKg() + "kg，库存"
                        + before.getStockKg() + "->" + lot.getStockKg() + "kg");
        return lot;
    }

    /**
     * 投喂前复检：过期批号即时冻结并拦截。返回仍在有效期内的在库批号。
     */
    @Transactional(rollbackFor = Exception.class)
    public int freezeExpiredLots() {
        List<FeedLot> expired = lotMapper.selectList(new QueryWrapper<FeedLot>()
                .eq("status", FeedLot.STATUS_IN_STOCK)
                .lt("expiry_date", LocalDate.now()));
        for (FeedLot lot : expired) {
            freeze(lot, "超过保质期" + lot.getExpiryDate() + "，系统复检冻结", null);
        }
        return expired.size();
    }

    /**
     * 权限收紧：全部在库批号停止推进。
     */
    @Transactional(rollbackFor = Exception.class)
    public int freezeAllInStock(String reason, String operator) {
        List<FeedLot> lots = lotMapper.selectList(
                new QueryWrapper<FeedLot>().eq("status", FeedLot.STATUS_IN_STOCK));
        for (FeedLot lot : lots) {
            freeze(lot, reason, operator);
        }
        return lots.size();
    }

    /**
     * 权限放开后效期复检：未过期批号解冻，已过期批号保持冻结。
     */
    @Transactional(rollbackFor = Exception.class)
    public int reopenFrozenLots() {
        List<FeedLot> frozen = lotMapper.selectList(new QueryWrapper<FeedLot>()
                .eq("status", FeedLot.STATUS_FROZEN)
                .ge("expiry_date", LocalDate.now()));
        int reopened = 0;
        for (FeedLot lot : frozen) {
            FeedLot before = copyOf(lot);
            lot.setStatus(FeedLot.STATUS_IN_STOCK);
            lot.setFrozenFlag(Boolean.FALSE);
            lotMapper.updateById(lot);
            evidenceService.record(FeedChangeEvidence.ENTITY_LOT, lot.getId(),
                    FeedChangeEvidence.ACTION_UPDATE, null, before, lot,
                    "权限放开并经效期复检，批号" + lot.getLotNo() + "解冻恢复推进");
            reopened++;
        }
        return reopened;
    }

    /**
     * 锁定/执行前校验批号可用性：在库、未过期、库存充足。
     */
    public void requireUsable(FeedLot lot, BigDecimal requiredKg, String action) {
        freezeExpiredLots();
        FeedLot reloaded = lotMapper.selectById(lot.getId());
        if (!FeedLot.STATUS_IN_STOCK.equals(reloaded.getStatus())) {
            throw new IllegalArgumentException(
                    "批号" + reloaded.getLotNo() + "已冻结，不能" + action);
        }
        if (reloaded.getExpiryDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "批号" + reloaded.getLotNo() + "已过保质期（" + reloaded.getExpiryDate() + "），不能" + action);
        }
        if (reloaded.getStockKg().compareTo(requiredKg) < 0) {
            throw new IllegalArgumentException(
                    "批号" + reloaded.getLotNo() + "库存不足：需要" + requiredKg + "kg，现存"
                            + reloaded.getStockKg() + "kg，不能" + action);
        }
    }

    /**
     * 投喂执行出库：扣减库存并写 OUT 流水（调用方保证执行请求幂等）。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedLot deductForExecution(FeedOrder order) {
        governanceService.requireLotAdvancementAllowed("执行投喂出库", order.getLotId(),
                "投喂单" + order.getOrderNo());
        FeedLot lot = getOrThrow(order.getLotId());
        requireUsable(lot, order.getActualQtyKg(), "执行投喂");
        FeedLot before = copyOf(lot);
        lot.setStockKg(lot.getStockKg().subtract(order.getActualQtyKg()));
        lotMapper.updateById(lot);
        appendTxn(lot, order.getId(), FeedLotTxn.DIRECTION_OUT, order.getActualQtyKg(),
                lot.getStockKg(), "投喂单" + order.getOrderNo() + "执行出库",
                order.getExecRequestId());
        evidenceService.record(FeedChangeEvidence.ENTITY_LOT, lot.getId(),
                FeedChangeEvidence.ACTION_UPDATE, order.getExecRequestId(), before, lot,
                "投喂单" + order.getOrderNo() + "出库" + order.getActualQtyKg()
                        + "kg，库存" + before.getStockKg() + "->" + lot.getStockKg() + "kg");
        // 出库后若恰好用尽，冻结批号防止再次领用
        if (lot.getStockKg().compareTo(BigDecimal.ZERO) == 0) {
            freeze(lot, "库存用尽冻结", null);
        }
        return lot;
    }

    public FeedLot getOrThrow(Long id) {
        FeedLot lot = lotMapper.selectById(id);
        if (lot == null) {
            throw new IllegalArgumentException("饲料批号不存在，id=" + id);
        }
        lot.setStockKg(currentBalance(lot.getId(), lot.getStockKg()));
        return lot;
    }

    public List<FeedLot> list(String status) {
        QueryWrapper<FeedLot> wrapper = new QueryWrapper<>();
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return lotMapper.selectList(wrapper);
    }

    public List<FeedLotTxn> listTxns(Long lotId) {
        return txnMapper.selectList(new QueryWrapper<FeedLotTxn>()
                .eq(lotId != null, "lot_id", lotId)
                .orderByAsc("id"));
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    private void freeze(FeedLot lot, String reason, String operator) {
        if (FeedLot.STATUS_FROZEN.equals(lot.getStatus())) {
            return;
        }
        FeedLot before = copyOf(lot);
        lot.setStatus(FeedLot.STATUS_FROZEN);
        lot.setFrozenFlag(Boolean.TRUE);
        lotMapper.updateById(lot);
        appendTxn(lot, null, FeedLotTxn.DIRECTION_FREEZE, BigDecimal.ZERO,
                lot.getStockKg(), reason, null);
        evidenceService.record(FeedChangeEvidence.ENTITY_LOT, lot.getId(),
                FeedChangeEvidence.ACTION_UPDATE, null, before, lot,
                "批号" + lot.getLotNo() + "冻结：" + reason
                        + (operator == null ? "" : "（操作人：" + operator + "）"));
    }

    private void appendTxn(FeedLot lot, Long orderId, String direction,
                          BigDecimal quantity, BigDecimal balance, String reason, String requestId) {
        FeedLotTxn txn = new FeedLotTxn();
        txn.setLotId(lot.getId());
        txn.setFeedOrderId(orderId);
        txn.setDirection(direction);
        txn.setQuantityKg(quantity);
        txn.setBalanceKg(balance);
        txn.setReason(reason);
        txn.setRequestId(requestId);
        txnMapper.insert(txn);
    }

    private BigDecimal currentBalance(Long lotId, BigDecimal fallback) {
        List<FeedLotTxn> txns = txnMapper.selectList(
                new QueryWrapper<FeedLotTxn>().eq("lot_id", lotId).orderByDesc("id").last("LIMIT 1"));
        return txns.isEmpty() ? fallback : txns.get(0).getBalanceKg();
    }

    private FeedLot copyOf(FeedLot source) {
        FeedLot copy = new FeedLot();
        copy.setId(source.getId());
        copy.setLotNo(source.getLotNo());
        copy.setFeedName(source.getFeedName());
        copy.setManufacturer(source.getManufacturer());
        copy.setProductionDate(source.getProductionDate());
        copy.setExpiryDate(source.getExpiryDate());
        copy.setStockKg(source.getStockKg());
        copy.setStatus(source.getStatus());
        copy.setFrozenFlag(source.getFrozenFlag());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
