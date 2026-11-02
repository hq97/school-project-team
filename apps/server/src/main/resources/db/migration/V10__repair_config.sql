-- V10：报修配置（故障类型 / 报修场地 / 审批流程）+ 工单故障描述字段
-- 事实源：team-lead 报修模块实现任务清单
-- 约束：报修配置独立于 R-ADMIN 专属的字典管理(M0505)与流程模板管理(M0507)，
--       由后勤角色 R-LOGISTICS 通过独立菜单 M0405 + 按钮 BTN-REP-CONFIG 维护。
--       只操作报修相关数据（sys_dict 的 DICT-FAULT-TYPE 项 / space / FT-REPAIR 流程节点）。
-- 幂等：IF NOT EXISTS / ON CONFLICT DO NOTHING，可重复执行。

-- ============ 1. 报修工单补「故障描述」非必填字段 ============
ALTER TABLE "repair_ticket" ADD COLUMN IF NOT EXISTS "description" TEXT;

-- ============ 2. 报修配置菜单（M04 报修预约目录下，sort=45） ============
-- component 遵循 V4 约定：去 leading '/' 的 path + '/index'（叶子菜单路由组件）
INSERT INTO "sys_menu" ("id","code","name","path","parentId","sort","listLike","component","icon") VALUES
 ('m0405','M0405','报修配置','/repair/config','m04',45,false,'repair/config/index','ep:setting')
ON CONFLICT ("code") DO UPDATE SET
  "name"=EXCLUDED."name",
  "path"=EXCLUDED."path",
  "parentId"=EXCLUDED."parentId",
  "sort"=EXCLUDED."sort",
  "listLike"=EXCLUDED."listLike",
  "component"=EXCLUDED."component",
  "icon"=EXCLUDED."icon";

-- ============ 3. 报修配置按钮（menuId 指向 m0405） ============
INSERT INTO "sys_button" ("id","code","name","menuId") VALUES
 ('btn_REP_CONFIG','BTN-REP-CONFIG','报修配置','m0405')
ON CONFLICT ("code") DO UPDATE SET "name"=EXCLUDED."name","menuId"=EXCLUDED."menuId";

-- ============ 4. R-LOGISTICS 授权：菜单 M0405 + 按钮 BTN-REP-CONFIG ============
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-LOGISTICS' AND m."code" = 'M0405'
ON CONFLICT DO NOTHING;

INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-LOGISTICS' AND b."code" = 'BTN-REP-CONFIG'
ON CONFLICT DO NOTHING;
