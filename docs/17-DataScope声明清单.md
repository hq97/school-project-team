# DataScope 声明清单（门禁 4 权限验收依据）

> 阶段：Phase 4 开发（门禁 4 权限门禁）| 产出人：架构与实现专家（高筑远）| 日期：2026-10-30（审计响应修订：2026-10-31）
> 事实源：`docs/16-UI设计约束说明.md §2.3`（控件 × 按钮权限点 × 数据对象 × 范围 × 越权码）+ `docs/02-ADR-架构决策.md §三`（逐接口权限基线）
> 机制：`docs/02 §1.5` 三层 fail-closed —— 全局 Guard（不可跳过）+ `@DataScope()` 启动期强制声明（缺声明拒绝启动）+ `ScopeQueryBuilder` 数据层唯一出口（lint 封堵裸 SQL）

**图例**：✅ 本片已落声明并有路由实现 ｜ 🟡 能力已实现（随其它路由下发，无独立路由）｜ ⬜ 本片未实现（见文末汇总清单）

---

## 一、来自 docs/16 §2.3（12 行）

| # | 界面控件 | 按钮权限点 / 菜单 | 数据对象 | 归属类型 × 范围 | 越权返回 | 声明落点 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 大屏 KPI / 图表 | M15 | dashboard_metric | ORG × 继承角色范围 | 40301/40303 | `dashboard.controller.ts#overview` `@DataScope({resource:'dashboard_metric',ownership:'ORG'})` | ✅ |
| 2 | 下钻明细 | M15 | dashboard_metric | ORG × 继承角色范围 | 40303/40304 | `dashboard.controller.ts#drill` | ✅ |
| 3 | 维度切换器 | M15 | — | 不改变可见性与越权判定 | — | `dashboard.controller.ts#overview` `dim` 默认 TERM | ✅ |
| 4 | 异常预警列表条目 | M15 | alert | ORG × 继承责任人范围 | 40303 | **GET /api/alerts 未建路由**；`space/asset` 侧预警随 overview 下发 | ⬜ |
| 5 | 「领用」按钮（物资卡） | BTN-MAT-APPLY | material_requisition / asset | ORG × SELF（写强制 applicantId）/ asset PUBLIC + 域隔离 | 40302/40303 | `material.controller.ts#apply` `@DataScope({...,write:true})` | ✅ |
| 6 | 「提交报修」按钮 | BTN-REP-SUBMIT | repair_ticket | ORG × SELF（写强制 reporterId） | 40302 | `repair.controller.ts#submit` | ✅ |
| 7 | 「请假申请」按钮 | BTN-LEAVE-APPLY | leave_request | ORG × SELF / 本班（班主任） | 40302/40303 | 德育域控制器未建 | ⬜ |
| 8 | 「调课/代课申请」按钮 | BTN-SCH-CHANGE-APPLY | schedule_change | ORG × SELF | 40302 | 教务域控制器未建 | ⬜ |
| 9 | 场地格子「点击预约」 | BTN-PARTY-ROOM-BOOK / BTN-SCI-LAB-BOOK | space + space_occupancy | PUBLIC 归属 + `openScope` 开放范围（docs/04 §3.11） | 40304/40901/40902 | `space.controller.ts#book` + `space-occupancy.service#reserve` | ✅ |
| 10 | 占用矩阵格子 | 同上 | space_occupancy | PUBLIC 归属 + `openScope`；**只下发状态不下发身份** | 40304 | `space.controller.ts#occupancies` | ✅ |
| 11 | `occupiedBy` / 「查看该场地课表」 | 同上 | timetable_entry | 范围覆盖才下发明细；`hasOccupancyDetail` 布尔 | 40303 | `space-occupancy.service#assertNoScheduleConflict`（分级脱敏） | 🟡 |
| 12 | 「改为申请 N 件」（40903） | BTN-MAT-APPLY | asset | PUBLIC + 域隔离 | 40302 | `asset.controller.ts#borrow` + `material.controller.ts#issue`（availableQty 实时读） | ✅ |

