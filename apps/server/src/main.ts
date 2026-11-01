import { NestFactory, Reflector } from '@nestjs/core';
import { Logger, ValidationPipe } from '@nestjs/common';
import { AppModule } from './app.module';
import { BizExceptionFilter } from './common/errors/biz-exception.filter';
import { DataScopeStartupScanner } from './common/startup/data-scope-scanner';
import { GlobalAuthGuard } from './common/auth/global-auth.guard';

async function bootstrap(): Promise<void> {
  const logger = new Logger('Bootstrap');

  // 启动期强校验：生产缺 JWT_SECRET 拒绝启动（fail-closed，门禁4 审计项）
  GlobalAuthGuard.assertJwtSecretOrExit();

  const app = await NestFactory.create(AppModule, { bufferLogs: true });

  app.useGlobalFilters(new BizExceptionFilter());
  // 门禁4 审计修正：whitelist 剔除未声明字段 + forbidNonWhitelisted 直接 400 拒绝（防参数走私）；
  // DTO 必须是 class（interface 会被 ValidationPipe 跳过 → 零入参校验）
  app.useGlobalPipes(
    new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }),
  );
  app.setGlobalPrefix(''); // 路由前缀已在各 Controller 内声明为 /api/...

  // 启动期扫描：任一路由缺 @DataScope() 声明即拒绝启动（ADR §1.5 ②，fail-closed）
  const report = DataScopeStartupScanner.assertOrExit(app);
  logger.log(
    `DataScope 声明覆盖：${report.declared}/${report.totalRoutes}（缺失 ${report.missing.length}）`,
  );

  await app.listen(process.env.PORT ? Number(process.env.PORT) : 3000);
  logger.log(`server listening on ${await app.getUrl()}`);
}

void bootstrap();
