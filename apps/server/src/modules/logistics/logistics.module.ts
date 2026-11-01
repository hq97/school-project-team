import { Module } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { ProcurementController } from './procurement.controller';
import { MaterialController } from './material.controller';
import { RepairController } from './repair.controller';

@Module({
  controllers: [ProcurementController, MaterialController, RepairController],
  providers: [PrismaService],
})
export class LogisticsModule {}
