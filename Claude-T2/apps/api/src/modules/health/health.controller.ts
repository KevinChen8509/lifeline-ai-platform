import { Controller, Get } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';

@ApiTags('健康检查')
@Controller()
export class HealthController {
  @Get('health')
  @ApiOperation({ summary: '健康检查', description: '检查API服务是否正常运行' })
  @ApiResponse({
    status: 200,
    description: '服务正常',
    schema: {
      type: 'object',
      properties: {
        code: { type: 'number', example: 0 },
        message: { type: 'string', example: 'success' },
        data: {
          type: 'object',
          properties: {
            status: { type: 'string', example: 'ok' },
            timestamp: { type: 'string', example: '2026-03-27T10:00:00.000Z' },
          },
        },
        timestamp: { type: 'string', example: '2026-03-27T10:00:00.000Z' },
      },
    },
  })
  check() {
    return {
      status: 'ok',
      timestamp: new Date().toISOString(),
    };
  }

  @Get()
  @ApiOperation({ summary: 'API根路径', description: '返回API基本信息' })
  root() {
    return {
      name: '生命线AI感知云平台 API',
      version: '1.0.0',
      description: 'Cloud-Edge-Device IoT Platform for Urban Lifeline Infrastructure',
      docs: '/api/docs',
      health: '/api/health',
    };
  }
}
