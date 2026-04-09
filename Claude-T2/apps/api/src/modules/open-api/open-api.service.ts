import { Injectable, Logger, NotFoundException, BadRequestException, UnauthorizedException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, LessThan, In } from 'typeorm';
import * as crypto from 'crypto';
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

@Injectable()
export class OpenApiService {
  private readonly logger = new Logger(OpenApiService.name);

  constructor(
    @InjectRepository(ApiKey)
    private readonly apiKeyRepository: Repository<ApiKey>,
    @InjectRepository(Webhook)
    private readonly webhookRepository: Repository<Webhook>,
    @InjectRepository(WebhookDelivery)
    private readonly deliveryRepository: Repository<WebhookDelivery>,
    @InjectRepository(ApiCallLog)
    private readonly callLogRepository: Repository<ApiCallLog>,
  ) {}

  // ==================== Story 7.1-7.3: API Key 管理 ====================

  async createApiKey(data: {
    name: string;
    description?: string;
    userId: string;
    projectId?: string;
    permissions?: string[];
    expiresInDays?: number;
  }): Promise<{ apiKey: ApiKey; secret: string }> {
    const rawKey = crypto.randomBytes(24).toString('hex');
    const key = `lk_live_${rawKey}`;
    const secret = crypto.randomBytes(32).toString('hex');
    const secretHash = crypto.createHash('sha256').update(secret).digest('hex');

    const expiresAt = data.expiresInDays
      ? new Date(Date.now() + data.expiresInDays * 24 * 60 * 60 * 1000)
      : undefined;

    const apiKey = this.apiKeyRepository.create({
      key,
      name: data.name,
      description: data.description,
      secret: secretHash,
      userId: data.userId,
      projectId: data.projectId,
      permissions: data.permissions || ['devices:read', 'alerts:read', 'telemetry:read'],
      status: ApiKeyStatus.ACTIVE,
      expiresAt,
    });

    const saved = await this.apiKeyRepository.save(apiKey);
    return { apiKey: saved, secret };
  }

  async listApiKeys(userId: string): Promise<ApiKey[]> {
    return this.apiKeyRepository.find({
      where: { userId },
      order: { createdAt: 'DESC' },
    });
  }

  async getApiKey(id: string): Promise<ApiKey> {
    const apiKey = await this.apiKeyRepository.findOne({ where: { id } });
    if (!apiKey) {
      throw new NotFoundException(`API Key 不存在: ${id}`);
    }
    return apiKey;
  }

  async revokeApiKey(id: string, userId: string): Promise<ApiKey> {
    const apiKey = await this.apiKeyRepository.findOne({ where: { id, userId } });
    if (!apiKey) {
      throw new NotFoundException(`API Key 不存在或无权操作`);
    }
    apiKey.status = ApiKeyStatus.DISABLED;
    return this.apiKeyRepository.save(apiKey);
  }

  async validateApiKey(key: string): Promise<ApiKey> {
    const apiKey = await this.apiKeyRepository.findOne({
      where: { key, status: ApiKeyStatus.ACTIVE },
    });

    if (!apiKey) {
      throw new UnauthorizedException('无效的 API Key');
    }

    if (apiKey.expiresAt && apiKey.expiresAt < new Date()) {
      apiKey.status = ApiKeyStatus.EXPIRED;
      await this.apiKeyRepository.save(apiKey);
      throw new UnauthorizedException('API Key 已过期');
    }

    // Update last used
    apiKey.lastUsedAt = new Date();
    await this.apiKeyRepository.save(apiKey);

    return apiKey;
  }

  async validateHmacSignature(
    apiKey: ApiKey,
    signature: string,
    timestamp: string,
    body: string,
  ): Promise<boolean> {
    // Reject timestamps older than 5 minutes
    const ts = parseInt(timestamp, 10);
    if (isNaN(ts) || Math.abs(Date.now() - ts) > 5 * 60 * 1000) {
      return false;
    }

    const expected = crypto
      .createHmac('sha256', apiKey.secret)
      .update(`${timestamp}.${body}`)
      .digest('hex');

    return crypto.timingSafeEqual(
      Buffer.from(signature),
      Buffer.from(expected),
    );
  }

