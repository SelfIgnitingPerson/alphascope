import { mount } from '@vue/test-utils';
import { defineComponent, ref } from 'vue';
import { expect, it } from 'vitest';
import SectionTabs from './SectionTabs.vue';

it('supports keyboard navigation and retains state in hidden panels', async () => {
  const wrapper = mount(
    defineComponent({
      components: { SectionTabs },
      setup() {
        return {
          active: ref('query'),
          value: ref(''),
          items: [
            { id: 'query', label: '查询' },
            { id: 'tasks', label: '任务' },
          ],
        };
      },
      template:
        '<SectionTabs v-model="active" label="研究分类" :items="items"><template #query><input v-model="value" aria-label="查询条件" /></template><template #tasks>任务结果</template></SectionTabs>',
    }),
    { attachTo: document.body },
  );
  await wrapper.get('input').setValue('300476.SZ');
  const tabs = wrapper.findAll('[role="tab"]');
  await tabs[0].trigger('keydown', { key: 'ArrowRight' });
  expect(tabs[1].attributes('aria-selected')).toBe('true');
  expect(document.activeElement).toBe(tabs[1].element);
  expect(wrapper.findAll('[role="tabpanel"]')[0].isVisible()).toBe(false);
  expect(tabs[1].attributes('aria-controls')).toBe(
    wrapper.findAll('[role="tabpanel"]')[1].attributes('id'),
  );
  await tabs[1].trigger('keydown', { key: 'Home' });
  expect(wrapper.get('input').element.value).toBe('300476.SZ');
  expect(wrapper.findAll('[role="tabpanel"]')[0].isVisible()).toBe(true);
  await tabs[0].trigger('keydown', { key: 'ArrowLeft' });
  expect(tabs[1].attributes('aria-selected')).toBe('true');
  wrapper.unmount();
});
