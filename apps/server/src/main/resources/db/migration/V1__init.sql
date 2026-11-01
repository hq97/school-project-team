-- init：六域 + 横切 + 平台 全部对象基线（全新项目，无存量数据）
-- 事实源：docs/02 §1.7 / docs/03 / docs/04 / docs/07 / docs/15
-- 约定：枚举一律英文码，中文显示名由 sys_dict 提供

CREATE TYPE "DataScope" AS ENUM ('SELF','GRADE','ALL');
CREATE TYPE "OwnershipType" AS ENUM ('ORG','RELATION','ROLE_GRANT');
CREATE TYPE "DeptType" AS ENUM ('SCHOOL','GRADE','CLASS','SUBJECT','OFFICE');
CREATE TYPE "SpaceType" AS ENUM ('CLASSROOM','LAB','MEETING_ROOM','SPORT_VENUE');
CREATE TYPE "SpaceStatus" AS ENUM ('AVAILABLE','DISABLED','REPAIRING');
CREATE TYPE "AssetType" AS ENUM ('MATERIAL','LAB_EQUIP','SPORT_EQUIP');
CREATE TYPE "AssetStatus" AS ENUM ('IN_STOCK','LENT','REPAIRING','SCRAPPED');
CREATE TYPE "OccupancySource" AS ENUM ('SCHEDULE','RESERVATION');
CREATE TYPE "OccupancyStatus" AS ENUM ('PENDING','EFFECTIVE','CANCELLED');
CREATE TYPE "AccountStatus" AS ENUM ('ACTIVE','DISABLED');
CREATE TYPE "StudentStatus" AS ENUM ('ENROLLED','GRADUATED','TRANSFERRED','LEFT','SUSPENDED');
CREATE TYPE "AlertStatus" AS ENUM ('OPEN','CLOSED');
CREATE TYPE "OutboxStatus" AS ENUM ('PENDING','SENT','FAILED');

