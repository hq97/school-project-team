-- V6：PG 原生枚举类型 → TEXT + CHECK 约束
-- 根因：MyBatis-Plus 枚举默认用 EnumTypeHandler（setString 绑定 varchar），
--       PG 拒绝 varchar → 自定义枚举的隐式 cast，所有枚举列的 INSERT/UPDATE 报
--       "column ... is of type ... but expression is of type character varying"。
-- 约定（docs/03）：枚举一律英文码，中文名由 sys_dict 提供；DB 层存纯文本码 + CHECK 兜底值域即可。
-- 本次把 15 列枚举改为 TEXT（Java 枚举不变，MyBatis-Plus 仍写 name() 字符串，varchar→text 兼容），
-- 随后 drop 掉 13 个枚举类型。

-- 1. sys_dept.deptType
ALTER TABLE "sys_dept" ALTER COLUMN "deptType" TYPE TEXT USING "deptType"::text;
ALTER TABLE "sys_dept" ADD CONSTRAINT "sys_dept_deptType_check" CHECK ("deptType" IN ('SCHOOL','GRADE','CLASS','SUBJECT','OFFICE'));

-- 2. sys_user
ALTER TABLE "sys_user" ALTER COLUMN "accountStatus" TYPE TEXT USING "accountStatus"::text;
ALTER TABLE "sys_user" ALTER COLUMN "accountStatus" SET DEFAULT 'ACTIVE';
ALTER TABLE "sys_user" ADD CONSTRAINT "sys_user_accountStatus_check" CHECK ("accountStatus" IN ('ACTIVE','DISABLED'));
ALTER TABLE "sys_user" ALTER COLUMN "studentStatus" TYPE TEXT USING "studentStatus"::text;
ALTER TABLE "sys_user" ADD CONSTRAINT "sys_user_studentStatus_check" CHECK ("studentStatus" IN ('ENROLLED','GRADUATED','TRANSFERRED','LEFT','SUSPENDED'));

-- 3. sys_role.dataScope
ALTER TABLE "sys_role" ALTER COLUMN "dataScope" TYPE TEXT USING "dataScope"::text;
ALTER TABLE "sys_role" ADD CONSTRAINT "sys_role_dataScope_check" CHECK ("dataScope" IN ('SELF','GRADE','ALL'));

-- 4. role_object_scope
ALTER TABLE "role_object_scope" ALTER COLUMN "ownership" TYPE TEXT USING "ownership"::text;
ALTER TABLE "role_object_scope" ADD CONSTRAINT "role_object_scope_ownership_check" CHECK ("ownership" IN ('ORG','RELATION','ROLE_GRANT'));
ALTER TABLE "role_object_scope" ALTER COLUMN "dataScope" TYPE TEXT USING "dataScope"::text;
ALTER TABLE "role_object_scope" ADD CONSTRAINT "role_object_scope_dataScope_check" CHECK ("dataScope" IN ('SELF','GRADE','ALL'));

-- 5. space
ALTER TABLE "space" ALTER COLUMN "spaceType" TYPE TEXT USING "spaceType"::text;
ALTER TABLE "space" ADD CONSTRAINT "space_spaceType_check" CHECK ("spaceType" IN ('CLASSROOM','LAB','MEETING_ROOM','SPORT_VENUE'));
ALTER TABLE "space" ALTER COLUMN "status" TYPE TEXT USING "status"::text;
ALTER TABLE "space" ALTER COLUMN "status" SET DEFAULT 'AVAILABLE';
ALTER TABLE "space" ADD CONSTRAINT "space_status_check" CHECK ("status" IN ('AVAILABLE','DISABLED','REPAIRING'));
ALTER TABLE "space" ALTER COLUMN "openScope" TYPE TEXT USING "openScope"::text;
ALTER TABLE "space" ALTER COLUMN "openScope" SET DEFAULT 'ALL';
ALTER TABLE "space" ADD CONSTRAINT "space_openScope_check" CHECK ("openScope" IN ('SELF','GRADE','ALL'));

-- 6. space_occupancy
-- status 列有 C9 部分唯一索引 uniq_effective_slot 依赖（WHERE 子句含枚举字面量），
-- 直接 ALTER TYPE 会因重建索引时 "text = OccupancyStatus" 无操作符而失败，故先 DROP 后重建。
DROP INDEX "uniq_effective_slot";
ALTER TABLE "space_occupancy" ALTER COLUMN "source" TYPE TEXT USING "source"::text;
ALTER TABLE "space_occupancy" ADD CONSTRAINT "space_occupancy_source_check" CHECK ("source" IN ('SCHEDULE','RESERVATION'));
ALTER TABLE "space_occupancy" ALTER COLUMN "status" TYPE TEXT USING "status"::text;
ALTER TABLE "space_occupancy" ALTER COLUMN "status" SET DEFAULT 'EFFECTIVE';
ALTER TABLE "space_occupancy" ADD CONSTRAINT "space_occupancy_status_check" CHECK ("status" IN ('PENDING','EFFECTIVE','CANCELLED'));
-- C9 ② 重建部分唯一索引（列已改 TEXT，WHERE 用文本字面量）
CREATE UNIQUE INDEX "uniq_effective_slot"
  ON "space_occupancy"("spaceId","termId","week","dayOfWeek","period")
  WHERE "status" IN ('PENDING','EFFECTIVE');

-- 7. asset
ALTER TABLE "asset" ALTER COLUMN "assetType" TYPE TEXT USING "assetType"::text;
ALTER TABLE "asset" ADD CONSTRAINT "asset_assetType_check" CHECK ("assetType" IN ('MATERIAL','LAB_EQUIP','SPORT_EQUIP'));
ALTER TABLE "asset" ALTER COLUMN "status" TYPE TEXT USING "status"::text;
ALTER TABLE "asset" ALTER COLUMN "status" SET DEFAULT 'IN_STOCK';
ALTER TABLE "asset" ADD CONSTRAINT "asset_status_check" CHECK ("status" IN ('IN_STOCK','LENT','REPAIRING','SCRAPPED'));

-- 8. alert.status
ALTER TABLE "alert" ALTER COLUMN "status" TYPE TEXT USING "status"::text;
ALTER TABLE "alert" ALTER COLUMN "status" SET DEFAULT 'OPEN';
ALTER TABLE "alert" ADD CONSTRAINT "alert_status_check" CHECK ("status" IN ('OPEN','CLOSED'));

-- 9. event_outbox.status
ALTER TABLE "event_outbox" ALTER COLUMN "status" TYPE TEXT USING "status"::text;
ALTER TABLE "event_outbox" ALTER COLUMN "status" SET DEFAULT 'PENDING';
ALTER TABLE "event_outbox" ADD CONSTRAINT "event_outbox_status_check" CHECK ("status" IN ('PENDING','SENT','FAILED'));

-- 10. 列已全部改为 TEXT，枚举类型不再被引用，drop 之
DROP TYPE "DataScope";
DROP TYPE "OwnershipType";
DROP TYPE "DeptType";
DROP TYPE "SpaceType";
DROP TYPE "SpaceStatus";
DROP TYPE "AssetType";
DROP TYPE "AssetStatus";
DROP TYPE "OccupancySource";
DROP TYPE "OccupancyStatus";
DROP TYPE "AccountStatus";
DROP TYPE "StudentStatus";
DROP TYPE "AlertStatus";
DROP TYPE "OutboxStatus";
