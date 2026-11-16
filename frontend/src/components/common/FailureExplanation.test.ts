import { mount } from '@vue/test-utils';
import { expect, it } from 'vitest';
import FailureExplanation from './FailureExplanation.vue';
it('shows cause, impact, action and the task location without relying on a colour', () => {
  const wrapper = mount(FailureExplanation, {
    props: {
      context: '任务#56 / 行情采集',
      diagnostic: {
        code: 'NETWORK_TIMEOUT',
        title: '请求超时',
        reason: '读取未完成',
        impact: '不能生成完整结论',
        action: '稍后重试',
      },
    },
  });
  expect(wrapper.get('details').attributes('open')).toBeDefined();
  expect(wrapper.findAll('dt').map((node) => node.text())).toEqual(['原因', '影响', '建议操作']);
  expect(wrapper.text()).toContain('任务#56');
  expect(wrapper.text()).toContain('NETWORK_TIMEOUT');
  wrapper.unmount();
});
it('keeps unknown legacy records empty and allows compact explanations to expand', async () => {
  const wrapper = mount(FailureExplanation, { props: { compact: true } });
  expect(wrapper.find('details').exists()).toBe(false);
  await wrapper.setProps({
    diagnostic: {
      code: 'UNKNOWN',
      title: '原因尚未分类',
      reason: '需核对',
      impact: '未完成',
      action: '保留编号',
    },
  });
  expect(wrapper.get('details').attributes('open')).toBeUndefined();
  wrapper.unmount();
});

it('shows supplier code separately from its local meaning and missing original message', () => {
  const wrapper = mount(FailureExplanation, {
    props: {
      diagnostic: {
        code: 'BAOSTOCK_API_ERROR_10001011',
        title: '黑名单用户',
        reason: '上游拒绝',
        impact: '未完成',
        action: '核对官网',
        providerError: {
          provider: 'BAOSTOCK',
          code: '10001011',
          message: null,
          meaning: '黑名单用户',
          messageState: 'NOT_RECORDED',
        },
      },
    },
  });
  expect(wrapper.get('[data-testid="provider-error"]').text()).toContain('错误码 10001011');
  expect(wrapper.text()).toContain('当时未保存供应商原文');
  expect(wrapper.text()).not.toContain('供应商原文：黑名单用户');
  wrapper.unmount();
});
it('distinguishes a safely captured upstream message from a withheld one', async () => {
  const diagnostic = {
    code: 'BAOSTOCK_API_ERROR_10001006',
    title: '权限不足',
    reason: '明确拒绝',
    impact: '未完成',
    action: '核验权限',
    providerError: {
      provider: 'BAOSTOCK',
      code: '10001006',
      message: '用户权限不足',
      meaning: '用户权限不足',
      messageState: 'UPSTREAM_SAFE_TEXT' as const,
    },
  };
  const wrapper = mount(FailureExplanation, { props: { diagnostic } });
  expect(wrapper.text()).toContain('供应商原文：用户权限不足');
  await wrapper.setProps({
    diagnostic: {
      ...diagnostic,
      providerError: { ...diagnostic.providerError, message: null, messageState: 'WITHHELD' },
    },
  });
  expect(wrapper.text()).toContain('未公开原文');
  expect(wrapper.text()).not.toContain('供应商原文：用户权限不足');
  wrapper.unmount();
});
