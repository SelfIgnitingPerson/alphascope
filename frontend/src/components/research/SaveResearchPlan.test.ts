import { expect, it, vi } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import SaveResearchPlan from './SaveResearchPlan.vue';
import { savePlan } from '../../api/plans';
vi.mock('../../api/plans', () => ({ savePlan: vi.fn() }));
it('only submits evidence identity after explicit user action, never client prices', async () => {
  vi.mocked(savePlan).mockResolvedValue({ id: 7 } as Awaited<ReturnType<typeof savePlan>>);
  const w = shallowMount(SaveResearchPlan, {
    props: {
      symbol: '600000.SH',
      date: '2026-11-16',
      horizon: 'SHORT',
      actionCode: 'OPEN_PULLBACK',
      actionTitle: '回踩企稳建仓',
    },
    global: {
      stubs: {
        ElButton: { template: '<button @click="$emit(\'click\')"><slot/></button>' },
        RouterLink: { template: '<a><slot/></a>' },
      },
    },
  });
  expect(w.text()).toContain('保存「回踩企稳建仓」观察方案');
  expect(savePlan).not.toHaveBeenCalled();
  await w.get('button').trigger('click');
  await flushPromises();
  expect(savePlan).toHaveBeenCalledWith({
    symbol: '600000.SH',
    date: '2026-11-16',
    horizon: 'SHORT',
    actionCode: 'OPEN_PULLBACK',
  });
  expect(w.text()).toContain('已冻结');
  w.unmount();
});
it('does not show a late save confirmation for a different stock', async () => {
  let finish!: (value: Awaited<ReturnType<typeof savePlan>>) => void;
  vi.mocked(savePlan).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  const w = shallowMount(SaveResearchPlan, {
    props: {
      symbol: '600000.SH',
      date: '2026-11-16',
      horizon: 'SHORT',
      actionCode: 'OPEN_PULLBACK',
    },
    global: {
      stubs: {
        ElButton: { template: '<button @click="$emit(\'click\')"><slot/></button>' },
        RouterLink: { template: '<a><slot/></a>' },
      },
    },
  });
  await w.get('button').trigger('click');
  await w.setProps({ symbol: '600001.SH' });
  finish({ id: 8 } as Awaited<ReturnType<typeof savePlan>>);
  await flushPromises();
  expect(w.text()).not.toContain('已冻结');
  w.unmount();
});
