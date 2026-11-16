import { mount, flushPromises } from '@vue/test-utils';
import { it, expect, vi } from 'vitest';
import ValidationExperimentPanel from './ValidationExperimentPanel.vue';
vi.mock('../../api/experiments', () => ({
  getExperiments: vi.fn(),
  saveExperiment: vi.fn(),
  getExperiment: vi.fn(),
  reproduceExperiment: vi.fn(),
}));
import { getExperiments, reproduceExperiment, saveExperiment } from '../../api/experiments';
it('演示模式禁止保存，已冻结实验仍可只用保存输入复算', async () => {
  vi.mocked(getExperiments).mockResolvedValue({
    items: [
      {
        id: 7,
        symbol: '300476.SZ',
        dataScope: 'SAVED',
        availabilityMode: 'RETROSPECTIVE',
        methodVersion: 'SAVED_SIGNAL_VALIDATION_V3',
        inputHash: 'a',
        resultHash: 'b',
        createdAt: '2026-11-06T09:00:00',
        result: null,
      },
    ],
    total: 1,
    page: 1,
    size: 20,
  });
  vi.mocked(reproduceExperiment).mockResolvedValue({
    experimentId: 7,
    inputVerified: true,
    resultMatches: true,
    expectedHash: 'b',
    actualHash: 'b',
    result: { dataStatus: 'INSUFFICIENT_DATA' },
  });
  const wrapper = mount(ValidationExperimentPanel, {
    props: {
      request: { symbol: '300476.SZ', start: '2026-10-01', end: '2026-10-30' },
      synthetic: true,
    },
    global: {
      stubs: {
        ElTable: { props: ['data'], template: '<div><slot /></div>' },
        ElTableColumn: { template: '<div><slot :row="{id:7}" /></div>' },
      },
    },
  });
  await flushPromises();
  expect(wrapper.get('[data-testid="save-experiment"]').attributes('disabled')).toBeDefined();
  expect(saveExperiment).not.toHaveBeenCalled();
  await wrapper.get('[data-testid="reproduce-experiment-7"]').trigger('click');
  await flushPromises();
  expect(reproduceExperiment).toHaveBeenCalledWith(7);
  expect(wrapper.text()).toContain('复算结果一致');
  wrapper.unmount();
});
