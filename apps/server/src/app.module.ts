import { Module } from '@nestjs/common';
import { APP_GUARD, Reflector } from '@nestjs/core';
import { PrismaService } from './common/prisma/prisma.service';
import { OwnershipResolver } from './common/scope/ownership-resolver';
import { GlobalAuthGuard } from './common/auth/global-auth.guard';
import { GlobalPermissionGuard } from './common/auth/global-permission.guard';
import { DataScopeGuard } from './common/auth/data-scope.guard';
import { RbacModule } from './modules/rbac/rbac.module';
import { LogisticsModule } from './modules/logistics/logistics.module';
import { PlatformModule } from './modules/platform/platform.module';
import { CrosscutModule } from './modules/crosscut/crosscut.module';

/**
 * 全局 Guard 注册顺序 = ADR §1.5 三层 fail-closed 的第一、二层：
 *   ① GlobalAuthGuard：认证 + 实时取角色/范围（401）
 *   ② GlobalPermissionGuard：菜单 40301 / 按钮 40302
 *   ③ DataScopeGuard：数据范围 40303 + 挂载 ScopeQueryBuilder（数据层唯一出口）
 * 注册为 APP_GUARD 即对所有路由生效，业务模块无法跳过（不经过管道到不了 Controller）。
 */
@Module({
  imports: [RbacModule, LogisticsModule, PlatformModule, CrosscutModule],
  providers: [
    PrismaService,
    OwnershipResolver,
    Reflector,
    { provide: APP_GUARD, useClass: GlobalAuthGuard },
    { provide: APP_GUARD, useClass: GlobalPermissionGuard },
    { provide: APP_GUARD, useClass: DataScopeGuard },
  ],
})
export class AppModule {}
