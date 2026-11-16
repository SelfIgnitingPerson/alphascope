<script setup lang="ts">
import { computed } from 'vue';
import type { PriceVolumeStudy } from '../../types/price-volume';
import { number } from '../../utils/format';
import TermHelp from '../common/TermHelp.vue';
const props = defineProps<{ studies: PriceVolumeStudy[] }>();
const available = computed(() => props.studies.filter((item) => item.status === 'AVAILABLE'));
</script>
<template>
  <section
    v-if="available.length"
    class="report-stock-fact price-reference-summary"
  >
    <h3>关键价位速览</h3>
    <p class="help-text">按本修订收盘冻结；红色用于强调价位，是否触发以各项策略条件为准。</p>
    <div class="price-reference-summary__scroll">
      <table>
        <thead>
          <tr>
            <th scope="col">股票</th>
            <th scope="col">
              <TermHelp
                term="resistance"
                label="最近压力区间"
              />
            </th>
            <th scope="col">
              <TermHelp
                term="stop-reference"
                label="结构止损参考"
              />
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="study in available"
            :key="study.symbol"
          >
            <th scope="row">
              {{ study.name }}
              <small>{{ study.symbol }}</small>
            </th>
            <td>
              <strong
                v-if="study.resistances.length"
                class="reference-price"
              >
                {{ number(study.resistances[0].lower) }}—{{ number(study.resistances[0].upper) }} 元
              </strong>
              <span v-else>上方无本窗口压力样本</span>
            </td>
            <td>
              <strong
                v-if="study.stopReference != null"
                class="reference-price"
              >
                {{ number(study.stopReference) }} 元
              </strong>
              <span v-else>暂无有效参考</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
<style scoped>
.price-reference-summary__scroll {
  overflow-x: auto;
}
table {
  width: 100%;
  min-width: 500px;
  border-collapse: collapse;
  text-align: left;
}
th,
td {
  padding: 14px 12px;
  border-bottom: 1px solid #e2e8f0;
  font-size: 14px;
}
thead th {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}
small {
  display: block;
  color: #64748b;
  font-size: 11px;
  font-weight: normal;
  margin-top: 4px;
}
.reference-price {
  white-space: nowrap;
}
</style>
