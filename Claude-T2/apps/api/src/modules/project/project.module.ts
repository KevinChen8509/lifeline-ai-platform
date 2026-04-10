import { Module, forwardRef } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Project } from './project.entity';
import { ProjectUser } from './project-user.entity';
import { ProjectService } from './project.service';
import { ProjectController } from './project.controller';
import { AuditModule } from '../audit/audit.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([Project, ProjectUser]),
    forwardRef(() => AuditModule),
  ],
  controllers: [ProjectController],
  providers: [ProjectService],
  exports: [ProjectService, TypeOrmModule],
})
export class ProjectModule {}
