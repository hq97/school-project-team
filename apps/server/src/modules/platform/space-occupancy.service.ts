import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { BizException } from '../../common/errors/biz-exception';
import { ConflictDetail } from '../../common/errors/biz-exception';
import { Principal } from '../../common/scope/scope.types';
import { effectiveScopeOf } from '../../common/scope/data-scope.options';
import { Prisma } from '@prisma/client';

export interface Slot {
  week: number;
  dayOfWeek: number;
  period: number;
}

const DAYS = ['一', '二', '三', '四', '五', '六', '日'];

/**
 * 占用判定单一真源（ADR §4.2）：
 * `GET /api/spaces/:id/free-slots`、`GET /api/spaces/:id/occupancies`、预约提交的 **40901 预检**
 * 三者共用本服务的同一套查询（SCHEDULE ∪ RESERVATION 有效占用，排除 CANCELLED）。
 * 占用口径任何变更只改这一处，避免「矩阵显示空闲但提交返 40901」。
 */
@Injectable()
export class SpaceOccupancyService {
  private readonly logger = new Logger('SpaceOccupancyService');

  constructor(private readonly prisma: PrismaService) {}

  /** 服务端渲染时段文案：前端不得本地拼接（ADR §3.2） */
  renderSlotText(slot: Slot): string {
    return `第${slot.week}周 周${DAYS[slot.dayOfWeek - 1] ?? slot.dayOfWeek} 第${slot.period}节`;
  }

  private async effective(spaceId: string, termId: string, week: number) {
    return this.prisma.space_occupancy.findMany({
      where: {
        spaceId,
        termId,
        week,
        status: { in: ['PENDING', 'EFFECTIVE'] }, // 与部分唯一索引同口径
      },
    });
  }

  /**
   * 占用矩阵：只承载状态，不承载占用人身份（docs/16 §2.2 决策 3）
   * 返回 key = `${dayOfWeek}-${period}`
   */
  async occupancyMatrix(
    spaceId: string,
    termId: string,
    week: number,
  ): Promise<Record<string, 'FREE' | 'SCHEDULE' | 'RESERVED'>> {
    const rows = await this.effective(spaceId, termId, week);
    const map: Record<string, 'SCHEDULE' | 'RESERVED'> = {};
    for (const r of rows) {
      map[`${r.dayOfWeek}-${r.period}`] = r.source === 'SCHEDULE' ? 'SCHEDULE' : 'RESERVED';
    }
    // 骨架：节次范围取自排课基础数据，默认 1..10
    const result: Record<string, 'FREE' | 'SCHEDULE' | 'RESERVED'> = {};
    for (let d = 1; d <= 7; d++) {
      for (let p = 1; p <= 10; p++) {
        result[`${d}-${p}`] = map[`${d}-${p}`] ?? 'FREE';
      }
    }
    return result;
  }

  /** 空闲时段：与占用矩阵、40901 预检同一口径 */
  async freeSlots(
    spaceId: string,
    termId: string,
    week: number,
  ): Promise<{ slot: Slot; slotText: string }[]> {
    const matrix = await this.occupancyMatrix(spaceId, termId, week);
    const out: { slot: Slot; slotText: string }[] = [];
    for (const [key, state] of Object.entries(matrix)) {
      if (state !== 'FREE') continue;
      const [dayOfWeek, period] = key.split('-').map(Number);
      const slot: Slot = { week, dayOfWeek, period };
      out.push({ slot, slotText: this.renderSlotText(slot) });
    }
    return out.sort((a, b) => a.slot.dayOfWeek - b.slot.dayOfWeek || a.slot.period - b.slot.period);
  }

