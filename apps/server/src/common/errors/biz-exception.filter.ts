import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  Logger,
} from '@nestjs/common';
import { Request, Response } from 'express';
import { BizCodeValue } from './biz-codes';

/**
 * 统一错误体：{ code, message, data, traceId, timestamp, path }
 * 40301–40304 / 40901–40904 均由前端 axios 拦截器统一提示（docs/16 决策），
 * 因此这里不做二次包装，保证 code 原样透出。
 */
@Catch()
export class BizExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger('BizException');

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const res = ctx.getResponse<Response>();
    const req = ctx.getRequest<Request & { traceId?: string }>();

    const traceId =
      req.traceId ??
      `t-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;

    if (exception instanceof HttpException) {
      const status = exception.getStatus();
      const payload = exception.getResponse() as
        | { code?: BizCodeValue; message?: string | string[]; data?: unknown }
        | string;

      if (typeof payload === 'object' && payload.code) {
        res.status(status).json({
          code: payload.code,
          message: payload.message,
          data: payload.data,
          traceId,
          timestamp: new Date().toISOString(),
          path: req.url,
        });
        return;
      }

      res.status(status).json({
        code: status,
        message: typeof payload === 'string' ? payload : payload.message ?? '请求失败',
        traceId,
        timestamp: new Date().toISOString(),
        path: req.url,
      });
      return;
    }

    this.logger.error(`未捕获异常 traceId=${traceId}`, exception as Error);
    res.status(500).json({
      code: 500,
      message: '服务内部错误',
      traceId,
      timestamp: new Date().toISOString(),
      path: req.url,
    });
  }
}
