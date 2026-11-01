-- seed_role_grants：补齐 12 个出厂角色的菜单/按钮授权（docs/03 §3.1 / §3.2 矩阵）
--
-- 门禁4 审计阻断项 4：迁移 001 只授权了 R-ADMIN 与 R-DEAN，其余 10 个角色 role_menu/role_button 全空，
-- 导致除这两个角色外所有人访问任何带 @Menu/@Button 的接口一律 40301——越权测试一条都跑不起来。
-- 本迁移按 docs/03 矩阵逐角色补齐，全部 ON CONFLICT DO NOTHING，可重复执行。
-- 数据范围 dataScope 已在 001 中按角色预置（ALL/GRADE/SELF），此处只补授权。
--
-- 【越权修正 · docs/03 决策 20（已确认落盘）】原 002 迁移 R-STUDENT 存在 4 处列位误读：
--   ① 菜单多授 M1102 班级推优申报（矩阵 R-STUDENT=—，推优系班主任 R-TEACHER 主责）
--   ② 菜单缺授 M0904 销假处理（矩阵 R-STUDENT=✅，REQ-I4 学生/班主任发起销假）
--   ③ 按钮多授 BTN-AWARD-NOMINATE 班级推优（矩阵 R-STUDENT=—）
--   ④ 按钮缺授 BTN-LEAVE-RETURN 销假（矩阵 R-STUDENT=✅）
-- 本迁移已在下方按矩阵修正：R-STUDENT 菜单补 M0904、删 M1102；按钮补 BTN-LEAVE-RETURN、删 BTN-AWARD-NOMINATE。

-- ==================== 菜单授权（role_menu） ====================

-- R-ADMIN：全量（001 已建，此处幂等兜底）
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m WHERE r."code" = 'R-ADMIN'
ON CONFLICT DO NOTHING;

-- R-PRINCIPAL 校长：全校查看视角 + 重大审批入口
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-PRINCIPAL' AND m."code" IN (
  'M01','M0101','M0102','M0103','M0104','M0105',
  'M02','M0201','M0202','M0203','M0204',
  'M03','M0301','M0302','M0303',
  'M04','M0401','M0402','M0404',
  'M07','M0701','M0702','M0703',
  'M08','M0803','M0804',
  'M09','M0903','M0905',
  'M10','M1002','M1003','M1004',
  'M11','M1103','M1104',
  'M12','M1201','M1202','M1203','M1204','M1205','M1206',
  'M13','M1301','M1303','M1304',
  'M14','M1401','M1402','M1403',
  'M15')
ON CONFLICT DO NOTHING;

-- R-VP 校领导（分管教学）：全校查看 + 教务协同 + 重大审批
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-VP' AND m."code" IN (
  'M01','M0101','M0102','M0103','M0104','M0105',
  'M02','M0201','M0202','M0203','M0204',
  'M03','M0301','M0302','M0303',
  'M04','M0401','M0402','M0404',
  'M06','M0601','M0602','M0603','M0604',
  'M07','M0701','M0702','M0703',
  'M08','M0801','M0802','M0803','M0804',
  'M09','M0903','M0905',
  'M10','M1002','M1003','M1004',
  'M11','M1103','M1104',
  'M12','M1201','M1202','M1203','M1204','M1205',
  'M13','M1301','M1303','M1304',
  'M14','M1402',
  'M15')
ON CONFLICT DO NOTHING;

-- R-DEAN 教务主任：教务域执行主责（M06/M07/M08 全开）+ 教务运营大屏（docs/03 注 6）
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-DEAN' AND m."code" IN (
  'M01','M0101','M0102','M0105',
  'M06','M0601','M0602','M0603','M0604',
  'M07','M0701','M0702','M0703',
  'M08','M0801','M0802','M0803','M0804',
  'M1201','M1204',
  'M15')
ON CONFLICT DO NOTHING;

-- R-LOGISTICS 后勤主管：采购/物资/报修全链路 + 体育器材
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-LOGISTICS' AND m."code" IN (
  'M01','M0101','M0102','M0103','M0104','M0105',
  'M02','M0201','M0202','M0203',
  'M03','M0301','M0302','M0303','M0304',
  'M04','M0401','M0402','M0403','M0404',
  'M1201',
  'M14','M1404',
  'M15')
ON CONFLICT DO NOTHING;

