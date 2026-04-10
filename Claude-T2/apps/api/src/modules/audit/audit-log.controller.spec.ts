import { Test, TestingModule } from '@nestjs/testing';
import { AuthGuard } from '@nestjs/passport';
import { AuditLogController } from './audit-log.controller';
import { AuditLogService } from './audit-log.service';
import { PermissionsGuard } from '../../common/guards/permissions.guard';
import { AuditLog } from './audit-log.entity';

describe('AuditLogController', () => {
  let controller: AuditLogController;
  let service: AuditLogService;

  const mockAuditLog: AuditLog = {
    id: '123e4567-e89b-12d3-a456-426614174000',
    action: 'LOGIN',
    targetType: 'User',
    targetId: 'user-uuid',
    operatorId: 'operator-uuid',
    operator: {
      id: 'operator-uuid',
      username: 'admin',
      name: '管理员',
    },
    oldValue: null,
    newValue: null,
    description: '用户登录成功: admin',
    ipAddress: '192.168.1.1',
    userAgent: 'Mozilla/5.0',
    createdAt: new Date(),
  };

  const mockAuditLogService = {
    findAll: jest.fn(),
    findOne: jest.fn(),
    findByTarget: jest.fn(),
    findByOperator: jest.fn(),
    createLog: jest.fn(),
  };

  // Mock guard
  const mockGuard = {
    canActivate: jest.fn().mockReturnValue(true),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [AuditLogController],
      providers: [
        {
          provide: AuditLogService,
          useValue: mockAuditLogService,
        },
      ],
    })
      .overrideGuard(AuthGuard('jwt'))
      .useValue(mockGuard)
      .overrideGuard(PermissionsGuard)
      .useValue(mockGuard)
      .compile();

    controller = module.get<AuditLogController>(AuditLogController);
    service = module.get<AuditLogService>(AuditLogService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('findAll', () => {
    it('应该返回分页审计日志列表', async () => {
      const mockResponse = {
        items: [mockAuditLog],
        total: 1,
        page: 1,
        pageSize: 20,
      };

      mockAuditLogService.findAll.mockResolvedValue(mockResponse);

      const result = await controller.findAll({
        page: 1,
        pageSize: 20,
      });

      expect(result).toEqual(mockResponse);
      expect(mockAuditLogService.findAll).toHaveBeenCalledWith({
        page: 1,
        pageSize: 20,
        action: undefined,
        targetType: undefined,
        targetId: undefined,
        operatorId: undefined,
        startDate: undefined,
        endDate: undefined,
      });
    });

    it('应该支持时间范围筛选', async () => {
      const mockResponse = {
        items: [],
        total: 0,
        page: 1,
        pageSize: 20,
      };

      mockAuditLogService.findAll.mockResolvedValue(mockResponse);

      const startTime = '2026-04-01T00:00:00Z';
      const endTime = '2026-04-02T23:59:59Z';

      await controller.findAll({
        page: 1,
        pageSize: 20,
        startTime,
        endTime,
      });

      expect(mockAuditLogService.findAll).toHaveBeenCalledWith(
        expect.objectContaining({
          startDate: new Date(startTime),
          endDate: new Date(endTime),
        }),
      );
    });

    it('应该支持操作类型筛选', async () => {
      const mockResponse = {
        items: [mockAuditLog],
        total: 1,
        page: 1,
        pageSize: 20,
      };

      mockAuditLogService.findAll.mockResolvedValue(mockResponse);

      await controller.findAll({
        page: 1,
        pageSize: 20,
        action: 'LOGIN',
      });

      expect(mockAuditLogService.findAll).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'LOGIN',
        }),
      );
    });

    it('应该支持用户ID筛选', async () => {
      const mockResponse = {
        items: [mockAuditLog],
        total: 1,
        page: 1,
        pageSize: 20,
      };

      mockAuditLogService.findAll.mockResolvedValue(mockResponse);

      await controller.findAll({
        page: 1,
        pageSize: 20,
        userId: 'operator-uuid',
      });

      expect(mockAuditLogService.findAll).toHaveBeenCalledWith(
        expect.objectContaining({
          operatorId: 'operator-uuid',
        }),
      );
    });
  });

  describe('export', () => {
    it('应该生成CSV并设置正确的响应头', async () => {
      const mockResponse = {
        items: [mockAuditLog],
        total: 1,
        page: 1,
        pageSize: 10000,
      };

      mockAuditLogService.findAll.mockResolvedValue(mockResponse);

      const mockRes = {
        setHeader: jest.fn(),
        send: jest.fn(),
      };

      await controller.export({}, mockRes as any);

      expect(mockRes.setHeader).toHaveBeenCalledWith(
        'Content-Type',
        'text/csv; charset=utf-8',
      );
      expect(mockRes.setHeader).toHaveBeenCalledWith(
        'Content-Disposition',
        expect.stringContaining('attachment; filename="audit-logs-'),
      );
      expect(mockRes.send).toHaveBeenCalled();

      // 验证CSV内容包含BOM
      const sentContent = mockRes.send.mock.calls[0][0];
      expect(sentContent.startsWith('\uFEFF')).toBe(true);
    });

    it('CSV应该包含正确的表头', async () => {
      const mockResponse = {
        items: [],
        total: 0,
        page: 1,
        pageSize: 10000,
      };

      mockAuditLogService.findAll.mockResolvedValue(mockResponse);

      const mockRes = {
        setHeader: jest.fn(),
        send: jest.fn(),
      };

      await controller.export({}, mockRes as any);

      const sentContent = mockRes.send.mock.calls[0][0];
      expect(sentContent).toContain('时间,用户名,操作类型,目标类型,目标ID,IP地址,描述');
    });

    it('应该限制最大导出条数为10000', async () => {
      const mockResponse = {
        items: [],
        total: 0,
        page: 1,
        pageSize: 10000,
      };

      mockAuditLogService.findAll.mockResolvedValue(mockResponse);

      const mockRes = {
        setHeader: jest.fn(),
        send: jest.fn(),
      };

      await controller.export({}, mockRes as any);

      expect(mockAuditLogService.findAll).toHaveBeenCalledWith(
        expect.objectContaining({
          page: 1,
          pageSize: 10000,
        }),
      );
    });

    it('应该正确处理包含特殊字符的描述', async () => {
      const logWithSpecialChars: AuditLog = {
        ...mockAuditLog,
        description: '用户 "admin" 登录成功, 测试',
      };

      const mockResponse = {
        items: [logWithSpecialChars],
        total: 1,
        page: 1,
        pageSize: 10000,
      };

      mockAuditLogService.findAll.mockResolvedValue(mockResponse);

      const mockRes = {
        setHeader: jest.fn(),
        send: jest.fn(),
      };

      await controller.export({}, mockRes as any);

      const sentContent = mockRes.send.mock.calls[0][0];
      // 验证双引号被正确转义
      expect(sentContent).toContain('"用户 ""admin"" 登录成功, 测试"');
    });
  });
});
