import { describe, expect, it } from 'vitest';
import { findTerm, researchTerms, ruleTermIds, searchTerms } from './research-terms';
import { getStatusHelp } from './status-help';
import { ruleNames } from '../utils/format';

describe('状态与术语内容边界', () => {
  it('covers the actual rule catalog and resolves aliases without duplicate term identifiers', () => {
    expect(new Set(researchTerms.map((item) => item.id)).size).toBe(researchTerms.length);
    for (const code of Object.keys(ruleNames)) expect(findTerm(ruleTermIds[code])).toBeDefined();
    expect(searchTerms('mA20').some((item) => item.id === 'ma')).toBe(true);
    expect(
      searchTerms('成交额', '价格与量能').every((item) => item.category === '价格与量能'),
    ).toBe(true);
    expect(findTerm('does-not-exist')).toBeUndefined();
  });

  it('separates channel availability, sufficient nontriggered data and missing evidence', () => {
    expect(getStatusHelp('AVAILABLE').label).toBe('可用');
    expect(getStatusHelp('AVAILABLE', 'channel').label).toBe('已连通');
    expect(getStatusHelp('UNAVAILABLE', 'holding').summary).toContain('持有周期');
    expect(getStatusHelp('UNAVAILABLE', 'channel').summary).toContain('通道能力');
    expect(getStatusHelp('UNAVAILABLE', 'quality').summary).toContain('数据核验');
    expect(getStatusHelp('NOT_TRIGGERED').summary).toContain('必要数据足够');
    expect(getStatusHelp('INSUFFICIENT_DATA').detail).toContain('不能理解为未触发');
    expect(getStatusHelp('NOT_APPLICABLE').detail).toContain('不评估个人持仓集中度');
  });

  it('treats arbitrary unknown codes as unknown rather than inheriting object properties', () => {
    for (const code of ['__proto__', 'toString', '<script>']) {
      const state = getStatusHelp(code);
      expect(state.label).toBe(code);
      expect(state.tone).toBe('muted');
      expect(state.summary).toContain('暂未收录');
    }
  });
});
