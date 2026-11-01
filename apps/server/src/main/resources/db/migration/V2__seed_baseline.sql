-- seed_baseline：出厂基线种子数据（可重复执行，ON CONFLICT 幂等）
-- 事实源：docs/03（12 预置角色 / M01–M15 菜单树 / 按钮权限点 / 10 个字典编码）、docs/07（流程缺省绑定）
-- 说明：后续 schema 或种子变更一律走新的编号迁移，禁止手工改库（ADR §1.4）

-- ============ 1. 部门树根节点（组织型归属载体） ============
INSERT INTO "sys_dept" ("id","name","deptType","parentId","path","sort")
VALUES ('dept_school','示例中学','SCHOOL',NULL,'/dept_school',0)
ON CONFLICT ("id") DO NOTHING;

-- ============ 2. 12 个出厂预置角色（docs/03 §二，含 R-DEAN） ============
INSERT INTO "sys_role" ("id","code","name","dataScope","scopeComplete","builtin") VALUES
 ('role_R_ADMIN','R-ADMIN','系统管理员','ALL',true,true),
 ('role_R_PRINCIPAL','R-PRINCIPAL','校长','ALL',true,true),
 ('role_R_VP','R-VP','校领导（副校长）','ALL',true,true),
 ('role_R_DEAN','R-DEAN','教务主任','ALL',true,true),
 ('role_R_LOGISTICS','R-LOGISTICS','后勤主管','ALL',true,true),
 ('role_R_MORAL','R-MORAL','德育处','ALL',true,true),
 ('role_R_PARTY','R-PARTY','党政办','ALL',true,true),
 ('role_R_GRADE','R-GRADE','年级组长','GRADE',true,true),
 ('role_R_SUBJECT','R-SUBJECT','教研组长','GRADE',true,true),
 ('role_R_TEACHER','R-TEACHER','普通教师','SELF',true,true),
 ('role_R_STUDENT','R-STUDENT','学生','SELF',true,true),
 ('role_R_PARENT','R-PARENT','家长','SELF',true,true)
ON CONFLICT ("code") DO UPDATE SET "name"=EXCLUDED."name","dataScope"=EXCLUDED."dataScope","scopeComplete"=true;

-- 预置编码不可删除（AC-RBAC-3）：删除拦截由 RoleService 校验器 + 触发器兜底
CREATE OR REPLACE FUNCTION trg_role_builtin_no_delete() RETURNS trigger AS $$
BEGIN
  IF OLD."builtin" THEN
    RAISE EXCEPTION '预置角色编码 R-* 不允许删除（AC-RBAC-3）：%', OLD."code";
  END IF;
  RETURN OLD;
END; $$ LANGUAGE plpgsql;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'role_builtin_no_delete') THEN
    CREATE TRIGGER role_builtin_no_delete BEFORE DELETE ON "sys_role"
      FOR EACH ROW EXECUTE FUNCTION trg_role_builtin_no_delete();
  END IF;
END $$;

