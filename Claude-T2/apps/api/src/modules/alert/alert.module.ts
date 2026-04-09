import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import {
  Alert,
  AlertStatusHistory,
  WorkOrder,
  AlertNotification,
} from './alert.entity';
import { AlertController } from './alert.controller';
import { AlertService } from './alert.service';
import { AuditModule } from '../audit/audit.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      Alert,
      AlertStatusHistory,
      WorkOrder,
      AlertNotification,
    ]),
    AuditModule,
  ],
  controllers: [AlertController],
  providers: [AlertService],
  exports: [AlertService],
})
export class AlertModule {}
