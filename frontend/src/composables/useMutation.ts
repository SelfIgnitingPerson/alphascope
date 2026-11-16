import { ref } from 'vue';
/** 防止重复提交，并将操作错误保留在当前表单。 */
export function useMutation() {
  const busy = ref(false);
  const error = ref('');
  async function run(action: () => Promise<unknown>): Promise<boolean> {
    if (busy.value) {
      return false;
    }
    busy.value = true;
    error.value = '';
    try {
      await action();
      return true;
    } catch (reason) {
      error.value = reason instanceof Error ? reason.message : String(reason);
      return false;
    } finally {
      busy.value = false;
    }
  }
  return { busy, error, run };
}
