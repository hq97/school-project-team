import { IsInt, Max, Min } from 'class-validator';

/**
 * 场地时段入参 DTO（门禁4 审计修正：DTO 从 interface 改为 class）。
 *
 * Slot 的 week/dayOfWeek/period 是占用判定的三要素，直接拼进
 * 部分唯一索引与 40901 预检——必须约束为合法整数区间，
 * 否则负数/超界值会污染占用矩阵并可能误触发唯一索引冲突。
 */
export class BookSlotDto {
  @IsInt()
  @Min(1)
  @Max(60) // 一学期周次上界，防全表扫
  week: number;

  @IsInt()
  @Min(1)
  @Max(7)
  dayOfWeek: number;

  @IsInt()
  @Min(1)
  @Max(12) // 节次上界（骨架按 10 节渲染，留余量给晚自习）
  period: number;

  /** Query 参数解析：非数字/越界统一回落到第 1 周（宽松只读接口，不 500） */
  static parseWeek(raw: string | undefined): number {
    const n = Number(raw ?? '1');
    return Number.isInteger(n) && n >= 1 && n <= 60 ? n : 1;
  }
}
