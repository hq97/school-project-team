import { Module } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { SpaceOccupancyService } from './space-occupancy.service';
import { SpaceController } from './space.controller';
import { AssetController } from './asset.controller';
import { DictController } from './dict.controller';
import { MessageController } from './message.controller';

@Module({
  controllers: [SpaceController, AssetController, DictController, MessageController],
  providers: [PrismaService, SpaceOccupancyService],
  exports: [SpaceOccupancyService],
})
export class PlatformModule {}
