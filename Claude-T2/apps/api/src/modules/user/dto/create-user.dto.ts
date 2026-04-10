import { IsString, IsEmail, IsOptional, MinLength, MaxLength, IsUUID, Matches } from 'class-validator';

export class CreateUserDto {
  @IsString()
  @MinLength(3, { message: '用户名长度不能少于3个字符' })
  @MaxLength(50, { message: '用户名长度不能超过50个字符' })
  @Matches(/^[a-zA-Z0-9_]+$/, { message: '用户名只能包含字母、数字和下划线' })
  username: string;

  @IsString()
  @MinLength(6, { message: '密码长度不能少于6个字符' })
  @MaxLength(100, { message: '密码长度不能超过100个字符' })
  @Matches(/^(?=.*[A-Za-z])(?=.*\d).+$/, { message: '密码必须包含字母和数字' })
  password: string; // 将在 service 中哈希后存为 passwordHash

  @IsString()
  @MaxLength(50)
  name: string;

  @IsOptional()
  @IsEmail()
  @MaxLength(100)
  email?: string;

  @IsOptional()
  @IsString()
  @MaxLength(20)
  phone?: string;

  @IsOptional()
  @IsUUID()
  roleId?: string;
}
