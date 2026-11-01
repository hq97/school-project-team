# 中学校务数字化平台 · 架构决策记录 ADR

> 阶段：Phase 2 架构 | 产出人：架构与实现专家（高筑远）| 门禁：门禁 2（架构门禁）
> 版本：v1.0 后勤域基线（2026-10-28）→ v2.0 六域 + 数据大屏 + 消息中心修订（2026-10-30）→ **v3.0 后端技术栈切换 Java/Spring Boot 修订（2026-10-31）**
> 事实源：docs/01～docs/12、docs/14、docs/15。**docs/13 全量总览 HTML 仅作流程示意，不作依据**（正在同步刷新，可能滞后）。

---

## v3.0 技术栈切换修订说明（2026-10-31，替代 v2.0 后端部分）

> **结论先行**：后端由 NestJS/TypeScript 切换为 **Java 21 + Spring Boot 3.x + MyBatis-Plus + Flyway + PostgreSQL 16 + Redis 7**。本次仅重写「技术选型 / ORM / 迁移 / 异步任务 / 排课引擎 / 审批流 / AI 落点 / 部署 server 容器」八类结论；**v2.0 其余全部横切机制与安全语义保持不变**（详见下「不变项清单」）。
>
> **本轮产出范围**：仅评估与文档，**不写 Java 代码**（代码在门禁 2 复审通过 + 用户确认后进入门禁 4）。
>
> **v3.0 替代的 v2.0 章节**：§1.1 技术选型（后端框架/ORM+迁移）、§1.2 的 ADR-02（排课）/ADR-03（异步）、§1.3 部署形态（server 容器）、§1.4 迁移机制（Prisma→Flyway）、§1.5 权限落点的实现载体（语义不变，仅换落点）、§五 实现代码（项目结构）。
>
> **明确不变项（本次范围外，一字不动）**：前端 vue-pure-admin + Vue3 + TS；大屏 ECharts 5；三级权限语义 SELF/GRADE/ALL + 三种归属类型（组织型/关系型/角色授权型）；错误码体系 40301/40302/40303/40304 + 40901/40902/40903/40904；枚举英文码约定；C9 并发三件套的 **SQL 语义**；越权红线（occupiedBy 分级脱敏 + hasOccupancyDetail 布尔）、space 单向校验、asset 域隔离、消息 transactional outbox、C10 超期调度等横切机制语义；docs/16 UI 设计约束与 docs/17 DataScope 声明清单的**语义**。

---

## v3.0 一、技术选型裁决（Java 版，逐项：结论 + 理由 + 置信度 + 验证方式）

| # | 决策点 | 结论 | 置信度 | 理由 | 验证方式 |
| --- | --- | --- | --- | --- | --- |
| 1 | Java 版本 + Spring Boot 版本 | **Java 21 LTS（推荐）＋ Spring Boot 3.x（≥3.2，推荐 3.3+）**；Java 17 LTS 为降级备选 | 高 | Java 21 为当前 LTS（支持至约 2031）；Spring Boot 3.x 需 Java 17+、官方支持 21。**虚拟线程（JEP 444）是本次切换的关键红利**：排课长任务与 AI 网关（I/O 密集 HTTP 调用）无需手写线程池/响应式，`@Async` + 虚拟线程即可支撑并发；records/sealed/pattern-matching 提升模型代码质量。17 仅作部署环境 JDK 被锁定时的降级 | 部署机 `java -version` 探测；`mvn -version` 编译通过；一个虚拟线程 + `@Async` 冒烟用例 |
| 2 | ORM | **MyBatis-Plus 3.5.x**（Mapper 层 SQL-first）；**Spring Data JPA(Hibernate) 为备选** | **中** | 见下方「C9 SQL 语义能否被 ORM 表达」裁决：4 项硬语义里 partial unique index 属 DDL、CAS 属原生 DML——**两者都落 Flyway / 原生 SQL，任何 ORM 都"表达不了"也无需表达**；乐观锁（@Version + OptimisticLockerInnerInterceptor）与 JSONB（JacksonTypeHandler）MyBatis-Plus 均内建/可行。选 MyBatis-Plus 的核心理由：① 项目 schema 已是手写 SQL，SQL-first 迁移成本最低；② C9 三件套 + 大屏聚合 + 范围过滤本质是 SQL，MyBatis 原生 SQL 最自然；③ 国内校务生态主流、单人排障成本低。**备选 JPA 的强项是 @Version 一等公民 + 实体校验，但 JSONB/CAS 仍需落原生 SQL，且"封堵原生 query"约束更松** | 最小 CRUD + CAS 扣减 + 范围过滤 demo，验证 4 项 SQL 语义可正确表达（门禁 4 前置冒烟） |
| 3 | 数据库迁移 | **Flyway**（替代 Prisma Migrate）；Liquibase 不采纳 | 高 | Flyway 文件名 `V{n}__desc.sql` 版本递增 + `flyway_schema_history` checksum 幂等可重入，与「时间戳递增、禁止手工改库、expand-contract」逐条对应；纯 SQL 迁移与现有 3 个 Prisma migration.sql **近乎零改写**（改名 V1/V2/V3 即可复用）；Spring Boot 内建 starter。Liquibase 回滚/precondition 更强但 XML 心智重、单人收益低 | `migrate` + `validate` + checksum 篡改拒绝 + `repair` 演练；备份 + down.sql + expand-contract 流程演练 |
| 4 | 权限三层 fail-closed 落点 | **Spring Security + 自定义注解 + 启动期反射扫描 + ArchUnit** | 高 | 见「v3.0 二、权限三层落点映射」逐层表。语义与 v2.0 §1.5 完全一致，仅实现载体由 NestJS Guard/Decorator 换成 Spring 等价物 | 门禁 6 复测 docs/04 §六 10 条越权场景 + AC-RBAC-1~4 + 缺声明拒绝启动 |
| 5 | 排课引擎 | **Timefold Solver Community（原 OptaPlanner，Apache-2.0）**；自建 Java 启发式为降级备选 | **中** | 见下方「排课引擎专项评估」。Java 生态最大红利点：**零新增容器**（进程内求解器，对比 OR-Tools Python sidecar +1 容器）、硬/软约束带权重天然建模（H1–H7/S1–S7）、分数解释可产出无解报告（AC-F5-4）、60 班×50 节≈3000 放置对 Timefold 秒级 | 门禁 6 以 60 班满规模压测 P95≤30s + 软约束违规数对比 + 无解报告断言 |
| 6 | 审批流 | **保留自建 workflow 引擎（Java 重写）**；Flowable 列 V2.0 扩展位、Activiti 不采纳 | 高 | 首版明确「固定线性 + 会签/或签 V2.0」（docs/07 §五 node_type 预留不启用），Flowable 90% BPMN 能力用不上；Flowable 自带 MyBatis 与 MyBatis-Plus 混用需 SqlSessionFactory 隔离（额外复杂度 + 事务一致性心智）；快照流转（AC-D3-3）+ 双校验（节点角色 + 范围覆盖）是校务特化逻辑，自建最贴合；单人项目 Flowable 学习/排障成本远高于价值 | 门禁 6 AC-D3-1~8 + 双校验用例 |
| 7 | 异步/定时任务 | **Spring `@Scheduled` + `@EnableAsync`(虚拟线程) + ShedLock(JDBC 锁表) + 自建 DB 任务表**；**移除 BullMQ/Redis 队列** | 高 | 原 BullMQ 是 v2.0 的「中」置信度项（高风险选型②），换 Java 后其备选路线「进程内调度 + DB 锁」成为自然主选。outbox 重试由 DB `event_outbox.retryCount` + 定时扫描兜底（ADR-04 语义不变）；排课互斥/进度由任务表（RUNNING 唯一 + progress）+ ShedLock 承担；**Redis 退出"任务正确性关键路径"，运维可随手重启 Redis 不伤任务** | 门禁 6 杀 worker 进程重启验证 outbox 不丢不重；排课任务同一时间仅一个在跑 |
| 8 | 缓存 | **保留 Redis 7**（会话吊销 / 限流 / 幂等键 / 大屏 60s 缓存） | 高 | 会话（JWT 刷新令牌吊销）、限流原子计数、幂等键加速、大屏聚合 TTL 均需 Redis；但 Redis 故障降级为「重新登录 + 直查 DB」，不影响主流程。库存/名额 `availableQty` 仍实时读、不进缓存（v2.0 §4.1 红线不变） | 门禁 6 大屏缓存命中 + 故障降级演练 |
| 9 | AI 预留三件套 | **Java `AiGateway` 门面接口 + `AiProvider` 适配器 + 配置化开关降级 + 出站/入站脱敏层** | 高 | 见「v3.0 三、AI 接入预留 Java 落点」。语义与 v2.0 §二 完全一致，仅语言落点 | 后续 AI 场景落地时按场景开关验收（本轮不实现） |
| 10 | 部署形态 | **四容器基线不变**：nginx + **openjdk:21-jre（Spring Boot fat-jar）** + postgres:16 + redis:7 | 高 | 仅替换 server 容器镜像（nestjs→JVM），网络边界不变（nginx 仅 80/443，server/db/redis 内网不暴露）；Timefold 进程内、自建 workflow 无新依赖 → **不加容器**；正式交付仍为「源码导出 + 自有服务器/云平台，Docker Compose 单机」 | 门禁 6 部署回滚演练 |

### v3.0 1.1 C9 SQL 语义能否被所选 ORM 表达（裁决）

| SQL 语义（跨语言通用，语义不变） | 归属层 | MyBatis-Plus 表达方式 | 结论 |
| --- | --- | --- | --- |
| ① CAS 原子扣减 `UPDATE ... SET n=n-:x WHERE id=:id AND n>=:x`，影响 0 行判失败 | DML | Mapper `@Update` 原生 SQL + 返回 int 判 0 | ✅ 原生 SQL，最自然（任何 ORM 都需落原生） |
| ② 部分唯一索引 `CREATE UNIQUE INDEX ... WHERE status IN ('PENDING','EFFECTIVE')` | DDL | **Flyway 迁移 SQL**（任何 ORM 都表达不了 partial index，JPA @Table 无 WHERE 语法，MyBatis-Plus 不做 DDL） | ✅ 沿用现有 init SQL 的 `uniq_effective_slot`，并保留守护脚本语义 |
| ③ 乐观锁版本号 `UPDATE ... SET version=version+1 WHERE id=:id AND version=:expected` | DML | MyBatis-Plus `@Version` + `OptimisticLockerInnerInterceptor` 自动改写 WHERE/SET | ✅ 内建 |
| ④ JSONB（排课软约束权重 / flow_instance.templateSnap 快照） | DDL + 映射 | Flyway 定义 `JSONB` 列类型 + 实体 `@TableField(typeHandler=JacksonTypeHandler.class)` | ✅ 可行（JPA 为 `@JdbcTypeCode(SqlTypes.JSON)`，两者等价） |

> **裁决**：4 项硬语义中，① ② 本就落在「原生 SQL / Flyway DDL」层，**ORM 不构成对 C9 SQL 语义的阻碍或风险**；③ ④ 两者均内建/可行。因此 ORM 选择的核心矛盾不在「能否表达 SQL」，而在「封堵裸 SQL 的难易 + 迁移成本 + 生态心智」——据此选 MyBatis-Plus（中置信度，需用户确认，见「风险与例外留痕」A）。

