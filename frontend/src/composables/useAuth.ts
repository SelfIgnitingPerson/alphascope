import { ref } from 'vue';
import { ApiError } from '../api/client';
import { getAuthStatus, getMe, logout, type AuthStatus, type User } from '../api/auth';
const status = ref<AuthStatus>();
const user = ref<User>();
const ready = ref(false);
const error = ref('');
/** 登录范围由服务端会话确定；账号切换会完整重建工作台，清除旧页面数据。 */
export function useAuth() {
  async function initialize() {
    ready.value = false;
    error.value = '';
    try {
      status.value = await getAuthStatus();
      try {
        user.value = await getMe();
      } catch (reason) {
        if (
          status.value.mode === 'LOCAL' ||
          !(reason instanceof ApiError) ||
          reason.code !== 'UNAUTHENTICATED'
        )
          throw reason;
        user.value = undefined;
      }
    } catch (reason) {
      error.value = reason instanceof Error ? reason.message : String(reason);
    } finally {
      ready.value = true;
    }
  }
  async function signOut() {
    await logout();
    user.value = undefined;
    window.location.reload();
  }
  return { status, user, ready, error, initialize, signOut };
}
window.addEventListener('alphascope-session-expired', () => {
  user.value = undefined;
});
