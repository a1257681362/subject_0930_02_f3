# EVOPS 基础工作区

这是 springboot的骨架项目。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2 本地文件数据库、Shiro、Thymeleaf。

启动前准备：

1. 执行 `mvn -q -DskipTests compile` 验证骨架构建。
2. 执行 `mvn spring-boot:run` 启动应用；Spring Boot 会自动执行 `src/main/resources/schema.sql`。
3. H2 数据文件默认写入工作区 `data/evops`。如需修改路径，编辑 `application.yml` 中的 `jdbc:h2:file:` 连接串。

根目录的 `..\..\docs\schema\evops.sql` 是交付副本，应与工作区 `src/main/resources/schema.sql` 保持一致。

## 海水养殖台账（aqua-1）

模块包路径 `com.evops.aqua`，已打通：建池 → 投苗 → 日常记录 → 饲料/用药 → 抽检 → 换池 → 出池。成本核算留待后续版本。

- 养殖池档案：水深、盐度、所属海区（`POST /api/aqua/ponds`）
- 投苗批次：苗种来源、入池数量、计划出池日；批次编号全局唯一（`POST /api/aqua/batches`）
- 饲料、用药、日常、抽检：按池按批留痕（`/api/aqua/records/**`），批次不在该池或已出池会被拒绝
- 换池：`POST /api/aqua/batches/{id}/transfer` 自动生成转移记录并更新批次所在池
- 出池：`POST /api/aqua/batches/{id}/harvest` 生成出池记录，批次封存
- 原始投苗单不可删除：无 DELETE 接口，表上无删除标记
- 批次全链路追溯：`GET /api/aqua/batches/{id}/trace`

接口需先登录：`POST /api/auth/login`（bootstrap / bootstrap）。

验收：`mvn test -Dtest=AquaLedgerAcceptanceTest`，从养殖池档案入口追到投苗批次落库，逐检查点输出报告，失败即停并给出停点。

## 水质采样计划与缺测补录（aqua-2）

模块包路径 `com.evops.aqua.wq`，在台账之上打通：分阶段规则版本 → 采样计划 → 按池生成任务 → 草稿/提交 → 缺测补录 → 主管审核 → 池塘健康曲线，全程留证。

- 指标规则版本化（`POST /api/aqua/wq/rule-versions`）：不同养殖阶段配置采样频率（间隔天数）与指标正常范围；新版本发布后旧版本自动停用，**新版本只作用于未开始任务**——在执行计划的版本指针切换，从未录入过的草稿任务按新版本重建指标快照；已暂存/已提交/已审核任务冻结旧版本不变。
- 采样计划（`/api/aqua/wq/plans`）：绑定规则版本与养殖阶段、覆盖日期区间与池塘；`POST /{id}/tasks/generate` **按池按频率生成任务**，同计划同池同日幂等，重复请求不产生第二条结果。
- 采样执行（`/api/aqua/wq/tasks/{id}`）：`PUT /draft` 可只录部分指标暂存草稿；`POST /submit` 提交时每个指标必须有值，**缺测项必须说明原因**，否则拒绝；重复提交返回同一任务。
- 缺测/历史补录（`POST /api/aqua/wq/backfills`，requestId 强制幂等）：**只能追加一行，绝不覆盖原采样值**；重复补录请求返回首次记录。
- 主管审核（`POST /api/aqua/wq/tasks/{id}/review`）：驳回必须填意见且退回草稿；**审核通过后已测值才进入池塘健康曲线**（`GET /api/aqua/wq/ponds/{pondId}/health-curve`，缺测项不入曲线，取原值不取补录值）；重复审核不重复写曲线。
- 所有修改保留证据：生成/暂存/提交/补录/驳回/通过/版本切换均写 `t_wq_change_evidence`（含操作前后快照与幂等请求号），可经 `GET /api/aqua/wq/evidence` 按对象或 requestId 追溯。

验收：`mvn test -Dtest=WaterQualitySamplingAcceptanceTest`，14 个检查点逐段输出报告，失败即停并给出停点。

## 饲料批号与投喂配方（aqua-3）

模块包路径 `com.evops.aqua.feed`，打通：饲料批号生命周期 → 按苗种/日龄/水温的配方版本 → 投喂单草稿/锁定/执行 → 试喂与正式分桶日汇总 → 海水养殖权限收紧停推进，全程留证。

- 饲料批号（`/api/aqua/feeding/lots`）：批号全局唯一、登记写首批入库流水；`POST /{id}/restock` 补货入库（requestId 强制幂等）；库存以只追加流水为事实来源，批号无删除接口、无删除标记。
- 效期与库存拦截：**登记时已过期直接冻结**；锁定/执行前复检，过期批号即时冻结拦截，**库存不足拦截**；出库后用尽自动冻结。
- 投喂配方版本（`POST /api/aqua/feeding/formula-versions`）：配方行按 **苗种 + 日龄区间（天）+ 水温区间（℃）** 闭区间匹配投喂量（kg/餐×餐次）；同苗种日龄/水温区间重叠拒绝发布；新版本发布后旧版本置 SUPERSEDED 但**永不删改、仍可查询**。
- 投喂单（`/api/aqua/feeding/orders`）：创建时按批次投苗日推算日龄、用当前生效配方匹配计划量（匹配不到拒绝开单，创建 requestId 幂等）；**必须先 `POST /{id}/lock` 锁定才允许 `POST /{id}/execute` 执行**；锁定时冻结配方版本 JSON 快照、批号快照，并校验批号饲料品种与配方一致。
- 临时减量：实际量小于计划量即减量，**必须登记减量原因与审批人**，否则锁定被拒绝；不允许超量。
- 执行扣库存：执行时写 OUT 流水并扣减批号库存；**执行 requestId 幂等，重复执行/换号重复执行都不会二次扣库存**。
- 新版本只作用于未锁定草稿：发布 V2 时同事务重绑 DRAFT 单并重算计划量；**已锁定/已执行投喂单继续引用旧版本快照，历史投喂事实不随后续改版变化**（V2 无匹配的草稿保留原版本并留痕待人工处理）。
- 日汇总（`GET /api/aqua/feeding/orders/daily-summary`）：按池按日仅统计已执行单，**formal（正式投喂）与 trial（试喂）严格分桶**，各含单数/实际总量/减量次数，不提供跨类型合并总量；草稿不进汇总。
- 海水养殖权限开关（`/api/aqua/feeding/governance`）：`POST /tighten` 收紧后**饲料批号停止推进**——在库批号整批冻结，登记/补货/开单/锁定/执行全部拦截（拦截本身独立事务留 BLOCK 证据）；**配方版本保留已确认事实**，只读可查、不删不改，新配方发布一并拦截。`POST /reopen` 放开后效期复检：未过期批号解冻恢复推进，过期批号继续冻结。
- 变更证据：所有推进动作写 `t_feed_change_evidence`（含操作前后快照与 requestId），经 `GET /api/aqua/feeding/evidence` 按对象/动作/requestId 追溯；配方匹配尝试另留 `t_feed_formula_match`（命中与否均留痕）。

验收：`mvn test -Dtest=FeedLotAndFormulaAcceptanceTest`，14 个检查点逐段输出报告，失败即停并给出停点。