-- ============ 3. M01–M15 菜单树（docs/03 §一） ============
INSERT INTO "sys_menu" ("id","code","name","path","parentId","sort","listLike") VALUES
 ('m01','M01','工作台',NULL,NULL,10,false),
 ('m0101','M0101','我的事务','/dashboard/my-affairs','m01',11,false),
 ('m0102','M0102','待办审批','/dashboard/approval','m01',12,true),
 ('m0103','M0103','我的申请','/dashboard/my-applications','m01',13,true),
 ('m0104','M0104','我的报修','/dashboard/my-repairs','m01',14,true),
 ('m0105','M0105','消息中心','/dashboard/messages','m01',15,true),
 ('m02','M02','采购看板',NULL,NULL,20,false),
 ('m0201','M0201','进度总览','/purchase/overview','m02',21,true),
 ('m0202','M0202','项目管理','/purchase/projects','m02',22,true),
 ('m0203','M0203','预算监控','/purchase/budget','m02',23,true),
 ('m0204','M0204','趋势报表','/purchase/trends','m02',24,true),
 ('m03','M03','物资领用',NULL,NULL,30,false),
 ('m0301','M0301','领用申请','/material/apply','m03',31,false),
 ('m0302','M0302','我的申请','/material/my-applications','m03',32,true),
 ('m0303','M0303','库存查询','/material/inventory','m03',33,true),
 ('m0304','M0304','领用审批','/material/approval','m03',34,true),
 ('m04','M04','报修预约',NULL,NULL,40,false),
 ('m0401','M0401','报修提交','/repair/submit','m04',41,false),
 ('m0402','M0402','我的报修','/repair/my-repairs','m04',42,true),
 ('m0403','M0403','工单处理','/repair/tickets','m04',43,true),
 ('m0404','M0404','响应统计','/repair/stats','m04',44,true),
 ('m05','M05','系统管理',NULL,NULL,50,false),
 ('m0501','M0501','用户管理','/system/users','m05',51,true),
 ('m0502','M0502','角色管理','/system/roles','m05',52,true),
 ('m0503','M0503','菜单管理','/system/menus','m05',53,true),
 ('m0504','M0504','部门管理','/system/depts','m05',54,true),
 ('m0505','M0505','数据字典管理','/system/dicts','m05',55,true),
 ('m0506','M0506','消息模板/触发规则','/system/message-templates','m05',56,true),
 ('m0507','M0507','流程模板管理','/system/flow-templates','m05',57,true),
 ('m06','M06','排课管理',NULL,NULL,60,false),
 ('m0601','M0601','排课基础数据','/schedule/basic-data','m06',61,true),
 ('m0602','M0602','课表编排','/schedule/arrange','m06',62,true),
 ('m0603','M0603','自动排课','/schedule/auto','m06',63,false),
 ('m0604','M0604','课表发布与版本','/schedule/publish','m06',64,true),
 ('m07','M07','课表查看',NULL,NULL,70,false),
 ('m0701','M0701','我的课表','/timetable/my','m07',71,true),
 ('m0702','M0702','班级课表','/timetable/class','m07',72,true),
 ('m0703','M0703','全校课表总览','/timetable/school','m07',73,true),
 ('m08','M08','调课管理',NULL,NULL,80,false),
 ('m0801','M0801','调课/代课申请','/schedule-change/apply','m08',81,false),
 ('m0802','M0802','我的调课/代课','/schedule-change/my','m08',82,true),
 ('m0803','M0803','调课/代课审批','/schedule-change/approval','m08',83,true),
 ('m0804','M0804','调课/代课统计','/schedule-change/stats','m08',84,true),
 ('m09','M09','请假管理',NULL,NULL,90,false),
 ('m0901','M0901','请假申请','/leave/apply','m09',91,false),
 ('m0902','M0902','我的请假','/leave/my','m09',92,true),
 ('m0903','M0903','请假审批','/leave/approval','m09',93,true),
 ('m0904','M0904','销假处理','/leave/return','m09',94,true),
 ('m0905','M0905','请假统计','/leave/stats','m09',95,true),
 ('m10','M10','违纪管理',NULL,NULL,100,false),
 ('m1001','M1001','违纪登记','/discipline/record','m10',101,false),
 ('m1002','M1002','违纪处理','/discipline/handle','m10',102,true),
 ('m1003','M1003','违纪查询','/discipline/query','m10',103,true),
 ('m1004','M1004','违纪统计','/discipline/stats','m10',104,true),
 ('m11','M11','评优评先',NULL,NULL,110,false),
 ('m1101','M1101','评选标准维护','/award/standards','m11',111,true),
 ('m1102','M1102','班级推优申报','/award/nominate','m11',112,false),
 ('m1103','M1103','评优审批','/award/approval','m11',113,true),
 ('m1104','M1104','公示与归档','/award/publish','m11',114,true),
 ('m12','M12','党政办公',NULL,NULL,120,false),
 ('m1201','M1201','通知公告','/party/notice','m12',121,true),
 ('m1202','M1202','公文收发','/party/document','m12',122,true),
 ('m1203','M1203','公文流转','/party/flow','m12',123,true),
 ('m1204','M1204','会议室预约','/party/meeting-room','m12',124,false),
 ('m1205','M1205','会议管理','/party/meeting','m12',125,true),
 ('m1206','M1206','值班安排','/party/duty','m12',126,true),
 ('m1207','M1207','用印/介绍信','/party/seal','m12',127,true),
 ('m13','M13','科学教育',NULL,NULL,130,false),
 ('m1301','M1301','科教资源库','/science/resource','m13',131,true),
 ('m1302','M1302','实验预约','/science/lab','m13',132,false),
 ('m1303','M1303','竞赛管理','/science/competition','m13',133,true),
 ('m1304','M1304','课题管理','/science/project','m13',134,true),
 ('m14','M14','体育',NULL,NULL,140,false),
 ('m1401','M1401','体测成绩','/sport/fitness','m14',141,true),
 ('m1402','M1402','达标统计','/sport/standard','m14',142,true),
 ('m1403','M1403','赛事管理','/sport/event','m14',143,true),
 ('m1404','M1404','器材管理','/sport/equipment','m14',144,true),
 ('m1405','M1405','训练队','/sport/team','m14',145,true),
 ('m15','M15','数据大屏','/dashboard/bigscreen',NULL,150,true)