### v3.0 1.2 排课引擎专项评估（Timefold vs 自建 Java 启发式）

| 维度 | Timefold Solver Community | 自建 Java 启发式（贪心+回溯+禁忌/退火） | 裁决 |
| --- | --- | --- | --- |
| 质量上限 | 高（成熟局部搜索 + 约束传播 + 分数解释） | 中（需大量调参，边界 case 多） | Timefold 优 |
| P95 压测目标 | 60 班×50 节≈3000 放置，秒级，P95≤30s 余量很大 | 可达 P95≤30s，但需逐轮调优 | Timefold 优 |
| 无解报告（AC-F5-4） | 原生支持（overconstrained + score explanation 定位无法放置项） | 需自建回溯定位逻辑 | Timefold 优 |
| 软约束带权重（S1–S7） | `@ConstraintProvider` + 权重权重原生表达 | 需自建多目标评分函数 | Timefold 优 |
| 运行时/容器 | **进程内求解器，零新增容器** | 进程内，零新增容器 | 平手 |
| 维护成本 | 引入求解器框架（@PlanningSolution/@PlanningEntity/@ConstraintProvider），约 3–5 天学习成本；社区版 Apache-2.0 免费 | 全自研，代码可控但长期维护与排障成本高 | Timefold 一次性学习成本，长期更低 |
| 额外复杂度 | 排课域需套 Timefold 领域注解 + 独立 solver 配置；输入/输出契约（docs/06 附录）**不变** | 无框架约束，但算法正确性全靠自证 | 各有取舍 |

> **裁决（推荐）**：采纳 **Timefold Solver Community**。这是 Java 生态相对原 TS 方案的最大红利点——质量上限、无解报告、软约束建模全面优于自建，且**不破坏四容器基线**（对比 OR-Tools Python sidecar 的 +1 容器）。**降级备选**：自建 Java 启发式，启用条件 = Timefold 集成受阻（如领域建模与 docs/06 契约冲突）或门禁 6 压测不达标。**此为中置信度项，需用户拍板**（见「风险与例外留痕」B）。

### v3.0 1.3 审批流专项评估（自建 vs Flowable/Activiti）

> **裁决**：**保留自建 workflow 引擎（Java 重写）**。理由：首版线性 + 快照（docs/07）语义简单；Flowable 自带 MyBatis 与 MyBatis-Plus 需 SqlSessionFactory 隔离；快照流转 + 双校验是校务特化逻辑；单人价值/成本比低。**Flowable 列为 V2.0 扩展位**（触发条件：出现会签/或签/条件分支/委派/撤回等复杂 BPMN 需求）；Activiti 不采纳（Flowable 是其活跃继任者）。**此项置信度高，但属"是否引入重框架"的架构取向，一并列入需用户知会项**（见「风险与例外留痕」C）。

---

## v3.0 二、权限三层 fail-closed 的 Spring 逐层落点映射（语义不变，仅换载体）

| 层 | v2.0 语义（保持不变） | NestJS 载体（作废） | Spring 载体（v3.0） |
| --- | --- | --- | --- |
| ① 路由层：未登录/无菜单/无按钮 | 未登录 401；菜单 40301；按钮 40302；停用账号旧 token 401；实时取角色不缓存 | `GlobalAuthGuard` + `GlobalPermissionGuard`（APP_GUARD 全局） | **Spring Security `SecurityFilterChain`（默认 `.anyRequest().authenticated()` 拒绝）+ JWT `OncePerRequestFilter`（解析 + 实时查库账号状态）+ 自定义 `@RequireMenu("M01")`/`@RequireButton("BTN-*")` 注解 + 全局 `HandlerInterceptor` 校验**。`AuthenticationEntryPoint`/`AccessDeniedHandler` 统一返回 401/40301/40302 |
| ② 接口层：数据接口缺声明即拒绝启动 + 运行时范围校验 | `@DataScope()` 方法级声明 + 启动期扫描缺声明 `process.exit(1)` + `DataScopeGuard` 校验最低范围 40303 + 挂载 ScopeQueryBuilder | `@DataScope` 装饰器 + `DataScopeStartupScanner` + `DataScopeGuard` | **自定义 `@DataScope(resource,ownership,requiredScope,ownerField,deptField,classField,termField,forceSelf,write)` 方法级注解 + `@PublicDataScope(reason)` 豁免 + 启动期 `ApplicationRunner` 用 `RequestMappingHandlerMapping.getHandlerMethods()` 枚举全部 HandlerMethod，缺声明即 `throw new IllegalStateException`（启动失败）**；运行时由 `DataScopeHandlerInterceptor` 校验最低范围 → 40303。**只认方法级注解（类级不放行）** |
| ③ 数据层：唯一出口 + 封堵裸 SQL | 列表/详情/导出/大屏聚合/下钻一律经 ScopeQueryBuilder；业务禁止裸查询 | `ScopeQueryBuilder` + `eslint-rules/no-raw-sql.js`（AST 方法级启发式） | **Java `ScopeQueryBuilder`（`buildWhere(LambdaQueryWrapper)` / `matchRecord(entity)` / `forceOwner(dto)` / `effectiveScope()`）+ ArchUnit 架构测试**。ArchUnit 规则：① `domains/**` 与业务包不得调用 `JdbcTemplate/NamedParameterJdbcTemplate/SqlSession.selectList/EntityManager.createNativeQuery`；② 业务 Mapper 接口不得声明 MyBatis `@Select/@Update/@Insert/@Delete` 原生注解；③ 业务 Service 内 `baseMapper.selectList` 等直查须同方法体内有 `scopeQuery` 调用（比 ESLint 文本匹配更精确的字节码级判定）。**ArchUnit 是编译/测试期强制，远强于原 ESLint 启发式** |

> **为什么仍能杜绝绕过（三层 fail-closed 不变）**：① SecurityFilterChain 默认拒绝，任何请求不经过 JWT filter + 权限拦截器到不了 Controller；② `RequestMappingHandlerMapping` 是 Spring 官方映射注册表，启动期扫描缺 `@DataScope` 即抛异常起不来（根治「漏配=裸奔」）；③ 数据访问收口 `ScopeQueryBuilder` + ArchUnit 把「业务裸查」拦在编译/测试期。**前端 v-auth 隐藏按钮仍不构成安全边界**。

---

## v3.0 三、AI 接入预留 Java 落点（门禁 2 硬性要求）

1. **统一抽象接口**：`AiGateway`（门面 `interface`，业务只依赖它）+ `AiProvider`（`interface`，`OpenAiProvider`/`QwenProvider`/`HunyuanProvider`/`LocalProvider` 适配器）+ `AiProviderFactory` 按 `ai.provider` 配置路由。Spring `@Service` + `@ConditionalOnProperty`/`@Profile` 注入。
2. **配置化开关与降级**：`ai.enabled` 全局 + 每场景开关（`ai.scenarios.{summary,draft,attribution,assist,tuning}.enabled`）；超时降级（`RestClient`/`WebClient` timeout + 虚拟线程 + `CompletableFuture.orTimeout`）；连续失败熔断（Resilience4j CircuitBreaker 或自建滑动窗口）；月预算熔断（计数 + 阈值配置）。**AI 仅增强输出，主流程（提交/审批/发布/扣减）不依赖 AI，失败静默降级**。
3. **脱敏层位置**：出站 `PiiFilter`（进 AI 前脱敏姓名/身份证/手机号/家庭住址，德育学生数据强制家长视角级脱敏）位于 `AiGateway` 内部、调用 `AiProvider` 之前的 `send()` 方法入口；入站结果过滤（剔除幻觉敏感信息）位于 `send()` 返回前。**供应商永远拿不到原始敏感数据**。
4. **红线不变（v2.0 §2.3）**：AI 不做排课求解、不做权限判定、不做消息推送决策。

---

## v3.0 四、门禁 2 五要素对照表

| 门禁要素 | 结论 | 对应章节 |
| --- | --- | --- |
| 技术选型 | ✅ Java 21 + Spring Boot 3.x + MyBatis-Plus + Flyway + PG16 + Redis7 + Timefold + 自建 workflow + @Scheduled/ShedLock（10 决策点逐项结论+理由+置信度+验证） | v3.0 一 |
| 部署形态 | ✅ Docker Compose 单机四容器（server 换 openjdk:21-jre），源码导出 + 自有服务器 | v3.0 一 #10 |
| 数据迁移策略 | ✅ Flyway `V{n}` 版本递增 + checksum 幂等 + backup/down.sql + expand-contract，禁止手工改库；现有 3 个 Prisma migration.sql 直接转为 V1/V2/V3 | v3.0 一 #3 |
| 决策置信度 | ✅ 逐项高/中标注 + 理由 + 验证方式；中置信度项（ORM / 排课 Timefold）均给备选与验证门禁 | v3.0 一 + v3.0 六 |
| AI 接入预留 | ✅ AiGateway 三件套（统一接口 + 适配器 + 开关降级 + 脱敏层位置）Java 落点 + 红线 | v3.0 三 |

---

## v3.0 五、NestJS 资产处置清单（44 文件）

| 类别 | 文件 | 处置 |
| --- | --- | --- |
| **纯 SQL 语义可迁移（零语义损失）** | `prisma/migrations/20261030000000_init/migration.sql`、`...00001_seed_baseline/migration.sql`、`...00002_seed_role_grants/migration.sql` | **直接转 Flyway V1__init.sql / V2__seed_baseline.sql / V3__seed_role_grants.sql**，DDL/枚举/部分唯一索引 `uniq_effective_slot`/种子数据全量复用 |
| **纯 SQL 语义可迁移** | `prisma/schema.prisma` | 表结构/枚举/字段注释/维护约定是 MyBatis-Plus 实体映射与 Flyway SQL 的**语义来源**，不直接运行 |
| **约定/脚本语义可迁移** | `scripts/assert-no-partial-index-drop.mjs` | 逻辑转 Java/CI 校验（Flyway 迁移中禁 `DROP INDEX "uniq_effective_slot"` 的守卫），守护语义不变 |
| **约定/文档可复用（零改动）** | `docs/02 §3.2/§三/§四`（错误码/权限基线/横切机制）、`docs/04`、`docs/07`、`docs/16`、`docs/17`、`docs/18` | **全部保留**，仅把「@DataScope 落点」「ScopeQueryBuilder」等实现名词替换为 Java 注解/类名（语义不变） |
| **语言/框架绑定需 Java 重写（作废）** | 全部 `src/**/*.ts`（guard/decorator/service/controller/DTO/module/main/filter/exception/scanner/prisma.service/scope 等约 32 文件） | **作废 TS 实现，语义照搬 Java 重写**；关键算法（ScopeQueryBuilder 三归属判定、OwnershipResolver 换届快照 E3 + 物化 path 找 GRADE、角色保存校验器、space 脱敏分级、no-raw-sql 判定）逻辑与注释**逐条迁移** |
| **构建/配置绑定作废** | `nest-cli.json`、`package.json`、`tsconfig.json`、`.eslintrc.js`、`eslint-rules/no-raw-sql.js`、`node_modules/`、`npm-install.log` | **作废**；ESLint no-raw-sql 的「方法级封堵」逻辑转 ArchUnit 规则（更强） |
| **环境变量复用** | `.env.example` / `.env` | 键名/语义复用（`DATABASE_URL`/`JWT_SECRET`/`REDIS_URL`/`ai.*`），改 Spring `application.yml` 承载 |
| **待补** | （无 .git） | 当前仓库**尚未 `git init`**；进入门禁 4 前先 `git init` + `.gitignore` + 首个 commit，遵守「先 Git 后写码」 |

