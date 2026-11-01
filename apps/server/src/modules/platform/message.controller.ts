import { Controller, Get, Param, Post, Req } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { Menu } from '../../common/decorators/require.decorator';
import { DataScope } from '../../common/decorators/data-scope.decorator';
import { ScopeRequest } from '../../common/auth/data-scope.guard';
import { BizException } from '../../common/errors/biz-exception';

/**
 * 平台 · 消息中心（M0105）
 * 站内消息仅接收人本人可见（SELF，按 receiverId 过滤），越权 40304（AC-MS7-1）。
 * 业务对象已读回执以业务端为唯一真源，这里只维护站内信自身已读（AC-MS8-1）。
 */
@Controller('api/messages')
export class MessageController {
  constructor(private readonly prisma: PrismaService) {}

  @Get()
  @Menu('M0105')
  @DataScope({
    resource: 'message',
    ownership: 'ORG',
    ownerField: 'receiverId',
    forceSelf: true, // 站内信恒本人可见（AC-MS7-1）：ALL 角色不得读全校、GRADE 角色不生成非法 dept 条件
  })
  async list(@Req() req: ScopeRequest) {
    const where = req.scopeQuery!.buildWhere();
    return this.prisma.message.findMany({ where, orderBy: { createdAt: 'desc' }, take: 50 });
  }

  @Post(':id/read')
  @Menu('M0105')
  @DataScope({
    resource: 'message',
    ownership: 'ORG',
    ownerField: 'receiverId',
    forceSelf: true,
  })
  async read(@Req() req: ScopeRequest, @Param('id') id: string) {
    const msg = await this.prisma.message.findUnique({ where: { id } });
    if (!msg || !req.scopeQuery!.matchRecord(msg as unknown as Record<string, unknown>)) {
      throw BizException.notFound();
    }
    await this.prisma.message.update({ where: { id }, data: { read: true } });
    return { read: true, id };
  }
}
