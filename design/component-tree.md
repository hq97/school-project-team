# 前端组件树（vue-pure-admin 目录约定）

> 落盘路径：`design/src/**`，开发阶段（门禁4）整体拷贝到 vue-pure-admin 的 `src/` 下即可运行。
> 目录前缀 `design/src/` ≡ vue-pure-admin 的 `src/`（`@/` 指向 `src/`）。

## 0. 总览（页面 → 区块 → 组件 → 状态）

```
src/
├─ design/
│  ├─ tokens.css                    # 设计 Token 唯一真源（CSS 变量）
│  └─ tokens.ts                     # 设计 Token（TS 侧，供 ECharts / 内联样式）
├─ api/
│  ├─ types.ts                      # 与后端 DTO 对齐的共享类型
│  ├─ bigscreen.ts                  # 大屏聚合 / 下钻接口（M15）
│  └─ biz.ts                        # 物资 / 报修 / 场地 / 请假 / 调课 + 错误码文案
├─ components/
│  ├─ ECharts/
│  │  ├─ useEChart.ts               # ECharts5 初始化 / resize / dispose
│  │  └─ ChartFrame.vue             # 四类图表统一容器（LINE/BAR/BAR_H/RADAR）
│  ├─ KpiCard/index.vue             # 指标卡（值 / 环比 / 同比 / 状态 / 下钻）
│  ├─ DimensionSwitcher/index.vue   # 「学期 / 自然月」维度切换器（默认学期）
│  ├─ MessageBell/index.vue         # 顶部铃铛 + 消息抽屉（不做正文预览）
│  ├─ ConflictAlert/index.vue       # 40901 / 40902 / 库存不足 / 名额已满 统一错误态
│  ├─ QuickApply/
│  │  ├─ schemas.ts                 # 6 个高频事务字段 Schema（含步数声明）
│  │  └─ Drawer.vue                 # 快捷申请抽屉（一屏表单 + 冲突就地重提）
│  └─ SpaceBooking/index.vue         # 场地预约通用视图（会议室 / 实验室 / 体育）
└─ views/
   ├─ dashboard/
   │  ├─ bigscreen/
   │  │  ├─ index.vue               # 数据大屏 M15（分角色渲染）
   │  │  └─ templates.ts            # 分角色「布局描述」（不含口径/过滤）
   │  └─ workbench/index.vue        # 教师个人工作台 M0101（不是大屏）
   ├─ material/apply/index.vue      # 物资领用申请 M0301
   ├─ repair/submit/index.vue       # 报修提交 M0401
   ├─ leave/apply/index.vue         # 请假申请 M0901
   ├─ schedule-change/apply/index.vue # 调课 / 代课申请 M0801
   ├─ party/meeting-room/index.vue  # 会议室预约 M1204
   └─ science/lab/index.vue         # 实验预约 M1302
```

## 1. 组件树（按页面展开）

### 1.1 数据大屏 `views/dashboard/bigscreen/index.vue`（M15，/dashboard/bigscreen）

| 区块 | 组件 | 关键状态 | 说明 |
| --- | --- | --- | --- |
| 页头 | `DimensionSwitcher` | `TERM`（默认）/ `MONTH` | 切换只重发请求，前端不重算 |
| 页头 | 数据截止时间 + 倒计时 | `dataAsOf` / `countdown` | 60s 定时刷新（REQ-DD7） |
| 异常预警 | 预警列表（自定义） | `alerts[]`，空则整块不渲染 | 校长首屏先看异常（硬指标①） |
| KPI 区 | `KpiCard` × N | `NORMAL` / `WARNING` / `DANGER` | 反向指标颜色跟随"好坏"不跟"升降" |
| 图表区 | `ChartFrame` × N | `LINE` / `BAR` / `BAR_H` / `RADAR` | AC-DD1-1 四类全覆盖 |
| 空态 | `el-empty` | 无数据 | 「当前角色没有可查看的大屏数据」 |

**分角色渲染链路（不在前端写角色分支）：**
```
GET /api/dashboard/overview?dim=TERM
   → { roleCode, templateKey, kpis[], charts[], alerts[], scopeText, dataAsOf }
   → TEMPLATES[templateKey] 取「布局描述」（栅格宽 / 高度 / 顺序）
   → 渲染
```
- `templateKey = NONE` → 教师 / 学生 / 家长，不渲染大屏（学生家长不开放大屏）。
- 新增角色 = 服务端加 key + `templates.ts` 加一行，**不动页面代码**。

