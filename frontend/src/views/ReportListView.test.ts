import { it, expect, vi } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import { defineComponent } from 'vue';
import ReportListView from './ReportListView.vue';
import { getReports } from '../api/reports';
vi.mock('../api/reports', () => ({
  getReports: vi.fn().mockResolvedValue({ items: [], total: 45, page: 1, size: 20 }),
}));
const Pagination = defineComponent({
  name: 'PaginationStub',
  props: ['currentPage', 'total', 'pageSize'],
  emits: ['update:currentPage'],
  template: '<div />',
});
it('loads older pages and resets pagination when the business date changes', async () => {
  vi.mocked(getReports).mockResolvedValue({ items: [], total: 45, page: 1, size: 20 });
  const wrapper = shallowMount(ReportListView, {
    props: { date: '2026-10-30', refreshToken: 0 },
    global: {
      stubs: {
        SectionCard: { template: '<section><slot name="actions"/><slot/></section>' },
        AsyncState: { template: '<div><slot/></div>' },
        'el-pagination': Pagination,
      },
    },
  });
  await flushPromises();
  expect(wrapper.findComponent(Pagination).props('total')).toBe(45);
  wrapper.findComponent(Pagination).vm.$emit('update:currentPage', 2);
  await flushPromises();
  expect(vi.mocked(getReports).mock.calls.at(-1)?.[0].page).toBe(2);
  await wrapper.setProps({ date: '2026-10-29' });
  await flushPromises();
  expect(vi.mocked(getReports).mock.calls.at(-1)?.[0].page).toBe(1);
  wrapper.unmount();
});
