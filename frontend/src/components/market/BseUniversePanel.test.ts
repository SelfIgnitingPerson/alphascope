import { expect, it, vi } from 'vitest';
import { flushPromises, shallowMount } from '@vue/test-utils';
import { ref } from 'vue';
import BseUniversePanel from './BseUniversePanel.vue';
import { getMarketUniverse } from '../../api/market';
import { fillMarketUniverse } from '../../api/jobs';
vi.mock('../../api/market', () => ({ getMarketUniverse: vi.fn() }));
vi.mock('../../api/jobs', () => ({ fillMarketUniverse: vi.fn() }));
vi.mock('../../utils/date', () => ({ today: () => '2026-11-17' }));
vi.mock('../../composables/useAuth', () => ({ useAuth: () => ({ user: ref({ role: 'ADMIN' }) }) }));
const stubs = {
  SectionCard: { template: '<section><slot/></section>' },
  AsyncState: false,
  ElButton: { template: '<button @click="$emit(\'click\')"><slot/></button>' },
  RouterLink: { template: '<a><slot/></a>' },
};
it('keeps historical gaps explicit and only collects today after an explicit action', async () => {
  vi.mocked(getMarketUniverse).mockImplementation(async (date) => ({
    businessDate: date,
    status: 'NOT_ARCHIVED',
    source: 'SINA',
    memberCount: null,
    snapshotId: null,
    contentHash: null,
    observedAt: null,
    sourceUrl: 'https://example.test',
    limitations: ['今天名单不能倒填历史日期'],
    members: [],
  }));
  vi.mocked(fillMarketUniverse).mockResolvedValue({ id: 90 } as Awaited<
    ReturnType<typeof fillMarketUniverse>
  >);
  const wrapper = shallowMount(BseUniversePanel, {
    props: { date: '2026-11-16', refreshToken: 0 },
    global: { stubs },
  });
  await flushPromises();
  expect(wrapper.text()).toContain('2026-11-16 没有目录归档');
  expect(fillMarketUniverse).not.toHaveBeenCalled();
  await wrapper
    .findAll('button')
    .find((button) => button.text().includes('采集当前'))!
    .trigger('click');
  await flushPromises();
  expect(fillMarketUniverse).toHaveBeenCalledWith('2026-11-17');
  expect(wrapper.text()).toContain('#90');
  expect(wrapper.text()).toContain('2026-11-17 没有目录归档');
  wrapper.unmount();
});

it('shows the exact supplier count and preserves it as a directory rather than price coverage', async () => {
  vi.mocked(getMarketUniverse).mockResolvedValue({
    businessDate: '2026-11-17',
    status: 'AVAILABLE',
    source: 'SINA',
    memberCount: 2,
    snapshotId: 3,
    contentHash: 'a'.repeat(64),
    observedAt: '2026-11-17T14:00:00',
    sourceUrl: 'https://example.test',
    limitations: ['日线、停牌与历史窗口尚需另行核验'],
    members: [
      { symbol: '920001.BJ', name: '测试一' },
      { symbol: '920002.BJ', name: '测试二' },
    ],
  });
  const wrapper = shallowMount(BseUniversePanel, {
    props: { date: '2026-11-17', refreshToken: 0 },
    global: { stubs },
  });
  await flushPromises();
  expect(wrapper.text()).toContain('2 只');
  expect(wrapper.text()).toContain('新浪在市目录');
  expect(wrapper.text()).toContain('历史窗口尚需另行核验');
  expect(wrapper.text()).toContain('归档 #3');
  wrapper.unmount();
});
