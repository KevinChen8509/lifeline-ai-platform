import { ApiPropertyOptional, PartialType } from '@nestjs/swagger';
import { CreateAiModelDto } from './create-ai-model.dto';

/**
 * 更新AI模型DTO
 * 所有字段均为可选
 */
export class UpdateAiModelDto extends PartialType(CreateAiModelDto) {}
