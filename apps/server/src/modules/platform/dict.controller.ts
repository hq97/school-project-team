import { Controller, Get, Query } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { DataScope } from '../../common/decorators/data-scope.decorator';

/**
 * 平台 · 字典项只读消费（docs/03 M0505）
 *
 * 注意：字典属平台级配置对象，不得作为大屏指标或下钻来源（AC-DD5-2）。
 *
 * 门禁4 审计项处置说明（**未采纳"加 @Menu('M0505')"，理由如下，已回 qa-audit 复核**）：
 *  - M0505「数据字典管理」在 docs/03 §3.1 矩阵里 **仅 R-ADMIN 可见**；若把只读消费也挂到 M0505，
 *    教师提交报修（DICT-FAULT-TYPE）、学生请假、体测录入等六域表单将取不到枚举，功能直接不可用。
 *  - 字典内容是**平台级业务枚举**（故障分类/学期/违纪等级/状态），不含任何个人数据，
 *    且本接口只返回 `enabled = true` 的项；真正的敏感操作是**维护**，由 BTN-DICT-EDIT（仅 R-ADMIN）管控。
 *  - 故本接口定位为「已认证用户只读」：认证由全局 GlobalAuthGuard 强制（未登录 401），
 *    PUBLIC 归属不做记录级过滤。如需给字典只读单独立菜单码，应由需求侧在 docs/03 增补
 *    （例如 M0508 字典只读消费），**不在开发侧擅自新增菜单语义**。
 */
@Controller('api/dicts')
export class DictController {
  constructor(private readonly prisma: PrismaService) {}

  @Get('items')
  @DataScope({ resource: 'sys_dict', ownership: 'PUBLIC' })
  async items(@Query('dictCode') dictCode: string) {
    return this.prisma.sys_dict.findMany({
      where: { dictCode, enabled: true },
      orderBy: { sort: 'asc' },
    });
  }

  /** 当前学期与周次：前端 CURRENT_WEEK 由此下发，不本地推算（同上：已认证只读） */
  @Get('current-term')
  @DataScope({ resource: 'sys_dict', ownership: 'PUBLIC' })
  async currentTerm() {
    const term = await this.prisma.sys_dict.findFirst({
      where: { dictCode: 'DICT-TERM', isCurrent: true, enabled: true },
    });
    return { termId: term?.itemCode ?? null, termName: term?.itemName ?? null };
  }
}