---

## 二、来自 docs/02 §三 逐接口基线（52 行）

### 平台层

| # | 路由 | 归属 × 范围 | 越权返回 | 声明落点 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 1 | GET/POST/PUT/DEL /api/system/users | PUBLIC × ALL（仅 R-ADMIN） | 40301/40302 | 未建控制器 | ⬜ |
| 2 | **POST /api/system/roles** | PUBLIC × ALL + 保存校验器 | 40302/40000 | `roles.controller.ts#save` + `RoleService.save` | ✅ |
| 3 | **DELETE /api/system/roles/:code** | PUBLIC × ALL | 40302 | `roles.controller.ts#remove`（预置 R-* 拒绝） | ✅ |
| 4 | GET/POST/PUT/DEL /api/system/menus | PUBLIC × ALL | 40301/40302 | 未建 | ⬜ |
| 5 | GET/POST/PUT/DEL /api/system/depts | PUBLIC × ALL | 40301/40302 | 未建 | ⬜ |
| 6 | GET /api/system/dicts | PUBLIC（只读） | — | 未建（只读能力见 #8） | ⬜ |
| 7 | POST/PUT/DEL /api/system/dicts | PUBLIC × ALL（BTN-DICT-EDIT） | 40302 | 未建 | ⬜ |
| 8 | **GET /api/dicts/items?dictCode=** | PUBLIC（只读） | — | `dict.controller.ts#items` | ✅ |
| 9 | GET/POST/PUT /api/system/flow-templates | PUBLIC × ALL（BTN-FLOW-TPL-EDIT） | 40301/40302 | 未建 | ⬜ |
| 10 | **GET /api/spaces?spaceType=** | ORG × 按开放范围 | 40303 | `space.controller.ts#list` | ✅ |
| 11 | **POST /api/spaces/:id/occupancies** | ORG；单向校验 40901 → 并发 40902 | 40901/40902 | `space.controller.ts#book` | ✅ |
| 12 | POST/PUT/DEL /api/spaces（维护/停启用） | PUBLIC × ALL（R-ADMIN/R-LOGISTICS） | 40302 | 未建 | ⬜ |
| 13 | **GET /api/spaces/:id/free-slots?week=N** | ORG；已排除 SCHEDULE 占用 | 40303 | `space.controller.ts#freeSlots` | ✅ |
| 14 | **GET /api/spaces/:id/occupancies?week=N** | ORG；只下发状态 | 40303 | `space.controller.ts#occupancies` | ✅ |
| 15 | **GET /api/assets?assetType=&keyword=** | PUBLIC + 域隔离 | — | `asset.controller.ts#list` | ✅ |
| 16 | **POST /api/assets/:id/borrow** | PUBLIC + assetType × 权限点 | 40302 | `asset.controller.ts#borrow` | ✅ |
| 17 | **POST /api/assets/:id/return** | ORG × SELF（借用人） | 40303 | `asset.controller.ts#returnAsset` | ✅ |
| 18 | POST /api/assets/:id/stock | PUBLIC × ALL（R-LOGISTICS） | 40302 | 未建 | ⬜ |
| 19 | **GET /api/messages** | ORG × SELF（receiverId） | 40304 | `message.controller.ts#list` | ✅ |
| 20 | **POST /api/messages/:id/read** | ORG × SELF | 40304 | `message.controller.ts#read` | ✅ |
| 21 | POST/PUT /api/system/message-templates | PUBLIC × ALL（M0506） | 40302 | 未建 | ⬜ |
| 22 | GET /api/alerts | ORG × 继承责任人 | 40303 | 未建（C10 调度片） | ⬜ |

### 横切层

