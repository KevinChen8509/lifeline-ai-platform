import { ApiProperty } from '@nestjs/swagger';
import {
  IsString,
  IsNotEmpty,
  IsOptional,
  MaxLength,
  MinLength,
  Matches,
} from 'class-validator';

export class CreateProjectDto {
  @ApiProperty({
    description: '项目名称',
    example: '智慧水务监测项目',
    maxLength: 100,
  })
  @IsString()
  @IsNotEmpty({ message: '项目名称不能为空' })
  @MaxLength(100, { message: '项目名称不能超过100个字符' })
  name: string;

  @ApiProperty({
    description: '项目编码（大写字母开头，可包含大写字母和数字，3-20字符）',
    example: 'WATER2024',
    minLength: 3,
    maxLength: 20,
  })
  @IsString()
  @IsNotEmpty({ message: '项目编码不能为空' })
  @MinLength(3, { message: '项目编码至少3个字符' })
  @MaxLength(20, { message: '项目编码不能超过20个字符' })
  @Matches(/^[A-Z][A-Z0-9]{2,19}$/, {
    message: '项目编码必须以大写字母开头，只能包含大写字母和数字',
  })
  code: string;

  @ApiProperty({
    description: '项目描述',
    example: '城市供水管网智能监测系统',
    required: false,
  })
  @IsString()
  @IsOptional()
  description?: string;
}
