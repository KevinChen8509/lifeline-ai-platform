import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AiModel } from './ai-model.entity';
import { DeviceModelBinding } from './device-model-binding.entity';
import { AiModelVersion } from './ai-model-version.entity';
import { ModelDeployment, DeviceDeployment } from './model-deployment.entity';
import { AiModelService } from './ai-model.service';
import { AiModelController } from './ai-model.controller';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      AiModel,
      DeviceModelBinding,
      AiModelVersion,
      ModelDeployment,
      DeviceDeployment,
    ]),
  ],
  controllers: [AiModelController],
  providers: [AiModelService],
  exports: [AiModelService],
})
export class AiModelModule {}