| # | 路由 | 归属 × 范围 | 越权返回 | 声明落点 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 23 | POST /api/workflow/instances/:id/transition | 节点 + 归属；双校验 | 40302/40303 | workflow 引擎片未建 | ⬜ |
| 24 | **GET /api/dashboard/overview?dim=** | ORG × 继承角色范围；服务端聚合 | 40303 | `dashboard.controller.ts#overview` | ✅ |
| 25 | **GET /api/dashboard/drill?kpiCode=&dim=** | ORG；下钻过滤 | 40303/40304 | `dashboard.controller.ts#drill` | ✅ |
| 26 | **GET /api/dashboard/term/current** | 豁免（不读业务数据） | — | `dashboard.controller.ts#termCurrent` `@PublicDataScope` | ✅ |

### 业务域层

| # | 路由 | 归属 × 范围 | 越权返回 | 声明落点 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 27 | **GET /api/procurement/dashboard/summary** | ORG × ALL | 40303 | `procurement.controller.ts#summary` | ✅ |
| 28 | **GET /api/procurement/projects** | ORG × 按范围截断 | 40303 | `procurement.controller.ts#list` | ✅ |
| 29 | GET /api/procurement/projects/:id | ORG；单条越权 40304 | 40303/40304 | `procurement.controller.ts#detail` | ✅ |
| 30 | **POST /api/procurement/projects** | ORG × SELF（写强制 creatorId） | 40303 | `procurement.controller.ts#create` | ✅ |
| 31 | **POST /api/material/requisitions** | ORG × SELF（写强制 applicantId） | 40302 | `material.controller.ts#apply` | ✅ |
| 32 | GET /api/material/requisitions | ORG × 按范围截断 | 40303 | `material.controller.ts#list` | ✅ |
| 33 | **POST /api/material/requisitions/:id/issue** | PUBLIC × ALL + C9 CAS | 40302/40903 | `material.controller.ts#issue` | ✅ |
| 34 | **POST /api/repair/tickets** | ORG × SELF（写强制 reporterId） | 40302 | `repair.controller.ts#submit` | ✅ |
| 35 | GET /api/repair/tickets | ORG × 按范围截断 | 40303 | `repair.controller.ts#list` | ✅ |
| 36 | **PATCH /api/repair/tickets/:id/status** | ORG × ALL（后勤） | 40302 | `repair.controller.ts#updateStatus` | ✅ |
| 37 | POST /api/schedule/auto-run | PUBLIC × ALL（BTN-SCH-AUTO） | 40302 | 排课引擎片未建 | ⬜ |
| 38 | POST /api/schedule/versions/:id/publish | PUBLIC × ALL + 乐观锁 | 40302 | 排课片未建（F4-2/F4-3/F4-4） | ⬜ |
| 39 | GET /api/timetable/my | ORG × SELF | — | 未建 | ⬜ |
| 40 | GET /api/timetable/class/:classId | ORG × 本班（head_teacher.classId） | 40304 | 未建 | ⬜ |
| 41 | POST /api/schedule-changes | ORG × SELF | 40302 | 未建 | ⬜ |
| 42 | POST /api/leave/requests | ORG × SELF | 40302 | 德育片未建 | ⬜ |
| 43 | GET /api/leave/requests | ORG + 关系型（家长） | 40303 | 德育片未建 | ⬜ |
| 44 | GET /api/discipline/records/:id | 关系型 + B4 字段过滤 | 40304 | 德育片未建 | ⬜ |
| 45 | POST /api/awards/:id/nominations | ORG × SELF/本班 + C9 名额 | 40302/40904 | 德育片未建 | ⬜ |
| 46 | POST /api/notices | ROLE_GRANT × ALL（R-PARTY） | 40302 | 党政办片未建 | ⬜ |
| 47 | GET /api/notices | ROLE_GRANT（可见角色 ∩ M1201） | 40304 | 党政办片未建 | ⬜ |
| 48 | POST /api/meeting-rooms/bookings | ORG + space 单向校验 | 40901/40902 | 逻辑已由 #11 通吃（统一 space 路由） | 🟡 |
| 49 | POST /api/documents/{reg,flow} | PUBLIC × ALL（R-PARTY） | 40302 | 党政办片未建 | ⬜ |
| 50 | POST /api/science/lab-bookings | ORG + space/asset 双重校验 | 40901/40902 | 科教片未建（复用 #11/#16） | ⬜ |
| 51 | POST /api/science/competitions/:id/apply | ORG × SELF + C9 名额 | 40302/40904 | 科教片未建 | ⬜ |
| 52 | POST /api/science/projects | ORG × SELF | 40302 | 科教片未建 | ⬜ |
| 53 | POST /api/sport/events/:id/schedule | ORG + space 占用 | 40901/40902 | 体育片未建（复用 #11） | ⬜ |
| 54 | POST /api/sport/equipment/borrow | PUBLIC + 域隔离 + C9 | 40302/40903 | 域隔离与 CAS 已在 #16 通吃，独立路由未建 | 🟡 |
| 55 | POST /api/sport/fitness/scores | ORG × 本班/本组 | 40302/40303 | 体育片未建 | ⬜ |

