import { Body, Controller, Get, Logger, Param, Post, Query, Req } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { Button, Menu } from '../../common/decorators/require.decorator';
import { DataScope } from '../../common/decorators/data-scope.decorator';
import { ScopeRequest } from '../../common/auth/data-scope.guard';
import { BizException } from '../../common/errors/biz-exception';
import { Principal, ScopeLevel } from '../../common/scope/scope.types';
import { SpaceOccupancyService, Slot } from './space-occupancy.service';
import { BookSlotDto } from './dto/book-slot.dto';

/**
 * 平台 · 统一场地 space（组织型归属，docs/04 §3.11）
 *
 * 门禁4 审计修正：原实现三条路由声明了 @DataScope 却完全没用（还 `void where`），
 * 任意已认证用户可遍历任意场地占用矩阵、对任意场地写预约，`space.openScope` 定义了从未校验。
 *
 * 归属语义澄清（重要）：占用/预约路由的记录级门禁**不是用户数据范围（ORG），而是场地的
 * 开放范围 `openScope`（docs/04 §3.11：ALL=全校 / GRADE=本年级 / SELF=归属部门）**。
 * 若按 ORG × SELF 判定，普通教师（SELF）将无法预约任何会议室——而 BTN-PARTY-ROOM-BOOK
 * 在 docs/03 §3.2 明确授给教师。故这三条路由声明 `ownership:'PUBLIC'`（记录级过滤由
 * `assertSpaceVisible()` 的 openScope 校验承担），list 管理视图仍走 ORG × 部门子树。
 */
@Controller('api/spaces')
export class SpaceController {
  private readonly logger = new Logger('SpaceController');

  constructor(
    private readonly prisma: PrismaService,
    private readonly occupancy: SpaceOccupancyService,
  ) {}

  @Get()
  @Menu('M1204')
  @DataScope({ resource: 'space', ownership: 'ORG', deptField: 'deptNodeId' })
  async list(@Req() req: ScopeRequest, @Query('spaceType') spaceType?: string) {
    const where = req.scopeQuery!.buildWhere(
      spaceType ? { spaceType: spaceType as never } : {},
    );
    // 只下发可预约状态；DISABLED 场地不进列表
    return this.prisma.space.findMany({
      where: { ...where, status: { in: ['AVAILABLE', 'REPAIRING'] } },
      orderBy: { spaceName: 'asc' },
      take: 50,
    });
  }

  /** 占用矩阵：只下发状态，不下发占用人身份；记录级门禁 = openScope 开放范围 */
  @Get(':id/occupancies')
  @Menu('M1204')
  @DataScope({ resource: 'space_occupancy', ownership: 'PUBLIC' })
  async occupancies(
    @Req() req: ScopeRequest,
    @Param('id') id: string,
    @Query('week') week = '1',
  ) {
    const space = await this.loadVisibleSpace(req, id);
    return this.occupancy.occupancyMatrix(
      space.id,
      req.principal!.currentTermId,
      BookSlotDto.parseWeek(week),
    );
  }

  /** 空闲时段：与占用矩阵、40901 预检同一口径；记录级门禁 = openScope 开放范围 */
  @Get(':id/free-slots')
  @Menu('M1204')
  @DataScope({ resource: 'space_occupancy', ownership: 'PUBLIC' })
  async freeSlots(
    @Req() req: ScopeRequest,
    @Param('id') id: string,
    @Query('week') week = '1',
  ) {
    const space = await this.loadVisibleSpace(req, id);
    return this.occupancy.freeSlots(
      space.id,
      req.principal!.currentTermId,
      BookSlotDto.parseWeek(week),
    );
  }

  /** 提交预约：40901 课表占用 / 40902 并发竞争；场地须 AVAILABLE 且在开放范围内 */
  @Post(':id/occupancies')
  @Menu('M1204')
  @Button('BTN-PARTY-ROOM-BOOK')
  @DataScope({ resource: 'space_occupancy', ownership: 'PUBLIC' })
  async book(@Req() req: ScopeRequest, @Param('id') id: string, @Body() body: BookSlotDto) {
    const space = await this.loadVisibleSpace(req, id);
    if (space.status !== 'AVAILABLE') {
      throw BizException.notFound(); // 40304：不可预约状态统一按"不可见"处理，防枚举
    }
    const slot: Slot = {
      week: body.week,
      dayOfWeek: body.dayOfWeek,
      period: body.period,
    };
    return this.occupancy.reserve(req.principal!, space.id, slot, 'meeting_room_booking');
  }

  /**
   * 取场地并做可见性判定。读取在路由方法内完成（配合 no-raw-sql 规则的方法级判定），
   * 判定本身是纯函数：openScope 开放范围（docs/04 §3.11）。
   * 不通过 / 不存在 → 40304（与"不存在"同码防枚举）。
   */
  private async loadVisibleSpace(req: ScopeRequest, id: string) {
    const space = await this.prisma.space.findUnique({ where: { id } });
    if (!space) throw BizException.notFound();
    // 有效范围取自 scopeQuery（含 AC-RBAC-2 SELF 降级 + 告警），不直读 principal.scope
    const eff: ScopeLevel = req.scopeQuery
      ? req.scopeQuery.effectiveScope()
      : 'SELF'; // 理论不可达：启动期扫描保证必然有 @DataScope 声明，这里按最小权限兜底
    this.assertSpaceVisible(req.principal!, space, eff);
    return space;
  }

  /** 纯判定（不碰库）：openScope 开放范围 + 部门归属 */
  private assertSpaceVisible(
    principal: Principal,
    space: { id: string; openScope: string; deptNodeId: string | null },
    eff: ScopeLevel,
  ): void {
    const visible = principal.visibleDeptIds.includes(space.deptNodeId ?? '__NO_DEPT__');
    const openOk =
      space.openScope === 'ALL' ||
      (space.openScope === 'GRADE' && eff !== 'SELF' && visible) ||
      (space.openScope === 'SELF' && visible);
    if (!openOk) {
      this.logger.warn(
        `[space] userId=${principal.userId} 访问空间 spaceId=${space.id} 超出开放范围 openScope=${space.openScope}`,
      );
      throw BizException.notFound();
    }
  }
}
