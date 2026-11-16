import { defineComponent } from 'vue';
import { expect, it, vi } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import WatchlistView from './WatchlistView.vue';
import { discoverInstruments, addDiscoveredWatch } from '../api/portfolio';
import type { InstrumentDiscovery } from '../api/portfolio';
vi.mock('../api/portfolio', () => ({
  getWatchlist: vi.fn().mockResolvedValue([]),
  removeWatch: vi.fn(),
  discoverInstruments: vi.fn(),
  addDiscoveredWatch: vi.fn(),
}));
const Input = defineComponent({
  name: 'ElInput',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<input />',
});
const Select = defineComponent({
  name: 'ElSelect',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div><slot/></div>',
});
it('invalidates old candidates and selections when the search keyword changes, including late responses', async () => {
  const wrapper = shallowMount(WatchlistView, {
    props: { date: '2026-11-12', refreshToken: 0 },
    global: {
      stubs: {
        SectionCard: { template: '<section><slot/><slot name="actions"/></section>' },
        'el-input': Input,
        'el-select': Select,
        AsyncState: true,
        'el-button': true,
        'el-option': true,
        'el-checkbox': true,
        'el-alert': true,
        'el-table': true,
        'el-table-column': true,
        'el-popconfirm': true,
      },
    },
  });
  const input = wrapper.findComponent(Input);
  input.vm.$emit('update:modelValue', '胜宏');
  vi.mocked(discoverInstruments).mockResolvedValueOnce({
    items: [
      { symbol: '300476.SZ', name: '胜宏科技', exchange: 'SZ', registered: true, source: 'SINA' },
    ],
    message: '已找到',
    remoteStatus: 'AVAILABLE',
  });
  await wrapper.get('form.filter-bar--search').trigger('submit');
  await flushPromises();
  wrapper.findComponent(Select).vm.$emit('update:modelValue', '300476.SZ');
  await flushPromises();
  expect(wrapper.find('[data-testid="watch-add"]').attributes('disabled')).toBe('false');
  input.vm.$emit('update:modelValue', '英维克');
  await flushPromises();
  expect(wrapper.findComponent(Select).props('modelValue')).toBe('');
  expect(wrapper.find('[data-testid="watch-add"]').attributes('disabled')).toBe('true');
  expect(wrapper.text()).not.toContain('已找到');
  expect(wrapper.findAll('el-option-stub')).toHaveLength(0);
  let resolve!: (data: InstrumentDiscovery) => void;
  vi.mocked(discoverInstruments).mockImplementationOnce(
    () =>
      new Promise((r) => {
        resolve = r;
      }),
  );
  await wrapper.get('form.filter-bar--search').trigger('submit');
  const signal = vi.mocked(discoverInstruments).mock.calls.at(-1)![1]!;
  input.vm.$emit('update:modelValue', '其他');
  await flushPromises();
  expect(signal.aborted).toBe(true);
  resolve({
    items: [
      { symbol: '002837.SZ', name: '英维克', exchange: 'SZ', registered: true, source: 'SINA' },
    ],
    message: '迟到结果',
    remoteStatus: 'AVAILABLE',
  });
  await flushPromises();
  expect(wrapper.text()).not.toContain('迟到结果');
  await wrapper.get('form.filter-bar--watch-add').trigger('submit');
  await flushPromises();
  expect(addDiscoveredWatch).not.toHaveBeenCalled();
  expect(wrapper.findAll('el-option-stub')).toHaveLength(0);
  wrapper.unmount();
});
