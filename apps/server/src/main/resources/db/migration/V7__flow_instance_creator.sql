-- V7：审批流引擎补充字段
-- 1. flow_instance 补 creatorId（AC-D3-4「审批人数据范围须覆盖申请单归属」需要发起人身份做归属判定）。
-- 2. templateSnap 由 JSONB 改 TEXT：快照仅「写入后只读」，无需 JSONB 内部字段查询；
--    JSONB 列 + MyBatis-Plus setString 绑定 varchar 会报「is of type jsonb but expression is of type character varying」
--    （与 V6 枚举同源问题），统一走 TEXT 路线，读时由应用层 Jackson 解析。
ALTER TABLE "flow_instance" ADD COLUMN "creatorId" TEXT;
ALTER TABLE "flow_instance" ALTER COLUMN "templateSnap" TYPE TEXT USING "templateSnap"::text;
