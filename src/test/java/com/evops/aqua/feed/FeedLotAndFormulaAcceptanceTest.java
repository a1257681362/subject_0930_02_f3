package com.evops.aqua.feed;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.feed.entity.FeedChangeEvidence;
import com.evops.aqua.feed.entity.FeedFormulaVersion;
import com.evops.aqua.feed.entity.FeedGovernance;
import com.evops.aqua.feed.entity.FeedLot;
import com.evops.aqua.feed.entity.FeedLotTxn;
import com.evops.aqua.feed.entity.FeedOrder;
import com.evops.aqua.feed.mapper.FeedChangeEvidenceMapper;
import com.evops.aqua.feed.mapper.FeedFormulaItemMapper;
import com.evops.aqua.feed.mapper.FeedFormulaVersionMapper;
import com.evops.aqua.feed.mapper.FeedGovernanceMapper;
import com.evops.aqua.feed.mapper.FeedLotMapper;
import com.evops.aqua.feed.mapper.FeedLotTxnMapper;
import com.evops.aqua.feed.mapper.FeedOrderMapper;
import com.evops.aqua.mapper.PondMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 饲料批号与投喂配方验收（aqua-3）。
 * 主线：建池投苗 → 发布按苗种/日龄/水温匹配的配方 → 登记批号（过期即冻结）
 * → 开单匹配 → 未锁定不许执行、过期/库存不足/减量缺审批全拦截
 * → 锁定冻结旧快照、执行扣库存且幂等 → 试喂与正式分桶日汇总
 * → 新版本发布只重绑未锁定草稿，历史投喂继续引用旧快照
 * → 权限收紧批号停推进、配方事实保留 → 放开后效期复检恢复。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FeedLotAndFormulaAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PondMapper pondMapper;
    @Autowired
    private FeedLotMapper lotMapper;
    @Autowired
    private FeedLotTxnMapper lotTxnMapper;
    @Autowired
    private FeedOrderMapper orderMapper;
    @Autowired
    private FeedFormulaVersionMapper versionMapper;
    @Autowired
    private FeedFormulaItemMapper itemMapper;
    @Autowired
    private FeedGovernanceMapper governanceMapper;
    @Autowired
    private FeedChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private static final LocalDate TODAY = LocalDate.now();
    private static final String SPECIES = "石斑鱼";

    private Long pondId;
    private Long batchId;
    private Long versionV1;
    private Long versionV2;
    private Long lotA;
    private Long lotExpired;
    private Long lotLow;
    private Long orderFormal;
    private Long orderTrial;
    private Long orderDraftKept;
    private Long orderLockedBeforeTighten;

    @Test
    void acceptanceFeedLotAndFormula() {
        checkpoint("CP0", "登录认证", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("username", "bootstrap");
            body.put("password", "bootstrap");
            ResponseEntity<String> resp = call(HttpMethod.POST, "/api/auth/login", body);
            assertSuccess(json(resp), "登录");
            List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
            check(cookies != null && !cookies.isEmpty(), "登录成功但未下发会话Cookie");
            sessionCookie = cookies.get(0).split(";")[0];
        });

        checkpoint("CP1", "建池并投苗（石斑鱼，投苗第10天）", () -> {
            Map<String, Object> pond = new HashMap<>();
            pond.put("pondCode", "FD-P01");
            pond.put("pondName", "饲料1号池");
            pond.put("seaArea", "东山湾海区");
            pond.put("waterDepthM", "2.00");
            pond.put("salinityPpt", "28.0");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", pond));
            assertSuccess(resp, "建池");
            pondId = resp.path("data").path("id").asLong();
            Pond row = pondMapper.selectById(pondId);
            check(row != null && "饲料1号池".equals(row.getPondName()), "养殖池落库异常");

            Map<String, Object> batch = new HashMap<>();
            batch.put("batchNo", "FD-BATCH-001");
            batch.put("pondId", pondId);
            batch.put("species", SPECIES);
            batch.put("seedSource", "东山育苗场");
            batch.put("quantity", 5000);
            batch.put("stockDate", TODAY.minusDays(10).toString());
            batch.put("plannedHarvestDate", TODAY.plusMonths(6).toString());
            JsonNode bresp = json(call(HttpMethod.POST, "/api/aqua/batches", batch));
            assertSuccess(bresp, "投苗");
            batchId = bresp.path("data").path("id").asLong();
        });

        checkpoint("CP2", "发布配方V1：按苗种/日龄/水温确定投喂量；重叠区间被拒绝", () -> {
            JsonNode overlap = json(call(HttpMethod.POST, "/api/aqua/feeding/formula-versions",
                    formulaBody("FD-FORMULA-BAD",
                            item(SPECIES, 0, 30, "20.00", "26.00", "石斑配合饲料", "2.0", 2),
                            item(SPECIES, 0, 30, "25.00", "30.00", "石斑配合饲料", "3.0", 2))));
            check(!overlap.path("success").asBoolean(true), "水温区间重叠的配方应被拒绝");
            check(overlap.path("message").asText().contains("重叠"),
                    "重叠提示语不符：" + overlap.path("message").asText());

            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/feeding/formula-versions",
                    formulaBody("FD-FORMULA-V1",
                            item(SPECIES, 0, 30, "20.00", "24.99", "石斑配合饲料", "2.000", 2),
                            item(SPECIES, 0, 30, "25.00", "30.00", "石斑配合饲料", "3.000", 2),
                            item(SPECIES, 31, 90, "20.00", "30.00", "石斑成鱼料", "4.000", 1))));
            assertSuccess(resp, "发布配方V1");
            versionV1 = resp.path("data").path("id").asLong();
            check(FeedFormulaVersion.STATUS_ACTIVE.equals(resp.path("data").path("status").asText()),
                    "V1应为生效版本");
            Long itemCount = itemMapper.selectCount(
                    new QueryWrapper<com.evops.aqua.feed.entity.FeedFormulaItem>()
                            .eq("version_id", versionV1));
            check(itemCount != null && itemCount == 3L, "V1应有3条配方行，实际=" + itemCount);
        });

        checkpoint("CP3", "登记饲料批号：正常批号在库，过期批号登记即冻结，批号号唯一，补货幂等", () -> {
            lotA = registerLot("FD-LOT-A", "石斑配合饲料", "100.00", TODAY.plusDays(30));
            check(FeedLot.STATUS_IN_STOCK.equals(lotMapper.selectById(lotA).getStatus()), "LOT-A应在库");

            JsonNode dup = json(call(HttpMethod.POST, "/api/aqua/feeding/lots",
                    lotBody("FD-LOT-A", "石斑配合饲料", "10.00", TODAY.plusDays(10))));
            check(!dup.path("success").asBoolean(true), "重复批号应被拒绝");

            lotExpired = registerLot("FD-LOT-EXP", "石斑配合饲料", "20.00", TODAY.minusDays(1));
            check(FeedLot.STATUS_FROZEN.equals(lotMapper.selectById(lotExpired).getStatus()),
                    "登记时已过期的批号应直接冻结");

            lotLow = registerLot("FD-LOT-LOW", "石斑配合饲料", "1.00", TODAY.plusDays(5));

            // 补货 requestId 幂等：同号两次只入一次库
            Map<String, Object> restock = new HashMap<>();
            restock.put("quantityKg", "5.00");
            restock.put("requestId", "rs-1");
            restock.put("reason", "验收补货");
            assertSuccess(json(call(HttpMethod.POST,
                    "/api/aqua/feeding/lots/" + lotA + "/restock", restock)), "首次补货");
            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/lots/" + lotA + "/restock", restock));
            assertSuccess(again, "重复补货请求");
            FeedLot a = lotMapper.selectById(lotA);
            check(new BigDecimal("105.00").compareTo(a.getStockKg()) == 0,
                    "重复补货不应二次入库，库存应为105.00，实际=" + a.getStockKg());
            Long inTxns = lotTxnMapper.selectCount(new QueryWrapper<FeedLotTxn>()
                    .eq("lot_id", lotA).eq("direction", FeedLotTxn.DIRECTION_IN));
            check(inTxns != null && inTxns == 2L, "LOT-A入库流水应为2条（登记+1次补货），实际=" + inTxns);
        });

        checkpoint("CP4", "开单按苗种/日龄/水温匹配配方（日龄10/水温23→2.0kg×2餐=4.0kg）；匹配不到拒绝", () -> {
            orderFormal = createOrder("FORMAL", "23.00", "fo-create-1");
            FeedOrder formal = orderMapper.selectById(orderFormal);
            check(FeedOrder.STATUS_DRAFT.equals(formal.getStatus()), "新单应为草稿");
            check(new BigDecimal("4.000").compareTo(formal.getPlannedQtyKg()) == 0,
                    "计划量应为4.000kg，实际=" + formal.getPlannedQtyKg());
            check(formal.getDayAge() == 10, "日龄应为投苗第10天，实际=" + formal.getDayAge());

            orderTrial = createOrder("TRIAL", "23.00", "fo-create-2");
            check(FeedOrder.TYPE_TRIAL.equals(orderMapper.selectById(orderTrial).getFeedType()),
                    "试喂单类型应为TRIAL");

            orderDraftKept = createOrder("FORMAL", "23.00", "fo-create-3");

            // 水温31超出所有区间 → 匹配不到，拒绝开单
            JsonNode noMatch = json(call(HttpMethod.POST, "/api/aqua/feeding/orders",
                    orderBody("FORMAL", "31.00", "fo-create-nomatch")));
            check(!noMatch.path("success").asBoolean(true), "无匹配配方应拒绝开单");
            check(noMatch.path("message").asText().contains("匹配不到"),
                    "无匹配提示语不符：" + noMatch.path("message").asText());

            // 创建 requestId 幂等
            JsonNode same = json(call(HttpMethod.POST, "/api/aqua/feeding/orders",
                    orderBody("FORMAL", "23.00", "fo-create-1")));
            assertSuccess(same, "重复开单请求");
            check(same.path("data").path("id").asLong() == orderFormal, "重复开单应返回首次投喂单");
        });

        checkpoint("CP5", "未锁定不许执行；过期批号、库存不足批号锁定时拦截", () -> {
            JsonNode execDraft = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderFormal + "/execute", execBody("ex-1")));
            check(!execDraft.path("success").asBoolean(true), "草稿单执行应被拒绝");
            check(execDraft.path("message").asText().contains("先锁定"),
                    "提示应要求先锁定：" + execDraft.path("message").asText());

            JsonNode lockExpired = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderFormal + "/lock",
                    lockBody(lotExpired, "3.00", "天气减量", "王主管")));
            check(!lockExpired.path("success").asBoolean(true), "过期批号锁定应被拒绝");
            check(lockExpired.path("message").asText().contains("冻结"),
                "过期批号提示语不符：" + lockExpired.path("message").asText());

            JsonNode lockLow = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderFormal + "/lock",
                    lockBody(lotLow, null, null, null)));
            check(!lockLow.path("success").asBoolean(true), "库存不足批号锁定应被拒绝");
            check(lockLow.path("message").asText().contains("库存不足"),
                    "库存不足提示语不符：" + lockLow.path("message").asText());

            check(FeedOrder.STATUS_DRAFT.equals(orderMapper.selectById(orderFormal).getStatus()),
                    "拦截后投喂单必须仍是草稿");
        });

        checkpoint("CP6", "临时减量必须登记原因和审批人；锁定后冻结配方版本快照", () -> {
            JsonNode noReason = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderFormal + "/lock",
                    lockBody(lotA, "3.00", null, null)));
            check(!noReason.path("success").asBoolean(true), "减量无原因应被拒绝");
            JsonNode noApprover = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderFormal + "/lock",
                    lockBody(lotA, "3.00", "天气减量", null)));
            check(!noApprover.path("success").asBoolean(true), "减量无审批人应被拒绝");

            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderFormal + "/lock",
                    lockBody(lotA, "3.00", "冷空气降温减料三成", "王主管")));
            assertSuccess(resp, "带原因审批人锁定");
            check(FeedOrder.STATUS_LOCKED.equals(resp.path("data").path("status").asText()),
                    "锁定后状态应为LOCKED");
            FeedOrder locked = orderMapper.selectById(orderFormal);
            check("冷空气降温减料三成".equals(locked.getReductionReason()), "减量原因未落库");
            check("王主管".equals(locked.getReductionApprover()), "审批人未落库");
            check(locked.getFormulaSnapshot() != null && locked.getFormulaSnapshot().contains("FD-FORMULA-V1"),
                    "锁定单必须冻结V1配方快照");
            check(locked.getFormulaSnapshot().contains("石斑配合饲料"), "快照应含饲料名称");
            check("FD-LOT-A".equals(locked.getLotNoSnapshot()), "应冻结批号快照FD-LOT-A");
        });

        checkpoint("CP7", "执行扣库存并写出库流水；重复执行不二次扣库存", () -> {
            BigDecimal before = lotMapper.selectById(lotA).getStockKg();
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderFormal + "/execute", execBody("ex-1")));
            assertSuccess(resp, "执行正式投喂单");
            check(FeedOrder.STATUS_EXECUTED.equals(resp.path("data").path("status").asText()),
                    "执行后状态应为EXECUTED");
            BigDecimal after = lotMapper.selectById(lotA).getStockKg();
            check(new BigDecimal("3.00").compareTo(before.subtract(after)) == 0,
                    "库存应扣减3.00kg：" + before + "->" + after);
            List<FeedLotTxn> outs = lotTxnMapper.selectList(new QueryWrapper<FeedLotTxn>()
                    .eq("lot_id", lotA).eq("direction", FeedLotTxn.DIRECTION_OUT));
            check(outs.size() == 1 && new BigDecimal("3.00").compareTo(outs.get(0).getQuantityKg()) == 0,
                    "应有1条3.00kg出库流水，实际=" + outs.size());

            JsonNode sameReq = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderFormal + "/execute", execBody("ex-1")));
            assertSuccess(sameReq, "同一执行请求重复到达");
            check(sameReq.path("data").path("id").asLong() == orderFormal, "重复执行应返回原单");
            check(new BigDecimal("102.00").compareTo(lotMapper.selectById(lotA).getStockKg()) == 0,
                    "重复执行不得二次扣库存，库存应保持102.00");
            Long outCount = lotTxnMapper.selectCount(new QueryWrapper<FeedLotTxn>()
                    .eq("lot_id", lotA).eq("direction", FeedLotTxn.DIRECTION_OUT));
            check(outCount != null && outCount == 1L, "出库流水应仍为1条，实际=" + outCount);

            JsonNode otherReq = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderFormal + "/execute", execBody("ex-OTHER")));
            check(!otherReq.path("success").asBoolean(true), "换请求号重复执行必须被拒绝");
        });

        checkpoint("CP8", "日汇总不能把试喂和正式投喂混在一起（分桶统计、减量计数）", () -> {
            // 试喂单减量到1.0kg并执行
            assertSuccess(json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderTrial + "/lock",
                    lockBody(lotA, "1.00", "试喂新配方半量观察", "李技师"))), "锁定试喂单");
            assertSuccess(json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderTrial + "/execute", execBody("ex-2"))), "执行试喂单");

            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/feeding/orders/daily-summary?pondId=" + pondId
                            + "&feedDate=" + TODAY, null));
            assertSuccess(resp, "查询日汇总");
            JsonNode formal = resp.path("data").path("formal");
            JsonNode trial = resp.path("data").path("trial");
            check(formal.path("feedType").asText().equals("FORMAL")
                    && trial.path("feedType").asText().equals("TRIAL"), "日汇总必须分试喂/正式两桶");
            check(formal.path("orderCount").asInt() == 1, "正式桶应1单，实际=" + formal.path("orderCount").asInt());
            check(new BigDecimal("3.00").compareTo(new BigDecimal(formal.path("totalActualQtyKg").asText())) == 0,
                    "正式桶总量应为3.00kg：" + formal.path("totalActualQtyKg").asText());
            check(formal.path("reductionCount").asInt() == 1, "正式桶应有1次减量记录");
            check(trial.path("orderCount").asInt() == 1, "试喂桶应1单，实际=" + trial.path("orderCount").asInt());
            check(new BigDecimal("1.00").compareTo(new BigDecimal(trial.path("totalActualQtyKg").asText())) == 0,
                    "试喂桶总量应为1.00kg：" + trial.path("totalActualQtyKg").asText());
            // 未执行草稿不得进入汇总
            check(formal.path("orderCount").asInt() == 1 && trial.path("orderCount").asInt() == 1,
                    "草稿单混入了日汇总");
        });

        checkpoint("CP9", "配方调整产生V2：未锁定草稿重绑V2，已执行历史投喂继续引用V1旧快照", () -> {
            FeedOrder draftBefore = orderMapper.selectById(orderDraftKept);
            check(versionV1.equals(draftBefore.getFormulaVersionId()), "留档草稿发布前应绑定V1");
            check(new BigDecimal("4.000").compareTo(draftBefore.getPlannedQtyKg()) == 0,
                    "草稿发布前计划量应为V1的4.000");

            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/feeding/formula-versions",
                    formulaBody("FD-FORMULA-V2",
                            item(SPECIES, 0, 30, "20.00", "25.00", "石斑配合饲料", "2.500", 2),
                            item(SPECIES, 31, 90, "20.00", "30.00", "石斑成鱼料", "4.000", 1))));
            assertSuccess(resp, "发布配方V2");
            versionV2 = resp.path("data").path("id").asLong();
            check(FeedFormulaVersion.STATUS_SUPERSEDED.equals(versionMapper.selectById(versionV1).getStatus()),
                    "发布V2后V1应停用");
            check(FeedFormulaVersion.STATUS_ACTIVE.equals(versionMapper.selectById(versionV2).getStatus()),
                    "V2应为生效版本");

            // 未锁定草稿自动重绑 V2 并重算：2.5kg×2餐=5.0kg
            FeedOrder draftAfter = orderMapper.selectById(orderDraftKept);
            check(versionV2.equals(draftAfter.getFormulaVersionId()),
                    "未锁定草稿应重绑V2，实际=" + draftAfter.getFormulaVersionId());
            check(new BigDecimal("5.000").compareTo(draftAfter.getPlannedQtyKg()) == 0,
                    "草稿计划量应按V2重算为5.000，实际=" + draftAfter.getPlannedQtyKg());

            // 新开的草稿直接按V2
            FeedOrder newDraft = orderMapper.selectById(createOrder("FORMAL", "23.00", "fo-create-v2"));
            check(versionV2.equals(newDraft.getFormulaVersionId())
                    && new BigDecimal("5.000").compareTo(newDraft.getPlannedQtyKg()) == 0,
                    "新单应按V2匹配5.000kg");

            // 已执行历史投喂：状态、版本指针、冻结快照全部维持V1事实
            FeedOrder history = orderMapper.selectById(orderFormal);
            check(FeedOrder.STATUS_EXECUTED.equals(history.getStatus()), "历史投喂单必须仍是已执行");
            check(versionV1.equals(history.getFormulaVersionId()),
                    "历史投喂单必须继续引用V1，实际=" + history.getFormulaVersionId());
            check(history.getFormulaSnapshot().contains("FD-FORMULA-V1")
                    && new BigDecimal("3.00").compareTo(history.getActualQtyKg()) == 0,
                    "历史投喂必须保留V1快照与实际3.00kg事实");
        });

        checkpoint("CP10", "配方版本保留已确认事实：停用的V1行数据不删不改、仍可查询", () -> {
            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/feeding/formula-versions/" + versionV1, null));
            assertSuccess(resp, "查询已停用V1");
            check("SUPERSEDED".equals(resp.path("data").path("version").path("status").asText()),
                    "V1应为停用态");
            check(resp.path("data").path("items").size() == 3,
                    "V1的3条配方行必须作为事实保留，实际="
                            + resp.path("data").path("items").size());
            JsonNode listResp = json(call(HttpMethod.GET, "/api/aqua/feeding/formula-versions", null));
            check(listResp.path("data").size() == 2, "V1/V2两个版本都应可查");
        });

        checkpoint("CP11", "权限收紧：饲料批号停止推进（登记/补货/开单/锁定/执行全拦截），在库批号冻结", () -> {
            // 收紧前再准备一张已锁定未执行的投喂单（用小库存批号减量锁）
            Long preLocked = createOrder("FORMAL", "23.00", "fo-create-prelock");
            assertSuccess(json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + preLocked + "/lock",
                    lockBody(lotLow, "0.50", "收紧前预留锁定单", "王主管"))), "收紧前锁定预留单");
            orderLockedBeforeTighten = preLocked;
            BigDecimal lowBefore = lotMapper.selectById(lotLow).getStockKg();

            JsonNode t = json(call(HttpMethod.POST, "/api/aqua/feeding/governance/tighten",
                    governanceBody("RESTRICTED", "海区环保督察，暂停饲料作业", "张场长")));
            assertSuccess(t, "收紧权限");
            check(FeedGovernance.MODE_RESTRICTED.equals(t.path("data").path("accessMode").asText()),
                    "模式应为RESTRICTED");
            check(FeedLot.STATUS_FROZEN.equals(lotMapper.selectById(lotA).getStatus()),
                    "收紧后在库批号LOT-A应冻结");
            check(FeedLot.STATUS_FROZEN.equals(lotMapper.selectById(lotLow).getStatus()),
                    "收紧后在库批号LOT-LOW应冻结");

            // 批号推进类动作全部拦截
            JsonNode reg = json(call(HttpMethod.POST, "/api/aqua/feeding/lots",
                    lotBody("FD-LOT-X", "石斑配合饲料", "9.00", TODAY.plusDays(10))));
            check(!reg.path("success").asBoolean(true) && reg.path("message").asText().contains("收紧"),
                    "收紧期登记批号必须被拦截");
            JsonNode rs = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/lots/" + lotA + "/restock",
                    mapOf("quantityKg", "1.00", "requestId", "rs-x")));
            check(!rs.path("success").asBoolean(true), "收紧期补货必须被拦截");
            JsonNode create = json(call(HttpMethod.POST, "/api/aqua/feeding/orders",
                    orderBody("FORMAL", "23.00", "fo-create-blocked")));
            check(!create.path("success").asBoolean(true), "收紧期创建投喂单必须被拦截");
            JsonNode lock = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderDraftKept + "/lock",
                    lockBody(lotA, "5.00", null, null)));
            check(!lock.path("success").asBoolean(true), "收紧期锁定投喂单必须被拦截");
            JsonNode exec = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderLockedBeforeTighten + "/execute", execBody("ex-blocked")));
            check(!exec.path("success").asBoolean(true) && exec.path("message").asText().contains("收紧"),
                    "收紧期执行出库必须被拦截");
            check(lowBefore.compareTo(lotMapper.selectById(lotLow).getStockKg()) == 0,
                    "收紧期拦截执行后库存不得变化");
            check(FeedOrder.STATUS_LOCKED.equals(orderMapper.selectById(orderLockedBeforeTighten).getStatus()),
                    "被拦截的锁定单必须仍为LOCKED");

            // 配方版本只保留事实：发布被拦截，V1/V2 仍可查
            JsonNode pubV3 = json(call(HttpMethod.POST, "/api/aqua/feeding/formula-versions",
                    formulaBody("FD-FORMULA-V3",
                            item(SPECIES, 0, 30, "20.00", "25.00", "石斑配合饲料", "9.0", 1))));
            check(!pubV3.path("success").asBoolean(true) && pubV3.path("message").asText().contains("收紧"),
                    "收紧期不得发布新配方版本");
            Long v3Count = versionMapper.selectCount(
                    new QueryWrapper<FeedFormulaVersion>().eq("version_no", "FD-FORMULA-V3"));
            check(v3Count != null && v3Count == 0L, "被拦截的V3不得落库");
            Long versionTotal = versionMapper.selectCount(new QueryWrapper<>());
            check(versionTotal != null && versionTotal == 2L, "收紧期版本事实必须保留为V1/V2，实际=" + versionTotal);

            Long blocks = evidenceMapper.selectCount(new QueryWrapper<FeedChangeEvidence>()
                    .eq("action", FeedChangeEvidence.ACTION_BLOCK));
            check(blocks != null && blocks >= 5L, "收紧期拦截证据应至少5条，实际=" + blocks);
        });

        checkpoint("CP12", "权限放开：有效批号效期复检解冻、过期批号继续冻结，被拦的锁定单恢复执行", () -> {
            JsonNode r = json(call(HttpMethod.POST, "/api/aqua/feeding/governance/reopen",
                    governanceBody("OPEN", "督察结束恢复作业", "张场长")));
            assertSuccess(r, "放开权限");
            check(FeedLot.STATUS_IN_STOCK.equals(lotMapper.selectById(lotA).getStatus()),
                    "未过期批号LOT-A应解冻恢复推进");
            check(FeedLot.STATUS_IN_STOCK.equals(lotMapper.selectById(lotLow).getStatus()),
                    "未过期批号LOT-LOW应解冻");
            check(FeedLot.STATUS_FROZEN.equals(lotMapper.selectById(lotExpired).getStatus()),
                    "已过期批号LOT-EXP必须保持冻结");

            // 收紧期被拦的锁定单现在可以执行
            BigDecimal lowBefore = lotMapper.selectById(lotLow).getStockKg();
            JsonNode exec = json(call(HttpMethod.POST,
                    "/api/aqua/feeding/orders/" + orderLockedBeforeTighten + "/execute", execBody("ex-blocked")));
            assertSuccess(exec, "放开后执行预留锁定单");
            check(FeedOrder.STATUS_EXECUTED.equals(exec.path("data").path("status").asText()),
                    "预留单应执行成功");
            check(new BigDecimal("0.50").compareTo(
                    lowBefore.subtract(lotMapper.selectById(lotLow).getStockKg())) == 0,
                    "预留单应实扣0.50kg");

            // 推进能力恢复：可以登记新批号
            Long newLot = registerLot("FD-LOT-NEW", "石斑配合饲料", "8.00", TODAY.plusDays(20));
            check(FeedLot.STATUS_IN_STOCK.equals(lotMapper.selectById(newLot).getStatus()),
                    "放开后新批号应可正常登记在库");
        });

        checkpoint("CP13", "全链路变更证据：创建/锁定/执行/补货/收紧/放开/拦截均只追加可追溯", () -> {
            Long lockEvidence = evidenceMapper.selectCount(new QueryWrapper<FeedChangeEvidence>()
                    .eq("entity_type", FeedChangeEvidence.ENTITY_FEED_ORDER)
                    .eq("action", FeedChangeEvidence.ACTION_LOCK));
            check(lockEvidence != null && lockEvidence >= 3L, "锁定证据应至少3条，实际=" + lockEvidence);

            Long execEvidence = evidenceMapper.selectCount(new QueryWrapper<FeedChangeEvidence>()
                    .eq("entity_type", FeedChangeEvidence.ENTITY_FEED_ORDER)
                    .eq("action", FeedChangeEvidence.ACTION_EXECUTE));
            check(execEvidence != null && execEvidence == 3L, "执行证据应为3条（正式/试喂/预留），实际=" + execEvidence);

            Long tightens = evidenceMapper.selectCount(new QueryWrapper<FeedChangeEvidence>()
                    .eq("action", FeedChangeEvidence.ACTION_TIGHTEN));
            Long reopens = evidenceMapper.selectCount(new QueryWrapper<FeedChangeEvidence>()
                    .eq("action", FeedChangeEvidence.ACTION_REOPEN));
            check(tightens != null && tightens == 1L, "收紧证据应为1条，实际=" + tightens);
            check(reopens != null && reopens == 1L, "放开证据应为1条，实际=" + reopens);

            // 收紧证据必须说明“批号停止推进、版本事实保留”
            List<FeedChangeEvidence> tightenRows = evidenceMapper.selectList(
                    new QueryWrapper<FeedChangeEvidence>().eq("action", FeedChangeEvidence.ACTION_TIGHTEN));
            String detail = tightenRows.get(0).getDetail();
            check(detail.contains("冻结") && detail.contains("配方版本"),
                    "收紧证据应记载批号冻结与配方版本保留：" + detail);

            // 历史投喂单证据链可经 requestId 追溯，且其 V1 快照未被任何后续动作改写
            List<FeedChangeEvidence> ex1 = evidenceMapper.selectList(
                    new QueryWrapper<FeedChangeEvidence>().eq("request_id", "ex-1"));
            boolean ex1Executed = ex1.stream().anyMatch(
                    e -> FeedChangeEvidence.ACTION_EXECUTE.equals(e.getAction())
                            && FeedChangeEvidence.ENTITY_FEED_ORDER.equals(e.getEntityType()));
            check(ex1Executed, "执行证据应能按requestId=ex-1追溯到投喂单EXECUTE记录，实际=" + ex1.size());
            check(orderMapper.selectById(orderFormal).getFormulaSnapshot().contains("FD-FORMULA-V1"),
                    "历史投喂V1快照在全部操作后仍未被改写");
        });

        report.add("验收结论：全部检查点通过——按苗种/日龄/水温匹配配方、未锁定不执行、过期与库存不足拦截、"
                + "减量登记原因审批人、执行扣减幂等、试喂与正式分桶日汇总、新版本只重绑未锁定草稿且历史引用旧快照、"
                + "权限收紧批号停推进而配方版本事实保留、放开后效期复检恢复、全链路留证");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Long registerLot(String lotNo, String feedName, String stockKg, LocalDate expiry) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/feeding/lots",
                lotBody(lotNo, feedName, stockKg, expiry)));
        assertSuccess(resp, "登记批号" + lotNo);
        return resp.path("data").path("id").asLong();
    }

    private Long createOrder(String feedType, String waterTemp, String requestId) throws Exception {
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/feeding/orders",
                orderBody(feedType, waterTemp, requestId)));
        assertSuccess(resp, "创建投喂单");
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> lotBody(String lotNo, String feedName, String stockKg, LocalDate expiry) {
        Map<String, Object> m = new HashMap<>();
        m.put("lotNo", lotNo);
        m.put("feedName", feedName);
        m.put("manufacturer", "验收饲料厂");
        m.put("productionDate", TODAY.minusDays(5).toString());
        m.put("expiryDate", expiry.toString());
        m.put("stockKg", stockKg);
        return m;
    }

    private Map<String, Object> item(String species, int from, int to, String tFrom, String tTo,
                                     String feedName, String dose, int meals) {
        Map<String, Object> m = new HashMap<>();
        m.put("species", species);
        m.put("dayAgeFrom", from);
        m.put("dayAgeTo", to);
        m.put("waterTempFromC", tFrom);
        m.put("waterTempToC", tTo);
        m.put("feedName", feedName);
        m.put("doseKg", dose);
        m.put("mealCount", meals);
        return m;
    }

    private Map<String, Object> formulaBody(String versionNo, Map<String, Object>... items) {
        Map<String, Object> m = new HashMap<>();
        m.put("versionNo", versionNo);
        m.put("remark", "验收配方" + versionNo);
        m.put("items", list(items));
        return m;
    }

    private Map<String, Object> orderBody(String feedType, String waterTemp, String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("pondId", pondId);
        m.put("batchId", batchId);
        m.put("feedDate", TODAY.toString());
        m.put("feedType", feedType);
        m.put("waterTempC", waterTemp);
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> lockBody(Long lotId, String actualQty, String reason, String approver) {
        Map<String, Object> m = new HashMap<>();
        m.put("lotId", lotId);
        if (actualQty != null) {
            m.put("actualQtyKg", actualQty);
        }
        if (reason != null) {
            m.put("reductionReason", reason);
        }
        if (approver != null) {
            m.put("reductionApprover", approver);
        }
        m.put("requestId", "lock-" + lotId + "-" + (actualQty == null ? "full" : actualQty) + "-" + reason);
        return m;
    }

    private Map<String, Object> execBody(String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> governanceBody(String mode, String reason, String operator) {
        Map<String, Object> m = new HashMap<>();
        m.put("accessMode", mode);
        m.put("reason", reason);
        m.put("operator", operator);
        return m;
    }

    private Map<String, Object> mapOf(String k, Object v, String k2, Object v2) {
        Map<String, Object> m = new HashMap<>();
        m.put(k, v);
        m.put(k2, v2);
        return m;
    }

    @SafeVarargs
    private final <T> List<T> list(T... values) {
        List<T> l = new ArrayList<>();
        for (T v : values) {
            l.add(v);
        }
        return l;
    }

    private void checkpoint(String id, String name, CheckpointBody body) {
        try {
            body.run();
            String line = "通过 " + id + " " + name;
            report.add(line);
            System.out.println("[验收] " + line);
        } catch (Throwable t) {
            String stop = "停点 " + id + "「" + name + "」失败，原因：" + t.getMessage();
            report.add(stop);
            report.add("验收结论：未通过，后续检查点未执行");
            printReport();
            fail("验收" + stop);
        }
    }

    private void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 饲料批号与投喂配方验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("===============================================\n");
        System.out.println(sb);
    }

    private ResponseEntity<String> call(HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertSuccess(JsonNode node, String what) {
        if (!node.path("success").asBoolean(false)) {
            throw new IllegalStateException(what + "接口返回失败：" + node.path("message").asText());
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface CheckpointBody {
        void run() throws Exception;
    }
}
