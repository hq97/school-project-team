import { Body, Controller, Get, Param, Patch, Post, Req } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { Button, Menu } from '../../common/decorators/require.decorator';
import { DataScope } from '../../common/decorators/data-scope.decorator';
import { ScopeRequest } from '../../common/auth/data-scope.guard';
import { BizException } from '../../common/errors/biz-exception';

/** 后勤域 · 报修（M04）归属字段：reporterId + reporterDeptId（docs/04 §4.1） */
@Controller('api/repair')
export class RepairController {
  constructor(private readonly prisma: PrismaService) {}

  @Get('tickets')
  @Menu('M0402')
  @DataScope({
    resource: 'repair_ticket',
    ownership: 'ORG',
    ownerField: 'reporterId',
    deptField: 'reporterDeptId',
    termField: 'termId',
  })
  async list(@Req() req: ScopeRequest) {
    const where = req.scopeQuery!.buildWhere();
    return this.prisma.repair_ticket.findMany({ where, take: 50 });
  }

  /** 提交报修：强制 reporterId = 当前用户 */
  @Post('tickets')
  @Menu('M0401')
  @Button('BTN-REP-SUBMIT')
  @DataScope({
    resource: 'repair_ticket',
    ownership: 'ORG',
    ownerField: 'reporterId',
    deptField: 'reporterDeptId',
    write: true,
  })
  async submit(@Req() req: ScopeRequest, @Body() body: Record<string, unknown>) {
    return req.scopeQuery!.forceOwner(body);
  }

  /** 分派/状态更新：仅后勤（ALL）+ 状态机合法流转 */
  @Patch('tickets/:id/status')
  @Menu('M0403')
  @Button('BTN-REP-STATUS')
  @DataScope({ resource: 'repair_ticket', ownership: 'ORG', requiredScope: 'ALL' })
  async updateStatus(
    @Req() req: ScopeRequest,
    @Param('id') id: string,
    @Body() body: { status?: string },
  ) {
    // 状态机合法流转（骨架口径：PENDING→APPROVING→COMPLETED，驳回可回 PENDING）
    const ALLOWED: Record<string, string[]> = {
      PENDING: ['APPROVING', 'REJECTED'],
      APPROVING: ['COMPLETED', 'REJECTED'],
      REJECTED: ['PENDING', 'CANCELLED'],
      COMPLETED: [],
      CANCELLED: [],
    };
    const next = body?.status;
    if (!next || !(next in ALLOWED)) throw BizException.actionDenied();

    const ticket = await this.prisma.repair_ticket.findUnique({ where: { id } });
    if (!ticket || !req.scopeQuery!.matchRecord(ticket as unknown as Record<string, unknown>)) {
      throw BizException.notFound(); // 40304 防枚举
    }
    if (!ALLOWED[ticket.status]?.includes(next)) {
      throw BizException.actionDenied(); // 非法流转 → 40302
    }
    await this.prisma.repair_ticket.update({ where: { id }, data: { status: next } });
    return { updated: true, status: next };
  }
}
