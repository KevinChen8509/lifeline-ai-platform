import * as request from 'supertest';
import { INestApplication } from '@nestjs/common';
import { createTestApp, generateTestToken } from './test-utils';

describe('AuthController (e2e)', () => {
  let app: INestApplication;

  beforeAll(async () => {
    app = await createTestApp([]);
  });

  afterAll(async () => {
    if (app) await app.close();
  });

  describe('POST /api/auth/login', () => {
    it('should return 401 for invalid credentials', () => {
      return request(app.getHttpServer())
        .post('/api/auth/login')
        .send({ username: 'nonexistent', password: 'wrongpassword' })
        .expect(401);
    });

    it('should return 400 for missing fields', () => {
      return request(app.getHttpServer())
        .post('/api/auth/login')
        .send({})
        .expect(400);
    });
  });

  describe('POST /api/auth/refresh', () => {
    it('should return 401 for invalid refresh token', () => {
      return request(app.getHttpServer())
        .post('/api/auth/refresh')
        .send({ refreshToken: 'invalid-token' })
        .expect(401);
    });
  });

  describe('POST /api/auth/logout', () => {
    it('should return 401 without auth token', () => {
      return request(app.getHttpServer())
        .post('/api/auth/logout')
        .expect(401);
    });

    it('should return success with valid token', () => {
      const token = generateTestToken(app);
      return request(app.getHttpServer())
        .post('/api/auth/logout')
        .set('Authorization', `Bearer ${token}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
          expect(res.body.data.message).toBe('登出成功');
        });
    });
  });
});
