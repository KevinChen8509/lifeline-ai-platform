import { Injectable, Logger, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';

@Injectable()
export class RedisService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(RedisService.name);
  private client: Redis;

  constructor(private readonly configService: ConfigService) {}

  onModuleInit() {
    const host = this.configService.get<string>('REDIS_HOST', 'localhost');
    const port = this.configService.get<number>('REDIS_PORT', 6379);
    const password = this.configService.get<string>('REDIS_PASSWORD');

    this.client = new Redis({
      host,
      port,
      password: password || undefined,
      retryStrategy: (times: number) => {
        if (times > 3) {
          this.logger.error('Redis connection failed after 3 retries');
          return null; // Stop retrying
        }
        const delay = Math.min(times * 200, 2000);
        this.logger.warn(`Redis connection retry in ${delay}ms (attempt ${times})`);
        return delay;
      },
    });

    this.client.on('connect', () => {
      this.logger.log(`Redis connected: ${host}:${port}`);
    });

    this.client.on('error', (err) => {
      this.logger.error(`Redis error: ${err.message}`);
    });
  }

  onModuleDestroy() {
    if (this.client) {
      this.client.disconnect();
      this.logger.log('Redis connection closed');
    }
  }

  /**
   * 设置键值
   * @param key 键名
   * @param value 值
   * @param ttl 过期时间（秒）
   */
  async set(key: string, value: string, ttl?: number): Promise<void> {
    try {
      if (ttl) {
        await this.client.setex(key, ttl, value);
      } else {
        await this.client.set(key, value);
      }
    } catch (error) {
      this.logger.error(`Redis set error for key ${key}: ${error}`);
      throw error;
    }
  }

  /**
   * 获取键值
   * @param key 键名
   * @returns 值或null
   */
  async get(key: string): Promise<string | null> {
    try {
      return await this.client.get(key);
    } catch (error) {
      this.logger.error(`Redis get error for key ${key}: ${error}`);
      throw error;
    }
  }

  /**
   * 删除键
   * @param key 键名
   */
  async del(key: string): Promise<void> {
    try {
      await this.client.del(key);
    } catch (error) {
      this.logger.error(`Redis del error for key ${key}: ${error}`);
      throw error;
    }
  }

  /**
   * 检查键是否存在
   * @param key 键名
   * @returns 是否存在
   */
  async exists(key: string): Promise<boolean> {
    try {
      const result = await this.client.exists(key);
      return result === 1;
    } catch (error) {
      this.logger.error(`Redis exists error for key ${key}: ${error}`);
      throw error;
    }
  }

  /**
   * 按模式删除键（使用SCAN避免阻塞）
   * @param pattern 键名模式（如 refresh:{userId}:*）
   */
  async delByPattern(pattern: string): Promise<number> {
    // 验证模式安全性，防止意外删除所有数据
    if (!pattern || pattern === '*' || !pattern.includes(':')) {
      throw new Error(`Invalid pattern: "${pattern}". Pattern must include ":" separator and cannot be "*"`);
    }

    try {
      let cursor = '0';
      let deletedCount = 0;
      const batchSize = 100;

      do {
        // 使用 SCAN 代替 KEYS，避免阻塞 Redis
        const result = await this.client.scan(cursor, 'MATCH', pattern, 'COUNT', batchSize);
        cursor = result[0];
        const keys = result[1];

        if (keys.length > 0) {
          // 批量删除，避免参数过多
          const chunks = this.chunkArray(keys, batchSize);
          for (const chunk of chunks) {
            await this.client.del(...chunk);
            deletedCount += chunk.length;
          }
        }
      } while (cursor !== '0');

      return deletedCount;
    } catch (error) {
      this.logger.error(`Redis delByPattern error for pattern ${pattern}: ${error}`);
      throw error;
    }
  }

  /**
   * 将数组分割成指定大小的块
   */
  private chunkArray<T>(array: T[], size: number): T[][] {
    const chunks: T[][] = [];
    for (let i = 0; i < array.length; i += size) {
      chunks.push(array.slice(i, i + size));
    }
    return chunks;
  }

  /**
   * 原子性获取并删除键（用于Token刷新）
   * @param key 键名
   * @returns 键的值，如果不存在则返回null
   */
  async getDel(key: string): Promise<string | null> {
    try {
      // 使用 Lua 脚本实现原子性 GET + DEL
      const script = `
        local value = redis.call('GET', KEYS[1])
        if value then
          redis.call('DEL', KEYS[1])
        end
        return value
      `;
      const result = await this.client.eval(script, 1, key);
      return result as string | null;
    } catch (error) {
      this.logger.error(`Redis getDel error for key ${key}: ${error}`);
      throw error;
    }
  }
}
