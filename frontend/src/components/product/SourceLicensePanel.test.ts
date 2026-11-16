import { expect, it } from 'vitest';
import { shallowMount } from '@vue/test-utils';
import SourceLicensePanel from './SourceLicensePanel.vue';
import type { SourceLicense } from '../../api/product';

const license = (status: string, changes: Partial<SourceLicense> = {}): SourceLicense => ({
  source: 'EASTMONEY_FINANCIAL',
  status,
  permissions: [],
  validUntil: null,
  evidenceUrl: null,
  reference: null,
  ...changes,
});
function create(enforced?: boolean, licenses = [license('MISSING')]) {
  return shallowMount(SourceLicensePanel, {
    props: { enforced, licenses },
    global: {
      stubs: {
        SectionCard: { template: '<section><slot /></section>' },
        RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
      },
    },
    slots: { default: '<form data-testid="registration-form"></form>' },
  });
}

it('distinguishes an unregistered source from quote failure and keeps registration collapsed', () => {
  const wrapper = create(false);
  expect(wrapper.get('[data-testid="license-guidance"]').text()).toContain('不强制填写');
  expect(wrapper.text()).toContain('未登记不会因此阻止采集');
  expect(wrapper.text()).toContain('不表示接口被封禁');
  expect(wrapper.get('.license-card h4').text()).toBe('东方财富 · 财报');
  expect(wrapper.get('.license-card .state-badge').text()).toBe('未登记');
  expect(wrapper.text()).not.toContain('MISSING');
  expect(wrapper.get('a[href="/channels"]').text()).toContain('查看原因与测试');
  expect(wrapper.get<HTMLDetailsElement>('[data-testid="license-editor"]').element.open).toBe(
    false,
  );
  expect(wrapper.find('[data-testid="registration-form"]').exists()).toBe(true);
  wrapper.unmount();
});

it('uses the actual server gate including explicit local enforcement and unknown configuration', async () => {
  const wrapper = create(true);
  expect(wrapper.get('[data-testid="license-guidance"]').text()).toContain('需要核对并登记');
  expect(wrapper.text()).toContain('会被拦截');
  await wrapper.setProps({ enforced: undefined });
  expect(wrapper.get('[data-testid="license-guidance"]').text()).toContain('尚未确认');
  expect(wrapper.text()).not.toContain('未登记不会因此阻止');
  wrapper.unmount();
});

it('explains expiry, future validity, declared scope and unknown states without claiming verified authorization', () => {
  const wrapper = create(true, [
    license('EXPIRED', { source: 'BAOSTOCK', validUntil: '2026-01-01' }),
    license('NOT_YET_VALID', { source: 'SINA' }),
    license('DECLARED_BY_OPERATOR', {
      source: 'EASTMONEY_FORECAST',
      permissions: ['DISPLAY', 'DERIVED_REPORT'],
      evidenceUrl: 'https://example.test/license',
    }),
    license('NEW_UNKNOWN_STATUS', { source: 'EXCHANGE_CALENDAR' }),
  ]);
  expect(wrapper.text()).toContain('登记已过期');
  expect(wrapper.text()).toContain('尚未生效');
  expect(wrapper.text()).toContain('已登记 · 待自行核验');
  expect(wrapper.text()).toContain('对外展示、生成研究报告');
  expect(wrapper.text()).toContain('系统不代表供应商确认授权');
  expect(wrapper.text()).toContain('登记状态待核对');
  expect(wrapper.get('a[href="https://example.test/license"]').attributes('rel')).toContain(
    'noopener',
  );
  wrapper.unmount();
});

it('does not activate unsafe evidence links and handles an empty source list', async () => {
  const wrapper = create(false, [license('MISSING', { evidenceUrl: 'javascript:alert(1)' })]);
  expect(wrapper.find('a[href^="javascript:"]').exists()).toBe(false);
  expect(wrapper.text()).toContain('依据链接不可用');
  await wrapper.setProps({ licenses: [] });
  expect(wrapper.text()).toContain('当前没有可列示的来源');
  wrapper.unmount();
});
