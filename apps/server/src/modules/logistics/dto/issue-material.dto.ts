import { IsInt, IsString, Max, Min } from 'class-validator';

/**
 * 发放/出库入参 DTO（门禁4 审计修正：DTO 从 interface 改为 class）。
 *
 * ValidationPipe 对 interface 不做校验；`quantity = -100` 会让
 * `updateMany({ where: { availableQty: { gte: -100 } } })` 恒真 + `decrement: -100`
 * **反向把库存加 100**，属于可静默破坏台账的入参漏洞。
 */
export class IssueMaterialDto {
  @IsString()
  assetId: string;

  @IsInt()
  @Min(1)
  @Max(9999)
  quantity: number;
}