ON CONFLICT ("code") DO UPDATE SET "name"=EXCLUDED."name","path"=EXCLUDED."path","listLike"=EXCLUDED."listLike";

-- ============ 4. 按钮权限点（docs/03 §一按钮表） ============
INSERT INTO "sys_button" ("id","code","name","menuId") VALUES
 ('btn_PUR_NEW','BTN-PUR-NEW','新建采购项目','m0202'),
 ('btn_PUR_EDIT','BTN-PUR-EDIT','编辑采购项目','m0202'),
 ('btn_PUR_DEL','BTN-PUR-DEL','删除采购项目','m0202'),
 ('btn_PUR_EXPORT','BTN-PUR-EXPORT','导出采购/预算/报表','m0202'),
 ('btn_APR_APPROVE','BTN-APR-APPROVE','审批通过（通用）','m0102'),
 ('btn_APR_REJECT','BTN-APR-REJECT','审批驳回（通用）','m0102'),
 ('btn_MAT_APPLY','BTN-MAT-APPLY','提交领用申请','m0301'),
 ('btn_MAT_APPROVE','BTN-MAT-APPROVE','领用审批通过','m0304'),
 ('btn_MAT_REJECT','BTN-MAT-REJECT','领用审批驳回','m0304'),
 ('btn_MAT_STOCK','BTN-MAT-STOCK','库存调整（入库/出库）','m0303'),
 ('btn_REP_SUBMIT','BTN-REP-SUBMIT','提交报修','m0401'),
 ('btn_REP_UPLOAD','BTN-REP-UPLOAD','报修上传照片','m0401'),
 ('btn_REP_ASSIGN','BTN-REP-ASSIGN','分派维修人','m0403'),
 ('btn_REP_STATUS','BTN-REP-STATUS','更新工单状态','m0403'),
 ('btn_REP_CLOSE','BTN-REP-CLOSE','关闭工单','m0403'),
 ('btn_SYS_USER_ADD','BTN-SYS-USER-ADD','新增用户','m0501'),
 ('btn_SYS_USER_EDIT','BTN-SYS-USER-EDIT','编辑用户','m0501'),
 ('btn_SYS_USER_DEL','BTN-SYS-USER-DEL','删除用户','m0501'),
 ('btn_SYS_USER_TOGGLE','BTN-SYS-USER-TOGGLE','启停用用户','m0501'),
 ('btn_SYS_ROLE_ADD','BTN-SYS-ROLE-ADD','新增角色','m0502'),
 ('btn_SYS_ROLE_EDIT','BTN-SYS-ROLE-EDIT','编辑角色','m0502'),
 ('btn_SYS_ROLE_DEL','BTN-SYS-ROLE-DEL','删除角色','m0502'),
 ('btn_SYS_ROLE_PERM','BTN-SYS-ROLE-PERM','分配角色权限','m0502'),
 ('btn_SYS_MENU_ADD','BTN-SYS-MENU-ADD','新增菜单','m0503'),
 ('btn_SYS_MENU_EDIT','BTN-SYS-MENU-EDIT','编辑菜单','m0503'),
 ('btn_SYS_MENU_DEL','BTN-SYS-MENU-DEL','删除菜单','m0503'),
 ('btn_SYS_DEPT_ADD','BTN-SYS-DEPT-ADD','新增部门','m0504'),
 ('btn_SYS_DEPT_EDIT','BTN-SYS-DEPT-EDIT','编辑部门','m0504'),
 ('btn_SYS_DEPT_DEL','BTN-SYS-DEPT-DEL','删除部门','m0504'),
 ('btn_MSG_TPL_ADD','BTN-MSG-TPL-ADD','新增消息模板','m0506'),
 ('btn_MSG_TPL_EDIT','BTN-MSG-TPL-EDIT','编辑消息模板','m0506'),
 ('btn_MSG_TPL_DEL','BTN-MSG-TPL-DEL','删除消息模板','m0506'),
 ('btn_MSG_TPL_TRIGGER','BTN-MSG-TPL-TRIGGER','消息触发规则配置','m0506'),
 ('btn_DICT_EDIT','BTN-DICT-EDIT','字典项增改删（仅 R-ADMIN）','m0505'),
 ('btn_FLOW_TPL_EDIT','BTN-FLOW-TPL-EDIT','流程模板维护（仅 R-ADMIN）','m0507'),
 ('btn_SCH_BASIC_EDIT','BTN-SCH-BASIC-EDIT','排课基础数据维护','m0601'),
 ('btn_SCH_ARRANGE','BTN-SCH-ARRANGE','手动排课/拖拽微调','m0602'),
 ('btn_SCH_AUTO','BTN-SCH-AUTO','一键自动排课','m0603'),
 ('btn_SCH_PUBLISH','BTN-SCH-PUBLISH','课表发布/版本管理','m0604'),
 ('btn_SCH_VIEW_ALL','BTN-SCH-VIEW-ALL','全校课表与课时统计查看','m0703'),
 ('btn_SCH_CHANGE_APPLY','BTN-SCH-CHANGE-APPLY','提交调课/代课申请','m0801'),
 ('btn_SCH_CHANGE_APPROVE','BTN-SCH-CHANGE-APPROVE','调课/代课审批通过','m0803'),
 ('btn_SCH_CHANGE_REJECT','BTN-SCH-CHANGE-REJECT','调课/代课审批驳回','m0803'),
 ('btn_SCH_CHANGE_EXPORT','BTN-SCH-CHANGE-EXPORT','调课/代课统计导出','m0804'),
 ('btn_LEAVE_APPLY','BTN-LEAVE-APPLY','提交请假申请','m0901'),
 ('btn_LEAVE_CONFIRM','BTN-LEAVE-CONFIRM','家长确认','m0902'),
 ('btn_LEAVE_APPROVE','BTN-LEAVE-APPROVE','请假审批通过','m0903'),
 ('btn_LEAVE_REJECT','BTN-LEAVE-REJECT','请假审批驳回','m0903'),
 ('btn_LEAVE_RETURN','BTN-LEAVE-RETURN','销假处理','m0904'),
 ('btn_LEAVE_EXPORT','BTN-LEAVE-EXPORT','请假统计导出','m0905'),
 ('btn_DISC_RECORD','BTN-DISC-RECORD','违纪登记','m1001'),
 ('btn_DISC_HANDLE','BTN-DISC-HANDLE','违纪处理','m1002'),
 ('btn_DISC_PARENT_CONFIRM','BTN-DISC-PARENT-CONFIRM','严重处分家长确认','m1002'),
 ('btn_DISC_FORCE_CLOSE','BTN-DISC-FORCE-CLOSE','严重处分超时强制结案','m1002'),
 ('btn_DISC_EXPORT','BTN-DISC-EXPORT','违纪统计导出','m1004'),
 ('btn_AWARD_STD_ADD','BTN-AWARD-STD-ADD','新增评选标准','m1101'),
 ('btn_AWARD_STD_EDIT','BTN-AWARD-STD-EDIT','编辑评选标准','m1101'),
 ('btn_AWARD_STD_DEL','BTN-AWARD-STD-DEL','删除评选标准','m1101'),
 ('btn_AWARD_NOMINATE','BTN-AWARD-NOMINATE','班级推优提交','m1102'),
 ('btn_AWARD_APPROVE','BTN-AWARD-APPROVE','评优审批通过','m1103'),
 ('btn_AWARD_REJECT','BTN-AWARD-REJECT','评优审批驳回','m1103'),
 ('btn_AWARD_PUBLISH','BTN-AWARD-PUBLISH','公示与归档','m1104'),
 ('btn_PARTY_NOTICE_PUB','BTN-PARTY-NOTICE-PUB','公告发布','m1201'),
 ('btn_PARTY_NOTICE_RETRACT','BTN-PARTY-NOTICE-RETRACT','公告撤回','m1201'),
 ('btn_PARTY_DOC_REG','BTN-PARTY-DOC-REG','公文收发登记','m1202'),
 ('btn_PARTY_DOC_FLOW','BTN-PARTY-DOC-FLOW','公文拟办/批示/传阅','m1203'),
 ('btn_PARTY_ROOM_BOOK','BTN-PARTY-ROOM-BOOK','会议室预约','m1204'),
 ('btn_PARTY_MEETING_NOTICE','BTN-PARTY-MEETING-NOTICE','会议通知','m1205'),
 ('btn_PARTY_MEETING_ARCHIVE','BTN-PARTY-MEETING-ARCHIVE','会议纪要归档','m1205'),
 ('btn_PARTY_DUTY_EDIT','BTN-PARTY-DUTY-EDIT','值班表编排','m1206'),
 ('btn_PARTY_DUTY_SWAP','BTN-PARTY-DUTY-SWAP','换班申请','m1206'),
 ('btn_PARTY_SEAL_APPLY','BTN-PARTY-SEAL-APPLY','用印/介绍信申请','m1207'),
 ('btn_SCI_RES_UPLOAD','BTN-SCI-RES-UPLOAD','科教资源上传','m1301'),
 ('btn_SCI_RES_DOWNLOAD','BTN-SCI-RES-DOWNLOAD','科教资源下载','m1301'),
 ('btn_SCI_LAB_BOOK','BTN-SCI-LAB-BOOK','实验预约','m1302'),
 ('btn_SCI_COMP_PUBLISH','BTN-SCI-COMP-PUBLISH','竞赛发布','m1303'),
 ('btn_SCI_COMP_APPLY','BTN-SCI-COMP-APPLY','竞赛报名','m1303'),
 ('btn_SCI_COMP_REVIEW','BTN-SCI-COMP-REVIEW','竞赛评审','m1303'),
 ('btn_SCI_PROJ_APPLY','BTN-SCI-PROJ-APPLY','课题申报','m1304'),
 ('btn_SCI_PROJ_REVIEW','BTN-SCI-PROJ-REVIEW','课题立项/结题审批','m1304'),
 ('btn_SPORT_FIT_INPUT','BTN-SPORT-FIT-INPUT','体测成绩录入','m1401'),
 ('btn_SPORT_EVENT_ARRANGE','BTN-SPORT-EVENT-ARRANGE','赛程编排','m1403'),
 ('btn_SPORT_EVENT_APPLY','BTN-SPORT-EVENT-APPLY','赛事报名','m1403'),
 ('btn_SPORT_EQUIP_BORROW','BTN-SPORT-EQUIP-BORROW','体育器材借用','m1404'),
 ('btn_SPORT_TEAM_MANAGE','BTN-SPORT-TEAM-MANAGE','训练队管理','m1405')
