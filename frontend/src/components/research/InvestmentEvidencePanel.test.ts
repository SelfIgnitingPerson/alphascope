import { beforeEach, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import InvestmentEvidencePanel from './InvestmentEvidencePanel.vue';
import {
  getValuationThesis,
  getCorporateActions,
  saveValuationThesis,
  saveCorporateAction,
} from '../../api/investment-evidence';
vi.mock('../../api/investment-evidence', () => ({
  getValuationThesis: vi.fn(),
  getCorporateActions: vi.fn(),
  saveValuationThesis: vi.fn(),
  saveCorporateAction: vi.fn(),
}));
vi.mock('../../composables/useAuth', () => ({
  useAuth: () => ({ user: { value: { role: 'USER' } } }),
}));
const stubs = {
  ElButton: { template: '<button @click="$emit(\'click\')"><slot/></button>' },
  ElAlert: { props: ['title'], template: '<p role="alert">{{ title }}</p>' },
  ElSkeleton: true,
  ElInput: true,
  ElInputNumber: true,
  ElDatePicker: true,
  ElCheckbox: true,
};
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getValuationThesis).mockResolvedValue(null);
  vi.mocked(getCorporateActions).mockResolvedValue([]);
});
it('explains missing inputs and never creates investment evidence just by opening or changing dates', async () => {
  const w = mount(InvestmentEvidencePanel, {
    props: { symbol: '600000.SH', date: '2026-11-17', refreshToken: 0 },
    global: { stubs },
  });
  await flushPromises();
  expect(w.text()).toContain('尚无估值情景');
  expect(w.text()).toContain('不能代替现金到账');
  expect(w.text()).not.toContain('根据公开公告登记公司行动');
  expect(saveValuationThesis).not.toHaveBeenCalled();
  expect(saveCorporateAction).not.toHaveBeenCalled();
  await w.setProps({ date: '2026-11-18' });
  await flushPromises();
  expect(getValuationThesis).toHaveBeenLastCalledWith(
    '600000.SH',
    '2026-11-18',
    expect.any(AbortSignal),
  );
  expect(saveValuationThesis).not.toHaveBeenCalled();
  w.unmount();
});
it('rejects an empty valuation form without inventing defaults or submitting it', async () => {
  const w = mount(InvestmentEvidencePanel, {
    props: { symbol: '600000.SH', date: '2026-11-17', refreshToken: 0 },
    global: { stubs },
  });
  await flushPromises();
  await w
    .findAll('button')
    .find((b) => b.text() === '保存我的长期情景')!
    .trigger('click');
  await flushPromises();
  expect(w.text()).toContain('请填写EPS');
  expect(saveValuationThesis).not.toHaveBeenCalled();
  w.unmount();
});
it('does not report an old stock save as a new stock valuation after navigation', async () => {
  const fixture = {
    symbol: '600000.SH',
    asOf: '2026-11-17',
    validUntil: '2026-12-17',
    epsLow: 1,
    epsHigh: 2,
    peLow: 10,
    peHigh: 20,
    safetyMargin: 0.2,
    businessConfirmed: true,
    rationale: '公开财报核对盈利、股本和行业倍数',
    invalidation: '盈利和现金流持续恶化则撤销假设',
    sourceUrl: 'https://example.test/report',
    financialSource: 'EASTMONEY_FINANCIAL',
  };
  vi.mocked(getValuationThesis).mockImplementation(async (symbol) =>
    symbol === fixture.symbol ? fixture : null,
  );
  let finish!: (value: null) => void;
  vi.mocked(saveValuationThesis).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  const w = mount(InvestmentEvidencePanel, {
    props: { symbol: fixture.symbol, date: fixture.asOf, refreshToken: 0 },
    global: { stubs },
  });
  await flushPromises();
  await w
    .findAll('button')
    .find((b) => b.text() === '载入已有假设')!
    .trigger('click');
  await w
    .findAll('button')
    .find((b) => b.text() === '保存我的长期情景')!
    .trigger('click');
  await w.setProps({ symbol: '600001.SH' });
  finish(null);
  await flushPromises();
  expect(saveValuationThesis).toHaveBeenCalledWith(
    expect.objectContaining({ symbol: '600000.SH' }),
  );
  expect(w.text()).not.toContain('长期估值情景已保存');
  expect(w.emitted('saved')).toBeUndefined();
  w.unmount();
});
