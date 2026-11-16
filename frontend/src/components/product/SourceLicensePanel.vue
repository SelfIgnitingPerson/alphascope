<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import SectionCard from '../common/SectionCard.vue';
import type { SourceLicense } from '../../api/product';
import { sourceName } from '../../utils/format';

/** 用实际许可检查开关解释登记用途，避免把许可缺项误作行情故障。 */
const props = defineProps<{ licenses: SourceLicense[]; enforced?: boolean }>();
const guidance = computed(() => {
  if (props.enforced === false)
    return {
      title: '当前使用：不强制填写此登记',
      detail:
        '服务端未启用强制许可检查，未登记不会因此阻止采集、展示或导出。你可以先进行本机研究；实际用途仍需符合数据来源的使用条款。',
    };
  if (props.enforced === true)
    return {
      title: '当前使用：需要核对并登记许可',
      detail:
        '服务端已启用强制许可检查。缺少有效登记或对应使用范围时，相关采集、展示或导出会被拦截；请管理员按实际取得的依据补齐。',
    };
  return {
    title: '当前许可检查设置尚未确认',
    detail: '尚未读取到服务端的许可检查设置，请刷新后核对；不能仅凭“未登记”判断是否会阻止使用。',
  };
});
const permissions: Record<string, string> = {
  COLLECT: '采集保存',
  DISPLAY: '对外展示',
  EXPORT: '证据导出',
  DERIVED_REPORT: '生成研究报告',
};
const states: Record<string, { label: string; detail: string; tone: string }> = {
  MISSING: {
    label: '未登记',
    detail: '系统尚未记录使用条款或授权依据，不代表行情通道不可用。',
    tone: 'muted',
  },
  EXPIRED: {
    label: '登记已过期',
    detail: '已登记的有效期已结束；取得新的有效依据后再更新登记。',
    tone: 'warning',
  },
  NOT_YET_VALID: {
    label: '尚未生效',
    detail: '已登记，但尚未到约定开始日期，不能按当前有效登记处理。',
    tone: 'warning',
  },
  DECLARED_BY_OPERATOR: {
    label: '已登记 · 待自行核验',
    detail: '管理员已填写依据与有效期；系统不代表供应商确认授权。',
    tone: 'info',
  },
};
const state = (code: string) =>
  states[code] || {
    label: '登记状态待核对',
    detail: '系统尚不能解释此登记状态，请核对登记资料。',
    tone: 'muted',
  };
/** 只启用可解析的HTTPS依据链接，拒绝脚本及含用户凭据的链接。 */
function evidenceLink(value: string | null): string | undefined {
  if (!value) return undefined;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : undefined;
  } catch {
    return undefined;
  }
}
const missing = computed(() => props.licenses.filter((row) => row.status === 'MISSING').length);
</script>

<template>
  <SectionCard
    title="数据使用许可"
    description="这是使用条款与授权依据的登记台账，用于说明各数据来源允许哪些用途。"
  >
    <div
      class="license-summary"
      data-testid="license-guidance"
    >
      <strong>{{ guidance.title }}</strong>
      <p>{{ guidance.detail }}</p>
      <p>“未登记”只表示这里还没有依据记录，不表示没有行情，也不表示接口被封禁。</p>
      <RouterLink to="/channels">行情获取失败？到行情通道查看原因与测试 →</RouterLink>
    </div>
    <p class="help-text">
      准备对外提供产品时，再按实际条款核对采集、展示、报告与导出范围。本页不能开通接口、购买会员或自动取得授权。
    </p>
    <div class="license-heading">
      <h3>各来源登记情况</h3>
      <span>共 {{ licenses.length }} 个来源 · {{ missing }} 个未登记</span>
    </div>
    <p
      v-if="!licenses.length"
      class="help-text"
    >
      当前没有可列示的来源，请核对数据来源配置。
    </p>
    <div class="license-grid">
      <article
        v-for="row in licenses"
        :key="row.source"
        class="license-card"
        :data-source="row.source"
      >
        <header>
          <h4>{{ sourceName(row.source) }}</h4>
          <span
            class="state-badge"
            :class="`state-badge--${state(row.status).tone}`"
          >
            {{ state(row.status).label }}
          </span>
        </header>
        <p>{{ state(row.status).detail }}</p>
        <dl>
          <div>
            <dt>已登记用途</dt>
            <dd>
              {{
                row.permissions.map((code) => permissions[code] || '待核对用途').join('、') ||
                '暂无已登记范围'
              }}
            </dd>
          </div>
          <div>
            <dt>登记截止日期</dt>
            <dd>{{ row.validUntil || '未提供' }}</dd>
          </div>
        </dl>
        <details class="license-evidence">
          <summary>查看登记依据与来源代码</summary>
          <p>
            来源代码：
            <code>{{ row.source }}</code>
          </p>
          <p>条款或合同定位：{{ row.reference || '尚未填写' }}</p>
          <a
            v-if="evidenceLink(row.evidenceUrl)"
            :href="evidenceLink(row.evidenceUrl)"
            target="_blank"
            rel="noopener noreferrer"
          >
            查看已登记依据 ↗
          </a>
          <p v-else>
            {{ row.evidenceUrl ? '依据链接不可用，请核对登记资料。' : '尚未填写依据链接。' }}
          </p>
        </details>
      </article>
    </div>

    <details
      class="reading-details license-editor"
      data-testid="license-editor"
    >
      <summary>
        <strong>登记或更新使用许可</strong>
        <span>取得实际依据后再填写 · 默认收起</span>
      </summary>
      <p class="help-text">
        选择来源 → 对照条款选择允许用途 → 填写依据和有效期 →
        保存。每次保存保留修订。密钥或密码不要填写到链接及备注。
      </p>
      <slot />
    </details>
  </SectionCard>
</template>

<style scoped>
.license-summary {
  padding: 16px;
  background: #f2f7fa;
  border-radius: 8px;
  line-height: 1.8;
}
.license-summary strong {
  color: #245c72;
}
.license-summary p {
  margin: 6px 0;
}
.license-heading {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  align-items: center;
  justify-content: space-between;
  margin: 24px 0 14px;
}
.license-heading h3 {
  margin: 0;
  font-size: 15px;
}
.license-heading span {
  color: #71839b;
  font-size: 12px;
}
.license-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.license-card {
  min-width: 0;
  border: 1px solid #e1e8f0;
  border-radius: 8px;
  padding: 18px;
  overflow-wrap: anywhere;
}
.license-card header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.license-card h4 {
  margin: 0;
  font-size: 14px;
}
.license-card p,
.license-card dl {
  font-size: 12px;
  line-height: 1.8;
}
.license-card > p {
  color: #657994;
}
.license-card dl {
  margin: 14px 0;
}
.license-card dl > div {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  gap: 8px;
  margin: 6px 0;
}
.license-card dt {
  color: #71839b;
}
.license-card dd {
  margin: 0;
}
.license-evidence {
  border-top: 1px solid #edf1f5;
  padding-top: 12px;
  font-size: 12px;
}
.license-evidence summary {
  color: #245c72;
  cursor: pointer;
}
.license-evidence code {
  white-space: normal;
}
.license-editor {
  margin-top: 24px;
}
@media (max-width: 800px) {
  .license-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