---

## v3.0 六、风险与例外留痕（需用户再拍板项）

| # | 子选型 | 推荐 | 备选 | 为什么需用户裁决 |
| --- | --- | --- | --- | --- |
| A | **ORM** | MyBatis-Plus 3.5.x（SQL-first，最贴合现有手写 SQL + C9 语义） | Spring Data JPA(Hibernate)（@Version 一等公民、实体校验强） | 影响面最大：决定全部六域 DAO 层写法与「封堵裸 SQL」约束形态；**中置信度** |
| B | **排课引擎** | Timefold Solver Community（质量上限/无解报告/零新增容器最优） | 自建 Java 启发式（全自研可控） | 是否引入外部求解器框架 + 3–5 天学习成本；**中置信度** |
| C | **审批流** | 自建 workflow（线性+快照，贴合首版） | Flowable（V2.0 复杂 BPMN 能力） | 是否预留重框架迁移路径；置信度高但属架构取向 |
| D | **Java 版本** | Java 21 LTS（虚拟线程红利） | Java 17 LTS | 取决于学校部署机 JDK 是否被锁定 17；影响虚拟线程能否启用 |
| E | **异步任务** | @Scheduled + ShedLock + DB 任务表（Redis 退出正确性关键路径） | 保留 BullMQ/Redis 队列 | 原 v2.0「中」置信度项重新裁决；影响运维心态（Redis 能否随手重启） |

> **说明**：C/D/E 已有明确推荐且置信度高，默认按推荐执行，除非用户明确反对；**A/B 为中置信度，务必请用户逐条拍板**后门禁 2 方可复审通过。

---

## v3.0 七、ADR v3.0 决策摘要表

| 决策项 | 结论 | 置信度 | 验证方式 |
| --- | --- | --- | --- |
| Java 版本 + Spring Boot | Java 21 LTS + Spring Boot 3.x（17 降级备选） | 高 | 部署机 JDK 探测 + 编译 + 虚拟线程冒烟 |
| ORM | MyBatis-Plus 3.5.x（JPA 备选） | **中** | CRUD+CAS+范围过滤 demo（4 项 SQL 语义表达） |
| 数据库迁移 | Flyway（替代 Prisma） | 高 | migrate/validate/checksum/repair 演练 |
| 权限三层落点 | Spring Security + 注解 + 启动扫描 + ArchUnit | 高 | 门禁 6 复测 docs/04 §六 10 场景 |
| 排课引擎 | Timefold Solver Community（自建启发式降级） | **中** | 门禁 6 60 班压测 P95≤30s |
| 审批流 | 自建 workflow（Flowable V2.0 扩展位） | 高 | 门禁 6 AC-D3-1~8 |
| 异步/定时任务 | @Scheduled + ShedLock + DB 任务表（移除 BullMQ） | 高 | 门禁 6 杀进程重启 outbox 不丢 + 排课互斥 |
| 缓存 | 保留 Redis 7（会话/限流/幂等/大屏 60s） | 高 | 门禁 6 缓存命中 + 故障降级 |
| AI 预留 | AiGateway 三件套 Java 落点（不变语义） | 高 | 后续按场景开关验收 |
| 部署形态 | 四容器基线不变（server 换 openjdk:21-jre） | 高 | 门禁 6 部署回滚演练 |

> **无「低」置信度决策**。两个「中」置信度项（ORM / 排课 Timefold）已给备选 + 验证门禁，须用户逐条拍板（v3.0 六 A/B）。

---



## 修订说明（v2.0，2026-10-30）

| # | 修订点 | 落在章节 | 性质 |
| --- | --- | --- | --- |
| 1 | 范围从后勤域扩展为六域 + 数据大屏 + 消息中心，模块划分与依赖关系 | §1.6 | 新增 |
| 2 | 服务端权限校验落点（fail-closed 三层）+ 三种归属类型 + 错误码对齐 docs/04（40301~40304）与 40901/40902 | §1.5、§三 | 修订 |
| 3 | 统一场地对象 space（排课权威源、单向校验）与统一资产台账 asset（域隔离） | §1.7、§4.2、§4.3 | 新增 |
| 4 | C9 并发统一策略（CAS / 部分唯一索引 / 乐观锁三件套，覆盖 8 个并发点） | §4.1 | 新增 |
| 5 | C10 超期调度（统一 alert 对象 + 定时扫描 + DICT-OVERDUE-DAYS 阈值） | §4.4 | 新增 |
| 6 | 时间口径：学期+周次为聚合/鉴权主口径，自然月仅展示维度（服务端聚合后重排） | §4.5 | 新增 |
| 7 | 自动排课引擎选型（REQ-F5，H1–H7/S1–S7，P95≤30s） | §1.2、§4.9 | 新增 |
| 8 | 数据大屏：ECharts + 服务端聚合 + Redis 缓存 + AC-DD5-2 平台配置对象过滤 | §4.7 | 新增 |
| 9 | 消息中心：统一消息接口 + transactional outbox + 已读唯一口径 + 课表变更补偿（事件驱动+兜底扫描） | §4.6、§4.8 | 新增 |
| 10 | 角色可扩展性服务端硬约束（角色保存校验器 + 运行时最小权限兜底，docs/03 §2.1） | §3.4 | 新增 |
| 11 | AI 预留场景清单（明确排除 AI 参与排课求解与权限判定） | §二 | 修订 |
| 12 | 迁移策略补充：六域新对象全部纳入 init 基线 / 编号迁移 | §1.4 | 修订 |

> v1.0 的既有基线（技术栈、部署形态、迁移机制、AiGateway 三件套）**全部沿用并确认**，见 §1.1/§1.3/§1.4/§二。

---

## 一、ADR 核心决策

### 1.1 技术选型（基线沿用，v1.0 → v2.0 确认）—— 置信度：高

| 层 | 选型 | 理由（v2.0 重申） |
| --- | --- | --- |
| 管理端 UI 基座 | vue-pure-admin（Vue3 + TS + Vite + Element Plus + Pinia） | 自带用户/角色/菜单/部门管理、动态路由、按钮级权限（v-auth），直接覆盖 docs/03 M05 与 12 预置角色的配置界面；六域业务页在其上页面级扩展 |
| 后端框架 | NestJS 10（Node 20 LTS + TypeScript） | 端到端 TS 与前端共享类型；NestJS 模块化天然映射「六业务域 = 六 Module」复制策略；全局 Guard + 装饰器体系让三级权限校验 fail-closed（见 §1.5） |
| 数据库 | PostgreSQL 16 | 事务 + 行级锁/咨询锁支撑 C9；**部分唯一索引（partial unique index）**支撑「时段占用格」唯一性（§4.1）；JSONB 支撑流程模板快照与排课软约束权重配置；CHECK 约束保障状态机合法 |
| 缓存 | Redis 7 | 会话 / 限流 / 幂等键 / 大屏聚合结果缓存（60s）/ AI 降级缓存 / BullMQ 任务队列（已确认 2026-10-28，Redis 持久化必选） |
| ORM + 迁移 | Prisma | 类型安全 schema + 内置迁移脚本（prisma/migrations），满足「schema 变更必须走迁移脚本」 |
| 后端替代（不采纳） | Spring Boot 3 | 若学校 IT 强 Java 或后续引入 Flowable/Activiti 复杂流程引擎更稳；当前单人全栈 TS 迭代更快，六域复制成本更低 |

> 关键裁决（重申）：vue-pure-admin 的 RBAC 只解决「菜单权限 + 按钮权限」，**不解决「数据权限范围」**。数据范围（本人/本年级/全校 + 关系型/角色授权型）必须由服务端统一裁决下发（docs/05 落地对照表一致），前端仅消费后端标记，绝不信任前端传参。

### 1.2 新增技术选型（v2.0）—— 逐项置信度 + 验证方式

| 编号 | 决策项 | 选型 | 置信度 | 理由 | 验证方式 |
| --- | --- | --- | --- | --- | --- |
| ADR-01 | 大屏图表库 | **ECharts 5**（按需引入） | 高 | 大屏/驾驶舱场景国内事实标准；KPI 卡、趋势折线、分布柱状、对比条形、达标雷达四类图表全覆盖（AC-DD1-1）；vue-pure-admin 生态集成案例多；下钻事件能力好 | 门禁3 设计稿出四类图表原型即可验证 |
| ADR-02 | 自动排课引擎（REQ-F5） | **自建 TS 启发式引擎：贪心构造 + 冲突驱动回溯 + 禁忌/模拟退火局部搜索**，以 Worker Thread 运行（避免阻塞 NestJS 事件循环），经异步任务队列提交（含进度/结果/耗时统计落库） | **中** | ① 规模上限（班级≤60×周节次≤50）≈3000 放置，启发式秒级可解，P95≤30s 余量大；② 软约束 S1–S7 带权重 = 多目标评分，局部搜索天然支持；③ 回溯阶段可定位无法放置的（班级×学科×约束），直接产出「无解报告」（AC-F5-4）；④ 零新增运行时，保持单机四容器基线，复制友好。**备选：OR-Tools CP-SAT Python sidecar（最优性与约束表达更强，但 +1 容器 + IPC，单人维护成本↑）** | 门禁6 以 60 班满规模随机数据压测 P95（AC-F5-2）；软约束违规数与人工排课对比（AC-F5-1）。**已由用户确认（2026-10-28）：采纳自建路线；OR-Tools 备选保留为降级路径，启用条件 = 门禁6 压测 P95>30s 或硬约束满足率不达标时切换；四容器基线确定不加容器** |
| ADR-03 | 异步/定时任务基座（C10 扫描、消息 outbox 投递、排课任务、大屏缓存预热） | **BullMQ（Redis 队列）**：单消费者语义天然防重复执行；job 持久化可重试 | **中** | C10 周期扫描 / 消息投递 / 排课长任务统一一套机制，代码心智一致；Redis 已在栈内。**备选：进程内 @nestjs/schedule + PG 咨询锁**（Redis 用途最小化、可随时重启，但队列语义（重试/进度/并发控制）需自建） | 门禁6 验证：杀掉 worker 进程后 job 自动恢复；Redis 重启后队列不丢任务。**已由用户确认（2026-10-28）：采纳 BullMQ，Redis 持久化（AOF）为必选项；备选保留为降级路径** |
| ADR-04 | 业务事件 → 消息投递机制 | **transactional outbox**：业务事务内写 event_outbox 表，异步 worker 拉取渲染投递 | 高 | 保证「业务写入成功 ⇔ 消息必达」原子性，规避「事务提交但消息丢失/消息先于事务可见」两类经典故障；与 BullMQ 组合为标准模式 | 门禁6 注入故障（投递中途宕机）验证消息不丢、不重复 |
| ADR-05 | 权限运行时策略 | **实时取角色，不做授权快照缓存**（docs/04 §2.2 硬约束）；单请求内一次联查取「角色+数据范围+部门挂靠」，请求级缓存 | 高 | 权限变更即时生效（防「临时改挂角色再访问」越权，docs/04 场景 10）；单校规模（≤2000 用户）该联查开销可忽略 | 门禁6 复测 docs/04 §六越权场景 10 |
| ADR-06 | 会话/认证 | JWT（短期 accessToken + Redis 刷新令牌）；账号锁定/初始密码策略按 docs/03 C8 基线 | 高 | 无状态校验适合单机；Redis 可随时吊销（停用账号旧 token 拒绝，docs/04 场景 8） | 门禁6 场景 8 |

