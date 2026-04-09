import { Module, OnModuleInit } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import {
  ReportTemplate,
  Report,
  ScheduledReport,
  ReportDeliveryLog,
} from './dashboard.entity';
import {
  DashboardController,
  StatisticsController,
  ReportTemplateController,
  ReportController,
  ScheduledReportController,
  SystemController,
} from './dashboard.controller';
import { DashboardService } from './dashboard.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      ReportTemplate,
      Report,
      ScheduledReport,
      ReportDeliveryLog,
    ]),
  ],
  controllers: [
    DashboardController,
    StatisticsController,
    ReportTemplateController,
    ReportController,
    ScheduledReportController,
    SystemController,
  ],
  providers: [DashboardService],
  exports: [DashboardService],
})
export class DashboardModule implements OnModuleInit {
  constructor(private readonly dashboardService: DashboardService) {}

  async onModuleInit() {
    await this.dashboardService.initDefaultTemplates();
  }
}
