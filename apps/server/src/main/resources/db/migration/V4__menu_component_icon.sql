-- V4：sys_menu 增加前端动态路由所需的 component / icon 两列（可空），并回填既有菜单
-- 事实源：docs/03 菜单树 + team-lead 门禁4 第三步契约（vue-pure-admin 动态路由）
--
-- 约定：
--   "component"  = 前端组件路径，相对 src/views/ 且不含扩展名（叶子菜单才有值）
--                  规则：去 leading '/' 的 path + '/index'（如 /system/users -> system/users/index）
--   "icon"       = Element Plus 图标名，统一 ep: 前缀（目录与叶子都回填）
--
-- 顶层目录（parentId IS NULL）的 component 不入库——路由组装时填字面量 "Layout"（见 MenuTreeBuilder）

-- 1. 加列（幂等）
ALTER TABLE "sys_menu" ADD COLUMN IF NOT EXISTS "component" TEXT;
ALTER TABLE "sys_menu" ADD COLUMN IF NOT EXISTS "icon" TEXT;

-- 2. 回填叶子 component（path 非空才回填）
UPDATE "sys_menu"
SET "component" = trim(leading '/' from "path") || '/index'
WHERE "path" IS NOT NULL AND "path" <> '';

-- 3. 回填 icon（目录 + 叶子，统一 ep: 前缀）
UPDATE "sys_menu" AS m
SET "icon" = v."icon"
FROM (VALUES
  ('M01','ep:home-filled'),
  ('M0101','ep:user'),
  ('M0102','ep:checked'),
  ('M0103','ep:document'),
  ('M0104','ep:tools'),
  ('M0105','ep:bell'),
  ('M02','ep:trend-charts'),
  ('M0201','ep:data-line'),
  ('M0202','ep:folder'),
  ('M0203','ep:money'),
  ('M0204','ep:trend-charts'),
  ('M03','ep:box'),
  ('M0301','ep:document-add'),
  ('M0302','ep:document'),
  ('M0303','ep:box'),
  ('M0304','ep:checked'),
  ('M04','ep:tools'),
  ('M0401','ep:edit-pen'),
  ('M0402','ep:tools'),
  ('M0403','ep:operation'),
  ('M0404','ep:data-analysis'),
  ('M05','ep:setting'),
  ('M0501','ep:user'),
  ('M0502','ep:avatar'),
  ('M0503','ep:menu'),
  ('M0504','ep:office-building'),
  ('M0505','ep:collection'),
  ('M0506','ep:message'),
  ('M0507','ep:connection'),
  ('M06','ep:calendar'),
  ('M0601','ep:setting'),
  ('M0602','ep:calendar'),
  ('M0603','ep:magic-stick'),
  ('M0604','ep:upload'),
  ('M07','ep:tickets'),
  ('M0701','ep:user'),
  ('M0702','ep:school'),
  ('M0703','ep:grid'),
  ('M08','ep:switch'),
  ('M0801','ep:edit-pen'),
  ('M0802','ep:document'),
  ('M0803','ep:checked'),
  ('M0804','ep:data-analysis'),
  ('M09','ep:document-checked'),
  ('M0901','ep:edit-pen'),
  ('M0902','ep:document'),
  ('M0903','ep:checked'),
  ('M0904','ep:refresh'),
  ('M0905','ep:data-analysis'),
  ('M10','ep:warning'),
  ('M1001','ep:edit-pen'),
  ('M1002','ep:operation'),
  ('M1003','ep:search'),
  ('M1004','ep:data-analysis'),
  ('M11','ep:medal'),
  ('M1101','ep:setting'),
  ('M1102','ep:medal'),
  ('M1103','ep:checked'),
  ('M1104','ep:folder'),
  ('M12','ep:office-building'),
  ('M1201','ep:bell'),
  ('M1202','ep:document'),
  ('M1203','ep:connection'),
  ('M1204','ep:office-building'),
  ('M1205','ep:chat-line-round'),
  ('M1206','ep:calendar'),
  ('M1207','ep:stamp'),
  ('M13','ep:microscope'),
  ('M1301','ep:folder'),
  ('M1302','ep:microscope'),
  ('M1303','ep:trophy'),
  ('M1304','ep:reading'),
  ('M14','ep:basketball'),
  ('M1401','ep:data-line'),
  ('M1402','ep:data-analysis'),
  ('M1403','ep:trophy'),
  ('M1404','ep:basketball'),
  ('M1405','ep:user-filled'),
  ('M15','ep:monitor')
) AS v("code","icon")
WHERE m."code" = v."code";