ON CONFLICT ("code") DO UPDATE SET "name"=EXCLUDED."name","menuId"=EXCLUDED."menuId";

-- ============ 5. 10 个字典编码（docs/03 M0505 清单，英文码 + 中文名） ============
INSERT INTO "sys_dict" ("id","dictCode","itemCode","itemName","sort","enabled","isCurrent") VALUES
 -- DICT-STATUS 跨六域通用业务状态
 ('d_status_1','DICT-STATUS','PENDING','待审批',1,true,NULL),
 ('d_status_2','DICT-STATUS','APPROVING','审批中',2,true,NULL),
 ('d_status_3','DICT-STATUS','APPROVED','已通过',3,true,NULL),
 ('d_status_4','DICT-STATUS','REJECTED','已驳回',4,true,NULL),
 ('d_status_5','DICT-STATUS','CANCELLED','已取消',5,true,NULL),
 ('d_status_6','DICT-STATUS','COMPLETED','已完成',6,true,NULL),
 -- DICT-FAULT-TYPE 报修故障分类
 ('d_fault_1','DICT-FAULT-TYPE','WATER_ELEC','水电',1,true,NULL),
 ('d_fault_2','DICT-FAULT-TYPE','DOOR_WINDOW','门窗',2,true,NULL),
 ('d_fault_3','DICT-FAULT-TYPE','DESK_CHAIR','桌椅',3,true,NULL),
 ('d_fault_4','DICT-FAULT-TYPE','MULTIMEDIA','多媒体',4,true,NULL),
 ('d_fault_5','DICT-FAULT-TYPE','AIR_COND','空调',5,true,NULL),
 ('d_fault_6','DICT-FAULT-TYPE','NETWORK','网络',6,true,NULL),
 ('d_fault_7','DICT-FAULT-TYPE','OTHER','其它',7,true,NULL),
 -- DICT-DISC-LEVEL 违纪处分等级
 ('d_disc_l1','DICT-DISC-LEVEL','TALK','谈话',1,true,NULL),
 ('d_disc_l2','DICT-DISC-LEVEL','WARNING','警告',2,true,NULL),
 ('d_disc_l3','DICT-DISC-LEVEL','SERIOUS_WARNING','严重警告',3,true,NULL),
 ('d_disc_l4','DICT-DISC-LEVEL','DEMERIT','记过',4,true,NULL),
 ('d_disc_l5','DICT-DISC-LEVEL','PROBATION','留校察看',5,true,NULL),
 -- DICT-TERM 学年+学期（当前学期标识 isCurrent）
 ('d_term_1','DICT-TERM','2025-2026-FIRST','2025-2026 学年 第一学期',1,true,true),
 ('d_term_2','DICT-TERM','2025-2026-SECOND','2025-2026 学年 第二学期',2,true,false),
 -- DICT-ASSET-TYPE 资产类型（英文码 = 库内取值，中文为显示名）
 ('d_asset_1','DICT-ASSET-TYPE','MATERIAL','后勤物资',1,true,NULL),
 ('d_asset_2','DICT-ASSET-TYPE','LAB_EQUIP','科教实验器材',2,true,NULL),
 ('d_asset_3','DICT-ASSET-TYPE','SPORT_EQUIP','体育器材',3,true,NULL),
 -- DICT-FITNESS-STD 体测标准（国标 + 可配置）
 ('d_fit_1','DICT-FITNESS-STD','NATIONAL_DEFAULT','国标默认',1,true,NULL),
 -- DICT-OVERDUE-DAYS 各类超期阈值（C10 调度消费）
 ('d_od_1','DICT-OVERDUE-DAYS','APPROVAL_STALL','审批滞留（天）',1,true,NULL),
 ('d_od_2','DICT-OVERDUE-DAYS','ASSET_OVERDUE','资产未归还（天）',2,true,NULL),
 ('d_od_3','DICT-OVERDUE-DAYS','LEAVE_NOT_RETURN','请假未销假（天）',3,true,NULL),
 ('d_od_4','DICT-OVERDUE-DAYS','REPAIR_UNASSIGNED','报修超时未派单（小时）',4,true,NULL),
 ('d_od_5','DICT-OVERDUE-DAYS','REPAIR_UNHANDLED','报修超时未处理（小时）',5,true,NULL),
 ('d_od_6','DICT-OVERDUE-DAYS','PARENT_UNCONFIRMED','家长未确认超期（天）',6,true,NULL),
 -- DICT-LEAVE-TYPE 请假类型
 ('d_leave_1','DICT-LEAVE-TYPE','SICK','病假',1,true,NULL),
 ('d_leave_2','DICT-LEAVE-TYPE','PERSONAL','事假',2,true,NULL),
 ('d_leave_3','DICT-LEAVE-TYPE','PUBLIC','公假',3,true,NULL),
 ('d_leave_4','DICT-LEAVE-TYPE','OUT','外出',4,true,NULL),
 -- DICT-DISC-TYPE 违纪类型
 ('d_disct_1','DICT-DISC-TYPE','LATE','迟到',1,true,NULL),
 ('d_disct_2','DICT-DISC-TYPE','ABSENT','旷课',2,true,NULL),
 ('d_disct_3','DICT-DISC-TYPE','CLASS_DISCIPLINE','课堂纪律',3,true,NULL),
 ('d_disct_4','DICT-DISC-TYPE','APPEARANCE','仪容仪表',4,true,NULL),
 ('d_disct_5','DICT-DISC-TYPE','OTHER','其它',5,true,NULL),
 -- DICT-SPACE-TYPE 场地类型（英文码 = 库内取值）
 ('d_space_1','DICT-SPACE-TYPE','CLASSROOM','教室',1,true,NULL),
 ('d_space_2','DICT-SPACE-TYPE','LAB','实验室',2,true,NULL),
 ('d_space_3','DICT-SPACE-TYPE','MEETING_ROOM','会议室',3,true,NULL),
 ('d_space_4','DICT-SPACE-TYPE','SPORT_VENUE','操场/体育场地',4,true,NULL)
