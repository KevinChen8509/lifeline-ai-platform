import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication, ValidationPipe, Module } from '@nestjs/common';
import { getRepositoryToken } from '@nestjs/typeorm';
import { JwtModule, JwtService } from '@nestjs/jwt';
import { PassportModule } from '@nestjs/passport';
import { ConfigModule } from '@nestjs/config';
import * as request from 'supertest';

import { HttpExceptionFilter } from '../src/common/filters/http-exception.filter';
import { TransformInterceptor } from '../src/common/interceptors/transform.interceptor';

// Controllers
import { AuthController } from '../src/modules/auth/auth.controller';
import { UserController } from '../src/modules/user/user.controller';
import { ProjectController } from '../src/modules/project/project.controller';
import { DeviceController } from '../src/modules/device/device.controller';
import { AlertController } from '../src/modules/alert/alert.controller';
import { TelemetryController } from '../src/modules/telemetry/telemetry.controller';
import { DashboardController, StatisticsController, ReportTemplateController, ReportController, ScheduledReportController, SystemController } from '../src/modules/dashboard/dashboard.controller';

// Services
import { AuthService } from '../src/modules/auth/auth.service';
import { UserService } from '../src/modules/user/user.service';
import { ProjectService } from '../src/modules/project/project.service';
import { DeviceService } from '../src/modules/device/device.service';
import { AlertService } from '../src/modules/alert/alert.service';
import { TelemetryService } from '../src/modules/telemetry/telemetry.service';
import { DashboardService } from '../src/modules/dashboard/dashboard.service';

// Strategy & Guards
import { JwtStrategy } from '../src/modules/auth/strategies/jwt.strategy';
import { PermissionsGuard } from '../src/common/guards/permissions.guard';
import { AbilityFactory } from '../src/modules/auth/ability/ability.factory';
import { Reflector } from '@nestjs/core';

// Entities
import { User } from '../src/modules/user/user.entity';
import { Role } from '../src/modules/role/role.entity';
import { Permission } from '../src/modules/permission/permission.entity';
import { AuditLog } from '../src/modules/audit/audit-log.entity';
import { Project } from '../src/modules/project/project.entity';
import { ProjectUser } from '../src/modules/project/project-user.entity';
import { Device } from '../src/modules/device/device.entity';
import { DeviceStatusHistory } from '../src/modules/device/device-status-history.entity';
import { AiModel } from '../src/modules/ai-model/ai-model.entity';
import { AiModelVersion } from '../src/modules/ai-model/ai-model-version.entity';
import { ModelDeployment } from '../src/modules/ai-model/model-deployment.entity';
import { DeviceModelBinding } from '../src/modules/ai-model/device-model-binding.entity';
import { AiAnalysisResult } from '../src/modules/ai-analysis/ai-analysis-result.entity';
import { Alert, AlertStatusHistory, WorkOrder, AlertNotification } from '../src/modules/alert/alert.entity';
import { DeviceTelemetry, BackupConfig, BackupLog, ArchivedDataMeta } from '../src/modules/telemetry/telemetry.entity';
import { ApiKey, Webhook, WebhookDelivery, ApiCallLog } from '../src/modules/open-api/open-api.entity';
import { ReportTemplate, Report, ScheduledReport, ReportDeliveryLog } from '../src/modules/dashboard/dashboard.entity';

import { RedisService } from '../src/modules/redis/redis.service';
import { AuditLogService } from '../src/modules/audit/audit-log.service';
import { EmailService } from '../src/modules/email/email.service';
import { RoleService } from '../src/modules/role/role.service';
import { DataSource } from 'typeorm';

export const createMockRepo = (): any => ({
  create: jest.fn(),
  save: jest.fn(),
  find: jest.fn(),
  findOne: jest.fn(),
  delete: jest.fn(),
  update: jest.fn(),
  remove: jest.fn(),
  count: jest.fn(),
  createQueryBuilder: jest.fn(() => ({
    where: jest.fn().mockReturnThis(),
    andWhere: jest.fn().mockReturnThis(),
    orderBy: jest.fn().mockReturnThis(),
    skip: jest.fn().mockReturnThis(),
    take: jest.fn().mockReturnThis(),
    select: jest.fn().mockReturnThis(),
    addSelect: jest.fn().mockReturnThis(),
    groupBy: jest.fn().mockReturnThis(),
    limit: jest.fn().mockReturnThis(),
    leftJoin: jest.fn().mockReturnThis(),
    leftJoinAndSelect: jest.fn().mockReturnThis(),
    getManyAndCount: jest.fn().mockResolvedValue([[], 0]),
    getRawMany: jest.fn().mockResolvedValue([]),
    getMany: jest.fn().mockResolvedValue([]),
    getCount: jest.fn().mockResolvedValue(0),
  })),
});

export const testUser = {
  sub: 'test-user-id',
  username: 'testadmin',
  role: 'admin',
};

// Full user object for PermissionsGuard compatibility
const testUserWithRole = {
  ...testUser,
  role: { code: 'admin' },
};

const JWT_SECRET = 'your-super-secret-key-at-least-32-characters';

