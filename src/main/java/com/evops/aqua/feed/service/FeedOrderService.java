package com.evops.aqua.feed.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.feed.dto.FeedOrderCreateRequest;
import com.evops.aqua.feed.dto.FeedOrderExecuteRequest;
import com.evops.aqua.feed.dto.FeedOrderLockRequest;
import com.evops.aqua.feed.entity.FeedChangeEvidence;
import com.evops.aqua.feed.entity.FeedFormulaItem;
import com.evops.aqua.feed.entity.FeedFormulaMatch;
import com.evops.aqua.feed.entity.FeedFormulaVersion;
import com.evops.aqua.feed.entity.FeedLot;
import com.evops.aqua.feed.entity.FeedLotTxn;
import com.evops.aqua.feed.entity.FeedOrder;
import com.evops.aqua.feed.mapper.FeedFormulaMatchMapper;
import com.evops.aqua.feed.mapper.FeedLotTxnMapper;
import com.evops.aqua.feed.mapper.FeedOrderMapper;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 投喂单：按苗种/日龄/水温用当前生效配方开单（草稿）→ 锁定批号与配方快照 → 执行出库。
 * 只有锁定单允许执行；临时减量必须登记原因与审批人；执行请求幂等不二次扣库存；
 * 未锁定草稿在新配方版本发布时重绑，已锁定/已执行单永远冻结旧快照；
 * 日汇总严格区分试喂与正式投喂。
 */
@Service
public class FeedOrderService {

    private final FeedOrderMapper orderMapper;
    private final FeedFormulaMatchMapper matchMapper;
    private final FeedLotTxnMapper txnMapper;
    private final SeedlingBatchService batchService;
    private final FeedFormulaVersionService formulaVersionService;
    private final FeedLotService lotService;
    private final FeedGovernanceService governanceService;
    private final FeedEvidenceService evidenceService;

    public FeedOrderService(FeedOrderMapper orderMapper,
                            FeedFormulaMatchMapper matchMapper,
                            FeedLotTxnMapper txnMapper,
                            SeedlingBatchService batchService,
                            FeedFormulaVersionService formulaVersionService,
                            FeedLotService lotService,
                            FeedGovernanceService governanceService,
                            FeedEvidenceService evidenceService) {
        this.orderMapper = orderMapper;
        this.matchMapper = matchMapper;
        this.txnMapper = txnMapper;
        this.batchService = batchService;
        this.formulaVersionService = formulaVersionService;
        this.lotService = lotService;
        this.governanceService = governanceService;
        this.evidenceService = evidenceService;
    }