  checkPermission(apiKey: ApiKey, required: string): boolean {
    return apiKey.permissions.includes(required) || apiKey.permissions.includes('*');
  }

  // ==================== Story 7.6-7.8: Webhook 管理 ====================

  async createWebhook(data: {
    name: string;
    url: string;
    events: WebhookEventType[];
    userId: string;
    projectId?: string;
    headers?: Record<string, string>;
  }): Promise<Webhook> {
    const secret = crypto.randomBytes(32).toString('hex');

    const webhook = this.webhookRepository.create({
      ...data,
      secret,
    });

    return this.webhookRepository.save(webhook);
  }

  async listWebhooks(userId: string): Promise<Webhook[]> {
    return this.webhookRepository.find({
      where: { userId },
      order: { createdAt: 'DESC' },
    });
  }

  async getWebhook(id: string): Promise<Webhook> {
    const webhook = await this.webhookRepository.findOne({ where: { id } });
    if (!webhook) {
      throw new NotFoundException(`Webhook 不存在: ${id}`);
    }
    return webhook;
  }

  async updateWebhook(id: string, data: Partial<Webhook>): Promise<Webhook> {
    const webhook = await this.getWebhook(id);
    Object.assign(webhook, data);
    return this.webhookRepository.save(webhook);
  }

  async deleteWebhook(id: string): Promise<void> {
    const result = await this.webhookRepository.delete(id);
    if (result.affected === 0) {
      throw new NotFoundException(`Webhook 不存在: ${id}`);
    }
  }

  async triggerWebhook(eventType: WebhookEventType, payload: Record<string, any>): Promise<void> {
    const webhooks = await this.webhookRepository.find({
      where: { status: WebhookStatus.ACTIVE, enabled: true },
    });

    const matching = webhooks.filter((w) => w.events.includes(eventType));

    for (const webhook of matching) {
      const delivery = this.deliveryRepository.create({
        webhookId: webhook.id,
        eventType,
        payload,
        status: DeliveryStatus.PENDING,
      });

      await this.deliveryRepository.save(delivery);
      await this.deliverWebhook(webhook, delivery);

      webhook.lastTriggeredAt = new Date();
      await this.webhookRepository.save(webhook);
    }
  }

  private async deliverWebhook(webhook: Webhook, delivery: WebhookDelivery): Promise<void> {
    const signature = crypto
      .createHmac('sha256', webhook.secret)
      .update(JSON.stringify(delivery.payload))
      .digest('hex');

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Webhook-Signature': signature,
      'X-Webhook-Event': delivery.eventType,
      'X-Webhook-Delivery': delivery.id,
      ...(webhook.headers || {}),
    };

    // TODO: 实际 HTTP 调用逻辑（使用 axios/fetch）
    // 这里仅模拟成功
    delivery.status = DeliveryStatus.DELIVERED;
    delivery.responseCode = 200;
    delivery.deliveredAt = new Date();
    delivery.attempts = 1;
    await this.deliveryRepository.save(delivery);

