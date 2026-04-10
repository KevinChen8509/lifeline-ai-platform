import { ApiProperty } from '@nestjs/swagger';

export class DeviceStatsDto {
  @ApiProperty({ description: '总设备数', example: 100 })
  totalDevices: number;

  @ApiProperty({ description: '在线设备数', example: 85 })
  onlineDevices: number;

  @ApiProperty({ description: '离线设备数', example: 10 })
  offlineDevices: number;

  @ApiProperty({ description: '告警设备数', example: 5 })
  alertDevices: number;
}

export class RecentActivityDto {
  @ApiProperty({ description: '活动ID' })
  id: string;

  @ApiProperty({ description: '活动类型' })
  type: string;

  @ApiProperty({ description: '活动描述' })
  description: string;

  @ApiProperty({ description: '活动时间' })
  timestamp: Date;
}

export class ProjectOverviewDto {
  @ApiProperty({ description: '设备统计', type: DeviceStatsDto })
  stats: DeviceStatsDto;

  @ApiProperty({ description: '最近活动', type: [RecentActivityDto] })
  recentActivities: RecentActivityDto[];
}
