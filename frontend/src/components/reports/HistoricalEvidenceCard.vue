<script setup lang="ts">
import type { MarketAcquisition } from '../../types/models';
import { sourceName, timestamp } from '../../utils/format';
defineProps<{ acquisition: MarketAcquisition[] }>();
</script>
<template>
  <section class="historical-evidence">
    <h3>本次使用已保存真实历史</h3>
    <p>
      历史补跑采用已保存的行情版本，原始采集时间与批次保持不变。日期覆盖、同源复权因子和行情数值已核对，供应商最新修订尚未联网复核。
    </p>
    <details>
      <summary>查看来源、有效区间与原采集时间</summary>
      <div class="evidence-table">
        <table>
          <thead>
            <tr>
              <th>标的 / 来源</th>
              <th>获取方式</th>
              <th>核对区间</th>
              <th>记录数</th>
              <th>原采集时间</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in acquisition"
              :key="item.symbol"
            >
              <td>{{ item.symbol }} · {{ sourceName(item.source) }}</td>
              <td>
                {{
                  item.mode === 'STORED_HISTORY'
                    ? '已存历史版本'
                    : item.mode === 'FRESH_CACHE'
                      ? '有效期内缓存'
                      : '本次采集'
                }}
              </td>
              <td>{{ item.validatedFrom }} 至 {{ item.validatedThrough }}</td>
              <td>{{ item.rows }}</td>
              <td>
                {{ timestamp(item.oldestFetchedAt) }} 至 {{ timestamp(item.newestFetchedAt) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </details>
  </section>
</template>
<style scoped>
.historical-evidence {
  min-width: 0;
  padding: 20px;
  border: 1px solid #dbe4ec;
  border-radius: 12px;
  background: #f8fafc;
}
.historical-evidence h3 {
  margin: 0 0 12px;
  font-size: 16px;
}
.historical-evidence p,
details {
  font-size: 13px;
  line-height: 1.9;
}
summary {
  cursor: pointer;
}
.evidence-table {
  overflow-x: auto;
  margin-top: 12px;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  padding: 10px;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
  white-space: nowrap;
}
th {
  color: #64748b;
}
</style>
