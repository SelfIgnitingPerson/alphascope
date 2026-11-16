<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAuth } from '../../composables/useAuth';
import SectionCard from './SectionCard.vue';
import { quickStart, moduleGuide, planReading } from '../../content/product-guide';
const group = ref('市场调研');
const { user, status: access } = useAuth();
const available = (route: string) =>
  ['/operations', '/product'].includes(route)
    ? user.value?.role === 'ADMIN'
    : route === '/portfolio'
      ? Boolean(access.value?.positionRiskEnabled)
      : true;
const modules = computed(() => moduleGuide.filter((item) => item.group === group.value));
</script>
<template>
  <div
    class="page-stack product-guide"
    data-testid="product-guide"
  >
    <SectionCard
      title="本产品要帮你完成什么"
      description="先洞察市场与产业，再研究关注股票，最后形成有证据、可跟踪的行动计划。"
    >
      <div class="guide-purpose">
        <article>
          <strong>1 · 洞察市场</strong>
          <p>了解市场背景、资金与广度、产业链样本及机构预期，先核对数据范围。</p>
        </article>
        <article>
          <strong>2 · 研究股票</strong>
          <p>按短期、中期、长期分别判断，明确买入、加仓、减仓、退出的条件与价位。</p>
        </article>
        <article>
          <strong>3 · 跟踪验证</strong>
          <p>用报告保存当期证据，下一交易日复核条件，再用历史验证检查方法效果。</p>
        </article>
      </div>
      <p class="query-note">
        贯穿流程：市场总览 → AI产业链 / 机构预测 → 自选股 → 任务同步 → 个股持有与买卖计划 → 研究报告
        → 下一期跟踪与历史验证。
      </p>
    </SectionCard>
    <SectionCard
      title="第一次使用：按这六步走"
      description="首次补采需要等待后台任务；看清业务日期和数据质量后再读行动条件。"
    >
      <ol class="guide-steps">
        <li
          v-for="(step, index) in quickStart"
          :key="step.title"
        >
          <span>{{ index + 1 }}</span>
          <div>
            <h3>{{ step.title }}</h3>
            <p>{{ step.detail }}</p>
            <RouterLink
              :to="step.to"
              class="text-link"
            >
              {{ step.action }} →
            </RouterLink>
          </div>
        </li>
      </ol>
    </SectionCard>
    <SectionCard title="每个模块干什么，怎样串起来">
      <div
        class="button-row guide-groups"
        role="group"
        aria-label="模块使用说明分组"
      >
        <button
          v-for="name in ['市场调研', '股票与计划', '任务与维护']"
          :key="name"
          type="button"
          :aria-pressed="group === name"
          @click="group = name"
        >
          {{ name }}
        </button>
      </div>
      <details
        v-for="item in modules"
        :key="item.title"
        class="reading-details"
      >
        <summary>
          {{ item.title }}
          <span>{{ item.purpose }}</span>
        </summary>
        <dl class="reading-definitions">
          <div>
            <dt>怎么操作</dt>
            <dd>{{ item.use }}</dd>
          </div>
          <div>
            <dt>得到什么</dt>
            <dd>{{ item.output }}</dd>
          </div>
          <div>
            <dt>下一步</dt>
            <dd>{{ item.next }}</dd>
          </div>
        </dl>
        <RouterLink
          v-if="available(item.to)"
          :to="item.to"
          class="text-link"
        >
          打开模块 →
        </RouterLink>
      </details>
    </SectionCard>
    <SectionCard title="怎样读懂买卖计划">
      <ol class="guide-plan-reading">
        <li
          v-for="item in planReading"
          :key="item.title"
        >
          <strong>{{ item.title }}</strong>
          <p>{{ item.detail }}</p>
        </li>
      </ol>
      <RouterLink
        to="/terms"
        class="text-link"
      >
        查看状态和股票术语 →
      </RouterLink>
    </SectionCard>
    <SectionCard title="常见情况怎么处理">
      <details class="reading-details">
        <summary>日期选了今天，为什么还是旧行情？</summary>
        <p>
          本产品研究已完成的收盘行情。点击计划状态旁的问号，会看到本条记录的研究日、最近已存行情日和具体缺项。盘前或供应商未发布时，可在页头选择已有资料的交易日回顾；需要当天结果时，待收盘数据齐备后再同步。系统不会自动替换研究日期。
        </p>
      </details>
      <details class="reading-details">
        <summary>任务成功了，为什么仍有数据不足？</summary>
        <p>
          执行成功与证据完整分开判断。查看具体缺项、来源、日期与覆盖；部分资料不足不会自动补成零。
        </p>
      </details>
      <details class="reading-details">
        <summary>计划与旧报告为什么不同？</summary>
        <p>
          个股计划读取当前保存版本和页头日期；报告读取当期冻结输入。先对齐日期与版本，再比较条件变化。
        </p>
      </details>
      <details class="reading-details">
        <summary>其他人如何登录使用？</summary>
        <p>
          本机模式打开已运行的工作台即可。PUBLIC模式使用管理员提供的账号或邀请码登录、注册；未开放注册时联系管理员。每个账号的自选、报告和任务分别保存。
        </p>
      </details>
      <details class="reading-details">
        <summary>是否会自动买卖？</summary>
        <p>
          本产品提供研究与条件计划，不连接券商下单。系统没有核实个人成本、风险承受度和仓位额度，不计算下单数量。
        </p>
      </details>
    </SectionCard>
  </div>
</template>
<style scoped>
.guide-purpose {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.guide-purpose article {
  padding: 16px;
  background: #f7f9fb;
  border-radius: 8px;
}
.guide-purpose strong {
  font-size: 14px;
}
p {
  font-size: 13px;
  line-height: 1.8;
  margin: 10px 0;
  color: #62778d;
}
.guide-steps {
  list-style: none;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 22px;
}
.guide-steps li {
  display: flex;
  gap: 14px;
  min-width: 0;
}
.guide-steps li > span {
  flex: 0 0 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #eaf2f4;
  color: #235970;
  font-size: 13px;
}
h3 {
  font-size: 14px;
  margin: 3px 0 8px;
}
.guide-groups button {
  padding: 9px 14px;
  background: #fff;
  border: 1px solid #dce5ed;
  border-radius: 7px;
  color: #536a80;
  cursor: pointer;
  font-size: 13px;
}
.guide-groups button[aria-pressed='true'] {
  background: #eaf2f4;
  border-color: #235970;
  color: #235970;
}
.guide-plan-reading {
  padding-left: 20px;
}
.guide-plan-reading li {
  margin-bottom: 18px;
  font-size: 13px;
}
@media (max-width: 760px) {
  .guide-purpose,
  .guide-steps {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
