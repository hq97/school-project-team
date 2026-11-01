import { HttpException } from '@nestjs/common';
import { BizCode, BizCodeValue, BIZ_CODE_MESSAGE, httpStatusOf } from './biz-codes';

/** 冲突详情：occupiedBy 为 undefined 即「范围不足 → 只下发占位文案」（ADR §3.2 越权红线） */
export interface ConflictDetail {
  spaceName: string;
  slotText: string; // 服务端渲染，前端不拼接
  occupiedBy?: string;
  hasOccupancyDetail: boolean; // 布尔标志：true 才可渲染「查看该场地课表」（禁止字符串比较）
  suggestions?: { slot: { week: number; dayOfWeek: number; period: number }; slotText: string }[];
}

export interface StockDetail {
  name: string;
  availableQty: number; // 扣减失败当次事务内实时读值，不经缓存
  requestedQty: number;
}

export interface QuotaDetail {
  name: string;
  quota: number;
}

export interface BizErrorData {
  conflict?: ConflictDetail;
  stock?: StockDetail;
  quota?: QuotaDetail;
}

/**
 * 统一业务异常。data 携带结构化详情，供前端三段式渲染（失败原因 / 冲突详情 / 一键恢复）。
 */
export class BizException extends HttpException {
  public readonly bizCode: BizCodeValue;
  public readonly data?: BizErrorData;

  constructor(bizCode: BizCodeValue, data?: BizErrorData, message?: string) {
    super(
      {
        code: bizCode,
        message: message ?? BIZ_CODE_MESSAGE[bizCode],
        data,
      },
      httpStatusOf(bizCode),
    );
    this.bizCode = bizCode;
    this.data = data;
  }

  static menuDenied(): BizException {
    return new BizException(BizCode.NO_MENU_PERM);
  }

  static actionDenied(): BizException {
    return new BizException(BizCode.NO_ACTION_PERM);
  }

  static scopeDenied(): BizException {
    return new BizException(BizCode.DATA_SCOPE_DENIED);
  }

  /** 单条越权 / 不存在：统一 40304 防枚举（docs/04 §4.3） */
  static notFound(): BizException {
    return new BizException(BizCode.RESOURCE_NOT_FOUND);
  }

  static scheduleConflict(detail: ConflictDetail): BizException {
    return new BizException(BizCode.SPACE_SCHEDULE_CONFLICT, { conflict: detail });
  }

  static reservationConflict(detail: ConflictDetail): BizException {
    return new BizException(BizCode.SPACE_RESERVATION_CONFLICT, { conflict: detail });
  }

  static stockShort(detail: StockDetail): BizException {
    return new BizException(BizCode.STOCK_SHORT, { stock: detail });
  }

  static quotaFull(detail: QuotaDetail): BizException {
    return new BizException(BizCode.QUOTA_FULL, { quota: detail });
  }
}
