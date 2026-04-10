import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsString,
  IsNotEmpty,
  MaxLength,
  MinLength,
  Matches,
  IsEnum,
  IsOptional,
  IsUrl,
  IsNumber,
  Min,
  IsArray,
  IsObject,
} from 'class-validator';
import { AiModelType, AiModelStatus } from '../ai-model.entity';

/**
 * 创建AI模型DTO
 */
export class CreateAiModelDto {
  @ApiProperty({
    description: '模型名称',
    example: '错混接检测模型',
    maxLength: 100,
  })
  @IsString()
  @IsNotEmpty({ message: '模型名称不能为空' })
  @MaxLength(100, { message: '模型名称不能超过100个字符' })
  name: string;

  @ApiProperty({
    description: '模型编码',
    example: 'MIXED_CONNECTION_V2',
    maxLength: 50,
  })
  @IsString()
  @IsNotEmpty({ message: '模型编码不能为空' })
  @MaxLength(50, { message: '模型编码不能超过50个字符' })
  @Matches(/^[A-Z][A-Z0-9_]*$/, {
    message: '模型编码必须以大写字母开头，只能包含大写字母、数字和下划线',
  })
  code: string;

  @ApiProperty({
    description: '模型版本',
    example: 'v2.1.0',
    maxLength: 20,
  })
  @IsString()
  @IsNotEmpty({ message: '模型版本不能为空' })
  @MaxLength(20, { message: '模型版本不能超过20个字符' })
  @Matches(/^v\d+\.\d+\.\d+(-[a-zA-Z0-9]+)?$/, {
    message: '版本格式应为 vX.Y.Z 或 vX.Y.Z-suffix',
  })
  version: string;

  @ApiProperty({
    description: '模型类型',
    enum: AiModelType,
    example: AiModelType.MIXED_CONNECTION,
  })
  @IsEnum(AiModelType, { message: '无效的模型类型' })
  type: AiModelType;

  @ApiPropertyOptional({
    description: '模型描述',
    example: '基于深度学习的管网错混接智能检测模型',
  })
  @IsString()
  @IsOptional()
  description?: string;

  @ApiPropertyOptional({
    description: '模型文件下载URL',
    example: 'https://obs.example.com/models/mixed_connection_v2.1.0.bin',
  })
  @IsUrl({}, { message: '请提供有效的URL' })
  @IsOptional()
  fileUrl?: string;

  @ApiPropertyOptional({
    description: '模型文件大小（字节）',
    example: 20971520,
  })
  @IsNumber()
  @Min(0, { message: '文件大小不能为负数' })
  @IsOptional()
  fileSize?: number;

  @ApiPropertyOptional({
    description: '模型文件校验和（SHA256）',
    example: 'sha256:abc123...',
    maxLength: 128,
  })
  @IsString()
  @MaxLength(128)
  @IsOptional()
  checksum?: string;

  @ApiPropertyOptional({
    description: '模型状态',
    enum: AiModelStatus,
    default: AiModelStatus.DRAFT,
  })
  @IsEnum(AiModelStatus, { message: '无效的模型状态' })
  @IsOptional()
  status?: AiModelStatus;

  @ApiPropertyOptional({
    description: '技术规格',
    example: {
      size: '20MB',
      latency: '≤100ms',
      input: '水位、流量数据',
      output: '分析结果及置信度',
    },
  })
  @IsObject()
  @IsOptional()
  specs?: Record<string, any>;

  @ApiPropertyOptional({
    description: '适用设备类型列表',
    example: ['WATER_LEVEL_SENSOR', 'FLOW_METER'],
    type: [String],
  })
  @IsArray()
  @IsString({ each: true })
  @IsOptional()
  applicableDeviceTypes?: string[];
}
