import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Device } from '../device/device.entity';
import { DeviceModelBinding } from '../ai-model/device-model-binding.entity';
import { ModelDeployment, DeviceDeployment } from '../ai-model/model-deployment.entity';
import { AiModel } from '../ai-model/ai-model.entity';
import { MonitoringController } from './monitoring.controller';
import { MonitoringService } from './monitoring.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      Device,
      DeviceModelBinding,
      ModelDeployment,
      DeviceDeployment,
      AiModel,
    ]),
  ],
  controllers: [MonitoringController],
  providers: [MonitoringService],
  exports: [MonitoringService],
})
export class MonitoringModule {}