    /**
     * 创建投喂单草稿：按批次投苗日推算日龄，用当前生效配方匹配投喂量。
     * requestId 幂等；匹配不到配方拒绝开单（配方是投喂量的唯一来源）。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedOrder create(FeedOrderCreateRequest request) {
        governanceService.requireOpen("创建投喂单", FeedChangeEvidence.ENTITY_FEED_ORDER, null, null);

        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            List<FeedOrder> same = orderMapper.selectList(
                    new QueryWrapper<FeedOrder>().eq("create_request_id", request.getRequestId()));
            if (!same.isEmpty()) {
                return same.get(0);
            }
        }

        SeedlingBatch batch = batchService.getOrThrow(request.getBatchId());
        batchService.requireStocked(batch, "创建投喂单");
        if (!batch.getPondId().equals(request.getPondId())) {
            throw new IllegalArgumentException(
                    "批次" + batch.getBatchNo() + "当前不在池塘id=" + request.getPondId() + "，请核对池号或先登记换池");
        }
        if (request.getFeedDate().isBefore(batch.getStockDate())) {
            throw new IllegalArgumentException("投喂日期不能早于批次投苗日" + batch.getStockDate());
        }
        int dayAge = (int) ChronoUnit.DAYS.between(batch.getStockDate(), request.getFeedDate());
        String feedType = normalizeType(request.getFeedType());

        FeedFormulaVersion version = formulaVersionService.currentActive();
        Optional<FeedFormulaItem> matched =
                formulaVersionService.match(version.getId(), batch.getSpecies(), dayAge, request.getWaterTempC());

        FeedFormulaMatch match = new FeedFormulaMatch();
        match.setSpecies(batch.getSpecies());
        match.setDayAge(dayAge);
        match.setWaterTempC(request.getWaterTempC());
        match.setFormulaVersionId(version.getId());
        if (!matched.isPresent()) {
            match.setMatchedFlag(Boolean.FALSE);
            match.setDetail("生效配方" + version.getVersionNo()
                    + "无匹配行：苗种=" + batch.getSpecies() + "，日龄=" + dayAge
                    + "，水温=" + request.getWaterTempC());
            matchMapper.insert(match);
            throw new IllegalArgumentException(
                    "当前生效配方" + version.getVersionNo() + "匹配不到投喂量：苗种"
                            + batch.getSpecies() + "、日龄" + dayAge + "天、水温"
                            + request.getWaterTempC() + "℃，不能开单");
        }
        FeedFormulaItem item = matched.get();
        match.setFormulaItemId(item.getId());
        match.setMatchedFlag(Boolean.TRUE);
        match.setDetail("命中配方行id=" + item.getId() + "，投喂量=" + item.getDoseKg() + "kg/餐×"
                + item.getMealCount() + "餐");
        matchMapper.insert(match);

        FeedOrder order = new FeedOrder();
        order.setOrderNo(generateOrderNo(request, batch.getId()));
        order.setPondId(request.getPondId());
        order.setBatchId(batch.getId());
        order.setFeedDate(request.getFeedDate());
        order.setFeedType(feedType);
        order.setSpecies(batch.getSpecies());
        order.setDayAge(dayAge);
        order.setWaterTempC(request.getWaterTempC());
        order.setFormulaVersionId(version.getId());
        order.setStatus(FeedOrder.STATUS_DRAFT);
        order.setPlannedQtyKg(totalDose(item));
        order.setRemark(request.getRemark());
        order.setCreateRequestId(request.getRequestId());
        orderMapper.insert(order);

        // 回填匹配留痕对应的投喂单
        match.setFeedOrderId(order.getId());
        matchMapper.updateById(match);

        evidenceService.record(FeedChangeEvidence.ENTITY_FEED_ORDER, order.getId(),
                FeedChangeEvidence.ACTION_CREATE, request.getRequestId(), null, order,
                "创建投喂单" + order.getOrderNo() + "（" + typeText(feedType)
                        + "），按配方" + version.getVersionNo() + "匹配计划量"
                        + order.getPlannedQtyKg() + "kg");
        return order;
    }

    /**
     * 锁定投喂单：校验批号未过期、库存充足；冻结配方版本快照与批号快照。
     * 实际量小于计划量即临时减量，必须登记减量原因与审批人。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedOrder lock(Long orderId, FeedOrderLockRequest request) {
        FeedOrder order = getOrThrow(orderId);
        if (!FeedOrder.STATUS_DRAFT.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有草稿投喂单才能锁定，当前状态：" + order.getStatus());
        }
        governanceService.requireLotAdvancementAllowed("锁定投喂单", request.getLotId(),
                "投喂单" + order.getOrderNo());

        BigDecimal actualQty = request.getActualQtyKg() == null
                ? order.getPlannedQtyKg() : request.getActualQtyKg();
        if (actualQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("实际投喂量必须大于0");
        }
        if (actualQty.compareTo(order.getPlannedQtyKg()) > 0) {
            throw new IllegalArgumentException(
                    "实际投喂量" + actualQty + "kg不能大于计划量" + order.getPlannedQtyKg()
                            + "kg，加量请重新走配方开单");
        }
        boolean reduced = actualQty.compareTo(order.getPlannedQtyKg()) < 0;
        if (reduced) {
            if (request.getReductionReason() == null || request.getReductionReason().trim().isEmpty()) {
                throw new IllegalArgumentException("临时减量必须登记减量原因");
            }
            if (request.getReductionApprover() == null || request.getReductionApprover().trim().isEmpty()) {
                throw new IllegalArgumentException("临时减量必须登记审批人");
            }
        }

        FeedLot lot = lotService.getOrThrow(request.getLotId());
        lotService.requireUsable(lot, actualQty, "锁定投喂单");
        // 饲料品种必须与配方命中行一致，防止拿错批号
        FeedFormulaVersion version = formulaVersionService.getOrThrow(order.getFormulaVersionId());
        FeedFormulaItem item = formulaVersionService
                .match(version.getId(), order.getSpecies(), order.getDayAge(), order.getWaterTempC())
                .orElseThrow(() -> new IllegalArgumentException(
                        "投喂单绑定的配方版本已无法匹配原条件，不能锁定"));
        if (!lot.getFeedName().equals(item.getFeedName())) {
            throw new IllegalArgumentException(
                    "批号饲料[" + lot.getFeedName() + "]与配方要求饲料[" + item.getFeedName() + "]不一致，不能锁定");
        }

        FeedOrder before = copyOf(order);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("formulaVersionId", version.getId());
        snapshot.put("formulaVersionNo", version.getVersionNo());
        snapshot.put("formulaItemId", item.getId());
        snapshot.put("species", item.getSpecies());
        snapshot.put("dayAgeFrom", item.getDayAgeFrom());
        snapshot.put("dayAgeTo", item.getDayAgeTo());
        snapshot.put("waterTempFromC", item.getWaterTempFromC());
        snapshot.put("waterTempToC", item.getWaterTempToC());
        snapshot.put("feedName", item.getFeedName());
        snapshot.put("doseKg", item.getDoseKg());
        snapshot.put("mealCount", item.getMealCount());
        snapshot.put("plannedQtyKg", order.getPlannedQtyKg());
        snapshot.put("actualQtyKg", actualQty);
        snapshot.put("reducedFlag", reduced);

        order.setLotId(lot.getId());
        order.setLotNoSnapshot(lot.getLotNo());
        order.setFeedNameSnapshot(item.getFeedName());
        order.setFormulaSnapshot(evidenceService.snapshotJson(snapshot));
        order.setActualQtyKg(actualQty);
        if (reduced) {
            order.setReductionReason(request.getReductionReason().trim());
            order.setReductionApprover(request.getReductionApprover().trim());
        }
        order.setStatus(FeedOrder.STATUS_LOCKED);
        order.setLockedTime(LocalDateTime.now());
        orderMapper.updateById(order);

        evidenceService.record(FeedChangeEvidence.ENTITY_FEED_ORDER, order.getId(),
                FeedChangeEvidence.ACTION_LOCK, request.getRequestId(), before, order,
                "锁定投喂单" + order.getOrderNo() + "，批号=" + lot.getLotNo()
                        + "，冻结配方" + version.getVersionNo() + "快照"
                        + (reduced ? "；临时减量" + order.getPlannedQtyKg() + "->" + actualQty
                        + "kg，原因=" + order.getReductionReason()
                        + "，审批人=" + order.getReductionApprover() : ""));
        return order;
    }

    /**
     * 执行投喂：仅 LOCKED 单可执行；过期/库存不足/收紧期由批号服务拦截。
     * requestId 幂等：重复执行返回原单，绝不二次扣库存。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedOrder execute(Long orderId, FeedOrderExecuteRequest request) {
        FeedOrder order = getOrThrow(orderId);
        if (FeedOrder.STATUS_EXECUTED.equals(order.getStatus())) {
            if (request.getRequestId().equals(order.getExecRequestId())) {
                return order; // 同一执行请求重复到达
            }
            throw new IllegalArgumentException("投喂单" + order.getOrderNo() + "已执行，不能重复执行");
        }
        if (!FeedOrder.STATUS_LOCKED.equals(order.getStatus())) {
            throw new IllegalArgumentException("投喂单必须先锁定才能执行，当前状态：" + order.getStatus());
        }
        List<FeedLotTxn> sameRequest = txnMapper.selectList(new QueryWrapper<FeedLotTxn>()
                .eq("request_id", request.getRequestId())
                .eq("direction", FeedLotTxn.DIRECTION_OUT));
        if (!sameRequest.isEmpty()) {
            FeedOrder first = orderMapper.selectById(sameRequest.get(0).getFeedOrderId());
            if (first != null) {
                return first;
            }
        }
        order.setExecRequestId(request.getRequestId());
        orderMapper.updateById(order);

        lotService.deductForExecution(order);

        FeedOrder before = copyOf(order);
        order.setStatus(FeedOrder.STATUS_EXECUTED);
        order.setExecutedTime(LocalDateTime.now());
        orderMapper.updateById(order);

        evidenceService.record(FeedChangeEvidence.ENTITY_FEED_ORDER, order.getId(),
                FeedChangeEvidence.ACTION_EXECUTE, request.getRequestId(), before, order,
                "执行投喂单" + order.getOrderNo() + "，批号" + order.getLotNoSnapshot()
                        + "实际出库" + order.getActualQtyKg() + "kg");
        return order;
    }

    /**
     * 新配方版本发布后，未锁定草稿按新版本重绑并重算计划量；
     * 已锁定/已执行单不动，继续冻结旧快照。新版本无匹配的草稿保留原版本并留痕待人工处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rebindDraftOrders(Long newVersionId) {
        FeedFormulaVersion newVersion = formulaVersionService.getOrThrow(newVersionId);
        List<FeedFormulaItem> newItems = formulaVersionService.listItems(newVersionId);
        List<String> species = new java.util.ArrayList<>();
        for (FeedFormulaItem item : newItems) {
            if (!species.contains(item.getSpecies())) {
                species.add(item.getSpecies());
            }
        }
        for (String sp : species) {
            List<FeedOrder> drafts = orderMapper.selectList(new QueryWrapper<FeedOrder>()
                    .eq("status", FeedOrder.STATUS_DRAFT)
                    .eq("species", sp)
                    .ne("formula_version_id", newVersionId));
            for (FeedOrder order : drafts) {
                Optional<FeedFormulaItem> rematch = formulaVersionService
                        .match(newVersionId, sp, order.getDayAge(), order.getWaterTempC());
                FeedOrder before = copyOf(order);
                if (!rematch.isPresent()) {
                    evidenceService.record(FeedChangeEvidence.ENTITY_FEED_ORDER, order.getId(),
                            FeedChangeEvidence.ACTION_UPDATE, null, before, order,
                            "新配方" + newVersion.getVersionNo()
                                    + "无匹配行，草稿保留原版本，需人工调整后再锁定");
                    continue;
                }
                FeedFormulaItem item = rematch.get();
                BigDecimal newDose = totalDose(item);
                order.setFormulaVersionId(newVersionId);
                order.setPlannedQtyKg(newDose);
                orderMapper.updateById(order);
                evidenceService.record(FeedChangeEvidence.ENTITY_FEED_ORDER, order.getId(),
                        FeedChangeEvidence.ACTION_UPDATE, null, before, order,
                        "新版本" + newVersion.getVersionNo() + "发布，未锁定草稿重绑，计划量"
                                + before.getPlannedQtyKg() + "->" + newDose + "kg");
            }
        }
    }

    public FeedOrder getOrThrow(Long id) {
        FeedOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("投喂单不存在，id=" + id);
        }
        return order;
    }

    public List<FeedOrder> listOrders(Long pondId, Long batchId, String status, String feedType,
                                      LocalDate feedDate) {
        QueryWrapper<FeedOrder> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.eq(feedType != null && !feedType.trim().isEmpty(), "feed_type", feedType);
        wrapper.eq(feedDate != null, "feed_date", feedDate);
        wrapper.orderByAsc("feed_date", "id");
        return orderMapper.selectList(wrapper);
    }

    public Map<String, Object> detail(Long orderId) {
        FeedOrder order = getOrThrow(orderId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("order", order);
        detail.put("formulaVersion", order.getFormulaVersionId() == null
                ? null : formulaVersionService.getOrThrow(order.getFormulaVersionId()));
        detail.put("matches", matchMapper.selectList(new QueryWrapper<FeedFormulaMatch>()
                .eq("feed_order_id", orderId).orderByAsc("id")));
        detail.put("lotTxns", order.getLotId() == null ? java.util.Collections.emptyList()
                : txnMapper.selectList(new QueryWrapper<FeedLotTxn>()
                .eq("feed_order_id", orderId).orderByAsc("id")));
        return detail;
    }

    /**
     * 日汇总：按投喂类型分桶统计已执行投喂单——试喂与正式投喂绝不混在一个桶里。
     */
    public Map<String, Object> dailySummary(Long pondId, LocalDate feedDate) {
        List<FeedOrder> executed = orderMapper.selectList(new QueryWrapper<FeedOrder>()
                .eq("feed_date", feedDate)
                .eq("pond_id", pondId)
                .eq("status", FeedOrder.STATUS_EXECUTED)
                .orderByAsc("feed_type", "id"));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pondId", pondId);
        summary.put("feedDate", feedDate);
        summary.put("formal", bucket(FeedOrder.TYPE_FORMAL, executed));
        summary.put("trial", bucket(FeedOrder.TYPE_TRIAL, executed));
        // 注意：不提供跨类型合并总量——试喂与正式投喂不能混在一起
        return summary;
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    private Map<String, Object> bucket(String type, List<FeedOrder> orders) {
        Map<String, Object> bucket = new LinkedHashMap<>();
        bucket.put("feedType", type);
        bucket.put("orderCount", 0);
        bucket.put("totalActualQtyKg", BigDecimal.ZERO);
        bucket.put("reductionCount", 0);
        List<Map<String, Object>> refs = new java.util.ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        int reductions = 0;
        for (FeedOrder o : orders) {
            if (!type.equals(o.getFeedType())) {
                continue;
            }
            count++;
            total = total.add(o.getActualQtyKg());
            if (o.getActualQtyKg().compareTo(o.getPlannedQtyKg()) < 0) {
                reductions++;
            }
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("orderId", o.getId());
            ref.put("orderNo", o.getOrderNo());
            ref.put("lotNo", o.getLotNoSnapshot());
            ref.put("plannedQtyKg", o.getPlannedQtyKg());
            ref.put("actualQtyKg", o.getActualQtyKg());
            refs.add(ref);
        }
        bucket.put("orderCount", count);
        bucket.put("totalActualQtyKg", total);
        bucket.put("reductionCount", reductions);
        bucket.put("orders", refs);
        return bucket;
    }

    private BigDecimal totalDose(FeedFormulaItem item) {
        return item.getDoseKg().multiply(BigDecimal.valueOf(item.getMealCount()));
    }

    private String normalizeType(String feedType) {
        if (feedType == null || feedType.trim().isEmpty()) {
            return FeedOrder.TYPE_FORMAL;
        }
        String type = feedType.trim().toUpperCase();
        if (!FeedOrder.TYPE_TRIAL.equals(type) && !FeedOrder.TYPE_FORMAL.equals(type)) {
            throw new IllegalArgumentException("投喂类型只能是 TRIAL（试喂）或 FORMAL（正式投喂）：" + feedType);
        }
        return type;
    }

    private String typeText(String type) {
        return FeedOrder.TYPE_TRIAL.equals(type) ? "试喂" : "正式投喂";
    }

    private String generateOrderNo(FeedOrderCreateRequest request, Long batchId) {
        String datePart = request.getFeedDate().toString().replace("-", "");
        return "FO-" + datePart + "-" + batchId + "-" + Long.toHexString(System.nanoTime());
    }

    private FeedOrder copyOf(FeedOrder source) {
        FeedOrder copy = new FeedOrder();
        copy.setId(source.getId());
        copy.setOrderNo(source.getOrderNo());
        copy.setPondId(source.getPondId());
        copy.setBatchId(source.getBatchId());
        copy.setFeedDate(source.getFeedDate());
        copy.setFeedType(source.getFeedType());
        copy.setSpecies(source.getSpecies());
        copy.setDayAge(source.getDayAge());
        copy.setWaterTempC(source.getWaterTempC());
        copy.setFormulaVersionId(source.getFormulaVersionId());
        copy.setStatus(source.getStatus());
        copy.setPlannedQtyKg(source.getPlannedQtyKg());
        copy.setActualQtyKg(source.getActualQtyKg());
        copy.setReductionReason(source.getReductionReason());
        copy.setReductionApprover(source.getReductionApprover());
        copy.setLotId(source.getLotId());
        copy.setLotNoSnapshot(source.getLotNoSnapshot());
        copy.setFeedNameSnapshot(source.getFeedNameSnapshot());
        copy.setFormulaSnapshot(source.getFormulaSnapshot());
        copy.setLockedTime(source.getLockedTime());
        copy.setExecutedTime(source.getExecutedTime());
        copy.setExecRequestId(source.getExecRequestId());
        copy.setCreateRequestId(source.getCreateRequestId());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
