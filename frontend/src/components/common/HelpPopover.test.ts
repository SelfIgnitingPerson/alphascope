import { mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import { nextTick } from 'vue';
import HelpPopover from './HelpPopover.vue';
import StateBadge from './StateBadge.vue';
import TermHelp from './TermHelp.vue';

const wrappers: VueWrapper[] = [];
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => {
    if (wrapper.exists()) wrapper.unmount();
  });
});
function render() {
  const wrapper = mount(HelpPopover, {
    attachTo: document.body,
    props: { label: '规则状态', title: '说明', summary: '数据不足不是未触发。' },
  });
  wrappers.push(wrapper);
  return wrapper;
}
const dialog = () => document.querySelector('[data-testid="research-help-popover"]');

describe('就地帮助的键盘、触屏和生命周期', () => {
  it('teleports outside clipped tables and restores focus after Escape', async () => {
    const wrapper = render();
    const button = wrapper.get('button');
    await button.trigger('click');
    await nextTick();
    expect(dialog()?.textContent).toContain('数据不足不是未触发');
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(button.attributes('aria-expanded')).toBe('true');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await nextTick();
    expect(dialog()).toBeNull();
    expect(document.activeElement).toBe(button.element);
  });

  it('keeps panel scrolling readable but dismisses outside clicks and removes listeners on unmount', async () => {
    const wrapper = render();
    await wrapper.get('button').trigger('click');
    await nextTick();
    dialog()?.dispatchEvent(new Event('scroll'));
    await nextTick();
    expect(dialog()).not.toBeNull();
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    await nextTick();
    expect(dialog()).toBeNull();
    await wrapper.get('button').trigger('click');
    await nextTick();
    wrapper.unmount();
    expect(dialog()).toBeNull();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
  });

  it('explains severity separately from trigger status and leaves unknown terms as plain text', async () => {
    const badge = mount(StateBadge, {
      attachTo: document.body,
      props: { value: 'HIGH' },
      global: { stubs: { RouterLink: true } },
    });
    wrappers.push(badge);
    await badge.get('button').trigger('click');
    await nextTick();
    expect(dialog()?.textContent).toContain('不表示本次已经触发');
    expect(dialog()?.textContent).toContain('未触发｜高');
    expect(dialog()?.querySelector('router-link-stub')?.getAttribute('to')).toBe('/terms');
    badge.unmount();
    const unknown = mount(TermHelp, { props: { term: 'unregistered', label: '未收录词' } });
    wrappers.push(unknown);
    expect(unknown.text()).toBe('未收录词');
    expect(unknown.find('button').exists()).toBe(false);
  });
});
