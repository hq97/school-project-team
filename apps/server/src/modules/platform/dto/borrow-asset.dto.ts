import { IsInt, IsOptional, IsString, Max, MaxLength, Min } from 'class-validator';

/**
 * 借用/领用数量入参 DTO（门禁4 审计修正：DTO 从 interface 改为 class）。
 *
 * 背景：ValidationPipe 对 **interface** 元类型不做任何校验直接跳过，
 * 服务端等同于零入参校验——`quantity: -100` 会让 `availableQty: { gte: -100 }` 恒真、
 * `decrement: -100` 反向把库存 +100。故所有数值入参必须是 class + 装饰器。
 */
export class BorrowAssetDto {
  /** 数量：正整数。上界 9999 防一次性把库存冲爆 */
  @IsInt()
  @Min(1)
  @Max(9999)
  quantity: number;

  /** 关联申请单号（C9 ① 幂等键，可选） */
  @IsOptional()
  @IsString()
  @MaxLength(64)
  applyNo?: string;
}
