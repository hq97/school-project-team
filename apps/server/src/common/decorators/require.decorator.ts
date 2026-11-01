import { SetMetadata } from '@nestjs/common';

export const REQUIRED_MENU_METADATA = 'school:required-menu';
export const REQUIRED_BUTTON_METADATA = 'school:required-button';

/** 菜单权限点（docs/03 M01–M15）：不满足 → 40301 */
export const Menu = (code: string) => SetMetadata(REQUIRED_MENU_METADATA, code);

/** 按钮/操作权限点（docs/03 BTN-*）：不满足 → 40302 */
export const Button = (code: string) => SetMetadata(REQUIRED_BUTTON_METADATA, code);
