# school-project-team

中学校务数字化平台 —— 覆盖教务 / 德育 / 后勤 / 党政办 / 科教 / 体育六大业务域，以及数据大屏与消息中心。

## 技术栈（ADR v3.0）

- 前端：vue-pure-admin + Vue3 + TypeScript + ECharts 5
- 后端：Java 21 + Spring Boot 3.x + MyBatis-Plus + Flyway
- 数据库：PostgreSQL 16 + Redis 7
- 排课引擎：Timefold Solver Community
- 部署：Docker Compose 单机四容器（nginx / openjdk / postgres / redis）

## 目录结构

- `docs/`：需求 PRD、架构决策 ADR、RBAC 配置基线、数据权限说明书等
- `design/`：UI 设计约束说明 + 可运行界面原型
- `apps/server/`：后端服务

## 项目进展

需求 → 架构（ADR v3.0）→ 设计 三阶段已通过门禁；开发阶段（门禁 4 权限骨架）进行中。
