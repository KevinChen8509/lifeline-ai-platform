import * as request from 'supertest';
import { INestApplication } from '@nestjs/common';
import { getRepositoryToken } from '@nestjs/typeorm';
import { createTestApp, generateTestToken, UUID } from './test-utils';
import { Project } from '../src/modules/project/project.entity';
import { ProjectController } from '../src/modules/project/project.controller';

describe('ProjectController (e2e)', () => {
  let app: INestApplication;
  let projectRepo: any;

  const getToken = () => generateTestToken(app);

  beforeAll(async () => {
    app = await createTestApp([ProjectController]);
    projectRepo = app.get(getRepositoryToken(Project));
  });

  afterAll(async () => {
    if (app) await app.close();
  });

  describe('POST /api/projects', () => {
    it('should create a project', () => {
      projectRepo.create.mockReturnValue({ id: UUID.project, name: 'Test Project' });
      projectRepo.save.mockResolvedValue({ id: UUID.project, name: 'Test Project' });

      return request(app.getHttpServer())
        .post('/api/projects')
        .set('Authorization', `Bearer ${getToken()}`)
        .send({ name: 'Test Project', code: 'TEST001', description: 'A test project' })
        .expect(201)
        .expect((res) => {
          expect(res.body.code).toBe(0);
          expect(res.body.data.name).toBe('Test Project');
        });
    });

    it('should return 400 for missing name', () => {
      return request(app.getHttpServer())
        .post('/api/projects')
        .set('Authorization', `Bearer ${getToken()}`)
        .send({})
        .expect(400);
    });
  });

  describe('GET /api/projects', () => {
    it('should return project list', () => {
      projectRepo.createQueryBuilder.mockImplementation(() => ({
        leftJoinAndSelect: jest.fn().mockReturnThis(),
        where: jest.fn().mockReturnThis(),
        andWhere: jest.fn().mockReturnThis(),
        orderBy: jest.fn().mockReturnThis(),
        skip: jest.fn().mockReturnThis(),
        take: jest.fn().mockReturnThis(),
        getManyAndCount: jest.fn().mockResolvedValue([[{ id: UUID.project, name: 'Project 1' }], 1]),
      }));

      return request(app.getHttpServer())
        .get('/api/projects')
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.code).toBe(0);
          expect(res.body.data.items).toHaveLength(1);
        });
    });
  });

  describe('GET /api/projects/:id', () => {
    it('should return project details', () => {
      projectRepo.findOne.mockResolvedValue({ id: UUID.project, name: 'Project 1', description: 'Desc' });

      return request(app.getHttpServer())
        .get(`/api/projects/${UUID.project}`)
        .set('Authorization', `Bearer ${getToken()}`)
        .expect(200)
        .expect((res) => {
          expect(res.body.data.id).toBe(UUID.project);
        });
    });
  });

  describe('PUT /api/projects/:id', () => {
    it('should update project', () => {
      projectRepo.findOne.mockResolvedValue({ id: UUID.project, name: 'Old' });
      projectRepo.save.mockImplementation(async (d: any) => d);

      return request(app.getHttpServer())
        .put(`/api/projects/${UUID.project}`)
        .set('Authorization', `Bearer ${getToken()}`)
        .send({ name: 'Updated' })
        .expect(200);
    });
  });
});
