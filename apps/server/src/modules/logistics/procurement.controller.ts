import { Body, Controller, Get, Param, Post, Req } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { Button, Menu } from '../../common/decorators/require.decorator';
import { DataScope } from '../../common/decorators/data-scope.decorator';
import { ScopeRequest } from '../../common/auth/data-scope.guard';
import { BizException } from '../../common/errors/biz-exception';

/** 后勤域 · 采购（M02）归属字段：creatorId + creatorDeptId（docs/04 §4.1） */
@Controller('api/procurement')
export class ProcurementController {
  constructor(private readonly prisma: PrismaService) {}

  @Get('projects')
  @Menu('M0202')
  @DataScope({
    resource: 'procurement_project',
    ownership: 'ORG',
    ownerField: 'creatorId',
    deptField: 'creatorDeptId',
    termField: 'termId',
  })
  async list(@Req() req: ScopeRequest) {
    const where = req.scopeQuery!.buildWhere();
    return this.prisma.procurement_project.findMany({ where, take: 50 });
  }

  @Get('projects/:id')
  @Menu('M0202')
  @DataScope({
    resource: 'procurement_project',
    ownership: 'ORG',
    ownerField: 'creatorId',
    deptField: 'creatorDeptId',
  })
  async detail(@Req() req: ScopeRequest, @Param('id') id: string) {
    const record = await this.prisma.procurement_project.findUnique({ where: { id } });
    // 单条越权统一 40304 防枚举（docs/04 §4.3）
    if (!record || !req.scopeQuery!.matchRecord(record as unknown as Record<string, unknown>)) {
      throw BizException.notFound();
    }
    return record;
  }

  /** 写操作：强制 creatorId = 当前用户，忽略前端传入（防伪造归属人） */
  @Post('projects')
  @Menu('M0202')
  @Button('BTN-PUR-NEW')
  @DataScope({
    resource: 'procurement_project',
    ownership: 'ORG',
    ownerField: 'creatorId',
    deptField: 'creatorDeptId',
    write: true,
  })
  async create(@Req() req: ScopeRequest, @Body() body: Record<string, unknown>) {
    const payload = req.scopeQuery!.forceOwner(body);
    return payload; // 骨架：落库与状态机由业务片实现
  }

  /** 全校汇总（校长/校领导）：ALL 才放行 */
  @Get('dashboard/summary')
  @Menu('M0201')
  @DataScope({ resource: 'procurement_project', ownership: 'ORG', requiredScope: 'ALL' })
  async summary(@Req() req: ScopeRequest) {
    const where = req.scopeQuery!.buildWhere();
    return this.prisma.procurement_project.count({ where });
  }
}
