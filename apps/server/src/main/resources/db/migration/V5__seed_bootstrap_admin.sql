-- V5：引导管理员账号（登录冒烟 + 系统管理入口必需）
-- 说明：V1-V3 只播种了角色/菜单/按钮/字典，没有 sys_user，登录接口无账号可用。
--       此处播种唯一引导管理员（R-ADMIN），密码为初始密码 Admin@123，上线前必须修改（docs/03 C8）。
-- 幂等：ON CONFLICT DO NOTHING。

INSERT INTO "sys_user" ("id","username","passwordHash","realName","accountStatus","createdAt") VALUES
 ('user_admin','admin','$2a$10$wxS84.qjNXApHz7t3KpW8OXRp2rhBq/TZMRqJVJXLmxwVkbaPz45K','系统管理员','ACTIVE',now())
ON CONFLICT ("id") DO NOTHING;

-- 授予 R-ADMIN 角色（role_R_ADMIN 由 V2 播种）
INSERT INTO "user_role" ("userId","roleId") VALUES ('user_admin','role_R_ADMIN')
ON CONFLICT ("userId","roleId") DO NOTHING;

-- 挂到学校根部门（dept_school 由 V2 播种）
INSERT INTO "user_dept" ("userId","deptId") VALUES ('user_admin','dept_school')
ON CONFLICT ("userId","deptId") DO NOTHING;