ON CONFLICT ("dictCode","itemCode") DO UPDATE SET "itemName"=EXCLUDED."itemName","sort"=EXCLUDED."sort";

-- ============ 6. 流程缺省绑定（docs/07：业务类型 → 流程模板，审批角色可配置不硬编码） ============
INSERT INTO "flow_template" ("id","code","name","businessType","enabled","version","rejectPolicy") VALUES
 ('ft_procurement','FT-PROCUREMENT','采购申请审批','procurement',true,1,'RETURN'),
 ('ft_material','FT-MATERIAL','物资领用审批','material',true,1,'RETURN'),
 ('ft_repair','FT-REPAIR','报修处理','repair',true,1,'RETURN'),
 ('ft_schedule_change','FT-SCHEDULE-CHANGE','调课/代课审批','schedule_change',true,1,'RETURN'),
 ('ft_leave','FT-LEAVE','请假审批','leave',true,1,'RETURN'),
 ('ft_award','FT-AWARD','评优审批','award',true,1,'RETURN')
ON CONFLICT ("code") DO UPDATE SET "name"=EXCLUDED."name","businessType"=EXCLUDED."businessType";

INSERT INTO "flow_node" ("id","templateId","seq","name","approveRole","timeoutHours","nodeType") VALUES
 ('ftn_p_1','ft_procurement',1,'后勤主管审批','R-LOGISTICS',72,'LINEAR'),
 ('ftn_m_1','ft_material',1,'后勤主管审批','R-LOGISTICS',48,'LINEAR'),
 ('ftn_r_1','ft_repair',1,'后勤分派','R-LOGISTICS',24,'LINEAR'),
 ('ftn_sc_1','ft_schedule_change',1,'教务主任审批','R-DEAN',24,'LINEAR'),
 ('ftn_l_1','ft_leave',1,'班主任/年级组长审批','R-GRADE',24,'LINEAR'),
 ('ftn_a_1','ft_award',1,'德育处审批','R-MORAL',72,'LINEAR')
