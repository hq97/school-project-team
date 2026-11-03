-- V13：教务域调课/代课支持（schedule_change 扩展 + 修 JSONB + 种演示数据），幂等。
--
-- 1) JSONB 禁用：schedule_change.targetSlot 原为 JSONB，MyBatis setString 绑 varchar 无法隐式转 jsonb
--    （历史 V7 已把 flow_instance.templateSnap 从 JSONB 改 TEXT 的教训）。改为 TEXT，应用层存 JSON 字符串 + Jackson 解析。
-- 2) 扩展 type / entryId / substituteTeacherId，供 CHANGE / SUBSTITUTE / LEAVE_SUBSTITUTE 三类闭环。
-- 3) 种演示数据：教务主任账号（审批）、已发布课表版本 + 演示教师课表（供调课联调），全部引用真实存在的 id。

-- 1. targetSlot JSONB -> TEXT（幂等：仅当仍为 jsonb 时转换）
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'schedule_change' AND column_name = 'targetSlot' AND data_type = 'jsonb'
  ) THEN
    ALTER TABLE "schedule_change" ALTER COLUMN "targetSlot" TYPE TEXT USING "targetSlot"::text;
  END IF;
END $$;

-- 2. 扩展列 + 值域约束（幂等）
ALTER TABLE "schedule_change" ADD COLUMN IF NOT EXISTS "type" TEXT NOT NULL DEFAULT 'CHANGE';
ALTER TABLE "schedule_change" ADD COLUMN IF NOT EXISTS "entryId" TEXT;
ALTER TABLE "schedule_change" ADD COLUMN IF NOT EXISTS "substituteTeacherId" TEXT;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'schedule_change_type_check') THEN
    ALTER TABLE "schedule_change"
      ADD CONSTRAINT "schedule_change_type_check" CHECK ("type" IN ('CHANGE','SUBSTITUTE','LEAVE_SUBSTITUTE'));
  END IF;
END $$;

-- 3. 演示教务主任账号（供 ft_schedule_change 节点 R-DEAN 审批联调；密码 Admin@123，与 V8 复用同一 bcrypt 哈希）
INSERT INTO "sys_user" ("id","username","passwordHash","realName","accountStatus") VALUES
 ('user_dean_li','dean_li','$2a$10$wxS84.qjNXApHz7t3KpW8OXRp2rhBq/TZMRqJVJXLmxwVkbaPz45K','李教务','ACTIVE')
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "user_role" ("userId","roleId")
SELECT u."id", r."id" FROM "sys_user" u JOIN "sys_role" r ON r."code" = 'R-DEAN'
WHERE u."username" = 'dean_li'
ON CONFLICT DO NOTHING;

INSERT INTO "user_dept" ("userId","deptId")
SELECT u."id", 'dept_school' FROM "sys_user" u WHERE u."username" = 'dean_li'
ON CONFLICT DO NOTHING;

-- 4. 已发布课表版本（当前学期 2025-2026-FIRST，第 1 周）+ 演示课表
--    teacher_li(user_teacher_li) 教 dept_class1 数学，场地 space_c101/c102/lab1；
--    另种一条 teacher_wang(user_teacher_wang) 的课，供「越权调他人课被拒」联调。
INSERT INTO "schedule_version" ("id","termId","effectiveWeek","version","status","publishedAt") VALUES
 ('sv_demo_1','2025-2026-FIRST',1,1,'PUBLISHED',now())
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "timetable_entry" ("id","versionId","spaceId","teacherId","classId","subject","termId","week","dayOfWeek","period","substituteTeacherId") VALUES
 ('te_demo_1','sv_demo_1','space_c101','user_teacher_li','dept_class1','数学','2025-2026-FIRST',1,1,1,NULL),
 ('te_demo_2','sv_demo_1','space_c102','user_teacher_li','dept_class1','数学','2025-2026-FIRST',1,1,2,NULL),
 ('te_demo_3','sv_demo_1','space_lab1','user_teacher_li','dept_class1','数学','2025-2026-FIRST',1,2,1,NULL),
 ('te_demo_4','sv_demo_1','space_c102','user_teacher_wang','dept_class2','语文','2025-2026-FIRST',1,1,3,NULL)
ON CONFLICT ("id") DO NOTHING;
