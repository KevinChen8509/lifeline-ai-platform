import * as request from 'supertest';
import { INestApplication } from '@nestjs/common';
import { getRepositoryToken } from '@nestjs/typeorm';
import { createTestApp, generateTestToken, UUID } from './test-utils';
import { DeviceTelemetry, BackupLog } from '../src/modules/telemetry/telemetry.entity';
import { TelemetryController } from '../src/modules/telemetry/telemetry.controller';

describe('TelemetryController (e2e)', () => {
  let app: INestApplication;
  let telemetryRepo: any;

  const getToken = () => generateTestToken(app);

  beforeAll(async () => {
    app = await createTestApp([TelemetryController]);
    telemetryRepo = app.get(getRepositoryToken(DeviceTelemetry));
  });

  afterAll(async () => {
    if (app) await app.close();
  });

  describe('GET /api/devices/:deviceId/telemetry', () => {
    it('should return telemetry data', () => {
      telemetryRepo.createQueryBuilder.mockImplementation(() => ({
        where: jest.fn().mockReturnThis(),
        andWhere: jest.fn().mockReturnThis(),
        orderBy: jest.fn().mockReturnThis(),
        skip: jest.fn().mockReturnThis(),
        take: jest.fn().mockReturnThis(),
        getManyAndCount: jest.fn().mockResolvedValue([[{ id: 't1', metrics: { level: 1.2 } }], 1]),
      }));

      return request(app.getHttpServer())
        .get(`/api/devices/${UUID.device}/telemetry`)
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
        });
    });
  });

  describe('POST /api/devices/:deviceId/telemetry', () => {
    it('should write telemetry data', () => {
      telemetryRepo.create.mockReturnValue({ deviceId: UUID.device, metrics: { level: 1.2 } });
      telemetryRepo.save.mockResolvedValue({ id: 't1', deviceId: UUID.device, metrics: { level: 1.2 } });

      return request(app.getHttpServer())
        .post(`/api/devices/${UUID.device}/telemetry`)
        .set('Authorization', `Bearer ${getToken()}`)
        .send({ metrics: { level: 1.2 } })
        .expect(201)
        .expect((res) => {
          expect(res.body.code).toBe(0);
        });
    });
  });

  describe('GET /api/devices/:deviceId/telemetry/chart', () => {
    it('should return chart data', () => {
      telemetryRepo.createQueryBuilder.mockImplementation(() => ({
        select: jest.fn().mockReturnThis(),
        addSelect: jest.fn().mockReturnThis(),
        where: jest.fn().mockReturnThis(),
        andWhere: jest.fn().mockReturnThis(),
        orderBy: jest.fn().mockReturnThis(),
        getRawMany: jest.fn().mockResolvedValue([{ timestamp: '2026-04-09T10:00:00', level: '1.2' }]),
      }));

      return request(app.getHttpServer())
        .get(`/api/devices/${UUID.device}/telemetry/chart?metrics=level`)
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
        });
    });
  });

  describe('GET /api/backup/logs', () => {
    it('should return backup logs', () => {
      const backupLogRepo = app.get(getRepositoryToken(BackupLog));
      backupLogRepo.createQueryBuilder.mockImplementation(() => ({
        orderBy: jest.fn().mockReturnThis(),
        limit: jest.fn().mockReturnThis(),
        andWhere: jest.fn().mockReturnThis(),
        getMany: jest.fn().mockResolvedValue([]),
      }));

      return request(app.getHttpServer())
        .get('/api/backup/logs')
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200);
    });
  });
});
