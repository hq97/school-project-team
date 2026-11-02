-- V12：新增「代完成工单」按钮，授权 R-ADMIN + R-LOGISTICS（工单页 M0403 代完成）
-- 事实源：team-lead 报修代完成 spec

INSERT INTO "sys_button" ("id","code","name","menuId") VALUES
 ('btn_REP_COMPLETE','BTN-REP-COMPLETE','代完成工单','m0403')
ON CONFLICT ("code") DO NOTHING;

INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" IN ('R-ADMIN','R-LOGISTICS') AND b."code" = 'BTN-REP-COMPLETE'
ON CONFLICT DO NOTHING;
