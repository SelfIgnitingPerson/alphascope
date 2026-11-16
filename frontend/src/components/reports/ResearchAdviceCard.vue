<script setup lang="ts">
import type { ResearchAdvice } from '../../types/models';
defineProps<{ advice: ResearchAdvice }>();
</script>
<template>
  <section class="report-followup">
    <h4>
      本期研究建议 ·
      {{
        advice.priority === 'REVIEW_FIRST'
          ? '优先核查'
          : advice.priority === 'EVIDENCE_FIRST'
            ? '先补证据'
            : '持续观察'
      }}
    </h4>
    <p>
      <strong>{{ advice.conclusion }}</strong>
    </p>
    <details>
      <summary>判断依据与待验证解释</summary>
      <h5>已观察到的事实</h5>
      <ul>
        <li
          v-for="item in advice.observations"
          :key="item"
        >
          {{ item }}
        </li>
      </ul>
      <template v-if="advice.hypotheses.length">
        <h5>可能解释，尚待验证</h5>
        <ul>
          <li
            v-for="item in advice.hypotheses"
            :key="item"
          >
            {{ item }}
          </li>
        </ul>
      </template>
    </details>
    <h5>下一步具体核查</h5>
    <ol>
      <li
        v-for="item in advice.verifyNext"
        :key="item"
      >
        {{ item }}
      </li>
    </ol>
    <template v-if="advice.reconsiderIf.length">
      <h5>哪些变化会调整判断</h5>
      <ul>
        <li
          v-for="item in advice.reconsiderIf"
          :key="item"
        >
          {{ item }}
        </li>
      </ul>
    </template>
    <p class="help-text">这是自选研究优先级与复核条件，不涉及个人仓位或自动下单。</p>
  </section>
</template>
