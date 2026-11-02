-- V11：报修接入审批流引擎（FlowEngine），移除硬编码状态机 + 冗余流程配置接口
-- 事实源：team-lead 报修 FlowEngine 后端重构 spec
-- 目标：①提交即启动 flow 实例 ②「分派维修人」= FT-REPAIR 节点1（R-LOGISTICS）
--       ③新增「维修人员」R-REPAIRMAN = 节点2（维修处理）
--       ④删除硬编码状态机 + 报修独立流程配置接口（配置入口回归 M0507 流程模板管理）
-- 幂等：ON CONFLICT DO UPDATE / DO NOTHING + DELETE 后重建，可重复执行。

-- ============ 1. 新增「维修人员」角色 R-REPAIRMAN（ALL / 内置） ============
INSERT INTO "sys_role" ("id","code","name","dataScope","scopeComplete","builtin") VALUES
 ('role_R_REPAIRMAN','R-REPAIRMAN','维修人员','ALL',true,true)
ON CONFLICT ("code") DO UPDATE SET
  "name"=EXCLUDED."name",
  "dataScope"=EXCLUDED."dataScope",
  "scopeComplete"=EXCLUDED."scopeComplete",
  "builtin"=EXCLUDED."builtin";

-- ============ 2. 重建 FT-REPAIR 为两级线性节点 ============
-- 节点1「分派维修人」R-LOGISTICS / 24h；节点2「维修处理」R-REPAIRMAN / 48h。
DELETE FROM "flow_node" WHERE "templateId" = 'ft_repair';

INSERT INTO "flow_node" ("id","templateId","seq","name","approveRole","timeoutHours","nodeType") VALUES
 ('ftn_r_1','ft_repair',1,'分派维修人','R-LOGISTICS',24,'LINEAR'),
 ('ftn_r_2','ft_repair',2,'维修处理','R-REPAIRMAN',48,'LINEAR')
ON CONFLICT ("templateId","seq") DO UPDATE SET
  "name"=EXCLUDED."name",
  "approveRole"=EXCLUDED."approveRole",
  "timeoutHours"=EXCLUDED."timeoutHours",
  "nodeType"=EXCLUDED."nodeType";

-- ============ 3. 演示维修人员用户（密码统一 Admin@123，哈希复用 V5/V8） ============
INSERT INTO "sys_user" ("id","username","passwordHash","realName","accountStatus") VALUES
 ('user_repairman','repairman_li','$2a$10$wxS84.qjNXApHz7t3KpW8OXRp2rhBq/TZMRqJVJXLmxwVkbaPz45K','李维修','ACTIVE')
ON CONFLICT ("id") DO NOTHING;

-- ============ 4. 绑定 R-REPAIRMAN 角色 ============
INSERT INTO "user_role" ("userId","roleId")
SELECT u."id", r."id" FROM "sys_user" u JOIN "sys_role" r ON r."code" = 'R-REPAIRMAN'
WHERE u."username" = 'repairman_li'
ON CONFLICT DO NOTHING;

-- ============ 5. 部门挂靠（数据范围载体：挂 dept_school） ============
INSERT INTO "user_dept" ("userId","deptId")
SELECT u."id", 'dept_school' FROM "sys_user" u WHERE u."username" = 'repairman_li'
ON CONFLICT DO NOTHING;

-- ============ 6. R-REPAIRMAN 授权：待办审批菜单 M0102 + 审批通过/驳回按钮 ============
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-REPAIRMAN' AND m."code" = 'M0102'
ON CONFLICT DO NOTHING;

INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-REPAIRMAN' AND b."code" IN ('BTN-APR-APPROVE','BTN-APR-REJECT')
ON CONFLICT DO NOTHING;