### 1.3 部署形态（基线沿用确认）—— 置信度：高

- 开发预览：WorkBuddy 托管**仅预览**，不作为正式环境。
- 正式交付：**源码导出 + 自有服务器/云平台**，Docker Compose 单机基线。
- 部署单元与网络边界：nginx（静态前端 + 反代 + TLS，仅暴露 80/443）→ nestjs server（内网）→ postgres:16 + redis:7（内网不暴露）。**四容器基线已确认（自建排课路线不新增容器）**；仅当门禁6 压测不达标触发 OR-Tools 降级切换时才 +1 容器。
- 替代方案：① 无 Docker 环境 → Node 直装 + systemd + nginx 脚本；② 高可用升级（PG 主从 + server 双实例）列为扩展位，单校规模不推荐。
- 复制策略（对应门禁 7）：单机 Compose 即「复制单元」，新校交付 = 源码导出 + `.env`（校名/域名/学期初始数据）+ init 迁移 + 预置角色/菜单/字典种子数据脚本。

### 1.4 数据迁移策略（基线沿用 + v2.0 对象清单）—— 置信度：高

- 机制不变：Prisma Migrate，`prisma/migrations/` 时间戳递增编号，`schema.prisma` 唯一真源；执行顺序按时间戳，`_prisma_migrations` 表幂等可重入；生产由 migrate job 先于 server 执行 `migrate deploy`，失败即中止部署。
- 回滚：迁移前自动 `pg_dump` + 手工 down.sql + `prisma migrate resolve`；破坏性变更强制 expand-contract（先加后删）。**禁止手工改库。**
- 全新项目无存量数据：**init 基线迁移直接包含 v2.0 全部对象**——六域业务表 + 平台对象（sys_dict / flow_template / flow_node / flow_instance / space / space_occupancy / asset / student_parent / notice / alert / message 系列表）+ 种子数据（12 预置角色、M01–M15 菜单树、按钮权限点、10 个字典编码、流程模板缺省绑定）。种子数据亦以迁移脚本落库（可重复执行的 seed migration），保证「复制到新校」开箱即用。
- 后续任何 schema 变更（含 space/asset/流程模板结构演进）一律走编号迁移，禁止绕过。

### 1.5 服务端权限校验落点（v2.0 新增）—— 置信度：高

**裁决：三层 fail-closed（全局 Guard 强制 → 接口声明强制 → 数据层唯一出口），不是单点拦截器。**

| 层 | 机制 | 防什么 |
| --- | --- | --- |
| ① 路由层 | `AuthGuard` + `PermissionGuard` 注册为**全局 Guard**：JWT 解析用户 → 实时取角色+数据范围+部门挂靠（ADR-05）→ 校验菜单权限（40301）与按钮权限（40302） | 未登录/无菜单/无按钮——**NestJS 请求管道强制环节，任何请求不经过 Guard 无法到达 Controller** |
| ② 接口层 | `@DataScope()` 装饰器显式声明每个数据读写接口的「归属类型 + 所需范围」；**启动期反射扫描：发现未声明 DataScope 的数据接口一律拒绝启动（fail-closed），杜绝「漏配=裸奔」**；运行时 `DataScopeGuard` 按声明执行 docs/04 §4.2 判定算法 A/B/C/D/E（组织型/班主任本班/关系型/资产域隔离/角色授权型） | 越权单条查询（40304 防枚举）、数据范围越权（40303）、家长视角字段过滤（docs/04 §4.2 B4） |
| ③ 数据层 | **统一 `ScopeQueryBuilder`（数据层唯一出口）**：列表/详情/导出/大屏聚合/下钻查询一律经它注入范围条件；业务模块**禁止**裸 `prisma.queryRaw` / 绕过构造器直查——以 ESLint 自定义规则 + 门禁5 Review 双重封堵 | 列表/导出/聚合越权（改 URL 参数、伪造 ID、导出任意 ID 列表，docs/04 §六场景 1/2/5） |

**为什么能杜绝绕过**：三道关卡全部 fail-closed——全局 Guard 不可跳过；缺 @DataScope 声明系统起不来；数据层裸查被 lint 拦截 + Review 门禁。**前端隐藏按钮（vue-pure-admin v-auth）只是体验优化，不构成任何安全边界**（docs/01 AC-E5-1、docs/05 关键边界 2）。

### 1.6 模块划分与依赖关系（v2.0 新增）—— 置信度：高

```
┌─ 业务域层 domains/（六域，互相零依赖——域隔离，复制单元）────────────┐
│  logistics 后勤(M02-04)   academic 教务(M06-08)   moral 德育(M09-11) │
│  party 党政办(M12)        science 科教(M13)       sport 体育(M14)   │
└──────────────┬────────────────────────────────────────────────────┘
               │ 只依赖平台层 + workflow
┌─ 横切层 crosscut/ ─────────────────────────────────────────────────┐
│  workflow 审批流实例引擎(docs/07：模板快照流转、双校验)               │
│  dashboard 数据大屏(M15：只读聚合六域，不写业务数据)                  │
└──────────────┬────────────────────────────────────────────────────┘
               │
┌─ 平台层 platform/（被所有层复用）──────────────────────────────────┐
│  auth 认证 │ rbac 用户/角色/菜单/部门+数据范围 │ dict 数据字典(M0505) │
│  flow-template 流程模板(M0507) │ message 消息中心(M0105/M0506)      │
│  space 统一场地 │ asset 统一资产台账 │ term 学期上下文(时间口径)      │
│  alert 超期提醒(C10) │ jobs 定时/异步任务 │ ai AiGateway              │
└────────────────────────────────────────────────────────────────────┘
```

依赖规则（门禁4/5 执行检查项）：
1. 业务域之间**零依赖**（严禁跨域 import；asset 跨域共享走平台层 asset 服务，域隔离校验在平台层统一实现）。
2. 业务域只依赖平台层与 workflow；六域审批一律走 workflow 引擎（模板配置审批角色，不硬编码，docs/07）。
3. dashboard **只读**聚合六域，不得反向写业务数据；大屏指标与下钻范围不得超出当前角色对目标对象的权限（AC-DD5-1/DD5-2）。
4. space/asset 为平台级共享对象，业务域不得自建「场地/器材」副本概念（docs/04 §1.6/§1.11）。
5. 六域全部通知收敛到 message 统一消息接口，**禁止域内自造通知**（docs/15 定位）。

### 1.7 统一数据对象与边界（v2.0 新增）—— 置信度：高

> **枚举码值约定（v2.1，与设计契约对齐）**：API 与库内一律使用**英文码值**，中文显示文案由 DICT-* 字典提供（字典增删改不改代码）：
> - `assetType`：`MATERIAL` 后勤物资 / `LAB_EQUIP` 科教实验器材 / `SPORT_EQUIP` 体育器材（DICT-ASSET-TYPE）
> - `asset.status`：`IN_STOCK` 在库 / `LENT` 借出 / `REPAIRING` 报修 / `SCRAPPED` 报废
> - `space.spaceType`：`CLASSROOM` / `LAB` / `MEETING_ROOM` / `SPORT_VENUE`（DICT-SPACE-TYPE，与 docs/04 §1.11 一致）
> - `space.status`：`AVAILABLE` 可用 / `DISABLED` 停用 / `REPAIRING` 维修中
> - `stat dim`：`TERM`（默认，聚合与鉴权口径）/ `MONTH`（仅展示维度）

| 对象 | 关键字段 | 归属类型 | 边界规则 |
| --- | --- | --- | --- |
| **space 场地** | spaceId / spaceName / spaceType（CLASSROOM\|LAB\|MEETING_ROOM\|SPORT_VENUE）/ capacity / deptNodeId / status（可用/停用/维修中） | 组织型（挂部门树，套三级） | 固定场所走 space；**可移动实物一律走 asset，二者不得混用**（「实验室」是 space、「实验器材」是 asset，docs/04 §3.11） |
| **space_occupancy 占用** | spaceId + 学期 + 周次 + 星期 + 节次 + source（SCHEDULE 排课\|RESERVATION 预约）+ 业务引用 + 状态 | 随来源 | 排课占用是**权威来源**；预约占用来自实验/会议室/赛事；单向校验见 §4.2 |
| **asset 资产台账** | id / name / assetType（后勤物资\|科教实验器材\|体育器材）/ quantity / availableQty / status（在库/借出/报修/报废）/ location | 全校共享（不套三级，按角色功能权限） | **严格域隔离**：assetType × 借用权限点双重校验，跨域请求 40302 拒绝（docs/04 §3.9 B5）；availableQty 原子扣减（§4.1） |
| **sys_dict 字典** | dict_code / item_code / item_name / sort / enabled（统一一张表，10 个字典编码见 docs/03 M0505 清单） | 平台级配置 | 仅 R-ADMIN 可维护（BTN-DICT-EDIT）；含 DICT-TERM（学年+学期+周次+当前学期标识）与 DICT-OVERDUE-DAYS（C10 阈值）——**被 term 服务与 C10 调度直接消费** |
| **流程模板** | flow_template / flow_node（审批角色、顺序、超时阈值）/ 业务绑定 / flow_instance（模板快照） | 平台级配置 | 仅 R-ADMIN 维护（M0507）；实例按创建时快照流转（AC-D3-3） |
| **alert 超期提醒** | 类型 + 业务对象引用 + 责任人 + 状态（C10 统一对象） | 继承业务对象 | 覆盖 docs/04 §3.7 全部类型；唯一约束 (类型, 业务对象ID) 防重复生成 |

---

## 二、AI 接入预留设计（v2.0 扩写）

### 2.1 三件套（基线沿用，确认有效）

1. **统一抽象接口**：`AiGateway` 门面 + `AiProvider` 接口（OpenAI / Qwen / Hunyuan / Local 适配器），业务只依赖接口，供应商配置化切换。
2. **配置化开关与降级**：`ai.enabled` 全局 + 每场景开关；超时降级 / 连续失败熔断 / 月预算熔断。**AI 仅做增强输出，所有业务主流程（提交/审批/发布/扣减）不依赖 AI，失败静默降级。**
3. **脱敏层位置**：出站 PIIFilter（进 AI 前脱敏姓名/身份证/手机号/家庭住址等）+ 入站结果过滤（剔除幻觉敏感信息），位于业务服务 ↔ AiGateway 之间，供应商永远拿不到原始敏感数据。**德育域学生数据（违纪/体测/请假）进 AI 前强制过家长视角级脱敏**（docs/04 §4.2 B4 同口径）。

### 2.2 接入场景清单（v2.0 建议，全部为 P1+ 增强，不进首版主流程）