-- R-MORAL 德育处：请假/违纪/评优三条线
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-MORAL' AND m."code" IN (
  'M01','M0101','M0102','M0105',
  'M09','M0903','M0904','M0905',
  'M10','M1001','M1002','M1003','M1004',
  'M11','M1101','M1103','M1104',
  'M1201',
  'M15')
ON CONFLICT DO NOTHING;

-- R-PARTY 党政办：公告/公文/会议/值班/用印 + 会议室预约
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-PARTY' AND m."code" IN (
  'M01','M0101','M0102','M0105',
  'M12','M1201','M1202','M1203','M1204','M1205','M1206','M1207',
  'M15')
ON CONFLICT DO NOTHING;

-- R-GRADE 年级组长：本年级管理视图（数据范围 GRADE，服务端收窄）
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-GRADE' AND m."code" IN (
  'M01','M0101','M0102','M0103','M0104','M0105',
  'M02','M0201','M0202',
  'M03','M0301','M0302','M0303',
  'M04','M0401','M0402',
  'M07','M0701','M0702',
  'M08','M0801','M0802','M0803','M0804',
  'M09','M0903','M0905',
  'M10','M1001','M1003','M1004',
  'M11','M1103','M1104',
  'M12','M1201','M1204','M1205',
  'M15')
ON CONFLICT DO NOTHING;

-- R-SUBJECT 教研组长：本组教学 + 科教 + 体育
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-SUBJECT' AND m."code" IN (
  'M01','M0101','M0102','M0103','M0104','M0105',
  'M02','M0201','M0202',
  'M03','M0301','M0302','M0303',
  'M04','M0401','M0402',
  'M06','M0601',
  'M07','M0701',
  'M08','M0801','M0802',
  'M09','M0903',
  'M11','M1104',
  'M12','M1201','M1204',
  'M13','M1301','M1302','M1303','M1304',
  'M14','M1401','M1402','M1403','M1404','M1405',
  'M15')
ON CONFLICT DO NOTHING;

-- R-TEACHER 普通教师：本人事务视图（数据范围 SELF）
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-TEACHER' AND m."code" IN (
  'M01','M0101','M0103','M0104','M0105',
  'M03','M0301','M0302','M0303',
  'M04','M0401','M0402',
  'M07','M0701',
  'M08','M0801','M0802',
  'M09','M0901','M0902','M0904',
  'M10','M1001','M1003',
  'M11','M1104',
  'M12','M1201','M1204','M1206','M1207',
  'M13','M1301','M1302','M1303','M1304',
  'M14','M1401','M1402','M1403','M1404','M1405')
ON CONFLICT DO NOTHING;

-- R-STUDENT 学生：本人自助查看与申请（数据范围 SELF）
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-STUDENT' AND m."code" IN (
  'M01','M0101','M0105',
  'M07','M0701','M0702',
  'M09','M0901','M0902','M0904',
  'M10','M1003',
  'M11','M1104',
  'M12','M1201',
  'M13','M1301','M1303',
  'M14','M1401','M1403','M1405')
ON CONFLICT DO NOTHING;

-- R-PARENT 家长：本人子女知情与确认（关系型归属）
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-PARENT' AND m."code" IN (
  'M01','M0101','M0105',
  'M09','M0902',
  'M10','M1003',
  'M11','M1104',
  'M12','M1201',
  'M14','M1401','M1405')
ON CONFLICT DO NOTHING;

-- ==================== 按钮授权（role_button） ====================

-- R-ADMIN：全量（001 已建，幂等兜底）
INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b WHERE r."code" = 'R-ADMIN'
ON CONFLICT DO NOTHING;

-- R-PRINCIPAL
INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-PRINCIPAL' AND b."code" IN (
  'BTN-PUR-NEW','BTN-PUR-EXPORT',
  'BTN-APR-APPROVE','BTN-APR-REJECT',
  'BTN-SCH-VIEW-ALL','BTN-SCH-CHANGE-APPROVE','BTN-SCH-CHANGE-REJECT','BTN-SCH-CHANGE-EXPORT',
  'BTN-LEAVE-APPROVE','BTN-LEAVE-REJECT','BTN-LEAVE-EXPORT',
  'BTN-DISC-HANDLE','BTN-DISC-EXPORT',
  'BTN-AWARD-APPROVE','BTN-AWARD-REJECT',
  'BTN-PARTY-DOC-FLOW','BTN-PARTY-ROOM-BOOK',
  'BTN-SCI-COMP-REVIEW','BTN-SCI-PROJ-REVIEW')
