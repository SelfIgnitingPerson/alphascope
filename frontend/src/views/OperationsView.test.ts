import { mount, flushPromises } from '@vue/test-utils';
import { describe, it, expect, vi } from 'vitest';
import OperationsView from './OperationsView.vue';
vi.mock('../api/operations', () => ({ getOperations: vi.fn(), requestBackup: vi.fn() }));
import { getOperations, requestBackup } from '../api/operations';
const state = {
  checkedAt: '2026-11-06T01:00:00Z',
  status: 'ATTENTION',
  expectedTradeDate: null,
  calendarStatus: 'UNKNOWN_CALENDAR',
  scheduledBackupEnabled: false,
  backupCron: '0 0 2 * * *',
  backup: { status: 'MISSING', fileName: null, verifiedAt: null, nextDue: null, sha256: null },
  latestAttempt: null,
  secondary: {
    status: 'NOT_CONFIGURED',
    observedAt: null,
    differentFileSystem: false,
    reason: '尚未配置第二存储',
  },
  instruments: [],
  issues: ['近期权威日历不足'],
};
describe('运维总览', () => {
  it('明确显示日历不足、缺少备份和未配置第二存储', async () => {
    vi.mocked(getOperations).mockResolvedValue(state);
    const wrapper = mount(OperationsView, {
      props: { date: '2026-11-06', refreshToken: 0 },
      global: {
        stubs: {
          ElTable: { template: '<div/>' },
          ElButton: { template: '<button><slot/></button>' },
        },
      },
    });
    await flushPromises();
    expect(wrapper.text()).toContain('尚不能确认');
    expect(wrapper.text()).toContain('未启用');
    expect(wrapper.text()).toContain('尚未配置第二存储');
    expect(wrapper.text()).toContain('近期权威日历不足');
    wrapper.unmount();
  });
  it('显式点击后才请求后台备份', async () => {
    vi.mocked(getOperations).mockResolvedValue(state);
    vi.mocked(requestBackup).mockResolvedValue('QUEUED');
    vi.mocked(requestBackup).mockClear();
    const wrapper = mount(OperationsView, {
      props: { date: '2026-11-06', refreshToken: 0 },
      global: {
        stubs: {
          ElTable: { template: '<div/>' },
          ElButton: { template: '<button><slot/></button>' },
        },
      },
    });
    await flushPromises();
    expect(requestBackup).not.toHaveBeenCalled();
    await wrapper.get('[data-testid="request-backup"]').trigger('click');
    await flushPromises();
    expect(requestBackup).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('备份请求已接收');
    wrapper.unmount();
  });
});