| 场景 | 输入 → 输出 | 降级路径 |
| --- | --- | --- |
| 审批辅助摘要 | 申请单结构化数据 → 给审批人的一段摘要建议 | 失败 → 无摘要，审批表单原样 |
| 公文/通知草稿（R-PARTY） | 要点清单 → 公文/公告草稿 | 失败 → 退回人工撰写 |
| 异常归因建议（大屏预警辅助） | alert 聚合统计 → 归因提示（如「报修超时集中于 3 栋老旧线路」） | 失败 → 仅展示规则归因（超期清单本身完整可用） |
| 智能填报助手 | 报修描述 → 故障类型字典推荐；领用历史 → 常用品推荐 | 失败 → 手动选择，无功能损失 |
| 排课软约束调参建议 | 软约束违规分布 → 权重调整建议（**仅建议，人改配置**） | 失败 → 按缺省权重 |

### 2.3 明确排除（AI 红线）

- **AI 不参与排课求解**：排课必须确定性算法（可复现、可出无解报告、P95 可控，AC-F5-2/F5-4），AI 只做 §2.2 的调参建议。
- **AI 不参与权限判定**：三级权限是安全边界，判定算法（docs/04 §4.2）必须是确定性代码。
- AI 不做消息推送决策（触发规则是确定性配置，docs/15 REQ-MS5）。

---

## 三、三级权限设计（v2.0 修订：三种归属类型 + 校验落点 + 统一错误码）

### 3.1 范围与归属类型（对齐 docs/04）

- **三级范围**：SELF 本人 / GRADE 本年级 / ALL 全校，严格递增，**不新增第四级**（docs/03 决策 18⑤）。
- **三种归属类型**：
  1. **组织型**：部门树（SCHOOL/GRADE/CLASS/SUBJECT/OFFICE）承载 SELF/GRADE/ALL；「本班」= GRADE 级 + `head_teacher.classId` 收窄到 CLASS 节点（仅学生相关数据，B7 边界）；「本组」= GRADE 级 + SUBJECT 节点细分；**换届快照【E3】**按记录落的 `classId + 学期` 判历史归属，不随换届漂移。
  2. **关系型**：家长-子女 `student_parent(studentId, parentUserId)` 多对多；家长范围 = 全部关联子女记录并集；越权 40304 防枚举。
  3. **角色授权型**：公告 notice——可见 = 命中公告可见角色 + 有 M1201 菜单权限，二者同时满足。
- **不套三级的公共对象**（仅菜单/按钮权限控制）：库存台账可领列表、菜单、角色、部门树、字典、审批流模板、评选标准只读部分（docs/04 §1.4）。
- **实时取角色**，不做授权快照缓存（docs/04 §2.2，ADR-05）。

### 3.2 统一错误码（对齐 docs/04 §4.3 + 409 段）

| 返回码 | 业务码 | 含义 |
| --- | --- | --- |
| 401 | UNAUTHENTICATED | 未登录 / 凭证失效 / 停用账号旧 token |
| 40301 | NO_MENU_PERM | 无菜单权限 |
| 40302 | NO_ACTION_PERM | 无按钮/操作权限（含 asset 跨域借用） |
| 40303 | DATA_SCOPE_DENIED | 数据范围越权 |
| 40304 | RESOURCE_NOT_FOUND | 记录不存在（防枚举，单条越权统一返此码） |
| 40901 | SPACE_SCHEDULE_CONFLICT | 场地已被课表占用（排课权威源，docs/04 §1.12） |
| 40902 | SPACE_RESERVATION_CONFLICT | 并发预约竞争失败（C9，各域对应 AC） |
| 40903 | STOCK_SHORT | 库存不足（C9 原子扣减失败，AC-B1-3/N8-2） |
| 40904 | QUOTA_FULL | 名额已满（C9 名额扣减失败，K2-2/M7-2） |

**错误体结构（v2.1，与设计契约 `design/src/api/types.ts` 对齐）**：冲突/库存/名额类失败统一返回结构化 `data`，供前端渲染三段式文案（失败原因 → 冲突详情 → 一键恢复）：

```
{ code, message,
  conflict?: { spaceName, slotText, occupiedBy?, hasOccupancyDetail, suggestions?: [{slot:{week,dayOfWeek,period}, slotText}] },
  stock?:    { name, availableQty, requestedQty },   // 40903
  quota?:    { name, quota }                          // 40904
}
```

**字段硬保证（v2.1，供前端直接消费）**：
- `slotText`：**由服务端渲染**（周次/星期/节次口径唯一，含 `suggestions[].slotText`），前端不得本地拼接，避免口径不一致。
- `hasOccupancyDetail`：**布尔标志**——`true` 表示 `occupiedBy` 是真实占用明细（可渲染「查看该场地课表」入口）；`false` 表示仅占位文案。**前端按此布尔判断，不得用占位文案做字符串比较**（文案会随字典/国际化变化）。M0703 入口可见性另由前端 RBAC 菜单权限独立判断，两者取交集。
- `stock.availableQty`（40903）/ `quota`（40904）：为**扣减失败当次事务内的实时读值**——CAS 失败后立即 `SELECT` 取当前值返回，**不经任何缓存**，供前端「改为申请 N 件」直接回填（回填后重试即可成功）。

> **越权红线（架构裁决）**：`occupiedBy`（如「高三(1)班 数学 张老师」）与 `/api/spaces/:id/occupancies` 的预约人身份**属课表/预约数据，必须按请求人数据范围分级脱敏**——
> ① 请求人数据范围覆盖该占用（ALL，或 GRADE 且命中该班级/年级）→ 下发「班级 + 学科 + 教师」；
> ② 否则**只下发「已被课表占用」占位文案，不下发班级/教师/预约人**（防 SELF 范围用户借冲突提示反查他人课表与预约人，docs/04 §4.3 防枚举同精神）。
> 前端 `ConflictAlert` 必须兼容 `occupiedBy` 缺失的降级文案（不因缺字段崩溃）。
> `suggestions`（推荐替代时段）只能来自「请求人在其 space 开放范围内可预约且当前 FREE」的格子，且同样过数据范围过滤，不得推荐无权预约的时段。

> v1.0 的「403 FORBIDDEN_ACTION / FORBIDDEN_SCOPE」粗粒度编码**废弃**，统一收敛到 docs/04 编号体系（本条为 v2.0 修订）。

### 3.3 逐接口权限基线（代表性接口 + 机制）

> 本表为**基线样例**：平台 + 横切 + 六域关键接口。完整「路由 × 归属类型 × 数据范围 × 校验点」登记表在门禁 4 开发阶段逐接口补全，每条路由必须挂 `@DataScope()` 声明（§1.5 fail-closed）。审批类接口一律再加 workflow **双校验**：①当前用户命中当前节点审批角色；②数据范围覆盖申请单归属（AC-D3-4/D3-8），任一不满足 40302/40303。

| 模块 | 路由 | 归属/范围 | 服务端校验点 | 越权返回 |
| --- | --- | --- | --- | --- |
| **平台-rbac** | GET/POST/PUT/DEL /api/system/{users,roles,menus,depts} | ALL（仅 R-ADMIN） | PermissionGuard 校验菜单+按钮 | 40301/40302 |
| 平台-rbac | POST /api/system/roles | ALL | **角色保存校验器**：菜单/按钮/数据范围(+归属类型) 三项齐全，缺一拒存并指明（AC-RBAC-1）；预置 R-* 删除拒绝（AC-RBAC-3） | 40302 |
| 平台-dict | GET /api/system/dicts | 只读（登录即可） | M0505 只读；字典枚举不含敏感数据 | — |
| 平台-dict | POST/PUT/DEL /api/system/dicts | ALL（仅 R-ADMIN，BTN-DICT-EDIT） | 含 R-PRINCIPAL 亦不可 | 40302 |
| 平台-flow | GET/POST/PUT /api/system/flow-templates | ALL（仅 R-ADMIN，BTN-FLOW-TPL-EDIT） | M0507 平台级配置 | 40301/40302 |
| 平台-space | GET /api/spaces | 组织型（SELF/GRADE/ALL 按开放范围） | space 开放范围 × 用户组织归属（docs/04 §3.11） | 40303 |
| 平台-space | POST /api/spaces/:id/occupancies | 组织型 | §4.2 单向校验：先查课表占用→40901；再并发竞争→40902 | 40901/40902 |
| 平台-space | POST/PUT/DEL /api/spaces（基础数据维护/停启用） | ALL（R-ADMIN/R-LOGISTICS） | docs/04 §3.11 维护行 | 40302 |
| 平台-asset | GET /api/assets | 全校共享（按各域借用场景） | 菜单+按钮控制，不做记录级过滤 | — |
| 平台-asset | POST /api/assets/:id/borrow | 全校共享 + **域隔离** | assetType × 借用权限点双重校验（B5），跨域拒绝 | **40302** |
| 平台-asset | POST /api/assets/:id/return | SELF（借用人本人） | 归还人 == 借用人，回补 availableQty | 40303 |
| 平台-asset | POST /api/assets/:id/stock（入库/调整/报废） | ALL（R-LOGISTICS 或流程配置入库角色） | docs/04 §3.9 | 40302 |
| 平台-message | GET /api/messages | SELF（receiverId） | 收件人过滤，越权他人消息拒绝（AC-MS7-1） | 40304 |
| 平台-message | POST /api/messages/:id/read | SELF | 仅本人站内信已读（业务对象已读回执归业务端，AC-MS8-1） | 40304 |
| 平台-message | POST/PUT /api/system/message-templates | ALL（仅 R-ADMIN，M0506） | 模板按业务类型独立（REQ-MS4） | 40302 |
| 平台-alert | GET /api/alerts | SELF/GRADE/ALL（继承责任人） | C10 提醒随责任人数据范围（docs/04 §3.7） | 40303 |
| **横切-workflow** | POST /api/workflow/instances/:id/transition | 按节点+归属 | 双校验：节点审批角色命中 + 范围覆盖申请单归属；模板快照流转 | 40302/40303 |
| 横切-dashboard | **GET /api/dashboard/overview?dim=TERM\|MONTH** | 按角色模板（继承角色数据权限） | **服务端聚合** + 数据范围过滤（AC-DD5-1）；平台配置对象（字典/流程模板/菜单/角色/部门）直接过滤不下发（AC-DD5-2）；先学期+周次聚合与鉴权、再按 MONTH 重排（§4.5）；返回 `roleCode/templateKey/kpis[]/charts[]/alerts[]/scope/scopeText/dataAsOf/currentTerm` | 40303 |
| 横切-dashboard | **GET /api/dashboard/drill?kpiCode=&dim=** | 同上 | 下钻明细仍过 ScopeQueryBuilder，越权 40303/40304（AC-DD6-1）；`drillTo` 由服务端下发但**不构成授权**，前端不得据此跳过校验 | 40303/40304 |
| 横切-dashboard | **GET /api/dashboard/term/current** | 全校（登录即可） | term 服务下发当前学期与当前周次；前端不得本地推算 CURRENT_WEEK | — |
| 平台-dict | **GET /api/dicts/items?dictCode=** | 登录即可（只读） | 字典项只读消费；**不得作为大屏指标或下钻来源**（AC-DD5-2） | — |
| 平台-asset | **GET /api/assets?assetType=&keyword=** | 全校共享（按各域借用场景） | 可借列表按 `assetType × 借用权限点` 过滤（域隔离 B5）；availableQty 为服务端实时值，前端不得本地扣减 | — |
| 平台-space | **GET /api/spaces?spaceType=** | 组织型（按开放范围） | space 开放范围 × 用户组织归属（docs/04 §3.11） | 40303 |
| 平台-space | **GET /api/spaces/:id/free-slots?week=N** | 组织型（按开放范围） | 服务端**已排除 SCHEDULE 占用**并按开放范围过滤——该结果即 40901 预检口径，前端默认选中第一个空闲时段；不得返回未过滤的全量时段 | 40303 |
| 平台-space | **GET /api/spaces/:id/occupancies?week=N** | 组织型（按开放范围） | 占用矩阵响应固定为 `Record<"${dayOfWeek}-${period}", "FREE"\|"SCHEDULE"\|"RESERVED">`（week 来自 query）；**响应体不承载任何身份字段**（无占用人/班级/教师/预约人），服务端即完成脱敏，不依赖前端不渲染——SELF 范围用户只见状态不见人（见 §3.2 越权红线） | 40303 |

