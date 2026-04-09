import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsString,
  IsNotEmpty,
  MaxLength,
  MinLength,
  Matches,
  IsOptional,
  IsUrl,
  IsNumber,
  Min,
  IsObject,
  IsBoolean,
} from 'class-validator';
import { ModelVersionStatus } from '../ai-model-version.entity';

/**
 * 创建模型版本DTO
 */
export class CreateModelVersionDto {
  @ApiProperty({
    description: '版本号（语义化版本）',
    example: 'v2.2.0',
    maxLength: 20,
  })
  @IsString()
  @IsNotEmpty({ message: '版本号不能为空' })
  @MaxLength(20, { message: '版本号不能超过20个字符' })
  @Matches(/^v\d+\.\d+\.\d+(-[a-zA-Z0-9]+)?$/, {
    message: '版本格式应为 vX.Y.Z 或 vX.Y.Z-suffix',
  })
  version: string;

  @ApiPropertyOptional({
    description: '模型文件下载URL',
    example: 'https://obs.example.com/models/mixed_connection_v2.2.0.bin',
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
    description: '文件签名（RSA-2048）',
  })
  @IsString()
  @IsOptional()
  signature?: string;

  @ApiPropertyOptional({
    description: '变更说明',
    example: '优化了错混接检测精度，降低了误报率',
  })
  @IsString()
  @IsOptional()
  changeLog?: string;

  @ApiPropertyOptional({
    description: '技术规格',
    example: {
      size: '20MB',
      latency: '≤80ms',
      memory: '512MB',
      input: '水位、流量数据',
    },
  })
  @IsObject()
  @IsOptional()
  specs?: Record<string, any>;
}

/**
 * 更新模型版本DTO
 */
export class UpdateModelVersionDto {
  @ApiPropertyOptional({
    description: '模型文件下载URL',
  })
  @IsUrl({}, { message: '请提供有效的URL' })
  @IsOptional()
  fileUrl?: string;

  @ApiPropertyOptional({
    description: '模型文件大小（字节）',
  })
  @IsNumber()
  @Min(0, { message: '文件大小不能为负数' })
  @IsOptional()
  fileSize?: number;

  @ApiPropertyOptional({
    description: '模型文件校验和（SHA256）',
    maxLength: 128,
  })
  @IsString()
  @MaxLength(128)
  @IsOptional()
  checksum?: string;

  @ApiPropertyOptional({
    description: '文件签名（RSA-2048）',
  })
  @IsString()
  @IsOptional()
  signature?: string;

  @ApiPropertyOptional({
    description: '变更说明',
  })
  @IsString()
  @IsOptional()
  changeLog?: string;

  @ApiPropertyOptional({
    description: '技术规格',
  })
  @IsObject()
  @IsOptional()
  specs?: Record<string, any>;
}

/**
 * 模型部署DTO
 */
export class DeployModelDto {
  @ApiProperty({
    description: '目标设备ID列表',
    example: ['device-uuid-1', 'device-uuid-2'],
    type: [String],
  })
  @IsString({ each: true })
  deviceIds: string[];

  @ApiPropertyOptional({
    description: '指定版本号（默认使用当前发布版本）',
    example: 'v2.2.0',
  })
  @IsString()
  @IsOptional()
  version?: string;

  @ApiPropertyOptional({
    description: '是否强制更新（即使设备已有该版本）',
    default: false,
  })
  @IsBoolean()
  @IsOptional()
  force?: boolean;
}
