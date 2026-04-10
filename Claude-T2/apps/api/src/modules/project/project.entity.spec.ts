import { Project, ProjectStatus } from './project.entity';
import { ProjectUser, ProjectRole } from './project-user.entity';

describe('Project Entity', () => {
  describe('Project', () => {
    it('should be defined', () => {
      const project = new Project();
      expect(project).toBeDefined();
    });

    it('should allow setting status to active', () => {
      const project = new Project();
      project.status = ProjectStatus.ACTIVE;
      expect(project.status).toBe(ProjectStatus.ACTIVE);
    });

    it('should allow setting settings', () => {
      const project = new Project();
      project.settings = { theme: 'dark' };
      expect(project.settings).toEqual({ theme: 'dark' });
    });

    it('should set properties correctly', () => {
      const project = new Project();
      project.id = 'test-uuid';
      project.name = '测试项目';
      project.code = 'TEST001';
      project.description = '这是一个测试项目';
      project.settings = { theme: 'dark', notifications: true };
      project.status = ProjectStatus.ACTIVE;

      expect(project.id).toBe('test-uuid');
      expect(project.name).toBe('测试项目');
      expect(project.code).toBe('TEST001');
      expect(project.description).toBe('这是一个测试项目');
      expect(project.settings).toEqual({ theme: 'dark', notifications: true });
      expect(project.status).toBe(ProjectStatus.ACTIVE);
    });

    it('should support archived status', () => {
      const project = new Project();
      project.status = ProjectStatus.ARCHIVED;
      expect(project.status).toBe(ProjectStatus.ARCHIVED);
    });
  });

  describe('ProjectStatus Enum', () => {
    it('should have ACTIVE value', () => {
      expect(ProjectStatus.ACTIVE).toBe('active');
    });

    it('should have ARCHIVED value', () => {
      expect(ProjectStatus.ARCHIVED).toBe('archived');
    });
  });
});

describe('ProjectUser Entity', () => {
  describe('ProjectUser', () => {
    it('should be defined', () => {
      const projectUser = new ProjectUser();
      expect(projectUser).toBeDefined();
    });

    it('should allow setting role to member', () => {
      const projectUser = new ProjectUser();
      projectUser.role = ProjectRole.MEMBER;
      expect(projectUser.role).toBe(ProjectRole.MEMBER);
    });

    it('should set properties correctly', () => {
      const projectUser = new ProjectUser();
      projectUser.id = 'test-uuid';
      projectUser.projectId = 'project-uuid';
      projectUser.userId = 'user-uuid';
      projectUser.role = ProjectRole.ADMIN;

      expect(projectUser.id).toBe('test-uuid');
      expect(projectUser.projectId).toBe('project-uuid');
      expect(projectUser.userId).toBe('user-uuid');
      expect(projectUser.role).toBe(ProjectRole.ADMIN);
    });

    it('should support admin role', () => {
      const projectUser = new ProjectUser();
      projectUser.role = ProjectRole.ADMIN;
      expect(projectUser.role).toBe(ProjectRole.ADMIN);
    });

    it('should support member role', () => {
      const projectUser = new ProjectUser();
      projectUser.role = ProjectRole.MEMBER;
      expect(projectUser.role).toBe(ProjectRole.MEMBER);
    });
  });

  describe('ProjectRole Enum', () => {
    it('should have ADMIN value', () => {
      expect(ProjectRole.ADMIN).toBe('admin');
    });

    it('should have MEMBER value', () => {
      expect(ProjectRole.MEMBER).toBe('member');
    });
  });
});
