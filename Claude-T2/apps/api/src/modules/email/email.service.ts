import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as nodemailer from 'nodemailer';
import { Transporter } from 'nodemailer';

export interface EmailConfig {
  host: string;
  port: number;
  secure: boolean;
  user: string;
  pass: string;
  from: string;
}

@Injectable()
export class EmailService implements OnModuleInit {
  private readonly logger = new Logger(EmailService.name);
  private transporter: Transporter | null = null;
  private config: EmailConfig | null = null;
  private isConfigured = false;

  constructor(private readonly configService: ConfigService) {}

  async onModuleInit() {
    this.config = {
      host: this.configService.get<string>('SMTP_HOST', ''),
      port: this.configService.get<number>('SMTP_PORT', 587),
      secure: this.configService.get<boolean>('SMTP_SECURE', false),
      user: this.configService.get<string>('SMTP_USER', ''),
      pass: this.configService.get<string>('SMTP_PASS', ''),
      from: this.configService.get<string>('SMTP_FROM', 'noreply@lifeline-ai.com'),
    };

    // 检查邮件服务是否配置
    if (this.config.host && this.config.user && this.config.pass) {
      this.transporter = nodemailer.createTransport({
        host: this.config.host,
        port: this.config.port,
        secure: this.config.secure,
        auth: {
          user: this.config.user,
          pass: this.config.pass,
        },
      });
      this.isConfigured = true;
      this.logger.log('邮件服务已配置');
    } else {
      this.logger.warn('邮件服务未配置，邮件发送功能将被跳过');
    }
  }

  /**
   * 检查邮件服务是否可用
   */
  isAvailable(): boolean {
    return this.isConfigured && this.transporter !== null;
  }

  /**
   * 发送激活邮件
   */
  async sendActivationEmail(
    email: string,
    username: string,
    activationLink?: string,
  ): Promise<boolean> {
    if (!this.isAvailable()) {
      this.logger.warn(`邮件服务不可用，跳过发送激活邮件给 ${email}`);
      return false;
    }

    try {
      const mailOptions = {
        from: this.config!.from,
        to: email,
        subject: '【生命线AI感知云平台】账号激活通知',
        html: this.getActivationEmailTemplate(username, activationLink),
      };

      await this.transporter!.sendMail(mailOptions);
      this.logger.log(`激活邮件已发送至 ${email}`);
      return true;
    } catch (error) {
      this.logger.error(`发送激活邮件失败: ${error.message}`, error.stack);
      return false;
    }
  }

  /**
   * 激活邮件模板
   */
  private getActivationEmailTemplate(username: string, activationLink?: string): string {
    const link = activationLink || `${process.env.FRONTEND_URL || 'http://localhost:5173'}/login`;

    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
          .container { max-width: 600px; margin: 0 auto; padding: 20px; }
          .header { background: #0050b3; color: white; padding: 20px; text-align: center; }
          .content { padding: 20px; background: #f5f5f5; }
          .button { display: inline-block; padding: 12px 24px; background: #0050b3; color: white; text-decoration: none; border-radius: 4px; }
          .footer { padding: 20px; text-align: center; color: #666; font-size: 12px; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <h1>生命线AI感知云平台</h1>
          </div>
          <div class="content">
            <h2>您好，${username}！</h2>
            <p>您的账号已创建成功，当前状态为"待激活"。</p>
            <p>请联系管理员激活您的账号，或点击下方按钮登录系统：</p>
            <p style="text-align: center; margin: 30px 0;">
              <a href="${link}" class="button">登录系统</a>
            </p>
            <p>如果您有任何问题，请联系系统管理员。</p>
          </div>
          <div class="footer">
            <p>© ${new Date().getFullYear()} 生命线AI感知云平台 - 城市生命线基础设施智能监测</p>
          </div>
        </div>
      </body>
      </html>
    `;
  }
}