### 1.2 教师个人工作台 `views/dashboard/workbench/index.vue`（M0101）

| 区块 | 组件 | 状态 |
| --- | --- | --- |
| 高频事务卡 | `button.qcard` × 6，`v-auth=btnCode` | 无权限入口隐藏（服务端仍是边界） |
| 我的课表 / 我的申请 / 我的报修 | 卡片入口 | 点击跳对应菜单 |
| 申请抽屉 | `QuickApply/Drawer.vue` | 提交中 / 成功 / 冲突 |

### 1.3 物资领用申请 `views/material/apply/index.vue`（M0301）

| 区块 | 组件 | 状态 |
| --- | --- | --- |
| 搜索 | `el-input` + 查询按钮 | loading |
| 物资卡网格 | 自定义卡片（名称 + 可用库存 tag + 领用按钮） | 无库存 → 按钮禁用（避免点必然失败） |
| 申请抽屉 | `QuickApply/Drawer.vue` | 库存不足 → `ConflictAlert` 三段式 + 一键改数量 |

### 1.4 场地预约 `components/SpaceBooking/index.vue`（M1204 / M1302 / 体育）

| 区块 | 组件 | 状态 |
| --- | --- | --- |
| 场地 / 周次选择 | `el-select` × 2 | 默认选中第一个场地 + 当前周次 |
| 占用矩阵 | 自定义 table（7 天 × 12 节） | `FREE` 白 / `SCHEDULE` 灰（不可点，40901 源） / `RESERVED` 浅蓝（40902 源）；**只渲染状态，不渲染占用人身份** |
| 图例 | 三色说明 | 让用户在第 2 步就看见冲突 |
| 申请抽屉 | `QuickApply/Drawer.vue` | 时段已预填（省一步） |

### 1.5 消息中心 `components/MessageBell/index.vue`（M0105）

| 区块 | 组件 | 状态 |
| --- | --- | --- |
| 铃铛 + 未读角标 | `el-badge` | 角标只反映站内信已读（≠ 业务已读回执） |
| 消息抽屉 | 列表行（分类 tag + 未读点 + 时间 + **仅标题**） | 点击 → 先置站内信已读 → 跳业务详情 |
| 空态 | `el-empty` | 暂无消息 |

## 2. 状态清单（跨页面共用）

| 状态 | 组件 / 表现 |
| --- | --- |
| loading | `v-loading`（大屏整页 / 场地矩阵 / 抽屉选项） |
| empty | `el-empty`（大屏无数据 / 物资无结果 / 消息为空） |
| 40301 / 40302 | axios 全局拦截器统一 toast，页面不重复弹窗 |
| 40303 / 40304 | 同上，文案统一「数据不存在或你无权访问」 |
| 40901 | `ConflictAlert` 警告态 + 替代时段 + 「查看该场地课表」（仅在拿到占用明细时给） |
| 40902 | `ConflictAlert` 错误态 + 替代时段 |
| 40903 库存不足 | `ConflictAlert` + 「改为申请 N 件」一键重提 |
| 40904 名额已满 | `ConflictAlert` + 「查看其它场次」 |
| `occupiedBy` 缺失/降级 | 兜底占位文案「已被课表占用」；`hasOccupancyDetail=false` → 不给课表入口（布尔判断，非字符串比较） |
| 提交成功 | `ElMessage.success(业务单号)` + 抽屉保留结果摘要 |

## 3. 目录落位对照（vue-pure-admin）

| 本仓库路径 | 拷贝到 | 说明 |
| --- | --- | --- |
| `design/src/design/tokens.css` | `src/design/tokens.css` | 在 `src/style/index.scss` 引入 |
| `design/src/api/*` | `src/api/*` | `@/utils/http` 为 pure-admin 自带 axios 封装 |
| `design/src/components/*` | `src/components/*` | 与 pure-admin 自带组件并存 |
| `design/src/views/*` | `src/views/*` | 路由按 docs/03 菜单 `path` 注册 |
| `design/preview/index.html` | （不拷贝） | 设计验证原型，仅用于门禁3 评审 |