> 行数说明：docs/02 §三 为 52 行；本表按「一行路由 = 一条声明」展开为 55 条（含 3 条同一行的读/写拆分，如 #28/#29、#31/#32、#34/#35），便于逐条核对。

---

## 三、覆盖统计

| 项 | 数量 |
| --- | --- |
| 清单总条目（docs/16 §2.3 12 条 + docs/02 §三 展开 55 条） | **67** |
| ✅ 已落 `@DataScope()` 声明且有路由实现 | **30** |
| 🟡 能力已实现（随其它路由通吃，无独立路由） | **3**（#11 occupiedBy 脱敏、#48 会议室预约、#54 体育器材借用） |
| ⬜ 本片未实现 | **34** |
| 代码内路由总数（启动期扫描口径） | **27**（26 条 `@DataScope()` + 1 条 `@PublicDataScope()` 豁免） |
| 缺声明路由 | **0**（缺声明即拒绝启动，故运行期必为 0） |

### 本片未实现清单（34 条，不含糊）

**平台/系统管理（9）**：#1 用户 CRUD、#4 菜单 CRUD、#5 部门 CRUD、#6/#7 字典读写（M0505 维护）、#9 流程模板 CRUD、#12 space 维护/停启用、#18 库存调整报废、#21 消息模板。
**横切（2）**：#22 GET /api/alerts（C10 调度片）、#23 workflow transition（审批流引擎片）。
**教务（5）**：#37 自动排课、#38 课表发布（F4-2/F4-3/F4-4）、#39 我的课表、#40 班级课表、#41 调课申请。
**德育（4）**：#42/#43 请假、#44 违纪、#45 评优名额（C9 K2-2）。
**党政办（3）**：#46/#47 公告（ROLE_GRANT 归属型）、#49 公文。
**科教（3）**：#50 实验预约、#51 竞赛名额（C9 M7-2）、#52 课题。
**体育（2）**：#53 赛事排期（C9 N5-2）、#55 体测录入。
**补齐项（6，清单外但属同批）**：GET /api/material/requisitions 之外的导出类接口、workflow 双校验（AC-D3-4/D3-8）、C10 定时扫描、消息 outbox 投递、排课引擎、大屏真实指标聚合。

---

## 四、三件套实现要点（为什么能杜绝绕过）

