import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { INestApplication } from '@nestjs/common';

export function setupSwagger(app: INestApplication) {
  const config = new DocumentBuilder()
    .setTitle('生命线AI感知云平台 API')
    .setDescription('IoT 设备管理、AI 分析、告警和遥测数据的开放 API')
    .setVersion('1.0')
    .addBearerAuth()
    .addApiKey({ type: 'apiKey', name: 'X-API-Key', in: 'header' }, 'api-key')
    .addTag('Auth', '认证授权')
    .addTag('Users', '用户管理')
    .addTag('Roles', '角色管理')
    .addTag('Projects', '项目管理')
    .addTag('Devices', '设备管理')
    .addTag('AI Models', 'AI 模型管理')
    .addTag('AI Analysis', 'AI 分析')
    .addTag('Monitoring', '设备监控')
    .addTag('Alerts', '告警管理')
    .addTag('Telemetry', '遥测数据')
    .addTag('API Keys', 'API Key 管理')
    .addTag('Webhooks', 'Webhook 管理')
    .addTag('Open API', '开放 API')
    .addTag('API Logs', 'API 调用日志')
    .build();

  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('api/docs', app, document, {
    swaggerOptions: {
      persistAuthorization: true,
      tagsSorter: 'alpha',
      operationsSorter: 'method',
    },
  });
}
