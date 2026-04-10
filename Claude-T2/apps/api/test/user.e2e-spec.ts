import * as request from 'supertest';
import { INestApplication } from '@nestjs/common';
import { getRepositoryToken } from '@nestjs/typeorm';
import { createTestApp, generateTestToken, UUID } from './test-utils';
import { User } from '../src/modules/user/user.entity';
import { UserController } from '../src/modules/user/user.controller';

describe('UserController (e2e)', () => {
  let app: INestApplication;
  let userRepo: any;

  const getToken = () => generateTestToken(app);

  beforeAll(async () => {
    app = await createTestApp([UserController]);
    userRepo = app.get(getRepositoryToken(User));
  });

  afterAll(async () => {
    if (app) await app.close();
  });

  describe('GET /api/users', () => {
    it('should return 401 without auth', () => {
      return request(app.getHttpServer()).get('/api/users').expect(401);
    });

    it('should return paginated users with auth', () => {
      userRepo.createQueryBuilder.mockImplementation(() => ({
        leftJoinAndSelect: jest.fn().mockReturnThis(),
        where: jest.fn().mockReturnThis(),
        andWhere: jest.fn().mockReturnThis(),
        orderBy: jest.fn().mockReturnThis(),
        skip: jest.fn().mockReturnThis(),
        take: jest.fn().mockReturnThis(),
        getManyAndCount: jest.fn().mockResolvedValue([[{ id: UUID.user, username: 'admin' }], 1]),
      }));

      return request(app.getHttpServer())
        .get('/api/users')
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
          expect(res.body.data).toHaveProperty('items');
        });
    });
  });

  describe('GET /api/users/:id', () => {
    it('should return user by id', () => {
      userRepo.findOne.mockResolvedValue({
        id: UUID.user, username: 'admin', name: 'Admin', email: 'admin@test.com',
      });

      return request(app.getHttpServer())
        .get(`/api/users/${UUID.user}`)
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
          expect(res.body.data.username).toBe('admin');
        });
    });
  });

  describe('POST /api/users', () => {
    it('should return 400 for invalid data', () => {
      return request(app.getHttpServer())
        .post('/api/users')
        .set('Authorization', `Bearer ${getToken()}`)
        .send({})
        .expect(400);
    });
  });

  describe('PATCH /api/users/:id/status', () => {
    it('should update user status', () => {
      userRepo.findOne.mockResolvedValue({ id: UUID.user, status: 'ACTIVE' });
      userRepo.save.mockImplementation(async (d: any) => d);

      return request(app.getHttpServer())
        .put(`/api/users/${UUID.user}/status`)
        .set('Authorization', `Bearer ${getToken()}`)
        .send({ status: 'DISABLED' })
        .expect(200);
    });
  });
});