> **门禁4 审计响应 · space 占用/预约路由的归属语义澄清（2026-10-31）**：
> 「组织型（按开放范围）」的**生效门禁是 `space.openScope` 开放范围 × 用户组织归属**（docs/04 §3.11），而非用户自身数据范围。实现上这三条路由的 `@DataScope` 声明为 `ownership:'PUBLIC'`（不做记录级过滤），记录级门禁由 `SpaceController` 内的 openScope 校验承担——若按 ORG × SELF 判定，普通教师（SELF 范围）将无法预约任何会议室，与 BTN-PARTY-ROOM-BOOK 授予教师（docs/03 §3.2）矛盾。矩阵/预检**响应不含任何身份字段**的脱敏红线不变。越权返回统一 40304（防枚举）。
| **后勤-采购** | GET /api/procurement/dashboard/summary | ALL | 校长/校领导 + ALL（硬指标①载体之一） | 40303 |
| 后勤-采购 | GET /api/procurement/projects | SELF/GRADE/ALL | ScopeQueryBuilder 按范围截断，不信任前端参数 | 40303 |
| 后勤-采购 | POST /api/procurement/projects | SELF | 强制 creatorId=当前用户 | 40303 |
| 后勤-领用 | POST /api/material/requisitions | SELF | 强制 applicantId=当前用户；BTN-MAT-APPLY | 40302 |
| 后勤-领用 | POST /api/material/requisitions/:id/issue | ALL（后勤） | **C9**：事务 + CAS 扣减 availableQty + 幂等键（申请单号唯一）+ 状态机 CAS | 40302 |
| 后勤-报修 | POST /api/repair/tickets | SELF | 强制 reporterId=当前用户；BTN-REP-SUBMIT | 40302 |
| 后勤-报修 | PATCH /api/repair/tickets/:id/{assign,status} | ALL（后勤） | 分派/状态按钮权限 + 状态机合法流转；超时阈值触发 C10 | 40302 |
| **教务-排课** | POST /api/schedule/auto-run | ALL（R-DEAN/R-VP，BTN-SCH-AUTO） | 异步任务（ADR-02）；同一时间仅一个排课任务在跑（互斥） | 40302 |
| 教务-排课 | POST /api/schedule/versions/:id/publish | ALL（R-DEAN/R-VP，BTN-SCH-PUBLISH） | **F4-3 乐观锁**（版本号落后拒绝）+ 生效周∈学期周次（AC-F4-1）+ **F4-2 写 space 占用** + **F4-4 占用比对补偿**（§4.8） | 40302/40902* |
| 教务-课表 | GET /api/timetable/my | SELF | 本人课表（≤2 步，AC-G1-1） | — |
| 教务-课表 | GET /api/timetable/class/:classId | SELF(本班班主任)/GRADE/ALL | 班主任 = head_teacher.classId 比对（AC-G2-1）；学生本人班级 | 40304 |
| 教务-调课 | POST /api/schedule-changes | SELF | 强制发起人；BTN-SCH-CHANGE-APPLY | 40302 |
| 教务-调课 | POST /api/workflow/.../transition（调课审批） | 按节点+归属 | 双校验 + **H3-2 二次校验**：审批通过写回课表前重查目标时段冲突，冲突则拦截回退 | 40302/40303 |
| **德育-请假** | POST /api/leave/requests | SELF（学生/教师） | BTN-LEAVE-APPLY；家长确认走 BTN-LEAVE-CONFIRM（关系型） | 40302 |
| 德育-请假 | GET /api/leave/requests | SELF/GRADE(班主任→CLASS)/ALL + 关系型(家长) | 算法 A/B/C 分支；销假超期触发 C10 | 40303 |
| 德育-违纪 | GET /api/discipline/records/:id | 关系型（家长）+ B4 字段过滤 | 家长仅见「违纪事实+通知」，内部处理意见/谈话记录不下发 | 40304 |
| 德育-评优 | POST /api/awards/:id/nominations | SELF/本班 | BTN-AWARD-NOMINATE；**C9**：名额 CAS 扣减（K2-2） | 40302 |
| **党政-公告** | POST /api/notices | ALL（R-PARTY，发布时选可见角色集合） | 角色授权型发布 | 40302 |
| 党政-公告 | GET /api/notices | 角色授权型 | 命中可见角色 + M1201 菜单权限；单条越权 40304 | 40304 |
| 党政-会议 | POST /api/meeting-rooms/bookings | 组织型 | **space 单向校验（40901/40902）+ C9 部分唯一索引**（L6-2） | 40901/40902 |
| 党政-公文 | POST /api/documents/{reg,flow} | ALL（R-PARTY 按钮） | 公文滞留触发 C10 | 40302 |
| **科教-实验** | POST /api/science/lab-bookings | 组织型/场景 | **双重**：场地走 space 校验（40901/40902）+ 器材走 asset CAS 扣减（M4-2） | 40901/40902 |
| 科教-竞赛 | POST /api/science/competitions/:id/apply | SELF/场景 | BTN-SCI-COMP-APPLY；**C9** 名额 CAS（M7-2） | 40302 |
| 科教-课题 | POST /api/science/projects | SELF | BTN-SCI-PROJ-APPLY；审批走 workflow | 40302 |
| **体育-赛事** | POST /api/sport/events/:id/schedule | ALL/场景 | **space 校验 + C9 部分唯一索引**（N5-2） | 40901/40902 |
| 体育-器材 | POST /api/sport/equipment/borrow | 全校共享+域隔离 | assetType=体育器材 × BTN-SPORT-EQUIP-BORROW；**C9** CAS（N8-2） | 40302 |
| 体育-体测 | POST /api/sport/fitness/scores | 本班/本组（BTN-SPORT-FIT-INPUT） | 录入范围校验；家长仅见本人子女（关系型） | 40302/40303 |

\* 课表发布主拒绝码为乐观锁业务提示「版本落后请刷新」，非 40902；40902 仅用于预约竞争失败。

### 3.4 角色可扩展性服务端硬约束（docs/03 §2.1，两处落点）—— 置信度：高

1. **角色保存校验器（写路径）**：`RoleService.save` 服务端校验「菜单授权 + 按钮授权 + 三级数据范围（含归属类型）」三项齐全，**缺一拒存并指明缺失项**（AC-RBAC-1）；数据范围不完整的角色不得被授予「列表/详情/导出」类菜单（保存即拒，AC-RBAC-2 前半）；预置 `R-*` 编码删除拒绝、可改名调授权（AC-RBAC-3）；数据范围枚举仅 SELF/GRADE/ALL（AC-RBAC-4）。
2. **DataScopeGuard 运行时兜底（读路径）**：历史数据或绕过前端直调接口出现的「缺数据范围角色」访问对应接口时，一律**按最小权限 SELF 返回并打告警日志**（AC-RBAC-2 后半）。
> 该约束**必须落在这两处服务端代码**，不得只做前端表单校验（对齐 docs/01 AC-E5-1）。自建角色建议 `R-CUSTOM-*` 前缀，预置 12 角色为基线非上限。

---

## 四、横切机制设计（v2.0 新增）

### 4.1 C9 并发统一策略 —— 置信度：高

**裁决：正确性一律由 PostgreSQL 事务 + 约束保证（数据库是唯一权威）；Redis 分布式锁不作为正确性来源**，仅用于长任务互斥（如同一时间仅允许一个自动排课任务）。理由：Redis 锁有超时/故障脑裂风险，单机部署无跨节点需求，DB 约束是最强兜底。

**统一三件套（可复用机制）**：

| 机制 | 形态 | 适用 |
| --- | --- | --- |
| ① CAS 原子扣减 | `UPDATE t SET n = n - :x WHERE id=:id AND n >= :x`，影响 0 行即失败 | **数量型**扣减 |
| ② 部分唯一索引 | `CREATE UNIQUE INDEX ... ON space_occupancy(space_id, term, week, day, lesson) WHERE status IN ('待确认','已生效')`，插入冲突即失败 | **时段占用格**唯一性 |
| ③ 版本号乐观锁 | `UPDATE t SET ..., version=version+1 WHERE id=:id AND version=:expected` | **多用户编辑同一记录** |

**8 个并发点全覆盖**：

| 并发点 | 机制 | 失败返回 |
| --- | --- | --- |
| B1-3 库存扣减（领用） | ① CAS on asset.availableQty + 幂等键（申请单号唯一） | 库存不足 |
| K2-2 评优名额 | ① CAS on 名额计数 | 名额已满 |
| L6-2 会议室时段 | ② 部分唯一索引 + 事务内先查课表占用 | 40901 / 40902 |
| M4-2 实验预约 | ② 场地唯一索引 + ① 器材 CAS（**双重**，docs/04 §3.11 边界注） | 40901 / 40902 |
| M7-2 竞赛名额 | ① CAS | 名额已满 |
| N8-2 体育器材 | ① CAS on asset.availableQty | 库存不足 |
| N5-2 赛事排期 | ② 部分唯一索引（space+时段） | 40901 / 40902 |
| F4-3 课表版本 | ③ 版本号乐观锁 | 版本落后请刷新 |

统一实现落点：平台层 `platform/concurrency`（CAS helper / 时段占用写入 / 乐观锁包装），六域复用，禁止各自手写。门禁 6 以并发压测逐点验证「不超卖/不超订/不回退」。**库存/名额的 `availableQty` 一律实时读，不进 Redis 缓存**（Redis 缓存仅用于大屏聚合，§4.7）；扣减失败返回的实时值语义见 §3.2。

### 4.2 space 场地占用（单向校验，排课权威源）—— 置信度：高

- **写入权威**：课表发布成功 / 调课·代课审批通过生效时，服务端在同一事务后置任务中把「场地+学期+周次+节次」写入 space_occupancy（source=SCHEDULE，AC-F4-2）。
- **预约提交流程**（实验预约 / 会议室 / 赛事排期统一走平台 `space` 服务）：
  1. 事务内先读该「场地+时段」的 **SCHEDULE 占用**（含正式发布版本与已生效调课/代课）→ 命中即拒绝 **40901**；
  2. 未命中则插入 RESERVATION 占用 → 部分唯一索引冲突即 **40902**（并发竞争失败）；
  3. 成功后按各域状态机走「待确认→已生效/取消」，取消/驳回即软删占用（status 置失效，索引不再约束）。