| 层 | 实现 | 为什么绕不过 |
| --- | --- | --- |
| ① 全局 Guard | `GlobalAuthGuard`（JWT + 账号状态实时校验）+ `GlobalPermissionGuard`（菜单 40301 / 按钮 40302），以 `APP_GUARD` 注册在 `app.module.ts` | 注册为全局守卫即 NestJS 请求管道的强制环节——**不经过它到不了 Controller**；业务模块无法用装饰器跳过；权限实时取角色不做快照缓存，改权限即时生效（docs/04 场景 10） |
| ② `@DataScope()` + 启动期扫描 | `DataScopeStartupScanner.assertOrExit()` 用 `ModulesContainer` 枚举全部 controller 方法的路由元数据，**缺声明即 `process.exit(1)`** | 不是告警、不是日志——**起不来**。这根治「漏配 = 裸奔」：新增路由忘写声明，服务根本启动不了。豁免必须显式 `@PublicDataScope(reason)` 并写原因 |
| ③ `ScopeQueryBuilder` 唯一出口 | 列表 `buildWhere()` / 单条 `matchRecord()` / 写操作 `forceOwner()`；三种归属类型（组织型含 CLASS 载体与换届快照、关系型、角色授权型）+ 公共对象；配套 `eslint-rules/no-raw-sql.js` 封堵业务模块裸查 | 业务代码拿不到「不带范围的查询」；伪造 `creatorId/applicantId` 被 `forceOwner` 覆盖；单条越权统一 40304 防枚举；裸 SQL 被 lint 拦在提交前 |
| 角色硬约束 | `RoleService.save` 三项齐全校验（AC-RBAC-1）、列表类菜单拒绝授予（AC-RBAC-2 前半）、预置 R-* 删除拒绝 + DB 触发器兜底（AC-RBAC-3）、枚举不含第四级（AC-RBAC-4）；读路径 `ScopeQueryBuilder.effectiveScope()` 按 SELF 兜底 + 告警日志（AC-RBAC-2 后半） | 写路径存不进、读路径兜底降级——**写读两侧都不依赖前端表单校验** |
| 错误码中间件 | `BizException` + `BizExceptionFilter`：40301–40304 / 40901–40904 + `40000`；结构化 `data.conflict`（含 `hasOccupancyDetail` 布尔、服务端渲染 `slotText`、`suggestions`）/ `data.stock`（实时 `availableQty`）/ `data.quota` | 前端 axios 拦截器统一提示；`occupiedBy` 按范围脱敏，SELF 用户借冲突提示反查他人课表的路径被切断 |

---

## 五、门禁 4 我判断还缺什么（不自判通过，报主理人）

1. **未实现路由 34 条**：本片只做了权限骨架 + 后勤/平台/大屏骨架路由，六域业务控制器（教务/德育/党政办/科教/体育）尚未建立——它们一旦建立，启动期扫描会强制补齐声明，但**这些路由本身未实现，门禁 4 不能按"权限已完备"判过**。
2. **权限三件套缺少自动化测试**：目前只有静态机制（启动扫描 + lint），**没有越权用例测试**。建议门禁 6 至少覆盖 docs/04 §六 的 10 条越权场景 + AC-RBAC-1~4 + 40901/40902 脱敏断言（测试专家可复用 `ScopeQueryBuilder.matchRecord` 直接单测）。
3. **C9 三件套只落了 ① 与 ②**：原子扣减（asset/material）、部分唯一索引（space_occupancy）已落；**③ 乐观锁（课表版本 F4-3）未落**（依赖教务片）；名额类（K2-2/M7-2）40904 未落。
4. **C10 超期调度 / 消息 outbox 投递 / 排课引擎 / 大屏真实指标聚合** 均未实现（本片明确范围外）。
5. **Prisma Client 未生成、未安装依赖、未跑过 `prisma migrate deploy`**：schema 与 init/seed 迁移已写，但**未经真实数据库验证**（本片未执行 npm install / migrate）。需门禁 6 或版本控制专家接手后补一次真实环境验证。
6. ~~**JWT 密钥/环境变量未纳入配置基线**~~ → **已在审计响应中修复**：生产缺 `JWT_SECRET` 启动期 `process.exit(1)`（`GlobalAuthGuard.assertJwtSecretOrExit()`，main.ts 调用）；`verify` 显式 `algorithms:['HS256']`。

---

## 六、门禁 4 审计响应（qa-audit 独立审计 → 修复记录，2026-10-31）

### 6.1 阻断项（5/5 已修）

