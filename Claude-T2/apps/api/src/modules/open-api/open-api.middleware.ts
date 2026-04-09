import { Injectable, NestMiddleware, UnauthorizedException } from '@nestjs/common';
import { Request, Response, NextFunction } from 'express';
import { OpenApiService } from './open-api.service';

@Injectable()
export class OpenApiAuthMiddleware implements NestMiddleware {
  constructor(private readonly openApiService: OpenApiService) {}

  async use(req: Request, res: Response, next: NextFunction) {
    const apiKeyHeader = req.headers['x-api-key'] as string;
    const signature = req.headers['x-api-signature'] as string;
    const timestamp = req.headers['x-api-timestamp'] as string;

    if (!apiKeyHeader) {
      throw new UnauthorizedException('缺少 X-API-Key 头');
    }

    const apiKey = await this.openApiService.validateApiKey(apiKeyHeader);

    // If signature and timestamp provided, validate HMAC
    if (signature && timestamp) {
      const body = JSON.stringify(req.body || {});
      const isValid = await this.openApiService.validateHmacSignature(
        apiKey,
        signature,
        timestamp,
        body,
      );
      if (!isValid) {
        throw new UnauthorizedException('签名验证失败');
      }
    }

    // Attach api key to request for controller use
    (req as any).apiKey = apiKey;
    next();
  }
}