- **占用判定单一真源（SlotOccupancy 判定器）**：`GET /api/spaces/:id/free-slots`、`GET /api/spaces/:id/occupancies`、预约提交时的 **40901 预检**——**三者共用平台层同一个判定器**（同一套查询：SCHEDULE 占用 ∪ RESERVATION 有效占用，排除已取消/已驳回/已失效），避免出现「矩阵显示空闲但提交返 40901」的口径打架。占用口径任何变更**只改判定器一处**；门禁6 需专门验「free-slots 返回空闲 ⇒ 提交必成功（无并发前提下）」。
- **反向不做（V2.0）**：预约不阻断排课；排课覆盖已生效预约时走 §4.8 补偿通知。**双向互斥/预约优先列 V2.0**（docs/04 §1.12）。
- 与 asset 的分界校验：涉及「场地+器材」的复合预约（实验）分别校验（space 走 §4.2、器材走 §4.1），不得混用一套逻辑。

### 4.3 asset 资产台账（域隔离）—— 置信度：高

- 统一 asset 对象（docs/04 §1.6 字段），三域业务场景独立、底层台账统一。
- **域隔离【B5】**：服务端按 `assetType × 借用权限点` 双重校验——体育器材仅体育借用流程可借、实验器材仅实验预约可借、物资仅后勤领用可借；跨域请求直接拒绝 **40302**。跨域借用列未来版本（届时再定义授权载体，首版不引入）。
- 借用/领用扣减走 §4.1 ①；归还仅借用人本人（SELF），回补 availableQty；入库/调整/报废仅 R-LOGISTICS 或流程配置角色。
- 超期未归还自动生成 alert（C10，§4.4）。

### 4.4 C10 超期调度 —— 置信度：高

- **统一数据对象 alert**（docs/04 §3.7）：类型覆盖——请假到期未销假、资产超期未归还、审批滞留、公文滞留、超期未到货、库存不足、家长未确认超期（严重处分催办）、报修提交后超时未派单、报修派单后超时未处理。
- **定时扫描任务**：BullMQ repeatable job（默认每 10 分钟，可配），逐类型扫描业务表超期记录；阈值统一取自 **DICT-OVERDUE-DAYS 字典**（管理员可调，无需改代码）。
- 产出：写 alert 记录（唯一约束 `(类型, 业务对象ID)` 防重复生成）→ 按责任人与数据范围推 **message** 消息中心 → 大屏异常预警列表取数同源（AC-DD4-1）。
- alert 的可见范围继承业务对象（SELF/GRADE/ALL，docs/04 §3.7 矩阵）。

### 4.5 时间口径（学期+周次为主口径）—— 置信度：高

- **平台层 term 服务**：当前学期取自 DICT-TERM 字典「当前学期」标识；提供学期→周次区间换算。
- **数据模型**：六域业务表统一携带 `termId`（学年学期）+ 周次类字段（业务有周概念的带 weekNo）；课表版本归属「学期+生效周」，生效周必须落在该学期周次范围内（AC-F4-1，服务端拒绝发布）。
- **聚合与鉴权一律先按学期+周次**：所有统计/大屏/报表接口在服务端完成「学期聚合 + 数据权限过滤」**之后**，才做自然月粒度重排（纯展示层转换）。**禁止以自然月作为聚合或鉴权主口径**（docs/04 §1.8、docs/14 L104）；「学期/自然月」维度切换不改变任何可见性与越权判定（AC-DD1-2）。
- 同比 = 去年同学期；环比 = 上一学期（docs/04 §1.8）。
- 六域不得自造时间维度（无「本季度」「自定义起止」主口径）；Code Review 门禁项。

### 4.6 消息中心（独立模块 + 统一消息接口）—— 置信度：高

- **链路**：业务事件 →（业务事务内）写 event_outbox → BullMQ worker 拉取 → 按 M0506 模板渲染（模板按业务类型**独立建**，REQ-MS4，防串味）→ 写接收人收件箱。接收人由各域业务逻辑计算（审批人/可见角色/家长/班主任），投递时服务端校验数据范围（AC-MS7-1）。
- **已读唯一口径【AC-MS8-1】**：业务对象（公告/审批单/请假单/会议通知）的已读回执**以业务端为唯一真源**——用户从 M0105 或业务入口打开对象，均由业务端写同一份回执；消息中心**只维护站内信自身已读/未读**（用于角标），不独立维护业务对象已读状态。「公告已读率」等指标统一取业务端口径。
- **交互硬约束（设计阶段执行）**：业务类消息**不做站内正文预览**，点击直接跳转业务对象详情页（防「角标清零但业务仍显示未读」的感知落差，docs/15 设计约束①）。
- 首版仅站内信（外部推送 V2.0）；不含 IM/撤回/定时发送。

### 4.7 数据大屏（M15，分角色驾驶舱）—— 置信度：高（实时聚合 + Redis 60s 缓存已确认 2026-10-28；预聚合表列 V2.0，触发条件：出现走廊挂墙 7×24 展示需求）

- **图表库**：ECharts 5（ADR-01），四类图表（KPI 卡/趋势折线/分布柱状/对比条形/达标雷达）。
- **服务端聚合**：`GET /api/dashboard/overview?dim=`（契约见 §3.3）按登录角色自动加载对应预置模板（AC-DD2-1），聚合查询**全部**过 ScopeQueryBuilder（数据权限过滤后再出数，禁止前端拉全量再算，REQ-DD5）；下钻明细同受约束（AC-DD6-1）。
- **`templateKey` 是布局指针，不是授权**：服务端按角色下发 `PRINCIPAL/VP/DEAN/LOGISTICS/MORAL/PARTY/GRADE/SUBJECT`，前端只取本地布局表（栅格宽/高/顺序，无口径无过滤）。**教师/学生/家长在 docs/03 M15 矩阵为「—」（无 M15 菜单）→ 服务端直接 40301**，`templateKey=NONE` 仅保留给「有 M15 菜单但无预置模板」的自建角色（`R-CUSTOM-*`）作优雅降级，**不作为教师/学生/家长放行大屏的依据**。新增角色 = 服务端加 templateKey + 前端模板表加一行，不动页面代码。
- **前端模板表红线（门禁4 回归点，架构级）**：`templates.ts` 一类布局表**只允许出现栅格宽 / 高 / 顺序，禁止任何 `if (role === ...)` 分支、指标过滤、口径计算或环比/同比自算**——一旦出现即构成「数据权限搬到前端」，AC-DD5-1 直接失效，且服务端调整过滤后前端不知情会造成静默分叉。指标增减一律走服务端（指标白名单，§4.7 AC-DD5-2），前端模板表只同步布局行。
- **展示层三项默认值（禁止改动）**：① 维度切换器默认 `dim=TERM`（改默认 MONTH = AC-DD1-2 失效）；② 图表配色由服务端下发 `colorKey`（六域固定色板），同一域跨图同色，前端不得本地随机取色；③ 趋势图不做曲线平滑、切换维度时整图重绘（避免旧 series 残留导致两个维度数据叠加）。
- **AC-DD5-2**：数据字典/流程模板/菜单/角色/部门等平台级配置对象**不得作为指标、不得出现在下钻结果**——服务端聚合时直接过滤（指标白名单制：指标清单只登记业务运行数据），不依赖前端不展示。
- **缓存与刷新**：聚合结果写 Redis（TTL=刷新间隔，默认 60s 可配，REQ-DD7）；每个指标带「数据截止时间」。（本方案已由用户确认 2026-10-28；预聚合表为 V2.0 扩展位，触发条件：走廊挂墙 7×24 展示需求再启用）
- 不做：拖拽自定义编辑器、WebSocket 实时流、BI 自助分析、大屏导出（docs/14 排除项）；学生/家长无大屏。

### 4.8 课表变更补偿（AC-F4-4）—— 置信度：高

**裁决：事件驱动为主 + 定时补偿扫描兜底（双保险）。**

1. **事件驱动（主）**：课表发布 / 调课·代课生效事务提交后，outbox 事件触发「占用比对 job」——diff 该（学期+生效周）新旧 space_occupancy（SCHEDULE），筛出被新增排课占用覆盖的**已生效 RESERVATION** 记录，向对应预约人推消息中心通知（走 §4.6 统一接口，**未推送视为不通过**）。
2. **定时补偿（兜底）**：每日一次全量比对「SCHEDULE 占用 ∩ 已生效 RESERVATION」重叠记录，幂等补推（防事件丢失）；重叠清单同时供大屏预警引用。
3. 首版排课为权威源，预约不反向阻断排课；双向互斥列 V2.0（docs/04 §1.12）。

### 4.9 自动排课引擎（REQ-F5 落地）—— 置信度：中（路线已由用户确认 2026-10-28，P95 待门禁6 压测；OR-Tools 为降级备选）

- **选型**：自建 TS 启发式引擎（ADR-02）：阶段 1 贪心构造（按约束紧度排序放置）→ 阶段 2 冲突驱动回溯（产出无解报告素材）→ 阶段 3 禁忌/模拟退火局部搜索（优化软约束加权评分）。
- **输入/输出契约**按 docs/06 附录：输入任课表/班级/教室（取 space）/节次 + 硬约束 H1–H7 + 软约束 S1–S7（权重存字典/配置）；输出课表方案或无解报告（无法满足的约束+冲突明细）+ 生成统计（硬约束满足数/软约束违规数/耗时，AC-F5-1/F5-4）。
- **执行形态**：Worker Thread 内运行（不阻塞事件循环），BullMQ 异步任务提交（互斥：同一时间仅一个排课任务），P95≤30s 目标在门禁 6 以 60 班满规模压测（AC-F5-2）。
- **结果衔接**：生成结果进「草稿课表」→ 拖拽微调 → 再校验（硬约束拦截/软约束提示，AC-F5-3）→ 发布走 §3.3 教务-排课发布接口（乐观锁 + 占用写入 + 补偿）。

### 4.10 设计侧可测验收（指针，不复制正文）—— 置信度：高

设计侧的 8 条可测试验收 **AC-UI-1 ~ AC-UI-8 以 `docs/16 §3.1` 为唯一正文来源**，ADR **只留指针不复制**，避免两处维护分叉；门禁6 执行时直接取 docs/16 最新版。与架构红线直接对应的两条：

- **AC-UI-1**：界面声明步数 ≤3 且与**实测点击次数**一致（硬指标②，逐个事务真点一遍比对 `stepText`）。
- **AC-UI-5**：`templates.ts` 不含 `if (role === ...)` —— 即 §4.7「前端模板表红线」的可执行检查（可直接 grep）。
- 其余 AC-UI-2（字段 ≤5）/ 3（默认学期，切 MONTH 可见集合不变）/ 4（大屏与下钻不含平台级配置对象）/ 6（40901–40904 三段式反馈）/ 7（冲突提示脱敏 + `hasOccupancyDetail=false` 且无课表入口）/ 8（六域跨图同色、趋势无平滑）由设计侧维护，分别对应本文档 §4.5、§4.7、§3.2。

> 开发期另有运行时断言（`design/src/components/QuickApply/schemas.ts` 的 `assertSchemaInvariants()`，DEV 环境执行、生产零开销）在更早一层拦截：步数解析失败、声明 >3 步、字段 >5、必填下拉缺 `options/dictCode` 均直接抛错。

