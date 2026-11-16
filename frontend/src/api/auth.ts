import { api, write } from './client';
export interface User {
  id: number;
  username: string;
  role: 'USER' | 'ADMIN';
}
export interface AuthStatus {
  mode: 'LOCAL' | 'PUBLIC';
  registrationEnabled: boolean;
  positionRiskEnabled: boolean;
}
/** 登录页公开开关，不包含任何凭据。 */
export const getAuthStatus = () => api<AuthStatus>('/auth/status');
/** 会话只存储在HttpOnly Cookie；页面不读写令牌。 */
export const getMe = () => api<User>('/auth/me');
export const login = (username: string, password: string) =>
  api<User>('/auth/login', write('POST', { username, password }));
export const register = (username: string, password: string, invitation: string) =>
  api<User>('/auth/register', write('POST', { username, password, invitation }));
export const logout = () => api<null>('/auth/logout', write('POST'));