ON CONFLICT DO NOTHING;

-- R-VP
INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-VP' AND b."code" IN (
  'BTN-PUR-NEW','BTN-PUR-EDIT','BTN-PUR-EXPORT',
  'BTN-APR-APPROVE','BTN-APR-REJECT',
  'BTN-SCH-BASIC-EDIT','BTN-SCH-ARRANGE','BTN-SCH-AUTO','BTN-SCH-PUBLISH','BTN-SCH-VIEW-ALL',
  'BTN-SCH-CHANGE-APPLY','BTN-SCH-CHANGE-APPROVE','BTN-SCH-CHANGE-REJECT','BTN-SCH-CHANGE-EXPORT',
  'BTN-LEAVE-APPROVE','BTN-LEAVE-REJECT','BTN-LEAVE-EXPORT',
  'BTN-DISC-HANDLE','BTN-DISC-EXPORT',
  'BTN-AWARD-APPROVE','BTN-AWARD-REJECT',
  'BTN-PARTY-DOC-FLOW','BTN-PARTY-ROOM-BOOK',
  'BTN-SCI-COMP-REVIEW','BTN-SCI-PROJ-REVIEW')
ON CONFLICT DO NOTHING;

-- R-DEAN
INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-DEAN' AND b."code" IN (
  'BTN-APR-APPROVE','BTN-APR-REJECT',
  'BTN-SCH-BASIC-EDIT','BTN-SCH-ARRANGE','BTN-SCH-AUTO','BTN-SCH-PUBLISH','BTN-SCH-VIEW-ALL',
  'BTN-SCH-CHANGE-APPLY','BTN-SCH-CHANGE-APPROVE','BTN-SCH-CHANGE-REJECT','BTN-SCH-CHANGE-EXPORT',
  'BTN-PARTY-ROOM-BOOK')
ON CONFLICT DO NOTHING;

-- R-LOGISTICS
INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-LOGISTICS' AND b."code" IN (
  'BTN-PUR-NEW','BTN-PUR-EDIT','BTN-PUR-EXPORT',
  'BTN-APR-APPROVE','BTN-APR-REJECT',
  'BTN-MAT-APPLY','BTN-MAT-APPROVE','BTN-MAT-REJECT','BTN-MAT-STOCK',
  'BTN-REP-SUBMIT','BTN-REP-UPLOAD','BTN-REP-ASSIGN','BTN-REP-STATUS','BTN-REP-CLOSE',
  'BTN-SPORT-EQUIP-BORROW')
ON CONFLICT DO NOTHING;

-- R-MORAL
INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-MORAL' AND b."code" IN (
  'BTN-APR-APPROVE','BTN-APR-REJECT',
  'BTN-LEAVE-APPROVE','BTN-LEAVE-REJECT','BTN-LEAVE-RETURN','BTN-LEAVE-EXPORT',
  'BTN-DISC-RECORD','BTN-DISC-HANDLE','BTN-DISC-FORCE-CLOSE','BTN-DISC-EXPORT',
  'BTN-AWARD-STD-ADD','BTN-AWARD-STD-EDIT','BTN-AWARD-STD-DEL',
  'BTN-AWARD-APPROVE','BTN-AWARD-REJECT','BTN-AWARD-PUBLISH')
ON CONFLICT DO NOTHING;

-- R-PARTY
INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-PARTY' AND b."code" IN (
  'BTN-APR-APPROVE','BTN-APR-REJECT',
  'BTN-PARTY-NOTICE-PUB','BTN-PARTY-NOTICE-RETRACT',
  'BTN-PARTY-DOC-REG','BTN-PARTY-DOC-FLOW',
  'BTN-PARTY-ROOM-BOOK','BTN-PARTY-MEETING-NOTICE','BTN-PARTY-MEETING-ARCHIVE',
  'BTN-PARTY-DUTY-EDIT','BTN-PARTY-DUTY-SWAP','BTN-PARTY-SEAL-APPLY')
ON CONFLICT DO NOTHING;

