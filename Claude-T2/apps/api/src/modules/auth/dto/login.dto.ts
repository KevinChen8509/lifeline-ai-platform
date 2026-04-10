import { IsString, MinLength, MaxLength } from 'class-validator';

export class LoginDto {
  @IsString()
  @MinLength(3, { message: '用户名长度不能少于3个字符' })
  @MaxLength(50, { message: '用户名长度不能超过50个字符' })
  username: string;

  @IsString()
  @MinLength(6, { message: '密码长度不能少于6个字符' })
  @MaxLength(100, { message: '密码长度不能超过100个字符' })
  password: string;
}
