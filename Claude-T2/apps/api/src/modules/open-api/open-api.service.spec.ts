import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { NotFoundException, BadRequestException, UnauthorizedException } from '@nestjs/common';
import { OpenApiService } from './open-api.service';
import {
  ApiKey,
  ApiKeyStatus,
  Webhook,
  WebhookStatus,
  WebhookEventType,
  WebhookDelivery,
  DeliveryStatus,
  ApiCallLog,
} from './open-api.entity';

describe('OpenApiService', () => {
  let service: OpenApiService;

  const mockApiKeyRepo = {
    create: jest.fn(),
    save: jest.fn(),
    find: jest.fn(),
    findOne: jest.fn(),
    delete: jest.fn(),
  };

  const mockWebhookRepo = {
    create: jest.fn(),
    save: jest.fn(),
    find: jest.fn(),
    findOne: jest.fn(),
    delete: jest.fn(),
  };

  const mockDeliveryRepo = {
    create: jest.fn(),
    save: jest.fn(),
    find: jest.fn(),
    findOne: jest.fn(),
  };

  const mockCallLogRepo = {
    create: jest.fn(),
    save: jest.fn(),
    createQueryBuilder: jest.fn(),
  };

  const mockQb = {
    where: jest.fn().mockReturnThis(),
    andWhere: jest.fn().mockReturnThis(),
    orderBy: jest.fn().mockReturnThis(),
    skip: jest.fn().mockReturnThis(),
    take: jest.fn().mockReturnThis(),
    select: jest.fn().mockReturnThis(),
    addSelect: jest.fn().mockReturnThis(),
    groupBy: jest.fn().mockReturnThis(),
    getCount: jest.fn(),
    getRawMany: jest.fn(),
    getManyAndCount: jest.fn(),
    limit: jest.fn().mockReturnThis(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        OpenApiService,
        { provide: getRepositoryToken(ApiKey), useValue: mockApiKeyRepo },
        { provide: getRepositoryToken(Webhook), useValue: mockWebhookRepo },
        { provide: getRepositoryToken(WebhookDelivery), useValue: mockDeliveryRepo },
        { provide: getRepositoryToken(ApiCallLog), useValue: mockCallLogRepo },
      ],
    }).compile();

    service = module.get<OpenApiService>(OpenApiService);
  });

  afterEach(() => jest.clearAllMocks());

  // ==================== API Key 测试 ====================

  describe('createApiKey', () => {
    it('应该创建 API Key 并返回 key 和 secret', async () => {
      const data = { name: 'Test Key', userId: 'user-1' };
      mockApiKeyRepo.create.mockImplementation((d) => d);
      mockApiKeyRepo.save.mockImplementation(async (d) => ({ ...d, id: 'key-1' }));

      const result = await service.createApiKey(data);
      expect(result.apiKey).toBeDefined();
      expect(result.secret).toBeDefined();
      expect(result.apiKey.key).toMatch(/^lk_live_/);
    });

    it('应该设置过期时间', async () => {
      const data = { name: 'Test Key', userId: 'user-1', expiresInDays: 30 };
      mockApiKeyRepo.create.mockImplementation((d) => d);
      mockApiKeyRepo.save.mockImplementation(async (d) => ({ ...d, id: 'key-1' }));

      const result = await service.createApiKey(data);
      expect(result.apiKey.expiresAt).toBeDefined();
    });
  });

  describe('listApiKeys', () => {
    it('应该返回用户的 API Keys', async () => {
      const keys = [{ id: 'key-1', name: 'Test' }];
      mockApiKeyRepo.find.mockResolvedValue(keys);

      const result = await service.listApiKeys('user-1');
      expect(result).toHaveLength(1);
    });
  });

  describe('getApiKey', () => {
    it('应该返回 API Key', async () => {
      const key = { id: 'key-1', name: 'Test' };
      mockApiKeyRepo.findOne.mockResolvedValue(key);

      const result = await service.getApiKey('key-1');
      expect(result.id).toBe('key-1');
    });

    it('不存在应抛出异常', async () => {
      mockApiKeyRepo.findOne.mockResolvedValue(null);
      await expect(service.getApiKey('non-existent')).rejects.toThrow(NotFoundException);
    });
  });

  describe('revokeApiKey', () => {
    it('应该吊销 API Key', async () => {
      const key = { id: 'key-1', userId: 'user-1', status: ApiKeyStatus.ACTIVE };
      mockApiKeyRepo.findOne.mockResolvedValue(key);
      mockApiKeyRepo.save.mockImplementation(async (d) => d);

      const result = await service.revokeApiKey('key-1', 'user-1');
      expect(result.status).toBe(ApiKeyStatus.DISABLED);
    });

    it('无权操作应抛出异常', async () => {
      mockApiKeyRepo.findOne.mockResolvedValue(null);
      await expect(service.revokeApiKey('key-1', 'user-2')).rejects.toThrow(NotFoundException);
    });
  });

  describe('validateApiKey', () => {
    it('有效 Key 应返回并更新 lastUsedAt', async () => {
      const key = { key: 'lk_live_test', status: ApiKeyStatus.ACTIVE, expiresAt: null };
      mockApiKeyRepo.findOne.mockResolvedValue(key);
      mockApiKeyRepo.save.mockImplementation(async (d) => d);

      const result = await service.validateApiKey('lk_live_test');
      expect(result.lastUsedAt).toBeDefined();
    });

    it('无效 Key 应抛出异常', async () => {
      mockApiKeyRepo.findOne.mockResolvedValue(null);
      await expect(service.validateApiKey('invalid')).rejects.toThrow(UnauthorizedException);
    });

    it('过期 Key 应更新状态并抛出异常', async () => {
      const key = {
        key: 'lk_live_test',
        status: ApiKeyStatus.ACTIVE,
        expiresAt: new Date('2020-01-01'),
      };
      mockApiKeyRepo.findOne.mockResolvedValue(key);
      mockApiKeyRepo.save.mockImplementation(async (d) => d);

      await expect(service.validateApiKey('lk_live_test')).rejects.toThrow(UnauthorizedException);
    });
  });

  describe('checkPermission', () => {
    it('有权限应返回 true', () => {
      const apiKey = { permissions: ['devices:read', 'alerts:read'] } as ApiKey;
      expect(service.checkPermission(apiKey, 'devices:read')).toBe(true);
    });

    it('无权限应返回 false', () => {
      const apiKey = { permissions: ['devices:read'] } as ApiKey;
      expect(service.checkPermission(apiKey, 'alerts:write')).toBe(false);
    });

    it('通配符应匹配所有权限', () => {
      const apiKey = { permissions: ['*'] } as ApiKey;
      expect(service.checkPermission(apiKey, 'anything:read')).toBe(true);
    });
  });

  // ==================== Webhook 测试 ====================

  describe('createWebhook', () => {
    it('应该创建 Webhook', async () => {
      const data = {
        name: 'Test Hook',
        url: 'https://example.com/webhook',
        events: [WebhookEventType.DEVICE_ONLINE],
        userId: 'user-1',
      };
      mockWebhookRepo.create.mockImplementation((d) => d);
      mockWebhookRepo.save.mockImplementation(async (d) => ({ ...d, id: 'hook-1' }));

      const result = await service.createWebhook(data);
      expect(result.id).toBe('hook-1');
      expect(result.secret).toBeDefined();
    });
  });

  describe('listWebhooks', () => {
    it('应该返回用户的 Webhooks', async () => {
      const hooks = [{ id: 'hook-1', name: 'Test' }];
      mockWebhookRepo.find.mockResolvedValue(hooks);

      const result = await service.listWebhooks('user-1');
      expect(result).toHaveLength(1);
    });
  });

  describe('getWebhook', () => {
    it('不存在应抛出异常', async () => {
      mockWebhookRepo.findOne.mockResolvedValue(null);
      await expect(service.getWebhook('non-existent')).rejects.toThrow(NotFoundException);
    });
  });

  describe('updateWebhook', () => {
    it('应该更新 Webhook', async () => {
      const webhook = { id: 'hook-1', name: 'Old', url: 'https://old.com' };
      mockWebhookRepo.findOne.mockResolvedValue(webhook);
      mockWebhookRepo.save.mockImplementation(async (d) => d);

      const result = await service.updateWebhook('hook-1', { name: 'New' });
      expect(result.name).toBe('New');
    });
  });

  describe('deleteWebhook', () => {
    it('应该删除 Webhook', async () => {
      mockWebhookRepo.delete.mockResolvedValue({ affected: 1 });
      await expect(service.deleteWebhook('hook-1')).resolves.toBeUndefined();
    });

    it('不存在应抛出异常', async () => {
      mockWebhookRepo.delete.mockResolvedValue({ affected: 0 });
      await expect(service.deleteWebhook('non-existent')).rejects.toThrow(NotFoundException);
    });
  });

  describe('triggerWebhook', () => {
    it('应该匹配并投递事件', async () => {
      const webhook = {
        id: 'hook-1',
        name: 'Test',
        status: WebhookStatus.ACTIVE,
        enabled: true,
        events: [WebhookEventType.DEVICE_ONLINE],
        secret: 'test-secret',
        headers: {},
        maxRetries: 3,
      };
      mockWebhookRepo.find.mockResolvedValue([webhook]);
      mockDeliveryRepo.create.mockImplementation((d) => ({ ...d, id: 'del-1' }));
      mockDeliveryRepo.save.mockImplementation(async (d) => d);
      mockWebhookRepo.save.mockImplementation(async (d) => d);

      await service.triggerWebhook(WebhookEventType.DEVICE_ONLINE, { deviceId: 'd1' });
      expect(mockDeliveryRepo.create).toHaveBeenCalled();
    });

    it('不匹配事件应跳过', async () => {
      const webhook = {
        id: 'hook-1',
        status: WebhookStatus.ACTIVE,
        enabled: true,
        events: [WebhookEventType.ALERT_CREATED],
      };
      mockWebhookRepo.find.mockResolvedValue([webhook]);

      await service.triggerWebhook(WebhookEventType.DEVICE_ONLINE, {});
      expect(mockDeliveryRepo.create).not.toHaveBeenCalled();
    });
  });

  describe('retryDelivery', () => {
    it('超过重试次数应抛出异常', async () => {
      const delivery = { id: 'del-1', webhookId: 'hook-1', attempts: 3 };
      mockDeliveryRepo.findOne.mockResolvedValue(delivery);
      mockWebhookRepo.findOne.mockResolvedValue({ id: 'hook-1', maxRetries: 3 });
      mockDeliveryRepo.save.mockImplementation(async (d) => d);

      await expect(service.retryDelivery('del-1')).rejects.toThrow(BadRequestException);
    });
  });

  // ==================== API 调用日志 测试 ====================

  describe('logApiCall', () => {
    it('应该记录 API 调用', async () => {
      const data = {
        method: 'GET',
        path: '/open-api/v1/devices',
        statusCode: 200,
        durationMs: 50,
      };
      mockCallLogRepo.create.mockImplementation((d) => d);
      mockCallLogRepo.save.mockImplementation(async (d) => d);

      const result = await service.logApiCall(data);
      expect(result.timestamp).toBeDefined();
    });
  });

  describe('getCallStats', () => {
    it('应该返回调用统计', async () => {
      const statsQb = { ...mockQb };
      mockCallLogRepo.createQueryBuilder.mockReturnValue(statsQb);
      statsQb.getCount.mockResolvedValue(100);
      statsQb.andWhere = jest.fn().mockReturnThis();
      statsQb.getRawMany.mockResolvedValue([
        { path: '/open-api/v1/devices', method: 'GET', count: '50' },
      ]);

      // Need separate mock for each query builder call
      let callCount = 0;
      mockCallLogRepo.createQueryBuilder.mockImplementation(() => {
        callCount++;
        const qb: any = {
          andWhere: jest.fn().mockReturnThis(),
          getCount: jest.fn(),
          select: jest.fn().mockReturnThis(),
          addSelect: jest.fn().mockReturnThis(),
          groupBy: jest.fn().mockReturnThis(),
          orderBy: jest.fn().mockReturnThis(),
          limit: jest.fn().mockReturnThis(),
          getRawMany: jest.fn(),
        };
        if (callCount === 1) qb.getCount.mockResolvedValue(100);
        else if (callCount === 2) {
          qb.andWhere = jest.fn().mockReturnThis();
          qb.getCount.mockResolvedValue(5);
        } else {
          qb.getRawMany.mockResolvedValue([
            { path: '/open-api/v1/devices', method: 'GET', count: '50' },
          ]);
        }
        return qb;
      });

      const result = await service.getCallStats({});
      expect(result.totalCalls).toBe(100);
    });
  });

  describe('getCallLogs', () => {
    it('应该返回分页调用日志', async () => {
      const logsQb = {
        orderBy: jest.fn().mockReturnThis(),
        skip: jest.fn().mockReturnThis(),
        take: jest.fn().mockReturnThis(),
        andWhere: jest.fn().mockReturnThis(),
        getManyAndCount: jest.fn().mockResolvedValue([[{ id: 'log-1' }], 1]),
      };
      mockCallLogRepo.createQueryBuilder.mockReturnValue(logsQb);

      const result = await service.getCallLogs({ page: 1, pageSize: 50 });
      expect(result.items).toHaveLength(1);
      expect(result.total).toBe(1);
    });
  });

  // ==================== Open API 查询 ====================

  describe('getOpenDevices', () => {
    it('应该返回设备列表占位数据', async () => {
      const apiKey = { permissions: ['devices:read'] } as ApiKey;
      const result = await service.getOpenDevices(apiKey, {});
      expect(result.data).toEqual([]);
    });
  });

  describe('getOpenAlerts', () => {
    it('应该返回告警列表占位数据', async () => {
      const apiKey = { permissions: ['alerts:read'] } as ApiKey;
      const result = await service.getOpenAlerts(apiKey, {});
      expect(result.data).toEqual([]);
    });
  });

  describe('getOpenTelemetry', () => {
    it('应该返回遥测数据占位', async () => {
      const apiKey = { permissions: ['telemetry:read'] } as ApiKey;
      const result = await service.getOpenTelemetry(apiKey, 'd1', {});
      expect(result.deviceId).toBe('d1');
    });
  });
});
