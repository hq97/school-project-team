-- V9：报修工单补时间戳（响应时长统计 + 完成时间），供 M0404 报修响应统计与异常提醒实时计算。
ALTER TABLE "repair_ticket" ADD COLUMN IF NOT EXISTS "assignedAt" TIMESTAMPTZ;
ALTER TABLE "repair_ticket" ADD COLUMN IF NOT EXISTS "completedAt" TIMESTAMPTZ;
