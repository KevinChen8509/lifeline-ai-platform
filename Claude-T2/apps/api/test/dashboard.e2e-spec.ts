import * as request from 'supertest';
import { INestApplication } from '@nestjs/common';
import { getRepositoryToken } from '@nestjs/typeorm';
import { createTestApp, generateTestToken, UUID } from './test-utils';
import { ReportTemplate, Report } from '../src/modules/dashboard/dashboard.entity';
import {
  DashboardController,
  StatisticsController,
  ReportTemplateController,
  ReportController,
} from '../src/modules/dashboard/dashboard.controller';

describe('DashboardController (e2e)', () => {
  let app: INestApplication;
  let templateRepo: any;
  let reportRepo: any;

  const getToken = () => generateTestToken(app);

  beforeAll(async () => {
    app = await createTestApp([
      DashboardController,
      StatisticsController,
      ReportTemplateController,
      ReportController,
    ]);
    templateRepo = app.get(getRepositoryToken(ReportTemplate));
    reportRepo = app.get(getRepositoryToken(Report));
  });

  afterAll(async () => {
    if (app) await app.close();
  });

  describe('GET /api/projects/:projectId/dashboard/device-stats', () => {
    it('should return device stats', () => {
      return request(app.getHttpServer())
        .get(`/api/projects/${UUID.project}/dashboard/device-stats`)
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
          expect(res.body.data).toHaveProperty('total');
          expect(res.body.data).toHaveProperty('onlineRate');
        });
    });
  });

  describe('GET /api/projects/:projectId/dashboard/alert-stats', () => {
    it('should return alert stats', () => {
      return request(app.getHttpServer())
        .get(`/api/projects/${UUID.project}/dashboard/alert-stats`)
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
          expect(res.body.data).toHaveProperty('todayTotal');
          expect(res.body.data).toHaveProperty('byLevel');
        });
    });
  });

  describe('GET /api/projects/:projectId/dashboard/kpi', () => {
    it('should return KPI data', () => {
      return request(app.getHttpServer())
        .get(`/api/projects/${UUID.project}/dashboard/kpi`)
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
          expect(res.body.data).toHaveProperty('avgHandleTime');
          expect(res.body.data).toHaveProperty('handleRate');
        });
    });
  });

  describe('GET /api/statistics/alert-type-distribution', () => {
    it('should return alert type distribution', () => {
      return request(app.getHttpServer())
        .get('/api/statistics/alert-type-distribution')
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
          expect(res.body.data.distribution).toHaveLength(5);
        });
    });
  });

  describe('GET /api/report-templates', () => {
    it('should return template list', () => {
      templateRepo.find.mockResolvedValue([{ id: UUID.template, name: '日报' }]);

      return request(app.getHttpServer())
        .get('/api/report-templates')
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
        });
    });
  });
});