  /**
   * 40901 预检 + 占用明细脱敏（ADR §3.2 越权红线）：
   * 范围覆盖才下发「班级+学科+教师」，否则只给占位文案 + hasOccupancyDetail=false
   */
  async assertNoScheduleConflict(
    principal: Principal,
    spaceId: string,
    slot: Slot,
  ): Promise<void> {
    const hit = await this.prisma.space_occupancy.findFirst({
      where: {
        spaceId,
        termId: principal.currentTermId,
        week: slot.week,
        dayOfWeek: slot.dayOfWeek,
        period: slot.period,
        status: { in: ['PENDING', 'EFFECTIVE'] },
      },
    });
    if (!hit) return;

    const slotText = this.renderSlotText(slot);
    const space = await this.prisma.space.findUnique({ where: { id: spaceId } });

    if (hit.source === 'RESERVATION') {
      throw BizException.reservationConflict({
        spaceName: space?.spaceName ?? '',
        slotText,
        hasOccupancyDetail: false, // 他人预约信息一律不下发
        suggestions: [],
      });
    }

    // SCHEDULE：按数据范围决定是否下发占用明细
    const entry = hit.bizRefId
      ? await this.prisma.timetable_entry.findUnique({ where: { id: hit.bizRefId } })
      : null;
    // 门禁4 审计修正：必须用 effectiveScopeOf（角色范围不完整时按 SELF 兜底，AC-RBAC-2），
    // 直读 principal.scope 会绕过降级，让范围不全的角色拿到占用明细（红线失效）。
    const effScope = effectiveScopeOf(principal);
    const canSee =
      effScope === 'ALL' ||
      (effScope === 'GRADE' && !!entry && principal.visibleDeptIds.includes(entry.classId));

    const suggestions = await this.freeSlots(spaceId, principal.currentTermId, slot.week);
    const detail: ConflictDetail = {
      spaceName: space?.spaceName ?? '',
      slotText,
      hasOccupancyDetail: canSee && !!entry,
      suggestions: suggestions.slice(0, 6),
    };
    if (canSee && entry) {
      // 门禁4 审计修正：occupiedBy 下发**可读名**（班级名 + 学科 + 任课教师），
      // 原实现拼 `entry.classId`（内部 UUID），对前端无意义且暴露内部标识。
      const [klass, teacher] = await Promise.all([
        this.prisma.sys_dept.findUnique({ where: { id: entry.classId }, select: { name: true } }),
        this.prisma.sys_user.findUnique({
          where: { id: entry.teacherId },
          select: { realName: true },
        }),
      ]);
      detail.occupiedBy = [klass?.name, entry.subject, teacher?.realName]
        .filter(Boolean)
        .join(' · ');
    }
    if (!canSee) {
      this.logger.warn(
        `[脱敏] userId=${principal.userId} 范围不足，40901 不下发占用明细（space=${spaceId}）`,
      );
    }
    throw BizException.scheduleConflict(detail);
  }

  /**
   * 预约占用写入（C9 ②）：命中部分唯一索引即竞争失败 → 40902
   */
  async reserve(
    principal: Principal,
    spaceId: string,
    slot: Slot,
    bizRefId: string,
  ): Promise<{ bookingId: string }> {
    await this.assertNoScheduleConflict(principal, spaceId, slot);
    try {
      const row = await this.prisma.space_occupancy.create({
        data: {
          spaceId,
          termId: principal.currentTermId,
          week: slot.week,
          dayOfWeek: slot.dayOfWeek,
          period: slot.period,
          source: 'RESERVATION',
          bizRefId,
          status: 'EFFECTIVE',
          createdBy: principal.userId,
        },
      });
      return { bookingId: row.id };
    } catch (e) {
      if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === 'P2002') {
        const space = await this.prisma.space.findUnique({ where: { id: spaceId } });
        const suggestions = await this.freeSlots(spaceId, principal.currentTermId, slot.week);
        throw BizException.reservationConflict({
          spaceName: space?.spaceName ?? '',
          slotText: this.renderSlotText(slot),
          hasOccupancyDetail: false,
          suggestions: suggestions.slice(0, 6),
        });
      }
      throw e;
    }
  }
}
