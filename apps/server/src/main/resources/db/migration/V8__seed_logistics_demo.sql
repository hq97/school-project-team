-- V8：后勤业务演示种子数据（asset / space / 示例部门用户），幂等（ON CONFLICT DO NOTHING）
-- 用途：采购/领用/报修/资产 四个业务模块的 E2E 自测 + 三级数据范围（SELF/GRADE/ALL）验证。
-- 密码：所有演示账号统一 Admin@123（bcrypt 哈希复用 V5 引导管理员的哈希，登录验证口径一致）。

-- ============ 1. 组织树：dept_school 下建 2 个 GRADE + 2 个 CLASS ============
INSERT INTO "sys_dept" ("id","name","deptType","parentId","path","sort") VALUES
 ('dept_grade1','七年级','GRADE','dept_school','/dept_school/dept_grade1',1),
 ('dept_grade2','八年级','GRADE','dept_school','/dept_school/dept_grade2',2),
 ('dept_class1','七年级1班','CLASS','dept_grade1','/dept_school/dept_grade1/dept_class1',1),
 ('dept_class2','八年级1班','CLASS','dept_grade2','/dept_school/dept_grade2/dept_class2',1)
ON CONFLICT ("id") DO NOTHING;

-- ============ 2. 资产台账：后勤物资（MATERIAL，borrowDomain=LOGISTICS） ============
INSERT INTO "asset" ("id","name","assetType","quantity","availableQty","status","location","borrowDomain") VALUES
 ('asset_a4','A4复印纸（包）','MATERIAL',100,100,'IN_STOCK','仓库A-01','LOGISTICS'),
 ('asset_pen','白板笔（盒）','MATERIAL',50,50,'IN_STOCK','仓库A-02','LOGISTICS'),
 ('asset_toner','打印硒鼓（个）','MATERIAL',20,20,'IN_STOCK','仓库A-03','LOGISTICS'),
 ('asset_glove','实验手套（盒）','MATERIAL',30,30,'IN_STOCK','仓库A-04','LOGISTICS'),
 ('asset_marker','记号笔（盒）','MATERIAL',40,40,'IN_STOCK','仓库A-05','LOGISTICS')
ON CONFLICT ("id") DO NOTHING;

-- ============ 3. 统一场地：教室/实验室/会议室 ============
INSERT INTO "space" ("id","spaceName","spaceType","capacity","deptNodeId","status","openScope") VALUES
 ('space_c101','101教室','CLASSROOM',45,NULL,'AVAILABLE','ALL'),
 ('space_c102','102教室','CLASSROOM',45,NULL,'AVAILABLE','ALL'),
 ('space_lab1','化学实验室','LAB',40,NULL,'AVAILABLE','ALL'),
 ('space_meet1','一楼会议室','MEETING_ROOM',20,NULL,'AVAILABLE','ALL'),
 ('space_meet2','三楼会议室','MEETING_ROOM',30,NULL,'AVAILABLE','ALL')
ON CONFLICT ("id") DO NOTHING;

-- ============ 4. 演示用户（密码统一 Admin@123） ============
INSERT INTO "sys_user" ("id","username","passwordHash","realName","accountStatus") VALUES
 ('user_teacher_li','teacher_li','$2a$10$wxS84.qjNXApHz7t3KpW8OXRp2rhBq/TZMRqJVJXLmxwVkbaPz45K','李老师','ACTIVE'),
 ('user_teacher_wang','teacher_wang','$2a$10$wxS84.qjNXApHz7t3KpW8OXRp2rhBq/TZMRqJVJXLmxwVkbaPz45K','王老师','ACTIVE'),
 ('user_logistics_wang','logistics_wang','$2a$10$wxS84.qjNXApHz7t3KpW8OXRp2rhBq/TZMRqJVJXLmxwVkbaPz45K','王后勤','ACTIVE'),
 ('user_principal_zhao','principal_zhao','$2a$10$wxS84.qjNXApHz7t3KpW8OXRp2rhBq/TZMRqJVJXLmxwVkbaPz45K','赵校长','ACTIVE'),
 ('user_grade_zhang','grade_zhang','$2a$10$wxS84.qjNXApHz7t3KpW8OXRp2rhBq/TZMRqJVJXLmxwVkbaPz45K','张组长','ACTIVE')
ON CONFLICT ("id") DO NOTHING;

-- ============ 5. 角色绑定 ============
INSERT INTO "user_role" ("userId","roleId")
SELECT u."id", r."id" FROM "sys_user" u JOIN "sys_role" r ON r."code" = 'R-TEACHER'
WHERE u."username" IN ('teacher_li','teacher_wang')
ON CONFLICT DO NOTHING;

INSERT INTO "user_role" ("userId","roleId")
SELECT u."id", r."id" FROM "sys_user" u JOIN "sys_role" r ON r."code" = 'R-LOGISTICS'
WHERE u."username" = 'logistics_wang'
ON CONFLICT DO NOTHING;

INSERT INTO "user_role" ("userId","roleId")
SELECT u."id", r."id" FROM "sys_user" u JOIN "sys_role" r ON r."code" = 'R-PRINCIPAL'
WHERE u."username" = 'principal_zhao'
ON CONFLICT DO NOTHING;

INSERT INTO "user_role" ("userId","roleId")
SELECT u."id", r."id" FROM "sys_user" u JOIN "sys_role" r ON r."code" = 'R-GRADE'
WHERE u."username" = 'grade_zhang'
ON CONFLICT DO NOTHING;

-- ============ 6. 部门挂靠（数据范围载体） ============
INSERT INTO "user_dept" ("userId","deptId")
SELECT u."id", 'dept_class1' FROM "sys_user" u WHERE u."username" = 'teacher_li'
ON CONFLICT DO NOTHING;

INSERT INTO "user_dept" ("userId","deptId")
SELECT u."id", 'dept_class2' FROM "sys_user" u WHERE u."username" = 'teacher_wang'
ON CONFLICT DO NOTHING;

INSERT INTO "user_dept" ("userId","deptId")
SELECT u."id", 'dept_school' FROM "sys_user" u WHERE u."username" IN ('logistics_wang','principal_zhao')
ON CONFLICT DO NOTHING;

INSERT INTO "user_dept" ("userId","deptId")
SELECT u."id", 'dept_grade1' FROM "sys_user" u WHERE u."username" = 'grade_zhang'
ON CONFLICT DO NOTHING;
