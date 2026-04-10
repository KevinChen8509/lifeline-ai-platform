import * as request from 'supertest';
import { INestApplication } from '@nestjs/common';
import { getRepositoryToken } from '@nestjs/typeorm';
import { createTestApp, generateTestToken, UUID } from './test-utils';
import { Device } from '../src/modules/device/device.entity';
import { DeviceStatusHistory } from '../src/modules/device/device-status-history.entity';
import { DeviceController } from '../src/modules/device/device.controller';

describe('DeviceController (e2e)', () => {
  let app: INestApplication;
  let deviceRepo: any;

  const getToken = () => generateTestToken(app);

  beforeAll(async () => {
    app = await createTestApp([DeviceController]);
    deviceRepo = app.get(getRepositoryToken(Device));
  });

  afterAll(async () => {
    if (app) await app.close();
  });

  describe('GET /api/devices', () => {
    it('should return device list', () => {
      deviceRepo.createQueryBuilder.mockImplementation(() => ({
        leftJoinAndSelect: jest.fn().mockReturnThis(),
        where: jest.fn().mockReturnThis(),
        andWhere: jest.fn().mockReturnThis(),
        orderBy: jest.fn().mockReturnThis(),
        skip: jest.fn().mockReturnThis(),
        take: jest.fn().mockReturnThis(),
        getManyAndCount: jest.fn().mockResolvedValue([[{ id: UUID.device, name: 'Sensor 01' }], 1]),
      }));

      return request(app.getHttpServer())
        .get('/api/devices')
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
          expect(res.body.data.items).toHaveLength(1);
        });
    });
  });

  describe('GET /api/devices/:id', () => {
    it('should return device detail', () => {
      deviceRepo.findOne.mockResolvedValue({ id: UUID.device, name: 'Sensor 01', status: 'online' });

      return request(app.getHttpServer())
        .get(`/api/devices/${UUID.device}`)
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.data.id).toBe(UUID.device);
        });
    });
  });

  describe('PUT /api/devices/:id/config', () => {
    it('should update device config', () => {
      deviceRepo.findOne.mockResolvedValue({ id: UUID.device, status: 'online' });
      deviceRepo.save.mockImplementation(async (d: any) => d);

      return request(app.getHttpServer())
        .put(`/api/devices/${UUID.device}/config`)
        .set('Authorization', `Bearer ${getToken()}`)
        .send({ collectInterval: 15, uploadInterval: 60 })
        .expect(200);
    });
  });

  describe('GET /api/devices/:id/status-history', () => {
    it('should return status history', () => {
      deviceRepo.findOne.mockResolvedValue({ id: UUID.device });

      const statusHistoryRepo = app.get(getRepositoryToken(DeviceStatusHistory));
      statusHistoryRepo.createQueryBuilder.mockImplementation(() => ({
        where: jest.fn().mockReturnThis(),
        andWhere: jest.fn().mockReturnThis(),
        orderBy: jest.fn().mockReturnThis(),
        skip: jest.fn().mockReturnThis(),
        take: jest.fn().mockReturnThis(),
        getManyAndCount: jest.fn().mockResolvedValue([[], 0]),
      }));

      return request(app.getHttpServer())
        .get(`/api/devices/${UUID.device}/status-history`)
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200);
    });
  });
});
