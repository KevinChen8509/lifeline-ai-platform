import * as request from 'supertest';
import { INestApplication } from '@nestjs/common';
import { getRepositoryToken } from '@nestjs/typeorm';
import { createTestApp, generateTestToken, UUID } from './test-utils';
import { Alert, AlertStatusHistory } from '../src/modules/alert/alert.entity';
import { AlertController } from '../src/modules/alert/alert.controller';

describe('AlertController (e2e)', () => {
  let app: INestApplication;
  let alertRepo: any;
  let statusHistoryRepo: any;

  const getToken = () => generateTestToken(app);

  beforeAll(async () => {
    app = await createTestApp([AlertController]);
    alertRepo = app.get(getRepositoryToken(Alert));
    statusHistoryRepo = app.get(getRepositoryToken(AlertStatusHistory));
  });

  afterAll(async () => {
    if (app) await app.close();
  });

  describe('GET /api/alerts', () => {
    it('should return alerts list', () => {
      const mockQb: any = {
        leftJoinAndSelect: jest.fn().mockReturnThis(),
        orderBy: jest.fn().mockReturnThis(),
        addOrderBy: jest.fn().mockReturnThis(),
        skip: jest.fn().mockReturnThis(),
        take: jest.fn().mockReturnThis(),
        andWhere: jest.fn().mockReturnThis(),
        getManyAndCount: jest.fn().mockResolvedValue([
          [{ id: UUID.alert, type: 'silt', status: 'pending', level: 'high' }],
          1,
        ]),
        // statsQb methods (second createQueryBuilder call)
        select: jest.fn().mockReturnThis(),
        addSelect: jest.fn().mockReturnThis(),
        where: jest.fn().mockReturnThis(),
        groupBy: jest.fn().mockReturnThis(),
        getRawMany: jest.fn().mockResolvedValue([{ status: 'pending', count: '5' }]),
      };
      alertRepo.createQueryBuilder.mockReturnValue(mockQb);

      return request(app.getHttpServer())
        .get('/api/alerts')
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
        });
    });
  });

  describe('GET /api/alerts/stats/summary', () => {
    it('should return alert stats', () => {
      alertRepo.createQueryBuilder.mockImplementation(() => ({
        select: jest.fn().mockReturnThis(),
        addSelect: jest.fn().mockReturnThis(),
        where: jest.fn().mockReturnThis(),
        andWhere: jest.fn().mockReturnThis(),
        groupBy: jest.fn().mockReturnThis(),
        getRawMany: jest.fn().mockResolvedValue([{ level: 'high', count: '5' }]),
      }));
      alertRepo.count.mockResolvedValue(2);

      return request(app.getHttpServer())
        .get('/api/alerts/stats/summary')
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
          expect(res.body.data).toHaveProperty('high');
        });
    });
  });

  describe('POST /api/alerts/:id/acknowledge', () => {
    it('should acknowledge alert', () => {
      alertRepo.findOne.mockResolvedValue({ id: UUID.alert, status: 'pending' });
      alertRepo.save.mockImplementation(async (d: any) => d);
      statusHistoryRepo.create.mockReturnValue({});
      statusHistoryRepo.save.mockResolvedValue({});

      return request(app.getHttpServer())
        .post(`/api/alerts/${UUID.alert}/acknowledge`)
        .set('Authorization', `Bearer ${getToken()}`)
        .send({ note: 'Acknowledged' })
        .expect(201);
    });
  });

  describe('POST /api/alerts/:id/close', () => {
    it('should close alert', () => {
      alertRepo.findOne.mockResolvedValue({ id: UUID.alert, status: 'in_progress', createdAt: new Date() });
      alertRepo.save.mockImplementation(async (d: any) => d);
      statusHistoryRepo.create.mockReturnValue({});
      statusHistoryRepo.save.mockResolvedValue({});

      return request(app.getHttpServer())
        .post(`/api/alerts/${UUID.alert}/close`)
        .set('Authorization', `Bearer ${getToken()}`)
        .send({ resolution: 'Fixed', rootCause: 'Sensor malfunction' })
        .expect(201);
    });
  });
});
