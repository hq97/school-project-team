-- V14：教务域「F. 排课管理」REQ-F1 教师任课表，幂等。
--
-- 1) 表 teacher_course：教师在某学期担任某班某学科的任课关系。
--    唯一索引 (teacherId, classId, subject, termId) 防重复任课。
-- 2) 种演示任课数据：teacher_li / teacher_wang / dean_li，termId 用当前学期
--    '2025-2026-FIRST'（即字典 DICT-TERM 项 d_term_1 的 itemCode），
--    classId 复用 V13 已有演示班级 dept_class1 / dept_class2。
-- 3) RBAC 补授权：R-TEACHER 需 M0601（排课基础数据/教师任课表）只读入口——
--    否则普通教师无法查看本人任课表（GET /api/schedule/teacher-courses 挂在 M0601 下）。

-- 1. 表（幂等）
CREATE TABLE IF NOT EXISTS "teacher_course" (
  "id" TEXT PRIMARY KEY,
  "teacherId" TEXT NOT NULL,
  "classId" TEXT NOT NULL,
  "subject" TEXT NOT NULL,
  "termId" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now(),
  "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS "teacher_course_unique"
  ON "teacher_course"("teacherId", "classId", "subject", "termId");

CREATE INDEX IF NOT EXISTS "teacher_course_teacher_idx" ON "teacher_course"("teacherId");
CREATE INDEX IF NOT EXISTS "teacher_course_class_idx" ON "teacher_course"("classId");

-- 2. 演示任课（幂等）
INSERT INTO "teacher_course" ("id","teacherId","classId","subject","termId") VALUES
 ('tc_demo_1','user_teacher_li','dept_class1','数学','2025-2026-FIRST'),
 ('tc_demo_2','user_teacher_li','dept_class1','物理','2025-2026-FIRST'),
 ('tc_demo_3','user_teacher_wang','dept_class2','语文','2025-2026-FIRST'),
 ('tc_demo_4','user_dean_li','dept_class1','英语','2025-2026-FIRST')
ON CONFLICT ("id") DO NOTHING;

-- 3. R-TEACHER 补授 M06/M0601（只读查看本人任课表，写操作仍由 BTN-SCH-BASIC-EDIT 门禁）
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-TEACHER' AND m."code" IN ('M06','M0601')
ON CONFLICT DO NOTHING;
