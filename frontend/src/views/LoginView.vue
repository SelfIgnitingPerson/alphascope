<script setup lang="ts">
import { ref } from 'vue';
import { login, register } from '../api/auth';
import { useAuth } from '../composables/useAuth';
import { useMutation } from '../composables/useMutation';
const { status, user } = useAuth();
const username = ref('');
const password = ref('');
const invitation = ref('');
const creating = ref(false);
const mutation = useMutation();
const { busy, error } = mutation;
async function submit() {
  await mutation.run(async () => {
    const authenticated = creating.value
      ? await register(username.value.trim(), password.value, invitation.value)
      : await login(username.value.trim(), password.value);
    password.value = '';
    invitation.value = '';
    user.value = authenticated;
  });
}
</script>
<template>
  <main class="login-screen">
    <section class="login-card">
      <p class="eyebrow">ALPHASCOPE</p>
      <h1>{{ creating ? '创建独立研究账号' : '登录研究工作台' }}</h1>
      <p>你的自选、持仓、报告和任务仅属于当前账号。</p>
      <form
        @submit.prevent="submit"
        class="login-form"
      >
        <label for="login-username">账号</label>
        <el-input
          id="login-username"
          v-model="username"
          autocomplete="username"
          maxlength="64"
          required
        />
        <label for="login-password">密码{{ creating ? '（至少12字符）' : '' }}</label>
        <el-input
          id="login-password"
          v-model="password"
          type="password"
          show-password
          :autocomplete="creating ? 'new-password' : 'current-password'"
          maxlength="128"
          required
        />
        <template v-if="creating">
          <label for="login-invitation">邀请码</label>
          <el-input
            id="login-invitation"
            v-model="invitation"
            type="password"
            autocomplete="off"
            required
          />
        </template>
        <el-alert
          v-if="error"
          :title="error"
          type="error"
          :closable="false"
        />
        <el-button
          type="primary"
          data-testid="auth-submit"
          native-type="submit"
          :loading="busy"
        >
          {{ creating ? '注册并进入' : '登录' }}
        </el-button>
        <el-button
          data-testid="auth-toggle"
          v-if="status?.registrationEnabled"
          link
          :disabled="busy"
          @click="
            creating = !creating;
            error = '';
          "
        >
          {{ creating ? '已有账号，返回登录' : '使用邀请码注册' }}
        </el-button>
      </form>
      <p class="help-text">
        会话过期需要重新登录，已保存的研究记录不会丢失。密码恢复请联系部署管理员。
      </p>
    </section>
  </main>
</template>