ON CONFLICT ("templateId","seq") DO UPDATE SET "approveRole"=EXCLUDED."approveRole","timeoutHours"=EXCLUDED."timeoutHours";

-- ============ 7. 角色授权：R-ADMIN 全量；R-DEAN 按 docs/03 决策 17 授权口径 ============
INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m WHERE r."code" = 'R-ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b WHERE r."code" = 'R-ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO "role_menu" ("roleId","menuId")
SELECT r."id", m."id" FROM "sys_role" r CROSS JOIN "sys_menu" m
WHERE r."code" = 'R-DEAN' AND m."code" IN (
  'M01','M0101','M0102','M0105','M06','M0601','M0602','M0603','M0604',
  'M07','M0701','M0702','M0703','M08','M0801','M0802','M0803','M0804',
  'M1201','M1204','M15')
ON CONFLICT DO NOTHING;

INSERT INTO "role_button" ("roleId","buttonId")
SELECT r."id", b."id" FROM "sys_role" r CROSS JOIN "sys_button" b
WHERE r."code" = 'R-DEAN' AND b."code" IN (
  'BTN-SCH-BASIC-EDIT','BTN-SCH-ARRANGE','BTN-SCH-AUTO','BTN-SCH-PUBLISH','BTN-SCH-VIEW-ALL',
  'BTN-SCH-CHANGE-APPLY','BTN-SCH-CHANGE-APPROVE','BTN-SCH-CHANGE-REJECT','BTN-SCH-CHANGE-EXPORT',
  'BTN-APR-APPROVE','BTN-APR-REJECT','BTN-PARTY-ROOM-BOOK')
ON CONFLICT DO NOTHING;