// Valid UUIDs for test use (avoids ParseUUIDPipe rejections)
export const UUID = {
  user: '550e8400-e29b-41d4-a716-446655440001',
  project: '550e8400-e29b-41d4-a716-446655440002',
  device: '550e8400-e29b-41d4-a716-446655440003',
  alert: '550e8400-e29b-41d4-a716-446655440004',
  template: '550e8400-e29b-41d4-a716-446655440005',
  report: '550e8400-e29b-41d4-a716-446655440006',
  config: '550e8400-e29b-41d4-a716-446655440007',
};

const mockRedisService = {
  get: jest.fn(),
  set: jest.fn(),
  del: jest.fn(),
  delByPattern: jest.fn().mockResolvedValue(0),
  getDel: jest.fn(),
  exists: jest.fn().mockResolvedValue(false),
};

const mockAuditLogService = {
  createLog: jest.fn().mockResolvedValue({}),
};

// All entity types that need repo mocks
const entityMap: [any, any][] = [
  [User, createMockRepo()],
  [Role, createMockRepo()],
  [Permission, createMockRepo()],
  [AuditLog, createMockRepo()],
  [Project, createMockRepo()],
  [ProjectUser, createMockRepo()],
  [Device, createMockRepo()],
  [DeviceStatusHistory, createMockRepo()],
  [AiModel, createMockRepo()],
  [AiModelVersion, createMockRepo()],
  [ModelDeployment, createMockRepo()],
  [DeviceModelBinding, createMockRepo()],
  [AiAnalysisResult, createMockRepo()],
  [Alert, createMockRepo()],
  [AlertStatusHistory, createMockRepo()],
  [WorkOrder, createMockRepo()],
  [AlertNotification, createMockRepo()],
  [DeviceTelemetry, createMockRepo()],
  [BackupConfig, createMockRepo()],
  [BackupLog, createMockRepo()],
  [ArchivedDataMeta, createMockRepo()],
  [ApiKey, createMockRepo()],
  [Webhook, createMockRepo()],
  [WebhookDelivery, createMockRepo()],
  [ApiCallLog, createMockRepo()],
  [ReportTemplate, createMockRepo()],
  [Report, createMockRepo()],
  [ScheduledReport, createMockRepo()],
  [ReportDeliveryLog, createMockRepo()],
];

export function getRepoProviders() {
  return entityMap.map(([entity, repo]) => ({
    provide: getRepositoryToken(entity),
    useValue: repo,
  }));
}

export async function createTestApp(controllers: any[]): Promise<INestApplication> {
  const allControllers = [
    AuthController, // Always include auth for JWT
    ...controllers,
  ];

  const moduleFixture: TestingModule = await Test.createTestingModule({
    imports: [
      ConfigModule.forRoot({
        isGlobal: true,
        load: [() => ({
          JWT_SECRET: JWT_SECRET,
          JWT_ACCESS_EXPIRATION: '1h',
          JWT_REFRESH_EXPIRATION: '7d',
        })],
      }),
      JwtModule.register({
        secret: JWT_SECRET,
        signOptions: { expiresIn: '1h' },
      }),
      PassportModule.register({ defaultStrategy: 'jwt' }),
    ],
    controllers: allControllers,
    providers: [
      // Services
      AuthService,
      UserService,
      ProjectService,
      DeviceService,
      AlertService,
      TelemetryService,
      DashboardService,
      // Strategy & Guards
      JwtStrategy,
      AbilityFactory,
      { provide: PermissionsGuard, useClass: PermissionsGuard },
      // Mocked external services
      { provide: RedisService, useValue: mockRedisService },
      { provide: AuditLogService, useValue: mockAuditLogService },
      { provide: EmailService, useValue: { sendEmail: jest.fn().mockResolvedValue(undefined) } },
      { provide: RoleService, useValue: { findAll: jest.fn().mockResolvedValue([]), findOne: jest.fn(), findByCode: jest.fn() } },
      { provide: DataSource, useValue: { createQueryRunner: jest.fn(), query: jest.fn(), transaction: jest.fn((cb: any) => cb({ create: jest.fn((_, data) => data), save: jest.fn((e: any) => e), update: jest.fn(), remove: jest.fn() })) } },
      // All repos
      ...getRepoProviders(),
    ],
  }).compile();

  const app = moduleFixture.createNestApplication();
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      transformOptions: { enableImplicitConversion: true },
    }),
  );
  app.useGlobalFilters(new HttpExceptionFilter());
  app.useGlobalInterceptors(new TransformInterceptor());
  app.setGlobalPrefix('api');

  await app.init();
  return app;
}

export function generateTestToken(app: INestApplication, payload?: any): string {
  const jwtService = app.get(JwtService);
  return jwtService.sign(payload || testUserWithRole, {
    secret: JWT_SECRET,
    expiresIn: '1h',
  });
}

export function authRequest(app: INestApplication, token?: string) {
  const t = token || generateTestToken(app);
  return request(app.getHttpServer()).set('Authorization', `Bearer ${t}`);
}