-- R-GRADE
INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-GRADE' AND b."code" IN (
  'BTN-PUR-NEW',
  'BTN-APR-APPROVE','BTN-APR-REJECT',
  'BTN-MAT-APPLY','BTN-REP-SUBMIT','BTN-REP-UPLOAD',
  'BTN-SCH-CHANGE-APPLY','BTN-SCH-CHANGE-APPROVE','BTN-SCH-CHANGE-REJECT','BTN-SCH-CHANGE-EXPORT',
  'BTN-LEAVE-APPROVE','BTN-LEAVE-REJECT','BTN-LEAVE-EXPORT',
  'BTN-DISC-RECORD','BTN-DISC-EXPORT',
  'BTN-AWARD-APPROVE','BTN-AWARD-REJECT',
  'BTN-PARTY-ROOM-BOOK')
ON CONFLICT DO NOTHING;

-- R-SUBJECT
INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-SUBJECT' AND b."code" IN (
  'BTN-PUR-NEW',
  'BTN-APR-APPROVE','BTN-APR-REJECT',
  'BTN-MAT-APPLY','BTN-REP-SUBMIT','BTN-REP-UPLOAD',
  'BTN-SCH-BASIC-EDIT',
  'BTN-SCH-CHANGE-APPLY',
  'BTN-LEAVE-APPROVE','BTN-LEAVE-REJECT',
  'BTN-PARTY-ROOM-BOOK',
  'BTN-SCI-RES-UPLOAD','BTN-SCI-RES-DOWNLOAD','BTN-SCI-LAB-BOOK',
  'BTN-SCI-COMP-PUBLISH','BTN-SCI-COMP-APPLY','BTN-SCI-COMP-REVIEW',
  'BTN-SCI-PROJ-APPLY','BTN-SCI-PROJ-REVIEW',
  'BTN-SPORT-FIT-INPUT','BTN-SPORT-EVENT-ARRANGE','BTN-SPORT-EVENT-APPLY',
  'BTN-SPORT-EQUIP-BORROW','BTN-SPORT-TEAM-MANAGE')
ON CONFLICT DO NOTHING;

-- R-TEACHER
INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-TEACHER' AND b."code" IN (
  'BTN-MAT-APPLY','BTN-REP-SUBMIT','BTN-REP-UPLOAD',
  'BTN-SCH-CHANGE-APPLY',
  'BTN-LEAVE-APPLY','BTN-LEAVE-RETURN',
  'BTN-DISC-RECORD',
  'BTN-AWARD-NOMINATE',
  'BTN-PARTY-ROOM-BOOK','BTN-PARTY-DUTY-SWAP','BTN-PARTY-SEAL-APPLY',
  'BTN-SCI-RES-UPLOAD','BTN-SCI-RES-DOWNLOAD','BTN-SCI-LAB-BOOK',
  'BTN-SCI-COMP-APPLY','BTN-SCI-PROJ-APPLY',
  'BTN-SPORT-FIT-INPUT','BTN-SPORT-EVENT-ARRANGE','BTN-SPORT-EVENT-APPLY',
  'BTN-SPORT-EQUIP-BORROW','BTN-SPORT-TEAM-MANAGE')
ON CONFLICT DO NOTHING;

-- R-STUDENT
INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-STUDENT' AND b."code" IN (
  'BTN-LEAVE-APPLY','BTN-LEAVE-RETURN',
  'BTN-SCI-RES-DOWNLOAD','BTN-SCI-COMP-APPLY',
  'BTN-SPORT-EVENT-APPLY')
ON CONFLICT DO NOTHING;

-- R-PARENT
INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-PARENT' AND b."code" IN (
  'BTN-LEAVE-CONFIRM',
  'BTN-DISC-PARENT-CONFIRM')
ON CONFLICT DO NOTHING;

-- ==================== C9 ② 部分唯一索引再断言 ====================
-- 该索引由 001 手写 SQL 创建、schema.prisma 故意不声明（Prisma 无法表达 partial index）。
-- 此处幂等重建，保证任何环境 deploy 后并发保护都在（口径固定 PENDING/EFFECTIVE，禁止改成全表唯一）。
CREATE UNIQUE INDEX IF NOT EXISTS "uniq_effective_slot"
  ON "space_occupancy" ("spaceId","termId","week","dayOfWeek","period")
  WHERE "status" IN ('PENDING','EFFECTIVE');
