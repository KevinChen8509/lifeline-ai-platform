import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import {
  DeviceTelemetry,
  BackupConfig,
  BackupLog,
  ArchivedDataMeta,
} from './telemetry.entity';
import { TelemetryController } from './telemetry.controller';
import { TelemetryService } from './telemetry.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      DeviceTelemetry,
      BackupConfig,
      BackupLog,
      ArchivedDataMeta,
    ]),
  ],
  controllers: [TelemetryController],
  providers: [TelemetryService],
  exports: [TelemetryService],
})
export class TelemetryModule {}