| # | 审计发现 | 修复 | 落点 |
| --- | --- | --- | --- |
| B1 | `returnAsset` `void req` 零归属校验，任何持 M0302 者可归还他人借用并回补库存（docs/04 §4.2-D14） | 取 `asset_borrow` 记录 → `matchRecord` + **借用人本人硬校验**双保险，不通过 40304 + 告警日志；`@DataScope` 补 `requiredScope:'SELF'`；归还原子回补 `availableQty` | `asset.controller.ts#returnAsset` |
| B2 | `assetType` 入参覆盖域隔离白名单（白名单白算） | 入参只能**收窄**白名单：`visible.filter(t => t === assetType)`；显式请求无权域 → 40302 | `asset.controller.ts#list` |
| B3 | `buildWhere` 中 `extra` 后展开会整体替换范围条件（导出接口按常规写法即越权，docs/04 §五.5） | 展开顺序反转：`{ ...extra, ...this.applyOwnership() }`——归属条件后置覆盖、不可被调用方覆盖 | `scope-query-builder.ts#buildWhere` |
| B4 | 种子只授权 R-ADMIN/R-DEAN，其余 10 角色 role_menu/role_button 全空 → 全员 40301，越权测试跑不起来 | 新迁移 `20261030000002_seed_role_grants`：按 docs/03 §3.1/§3.2 矩阵**逐角色补齐 12 角色菜单+按钮授权**（幂等，未回改已应用的 001） | `prisma/migrations/20261030000002_seed_role_grants/` |
| B5 | schema 的 `@@unique`（无 WHERE）会在下次 `migrate dev` 时生成"删部分索引+建全表唯一"的迁移，静默干掉 C9 ② | schema 去掉 `@@unique` 并写死维护约定（禁止自动回收）；新增守护脚本 `npm run prisma:guard`（扫全部迁移，出现 `DROP INDEX "uniq_effective_slot"` 即非零退出）；002 迁移幂等重建该索引 | `schema.prisma` + `scripts/assert-no-partial-index-drop.mjs` + 002 |

### 6.2 高危项（10/11 已修，1 项未采纳并说明理由）