    this.logger.log(`Webhook delivered: ${webhook.name} -> ${delivery.eventType}`);
  }

  async getWebhookDeliveries(webhookId: string, options?: { limit?: number }): Promise<WebhookDelivery[]> {
    return this.deliveryRepository.find({
      where: { webhookId },
      order: { createdAt: 'DESC' },
      take: options?.limit || 50,
    });
  }

  async retryDelivery(deliveryId: string): Promise<WebhookDelivery> {
    const delivery = await this.deliveryRepository.findOne({ where: { id: deliveryId } });
    if (!delivery) {
      throw new NotFoundException(`投递记录不存在: ${deliveryId}`);
    }

    const webhook = await this.getWebhook(delivery.webhookId);

    if (delivery.attempts >= webhook.maxRetries) {
      delivery.status = DeliveryStatus.FAILED;
      await this.deliveryRepository.save(delivery);
      throw new BadRequestException('已达最大重试次数');
    }

    delivery.status = DeliveryStatus.RETRYING;
    delivery.attempts += 1;
    await this.deliveryRepository.save(delivery);

    await this.deliverWebhook(webhook, delivery);
    return delivery;
  }

  // ==================== Story 7.9-7.12: Open API 查询 ====================

  async getOpenDevices(apiKey: ApiKey, options: { page?: number; pageSize?: number; status?: string }) {
    const { page = 1, pageSize = 50 } = options;

    // This is a placeholder - in production, query device service
    return {
      data: [],
      total: 0,
      page,
      pageSize,
    };
  }

  async getOpenAlerts(apiKey: ApiKey, options: { page?: number; pageSize?: number; level?: string; status?: string }) {
    const { page = 1, pageSize = 50 } = options;

    return {
      data: [],
      total: 0,
      page,
      pageSize,
    };
  }

  async getOpenTelemetry(apiKey: ApiKey, deviceId: string, options: { startTime?: string; endTime?: string; metrics?: string[] }) {
    return {
      deviceId,
      data: [],
      metrics: options.metrics || [],
    };
  }

  // ==================== Story 7.13-7.15: API 调用日志 ====================

  async logApiCall(data: {
    apiKeyId?: string;
    userId?: string;
    method: string;
    path: string;
    query?: string;
    userAgent?: string;
    ip?: string;
    statusCode: number;
    durationMs: number;
  }): Promise<ApiCallLog> {
    const log = this.callLogRepository.create({
      ...data,
      timestamp: new Date(),
    });
    return this.callLogRepository.save(log);
  }

  async getCallStats(options: { apiKeyId?: string; startDate?: string; endDate?: string }) {
    const qb = this.callLogRepository.createQueryBuilder('log');

    if (options.apiKeyId) {
      qb.andWhere('log.apiKeyId = :apiKeyId', { apiKeyId: options.apiKeyId });
    }
    if (options.startDate) {
      qb.andWhere('log.timestamp >= :startDate', { startDate: options.startDate });
    }
    if (options.endDate) {
      qb.andWhere('log.timestamp <= :endDate', { endDate: options.endDate });
    }

    const totalCalls = await qb.getCount();

    // Reset for next query
    const errorQb = this.callLogRepository.createQueryBuilder('log');
    if (options.apiKeyId) {
      errorQb.andWhere('log.apiKeyId = :apiKeyId', { apiKeyId: options.apiKeyId });
    }
    if (options.startDate) {
      errorQb.andWhere('log.timestamp >= :startDate', { startDate: options.startDate });
    }
    if (options.endDate) {
      errorQb.andWhere('log.timestamp <= :endDate', { endDate: options.endDate });
    }
    const errorCalls = await errorQb.andWhere('log.statusCode >= 400').getCount();

    const topEndpoints = await this.callLogRepository
      .createQueryBuilder('log')
      .select('log.path', 'path')
      .addSelect('log.method', 'method')
      .addSelect('COUNT(*)', 'count')
      .groupBy('log.path, log.method')
      .orderBy('count', 'DESC')
      .limit(10)
      .getRawMany();

    return {
      totalCalls,
      errorCalls,
      successRate: totalCalls > 0 ? ((totalCalls - errorCalls) / totalCalls * 100).toFixed(2) + '%' : 'N/A',
      topEndpoints,
    };
  }

  async getCallLogs(options: { page?: number; pageSize?: number; apiKeyId?: string; path?: string }) {
    const { page = 1, pageSize = 50 } = options;

    const qb = this.callLogRepository
      .createQueryBuilder('log')
      .orderBy('log.timestamp', 'DESC')
      .skip((page - 1) * pageSize)
      .take(pageSize);

    if (options.apiKeyId) {
      qb.andWhere('log.apiKeyId = :apiKeyId', { apiKeyId: options.apiKeyId });
    }
    if (options.path) {
      qb.andWhere('log.path LIKE :path', { path: `%${options.path}%` });
    }

    const [items, total] = await qb.getManyAndCount();
    return { items, total, page, pageSize };
  }
}