-- 组织与身份
CREATE TABLE "sys_dept" (
  "id" TEXT PRIMARY KEY,
  "name" TEXT NOT NULL,
  "deptType" "DeptType" NOT NULL,
  "parentId" TEXT,
  "path" TEXT NOT NULL,
  "sort" INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX "sys_dept_path_idx" ON "sys_dept"("path");
CREATE INDEX "sys_dept_deptType_idx" ON "sys_dept"("deptType");
ALTER TABLE "sys_dept" ADD CONSTRAINT "sys_dept_parentId_fkey" FOREIGN KEY ("parentId") REFERENCES "sys_dept"("id") ON DELETE SET NULL ON UPDATE CASCADE;

CREATE TABLE "sys_user" (
  "id" TEXT PRIMARY KEY,
  "username" TEXT UNIQUE NOT NULL,
  "passwordHash" TEXT NOT NULL,
  "realName" TEXT NOT NULL,
  "accountStatus" "AccountStatus" NOT NULL DEFAULT 'ACTIVE',
  "studentStatus" "StudentStatus",
  "studentId" TEXT,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE "user_dept" (
  "userId" TEXT NOT NULL, "deptId" TEXT NOT NULL,
  PRIMARY KEY ("userId","deptId"),
  FOREIGN KEY ("userId") REFERENCES "sys_user"("id") ON DELETE CASCADE,
  FOREIGN KEY ("deptId") REFERENCES "sys_dept"("id") ON DELETE CASCADE
);

-- 班主任身份（换届快照：classId + 学期）
CREATE TABLE "head_teacher" (
  "id" TEXT PRIMARY KEY,
  "teacherId" TEXT NOT NULL,
  "classId" TEXT NOT NULL,
  "termId" TEXT NOT NULL,
  FOREIGN KEY ("teacherId") REFERENCES "sys_user"("id") ON DELETE CASCADE,
  FOREIGN KEY ("classId") REFERENCES "sys_dept"("id") ON DELETE CASCADE
);
CREATE UNIQUE INDEX "head_teacher_unique" ON "head_teacher"("teacherId","classId","termId");
CREATE INDEX "head_teacher_teacher_term_idx" ON "head_teacher"("teacherId","termId");

-- 关系型：家长-子女
CREATE TABLE "student_parent" (
  "id" TEXT PRIMARY KEY,
  "studentId" TEXT NOT NULL,
  "parentUserId" TEXT NOT NULL,
  FOREIGN KEY ("studentId") REFERENCES "sys_user"("id") ON DELETE CASCADE,
  FOREIGN KEY ("parentUserId") REFERENCES "sys_user"("id") ON DELETE CASCADE
);
CREATE UNIQUE INDEX "student_parent_unique" ON "student_parent"("studentId","parentUserId");
CREATE INDEX "student_parent_parent_idx" ON "student_parent"("parentUserId");

-- RBAC
CREATE TABLE "sys_role" (
  "id" TEXT PRIMARY KEY,
  "code" TEXT UNIQUE NOT NULL,
  "name" TEXT NOT NULL,
  "dataScope" "DataScope",
  "scopeComplete" BOOLEAN NOT NULL DEFAULT false,
  "builtin" BOOLEAN NOT NULL DEFAULT false,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE "user_role" (
  "userId" TEXT NOT NULL, "roleId" TEXT NOT NULL,
  PRIMARY KEY ("userId","roleId"),
  FOREIGN KEY ("userId") REFERENCES "sys_user"("id") ON DELETE CASCADE,
  FOREIGN KEY ("roleId") REFERENCES "sys_role"("id") ON DELETE CASCADE
);
CREATE TABLE "sys_menu" (
  "id" TEXT PRIMARY KEY,
  "code" TEXT UNIQUE NOT NULL,
  "name" TEXT NOT NULL,
  "path" TEXT,
  "parentId" TEXT,
  "sort" INTEGER NOT NULL DEFAULT 0,
  "listLike" BOOLEAN NOT NULL DEFAULT false
);
CREATE TABLE "sys_button" (
  "id" TEXT PRIMARY KEY,
  "code" TEXT UNIQUE NOT NULL,
  "name" TEXT NOT NULL,
  "menuId" TEXT NOT NULL,
  FOREIGN KEY ("menuId") REFERENCES "sys_menu"("id") ON DELETE CASCADE
);
CREATE TABLE "role_menu" (
  "roleId" TEXT NOT NULL, "menuId" TEXT NOT NULL,
  PRIMARY KEY ("roleId","menuId"),
  FOREIGN KEY ("roleId") REFERENCES "sys_role"("id") ON DELETE CASCADE,
  FOREIGN KEY ("menuId") REFERENCES "sys_menu"("id") ON DELETE CASCADE
);
CREATE TABLE "role_button" (
  "roleId" TEXT NOT NULL, "buttonId" TEXT NOT NULL,
  PRIMARY KEY ("roleId","buttonId"),
  FOREIGN KEY ("roleId") REFERENCES "sys_role"("id") ON DELETE CASCADE,
  FOREIGN KEY ("buttonId") REFERENCES "sys_button"("id") ON DELETE CASCADE
);
CREATE TABLE "role_object_scope" (
  "id" TEXT PRIMARY KEY,
  "roleId" TEXT NOT NULL,
  "objectKey" TEXT NOT NULL,
  "ownership" "OwnershipType" NOT NULL,
  "dataScope" "DataScope" NOT NULL,
  FOREIGN KEY ("roleId") REFERENCES "sys_role"("id") ON DELETE CASCADE
);
CREATE UNIQUE INDEX "role_object_scope_unique" ON "role_object_scope"("roleId","objectKey");

-- 数据字典
CREATE TABLE "sys_dict" (
  "id" TEXT PRIMARY KEY,
  "dictCode" TEXT NOT NULL,
  "itemCode" TEXT NOT NULL,
  "itemName" TEXT NOT NULL,
  "sort" INTEGER NOT NULL DEFAULT 0,
  "enabled" BOOLEAN NOT NULL DEFAULT true,
  "isCurrent" BOOLEAN
);
CREATE UNIQUE INDEX "sys_dict_unique" ON "sys_dict"("dictCode","itemCode");
CREATE INDEX "sys_dict_code_idx" ON "sys_dict"("dictCode");

-- 流程模板（docs/07）
CREATE TABLE "flow_template" (
  "id" TEXT PRIMARY KEY,
  "code" TEXT UNIQUE NOT NULL,
  "name" TEXT NOT NULL,
  "businessType" TEXT NOT NULL,
  "enabled" BOOLEAN NOT NULL DEFAULT true,
  "version" INTEGER NOT NULL DEFAULT 1,
  "rejectPolicy" TEXT NOT NULL DEFAULT 'RETURN'
);
CREATE TABLE "flow_node" (
  "id" TEXT PRIMARY KEY,
  "templateId" TEXT NOT NULL,
  "seq" INTEGER NOT NULL,
  "name" TEXT NOT NULL,
  "approveRole" TEXT NOT NULL,
  "timeoutHours" INTEGER,
  "nodeType" TEXT NOT NULL DEFAULT 'LINEAR',
  FOREIGN KEY ("templateId") REFERENCES "flow_template"("id") ON DELETE CASCADE
);
CREATE UNIQUE INDEX "flow_node_unique" ON "flow_node"("templateId","seq");
CREATE TABLE "flow_instance" (
  "id" TEXT PRIMARY KEY,
  "businessType" TEXT NOT NULL,
  "businessId" TEXT NOT NULL,
  "templateId" TEXT NOT NULL,
  "templateSnap" JSONB NOT NULL,
  "currentSeq" INTEGER NOT NULL DEFAULT 1,
  "status" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY ("templateId") REFERENCES "flow_template"("id")
);
CREATE INDEX "flow_instance_biz_idx" ON "flow_instance"("businessType","businessId");
CREATE TABLE "flow_task" (
  "id" TEXT PRIMARY KEY,
  "instanceId" TEXT NOT NULL,
  "seq" INTEGER NOT NULL,
  "approverId" TEXT,
  "role" TEXT NOT NULL,
  "conclusion" TEXT,
  "comment" TEXT,
  "actedAt" TIMESTAMPTZ,
  FOREIGN KEY ("instanceId") REFERENCES "flow_instance"("id") ON DELETE CASCADE
);

-- 统一场地 space（docs/04 §1.11）
CREATE TABLE "space" (
  "id" TEXT PRIMARY KEY,
  "spaceName" TEXT NOT NULL,
  "spaceType" "SpaceType" NOT NULL,
  "capacity" INTEGER,
  "deptNodeId" TEXT,
  "status" "SpaceStatus" NOT NULL DEFAULT 'AVAILABLE',
  "openScope" "DataScope" NOT NULL DEFAULT 'ALL',
  FOREIGN KEY ("deptNodeId") REFERENCES "sys_dept"("id") ON DELETE SET NULL
);
CREATE INDEX "space_type_idx" ON "space"("spaceType");

CREATE TABLE "space_occupancy" (
  "id" TEXT PRIMARY KEY,
  "spaceId" TEXT NOT NULL,
  "termId" TEXT NOT NULL,
  "week" INTEGER NOT NULL,
  "dayOfWeek" INTEGER NOT NULL,
  "period" INTEGER NOT NULL,
  "source" "OccupancySource" NOT NULL,
  "bizRefId" TEXT,
  "status" "OccupancyStatus" NOT NULL DEFAULT 'EFFECTIVE',
  "createdBy" TEXT,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY ("spaceId") REFERENCES "space"("id") ON DELETE CASCADE
);
-- C9 ② 部分唯一索引：同一「场地+学期+周次+星期+节次」只允许一个有效占用（ADR §4.1）
CREATE UNIQUE INDEX "uniq_effective_slot"
  ON "space_occupancy"("spaceId","termId","week","dayOfWeek","period")
  WHERE "status" IN ('PENDING','EFFECTIVE');
CREATE INDEX "space_occupancy_slot_idx" ON "space_occupancy"("spaceId","termId","week");

-- 统一资产台账 asset（docs/04 §1.6，域隔离）
CREATE TABLE "asset" (
  "id" TEXT PRIMARY KEY,
  "name" TEXT NOT NULL,
  "assetType" "AssetType" NOT NULL,
  "quantity" INTEGER NOT NULL DEFAULT 0,
  "availableQty" INTEGER NOT NULL DEFAULT 0,
  "status" "AssetStatus" NOT NULL DEFAULT 'IN_STOCK',
  "location" TEXT,
  "borrowDomain" TEXT NOT NULL
);
CREATE INDEX "asset_type_idx" ON "asset"("assetType");
CREATE TABLE "asset_borrow" (
  "id" TEXT PRIMARY KEY,
  "assetId" TEXT NOT NULL,
  "borrowerId" TEXT NOT NULL,
  "quantity" INTEGER NOT NULL,
  "termId" TEXT,
  "dueAt" TIMESTAMPTZ,
  "returnedAt" TIMESTAMPTZ,
  "applyNo" TEXT UNIQUE NOT NULL,
  FOREIGN KEY ("assetId") REFERENCES "asset"("id") ON DELETE CASCADE
);
CREATE INDEX "asset_borrow_borrower_idx" ON "asset_borrow"("borrowerId");

-- 超期提醒 alert（C10，docs/04 §3.7）
CREATE TABLE "alert" (
  "id" TEXT PRIMARY KEY,
  "alertType" TEXT NOT NULL,
  "bizType" TEXT NOT NULL,
  "bizId" TEXT NOT NULL,
  "ownerId" TEXT,
  "ownerDeptId" TEXT,
  "termId" TEXT,
  "level" TEXT NOT NULL DEFAULT 'WARNING',
  "status" "AlertStatus" NOT NULL DEFAULT 'OPEN',
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX "alert_unique" ON "alert"("alertType","bizType","bizId");
CREATE INDEX "alert_owner_idx" ON "alert"("ownerId","status");

-- 消息中心 + 事件发件箱（docs/15，ADR-04）
CREATE TABLE "event_outbox" (
  "id" TEXT PRIMARY KEY,
  "eventType" TEXT NOT NULL,
  "payload" JSONB NOT NULL,
  "dedupeKey" TEXT UNIQUE NOT NULL,
  "status" "OutboxStatus" NOT NULL DEFAULT 'PENDING',
  "retryCount" INTEGER NOT NULL DEFAULT 0,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX "event_outbox_status_idx" ON "event_outbox"("status");
CREATE TABLE "message" (
  "id" TEXT PRIMARY KEY,
  "receiverId" TEXT NOT NULL,
  "category" TEXT NOT NULL,
  "title" TEXT NOT NULL,
  "bizType" TEXT,
  "bizId" TEXT,
  "linkTo" TEXT,
  "read" BOOLEAN NOT NULL DEFAULT false,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX "message_receiver_idx" ON "message"("receiverId","read");
CREATE TABLE "message_template" (
  "id" TEXT PRIMARY KEY,
  "bizType" TEXT NOT NULL,
  "eventType" TEXT NOT NULL,
  "titleTpl" TEXT NOT NULL,
  "contentTpl" TEXT NOT NULL
);
CREATE UNIQUE INDEX "message_template_unique" ON "message_template"("bizType","eventType");

-- 公告（角色授权型）+ 已读回执（业务端唯一真源）
CREATE TABLE "notice" (
  "id" TEXT PRIMARY KEY,
  "title" TEXT NOT NULL,
  "content" TEXT NOT NULL,
  "visibleRoles" TEXT[] NOT NULL,
  "publishedById" TEXT NOT NULL,
  "termId" TEXT,
  "publishedAt" TIMESTAMPTZ NOT NULL DEFAULT now(),
  "retracted" BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX "notice_term_idx" ON "notice"("termId");
CREATE TABLE "notice_receipt" (
  "id" TEXT PRIMARY KEY,
  "noticeId" TEXT NOT NULL,
  "userId" TEXT NOT NULL,
  "readAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX "notice_receipt_unique" ON "notice_receipt"("noticeId","userId");

-- 后勤域
CREATE TABLE "procurement_project" (
  "id" TEXT PRIMARY KEY,
  "applyNo" TEXT UNIQUE NOT NULL,
  "name" TEXT NOT NULL,
  "creatorId" TEXT NOT NULL,
  "creatorDeptId" TEXT,
  "termId" TEXT,
  "budget" DECIMAL(14,2),
  "status" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX "procurement_creator_idx" ON "procurement_project"("creatorId");
CREATE INDEX "procurement_dept_idx" ON "procurement_project"("creatorDeptId");

CREATE TABLE "material_requisition" (
  "id" TEXT PRIMARY KEY,
  "applyNo" TEXT UNIQUE NOT NULL,
  "applicantId" TEXT NOT NULL,
  "applicantDeptId" TEXT,
  "assetId" TEXT NOT NULL,
  "quantity" INTEGER NOT NULL,
  "termId" TEXT,
  "status" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX "material_applicant_idx" ON "material_requisition"("applicantId");

CREATE TABLE "repair_ticket" (
  "id" TEXT PRIMARY KEY,
  "ticketNo" TEXT UNIQUE NOT NULL,
  "reporterId" TEXT NOT NULL,
  "reporterDeptId" TEXT,
  "spaceId" TEXT,
  "faultType" TEXT,
  "termId" TEXT,
  "status" TEXT NOT NULL,
  "assignedTo" TEXT,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX "repair_reporter_idx" ON "repair_ticket"("reporterId");

-- 教务域
CREATE TABLE "schedule_version" (
  "id" TEXT PRIMARY KEY,
  "termId" TEXT NOT NULL,
  "effectiveWeek" INTEGER NOT NULL,
  "version" INTEGER NOT NULL,
  "status" TEXT NOT NULL,
  "publishedAt" TIMESTAMPTZ
);
CREATE UNIQUE INDEX "schedule_version_unique" ON "schedule_version"("termId","effectiveWeek","version");
CREATE TABLE "timetable_entry" (
  "id" TEXT PRIMARY KEY,
  "versionId" TEXT NOT NULL,
  "spaceId" TEXT,
  "teacherId" TEXT NOT NULL,
  "classId" TEXT NOT NULL,
  "subject" TEXT NOT NULL,
  "termId" TEXT NOT NULL,
  "week" INTEGER NOT NULL,
  "dayOfWeek" INTEGER NOT NULL,
  "period" INTEGER NOT NULL,
  "substituteTeacherId" TEXT
);
CREATE INDEX "timetable_term_week_idx" ON "timetable_entry"("termId","week");
CREATE TABLE "schedule_change" (
  "id" TEXT PRIMARY KEY,
  "applyNo" TEXT UNIQUE NOT NULL,
  "applicantId" TEXT NOT NULL,
  "termId" TEXT NOT NULL,
  "targetSlot" JSONB NOT NULL,
  "reason" TEXT,
  "status" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX "schedule_change_applicant_idx" ON "schedule_change"("applicantId");

-- 德育域
CREATE TABLE "leave_request" (
  "id" TEXT PRIMARY KEY,
  "applyNo" TEXT UNIQUE NOT NULL,
  "studentId" TEXT NOT NULL,
  "studentClassId" TEXT,
  "termId" TEXT,
  "leaveType" TEXT,
  "status" TEXT NOT NULL,
  "returnAt" TIMESTAMPTZ,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX "leave_student_idx" ON "leave_request"("studentId");
CREATE INDEX "leave_class_idx" ON "leave_request"("studentClassId");
CREATE TABLE "discipline_record" (
  "id" TEXT PRIMARY KEY,
  "studentId" TEXT NOT NULL,
  "studentClassId" TEXT,
  "termId" TEXT,
  "discType" TEXT,
  "level" TEXT,
  "internalNote" TEXT,
  "status" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX "discipline_class_idx" ON "discipline_record"("studentClassId");
CREATE TABLE "award_nomination" (
  "id" TEXT PRIMARY KEY,
  "applyNo" TEXT UNIQUE NOT NULL,
  "studentId" TEXT NOT NULL,
  "studentClassId" TEXT,
  "termId" TEXT,
  "quotaUsed" BOOLEAN NOT NULL DEFAULT false,
  "status" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 党政办域
CREATE TABLE "party_document" (
  "id" TEXT PRIMARY KEY,
  "docNo" TEXT UNIQUE NOT NULL,
  "title" TEXT NOT NULL,
  "ownerId" TEXT NOT NULL,
  "termId" TEXT,
  "status" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX "party_document_owner_idx" ON "party_document"("ownerId");
CREATE TABLE "meeting_room_booking" (
  "id" TEXT PRIMARY KEY,
  "bookingId" TEXT UNIQUE NOT NULL,
  "spaceId" TEXT NOT NULL,
  "bookerId" TEXT NOT NULL,
  "termId" TEXT NOT NULL,
  "slot" JSONB NOT NULL,
  "status" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX "meeting_room_booker_idx" ON "meeting_room_booking"("bookerId");

-- 科教域
CREATE TABLE "lab_booking" (
  "id" TEXT PRIMARY KEY,
  "bookingId" TEXT UNIQUE NOT NULL,
  "spaceId" TEXT NOT NULL,
  "bookerId" TEXT NOT NULL,
  "termId" TEXT NOT NULL,
  "slot" JSONB NOT NULL,
  "status" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE "competition" (
  "id" TEXT PRIMARY KEY,
  "name" TEXT NOT NULL,
  "quota" INTEGER NOT NULL,
  "usedQuota" INTEGER NOT NULL DEFAULT 0,
  "termId" TEXT
);
CREATE TABLE "competition_apply" (
  "id" TEXT PRIMARY KEY,
  "applyNo" TEXT UNIQUE NOT NULL,
  "competitionId" TEXT NOT NULL,
  "applicantId" TEXT NOT NULL,
  "status" TEXT NOT NULL,
  FOREIGN KEY ("competitionId") REFERENCES "competition"("id") ON DELETE CASCADE
);
CREATE UNIQUE INDEX "competition_apply_unique" ON "competition_apply"("competitionId","applicantId");

-- 体育域
CREATE TABLE "sport_event" (
  "id" TEXT PRIMARY KEY,
  "name" TEXT NOT NULL,
  "spaceId" TEXT,
  "termId" TEXT,
  "slot" JSONB,
  "quota" INTEGER NOT NULL DEFAULT 0,
  "usedQuota" INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE "fitness_record" (
  "id" TEXT PRIMARY KEY,
  "studentId" TEXT NOT NULL,
  "studentClassId" TEXT,
  "termId" TEXT,
  "score" JSONB
);
CREATE INDEX "fitness_class_idx" ON "fitness_record"("studentClassId");