| # | 审计发现 | 修复 | 落点 |
| --- | --- | --- | --- |
| H1 | E3 换届快照只取当前学期 → 查历史学期班级载体为空 → 班主任看全年级历史数据 | `Principal.headTeacherClassIds` 改为 `headTeacherTerms: {classId, termId}[]` **全量返回**；`ScopeQueryBuilder.classIdsFor(termId)` 按**记录的 termId**选取 | `scope.types.ts` / `ownership-resolver.ts` / `scope-query-builder.ts` |
| H2 | 脱敏直读 `principal.scope` 绕过 SELF 降级（AC-RBAC-2） | 新增 `effectiveScopeOf()` **唯一判定源**（data-scope.options.ts），脱敏/大屏 scope 展示全部改走它，禁止直读 `principal.scope` | `space-occupancy.service.ts` / `dashboard.controller.ts` |
| H3 | `occupiedBy` 下发内部 UUID（classId） | 改下发**可读名**：班级名 · 学科 · 任课教师（查 `sys_dept.name` / `sys_user.realName`） | `space-occupancy.service.ts` |
| H4 | space 三条路由声明了 `@DataScope` 却不用 `scopeQuery`；`openScope` 定义了从未校验 | 统一走 `loadVisibleSpace()`：**openScope 开放范围校验**（docs/04 §3.11）+ 状态校验 + 40304；归属语义澄清为 `ownership:'PUBLIC'` + openScope（按 ORG×SELF 判会把普通教师锁死，与 BTN-PARTY-ROOM-BOOK 授教师矛盾）；list 管理视图走 `buildWhere()` 真过滤 | `space.controller.ts` |
| H5 | 无 class-validator，DTO 全是 interface → 零入参校验，`quantity:-100` 反向加库存 | 新增 3 个 **class DTO**（borrow/issue/bookSlot），`@IsInt @Min(1) @Max(...)`；ValidationPipe 加 `forbidNonWhitelisted` | `dto/*.dto.ts` + `package.json` + `main.ts` |
| H6 | JWT `?? 'dev-secret'` 兜底 + 未显式 algorithms | 生产缺 `JWT_SECRET` 启动期 `process.exit(1)`；`verify` 显式 `algorithms:['HS256']`；开发态兜底打告警 | `global-auth.guard.ts` + `main.ts` |
| H7 | 站内信按 ORG 声明 → ALL 用户可读全校、GRADE 用户因无 deptId 触发 Prisma 异常 500 | `@DataScope` 新增 `forceSelf` 接口级约束（**不是第四级数据范围**，是接口恒本人的业务约束）：两条路由恒按 `receiverId=当前用户` 过滤 | `data-scope.options.ts` + `message.controller.ts` |
| H8 | `ownership-resolver` N+1（每请求 8–15 次 DB 往返，P95 头号风险） | `resolveGradeNodes` 改**一次批量查回全部祖先** + 内存按物化 path 逆序找第一个 GRADE；仍是每次请求实时计算（不违反 ADR-05） | `ownership-resolver.ts#resolveGradeNodes` |
| H9 | no-raw-sql 全文件文本匹配 → 一处 `scopeQuery` 豁免全文件裸查 | 改 **AST 按方法判定**：`CallExpression` visitor 找 `prisma.<model>.find*` 调用所属函数，函数体内无 scopeQuery 痕迹才报错 | `eslint-rules/no-raw-sql.js` |
| H10 | 启动扫描不遍历原型链；类级 `@DataScope` 会让全类方法免检 | 沿原型链逐层收集（到 Object.prototype 为止）；**类级声明不再放行**——只认方法级，类级声明记入 missing 拒绝启动 | `data-scope-scanner.ts` |
| H11 | dict 两条路由无 `@Menu` → 任何已登录账号可枚举全部字典 | **未采纳，理由已写入代码注释并回 qa-audit 复核**：M0505 在矩阵里仅 R-ADMIN 可见，挂上它会让六域表单（报修故障类型/请假/体测）取不到枚举直接不可用；字典是平台级业务枚举、无个人数据、只返回 enabled 项；敏感操作是**维护**（BTN-DICT-EDIT 仅 R-ADMIN）。如需收紧应由需求侧在 docs/03 增补"字典只读"菜单码，开发侧不擅自新增菜单语义 | `dict.controller.ts` 头注释 |

### 6.3 附带加固（非审计项）

- `repair.controller#updateStatus`：原骨架 `void req` → 补状态机合法流转表 + `matchRecord` + 落库。
- `dashboard#drill`：`where`（含 visibleDeptIds/ownerId）不再回显前端（泄露部门树）。
- `dashboard` overview/drill 的 `scope` 展示改走 `effectiveScopeOf`，杜绝"显示全校、实际只给本人"的口径错位。

### 6.4 覆盖统计（修订后不变 + 新增）

路由总数仍 **27 条**（26 `@DataScope` + 1 `@PublicDataScope`），缺声明 0；清单 67 条口径不变。
新增文件：`migrations/20261030000002_seed_role_grants/`、`scripts/assert-no-partial-index-drop.mjs`、`modules/platform/dto/borrow-asset.dto.ts`、`modules/platform/dto/book-slot.dto.ts`、`modules/logistics/dto/issue-material.dto.ts`。

### 6.5 遗留给 qa-audit 复核的两点

1. **H11（字典只读）**：请复核处置理由；如确认要收紧，走需求侧在 docs/03 增补菜单码，我再改。
2. **docs/03 自身一处不自洽**：`M1102 班级推优申报` 菜单授给 R-STUDENT（✅），但 `BTN-AWARD-NOMINATE 班级推优` 按钮授给 R-TEACHER（学生无此按钮）——菜单可见但按钮不可用。002 迁移**按文档字面**落（按钮给 A,TEACHER），是否要补 R-STUDENT 由需求侧裁决，我不擅自改矩阵。
