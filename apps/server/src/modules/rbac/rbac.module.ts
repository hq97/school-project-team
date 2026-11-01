import { Module } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { OwnershipResolver } from '../../common/scope/ownership-resolver';
import { RolesController } from './roles.controller';
import { RoleService } from './roles.service';

@Module({
  controllers: [RolesController],
  providers: [PrismaService, OwnershipResolver, RoleService],
  exports: [RoleService],
})
export class RbacModule {}
