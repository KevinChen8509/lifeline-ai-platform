import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import {
  ApiKey,
  Webhook,
  WebhookDelivery,
  ApiCallLog,
} from './open-api.entity';
import {
  ApiKeyController,
  WebhookController,
  OpenApiController,
  ApiLogController,
} from './open-api.controller';
import { OpenApiService } from './open-api.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      ApiKey,
      Webhook,
      WebhookDelivery,
      ApiCallLog,
    ]),
  ],
  controllers: [ApiKeyController, WebhookController, OpenApiController, ApiLogController],
  providers: [OpenApiService],
  exports: [OpenApiService],
})
export class OpenApiModule {}