---

## 五、实现代码（本阶段仅初始化 + 迁移机制说明，v2.0 更新）

> 按门禁约定本阶段不写业务代码，业务代码在 ADR 通过、设计阶段之后实现。

1. **项目初始化**（Git 自第一个提交纳入）——单仓 monorepo：

```
school-affairs/
├── apps/
│   ├── server/                     # NestJS
│   │   ├── prisma/                 # schema.prisma + migrations/（init 含全部 v2.0 对象与种子）
│   │   └── src/
│   │       ├── common/             # 全局 Guard(Auth/Permission/DataScope)、ScopeQueryBuilder、PIIFilter、拦截器
│   │       ├── platform/           # auth/rbac/dict/flow-template/message/space/asset/term/alert/jobs/ai
│   │       │   └── concurrency/    # C9 三件套统一实现
│   │       ├── domains/            # logistics/academic(含 engine 排课引擎 Worker)/moral/party/science/sport
│   │       └── crosscut/           # workflow(审批实例引擎)/dashboard(大屏聚合)
│   └── web/                        # vue-pure-admin（角色化入口分流：管理后台/看板/快捷工作台）
├── deploy/docker-compose.yml       # db + redis + server + web（四容器基线）
└── scripts/                        # migrate.sh / backup.sh
```

Git 顺序：`git init` → `.gitignore` → 首个 commit「chore: scaffold + ADR v2.0 + migration baseline」→ 后续每个功能/迁移独立 commit，禁止先写码后补 Git。

2. **迁移机制**：不变（§1.4）——`npx prisma migrate dev --name <desc>` 生成时间戳递增 migration.sql；生产 migrate job 先于 server 执行 `migrate deploy`，失败中止部署；回滚 = 备份 + down.sql + resolve；破坏性变更 expand-contract；种子数据（12 角色/M01–M15 菜单/按钮点/10 字典编码/流程缺省绑定）以可重复执行 seed migration 落库。

3. **并发与超期预留**（供测试阶段验证，不在此阶段实现）：C9 三件套（§4.1）与 C10 扫描（§4.4）的平台层统一实现是门禁 4 的强制交付物；门禁 6 逐点压测。

---

## 六、架构门禁自检（v2.0）

### 五要素对照

| 门禁要素 | 结论 |
| --- | --- |
| 技术选型 | ✅ 基线沿用（vue-pure-admin + NestJS 10/TS + PG16 + Redis7 + Prisma）+ 新增六项（§1.2，逐项置信度+验证方式） |
| 部署形态 | ✅ Docker Compose 单机四容器 + 源码导出/自有服务器；WorkBuddy 仅预览 |
| 数据迁移策略 | ✅ Prisma 时间戳编号 + init 含全部对象与种子 + backup/down + expand-contract，禁止手工改库 |
| 逐项置信度 | ✅ §1.2/§七 全部标注高/中/低 + 理由 + 验证方式；中置信度项均给出备选与验证门禁 |
| AI 预留 | ✅ AiGateway 三件套沿用 + 场景清单（§2.2）+ 红线（§2.3：不做排课求解/权限判定） |

### 11 条新约束逐条对照

| # | 约束 | 落点 | 结论 |
| --- | --- | --- | --- |
| 1 | 统一场地 space，排课权威，单向校验 40901/40902 | §1.7、§4.2 | ✅ |
| 2 | 统一资产 asset，严格域隔离 40302 | §1.7、§4.3 | ✅ |
| 3 | 服务端逐接口三级校验 + 三种归属类型 + 校验落点 | §1.5、§三 | ✅（fail-closed 三层，杜绝绕过） |
| 4 | C9 统一策略覆盖 8 并发点 | §4.1 | ✅（CAS/部分唯一索引/乐观锁三件套） |
| 5 | C10 超期调度统一对象 + 定时扫描 | §4.4 | ✅ |
| 6 | 学期+周次主口径，自然月仅展示维度 | §4.5 | ✅ |
| 7 | 自动排课引擎选型（P95≤30s） | §1.2 ADR-02、§4.9 | ✅（自建启发式，已用户确认 2026-10-28；P95 待门禁6 压测） |
| 8 | 大屏图表库 + 服务端聚合 + AC-DD5-2 过滤 | §4.7 | ✅（ECharts，置信度高） |
| 9 | 消息中心独立模块 + 统一接口 + 已读业务端唯一真源 | §4.6 | ✅（outbox + AC-MS8-1 口径） |
| 10 | 角色可扩展性服务端硬约束 | §3.4 | ✅（保存校验器 + 运行时兜底两处落点） |
| 11 | 课表变更补偿（事件驱动 or 定时） | §4.8 | ✅（事件驱动为主 + 定时补偿兜底） |

**架构门禁：通过，无缺项，可进入设计阶段（门禁 3）。** 中置信度项（ADR-02/ADR-03）路线已由用户确认（2026-10-28），验证方式保留至门禁6，不构成门禁阻断。

---

## 七、ADR 决策摘要表（置信度逐项汇总）

| 决策项 | 结论 | 置信度 | 验证方式 |
| --- | --- | --- | --- |
| 管理端 UI 基座 | 采纳 vue-pure-admin（沿用） | 高 | 门禁3 设计稿 |
| 前端共存策略 | 统一基座 + 角色化入口分流（沿用） | 高 | 门禁3 界面验收 |
| 后端框架 | NestJS 10 + TS（沿用）；Spring Boot 3 备选 | 高 | 门禁4 可运行 |
| 数据库 / 缓存 / ORM | PostgreSQL 16 / Redis 7 / Prisma（沿用） | 高 | 门禁4/6 |
| 部署形态 | Docker Compose 单机 + 源码导出（沿用） | 高 | 门禁6 部署回滚演练 |
| 数据迁移策略 | 时间戳编号 + init 全对象 + 种子 + expand-contract（沿用+扩展） | 高 | 门禁6 迁移/回滚演练 |
| 权限校验落点 | 全局 Guard + @DataScope 启动强制 + ScopeQueryBuilder 唯一出口（fail-closed 三层） | 高 | 门禁6 复测 docs/04 §六 10 条越权场景 |
| 错误码体系 | 40301/40302/40303/40304 + 40901/40902（对齐 docs/04） | 高 | 门禁6 越权场景断言 |
| 角色硬约束 | 角色保存校验器 + DataScopeGuard 运行时 SELF 兜底 + 告警 | 高 | 门禁6 AC-RBAC-1~4 |
| C9 并发 | DB 三件套（CAS/部分唯一索引/乐观锁）为唯一正确性来源；Redis 锁仅长任务互斥 | 高 | 门禁6 八点并发压测 |
| space 单向校验 | 排课权威源；预约先查课表（40901）再竞争（40902）；反向 V2.0 | 高 | 门禁6 并发预约用例 |
| asset 域隔离 | assetType × 借用权限点双重校验，跨域 40302 | 高 | 门禁6 跨域借用用例 |
| C10 超期调度 | alert 统一对象 + BullMQ 定时扫描 + DICT-OVERDUE-DAYS 阈值 | 高 | 门禁6 超期触发用例 |
| 时间口径 | termId+周次主口径，服务端聚合后月重排 | 高 | 门禁6 AC-DD1-2 切换一致性 |
| 消息中心 | outbox + 模板渲染 + 已读业务端唯一真源 + 业务消息直接跳转 | 高 | 门禁6 AC-MS5/7/8 |
| 大屏图表库 | ECharts 5 | 高 | 门禁3 四类图表原型 |
| 大屏聚合 | 服务端聚合 + ScopeQueryBuilder + Redis 60s 缓存 + 指标白名单（AC-DD5-2） | 高（实时聚合 + 60s 缓存已确认；预聚合表 V2.0） | 门禁6 分角色同接口数据范围验证 |
| 审批流落地 | flow_template/node/instance 快照流转 + 双校验（节点角色+范围覆盖） | 高 | 门禁6 AC-D3-1~8 |
| 课表变更补偿 | 事件驱动（outbox 触发比对）为主 + 每日补偿扫描兜底 | 高 | 门禁6 AC-F4-4 推送断言 |
| AI 场景 | 五场景（摘要/草稿/归因/填报/调参建议）+ 三红线（不做排课求解/权限判定/推送决策） | 高 | 后续版本落地时按场景开关验收 |
| **排课引擎** | 自建 TS 启发式（贪心+回溯+禁忌搜索），Worker Thread + BullMQ | **中** | 门禁6 60 班满规模压测 P95；已用户确认（2026-10-28），OR-Tools 为降级备选 |
| **异步任务基座** | BullMQ（Redis 队列）统一 C10/消息/排课任务 | **中** | 门禁6 故障恢复验证；已用户确认（2026-10-28），Redis 持久化必选 |

> 无「低」置信度决策。两个「中」置信度项均已给出备选路线与验证门禁，且用户已于 2026-10-28 确认采纳推荐方案（逐条裁决见 §八）。

---

## 八、高风险选型（已由用户确认，2026-10-28）

| # | 选型 | 推荐 | 备选 | 为什么曾需拍板 |
| --- | --- | --- | --- | --- |
| ① | **自动排课引擎路线**（REQ-F5，P95≤30s 硬指标） | 自建 TS 启发式（贪心+回溯+禁忌搜索）：零新增运行时、软约束友好、无解报告可控、复制友好 | OR-Tools CP-SAT Python sidecar：最优性与约束表达更强，但 +1 容器/运行时 + IPC，单人维护成本显著上升 | 排课质量是教务域核心体验，两条路线的质量上限与运维成本差异大，且「中」置信度项影响首版交付风险 |
| ② | **异步任务基座** | BullMQ：C10 扫描/消息投递/排课任务统一队列语义（重试/进度/互斥） | 进程内 @nestjs/schedule + PG 咨询锁：Redis 用途最小化（仅缓存），Redis 重启不伤任务 | 决定 Redis 是否成为「任务正确性关键路径」——影响运维心态（Redis 能否随手重启）与故障半径 |
| ③ | **大屏数据新鲜度方案** | 服务端实时聚合 + Redis 60s 缓存（校规模 ~2000 用户足够） | 预聚合表/物化视图（定时 job 写入）：适合 7×24 挂墙大屏/多并发只读场景，但引入聚合表一致性维护 | 取决于学校实际使用形态（办公查看 vs 走廊挂墙大屏），影响后续性能改造返工成本 |

**用户裁决（2026-10-28，逐条）：**
- ① **排课引擎：采纳推荐方案**（自建 TS 启发式）。备选 OR-Tools CP-SAT sidecar 保留为降级路径 + 启用条件：门禁6 以 60 班满规模压测，若 P95 >30s 或硬约束满足率不达标则切换；四容器基线确定不加容器。
- ② **异步任务基座：采纳推荐方案**（BullMQ）。Redis 持久化配置为必选项；备选 @nestjs/schedule + PG 咨询锁保留为降级路径；「杀 worker / 重启 Redis 验证任务恢复」列入门禁6 验证项。
- ③ **大屏数据新鲜度：采纳推荐方案**（服务端实时聚合 + Redis 60s 缓存，办公查看场景）。预聚合表列 V2.0 + 启用条件：若后续出现走廊挂墙 7×24 展示需求再启用。
