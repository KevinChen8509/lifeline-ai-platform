import { Directive, DirectiveBinding } from 'vue';
import { usePermissionStore } from '@/stores/permission';

/**
 * 权限指令
 * 用于控制元素的显示/隐藏
 *
 * @example
 * <button v-permission="'create:Device'">创建设备</button>
 * <button v-permission="['manage', 'Device']">管理设备</button>
 * <div v-permission="{ action: 'read', subject: 'Model' }">查看模型</div>
 */
export const permission: Directive<HTMLElement> = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const permissionStore = usePermissionStore();
    const { value } = binding;

    if (!value) {
      console.warn('v-permission: 缺少权限参数');
      return;
    }

    let action: string;
    let subject: string;

    // 支持多种参数格式
    if (typeof value === 'string') {
      // 格式: 'action:Subject' 或 'action:subject'
      const parts = value.split(':');
      if (parts.length !== 2) {
        console.warn('v-permission: 字符串格式应为 "action:Subject"');
        return;
      }
      action = parts[0];
      subject = parts[1];
    } else if (Array.isArray(value) && value.length >= 2) {
      // 格式: ['action', 'Subject']
      [action, subject] = value;
    } else if (typeof value === 'object' && value.action && value.subject) {
      // 格式: { action: 'action', subject: 'Subject' }
      action = value.action;
      subject = value.subject;
    } else {
      console.warn('v-permission: 无效的参数格式');
      return;
    }

    // 检查权限
    if (!permissionStore.can(action, subject)) {
      // 无权限，移除元素
      el.style.display = 'none';
      el.setAttribute('data-permission-denied', 'true');
    }
  },

  updated(el: HTMLElement, binding: DirectiveBinding) {
    const permissionStore = usePermissionStore();
    const { value } = binding;

    if (!value) return;

    let action: string;
    let subject: string;

    if (typeof value === 'string') {
      const parts = value.split(':');
      if (parts.length !== 2) return;
      [action, subject] = parts;
    } else if (Array.isArray(value) && value.length >= 2) {
      [action, subject] = value;
    } else if (typeof value === 'object' && value.action && value.subject) {
      action = value.action;
      subject = value.subject;
    } else {
      return;
    }

    // 检查权限
    const hasPermission = permissionStore.can(action, subject);
    const wasDenied = el.getAttribute('data-permission-denied') === 'true';

    if (!hasPermission && !wasDenied) {
      el.style.display = 'none';
      el.setAttribute('data-permission-denied', 'true');
    } else if (hasPermission && wasDenied) {
      el.style.display = '';
      el.removeAttribute('data-permission-denied');
    }
  },
};

/**
 * 注册权限指令
 */
export function setupPermissionDirective(app: any): void {
  app.directive('permission', permission);
}

export default permission;
