import { Module, forwardRef } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Device } from './device.entity';
import { DeviceStatusHistory } from './device-status-history.entity';
import { DeviceModelBinding } from '../ai-model/device-model-binding.entity';
import { AiModel } from '../ai-model/ai-model.entity';
import { DeviceService } from './device.service';
import { DeviceController } from './device.controller';
import { ProjectModule } from '../project/project.module';
import { AuditModule } from '../audit/audit.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([Device, DeviceStatusHistory, DeviceModelBinding, AiModel]),
    forwardRef(() => ProjectModule),
    AuditModule,
  ],
  controllers: [DeviceController],
  providers: [DeviceService],
  exports: [DeviceService, TypeOrmModule],
})
export class DeviceModule {}
