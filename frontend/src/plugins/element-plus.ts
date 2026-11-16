import type { App } from 'vue';
import { defineAsyncComponent } from 'vue';
import ElAlert from 'element-plus/es/components/alert/index';
import ElButton from 'element-plus/es/components/button/index';
import ElInput from 'element-plus/es/components/input/index';
import ElSkeleton from 'element-plus/es/components/skeleton/index';
import 'element-plus/es/components/alert/style/css';
import 'element-plus/es/components/button/style/css';
import 'element-plus/es/components/checkbox/style/css';
import 'element-plus/es/components/date-picker/style/css';
import 'element-plus/es/components/drawer/style/css';
import 'element-plus/es/components/empty/style/css';
import 'element-plus/es/components/form/style/css';
import 'element-plus/es/components/form-item/style/css';
import 'element-plus/es/components/input/style/css';
import 'element-plus/es/components/input-number/style/css';
import 'element-plus/es/components/option/style/css';
import 'element-plus/es/components/pagination/style/css';
import 'element-plus/es/components/popconfirm/style/css';
import 'element-plus/es/components/radio-button/style/css';
import 'element-plus/es/components/radio-group/style/css';
import 'element-plus/es/components/select/style/css';
import 'element-plus/es/components/skeleton/style/css';
import 'element-plus/es/components/switch/style/css';
import 'element-plus/es/components/table/style/css';
import 'element-plus/es/components/table-column/style/css';

// 交互组件按需加载；首屏必需的按钮、输入和状态组件同步注册。
const ElCheckbox = defineAsyncComponent(() => import('element-plus/es/components/checkbox/index'));
const ElDatePicker = defineAsyncComponent(
  () => import('element-plus/es/components/date-picker/index'),
);
const ElDrawer = defineAsyncComponent(() => import('element-plus/es/components/drawer/index'));
const ElEmpty = defineAsyncComponent(() => import('element-plus/es/components/empty/index'));
const ElForm = defineAsyncComponent(() => import('element-plus/es/components/form/index'));
const ElFormItem = defineAsyncComponent(() =>
  import('element-plus/es/components/form/index').then((module) => module.ElFormItem),
);
const ElInputNumber = defineAsyncComponent(
  () => import('element-plus/es/components/input-number/index'),
);
const ElOption = defineAsyncComponent(() =>
  import('element-plus/es/components/select/index').then((module) => module.ElOption),
);
const ElPagination = defineAsyncComponent(
  () => import('element-plus/es/components/pagination/index'),
);
const ElPopconfirm = defineAsyncComponent(
  () => import('element-plus/es/components/popconfirm/index'),
);
const ElRadioButton = defineAsyncComponent(() =>
  import('element-plus/es/components/radio/index').then((module) => module.ElRadioButton),
);
const ElRadioGroup = defineAsyncComponent(() =>
  import('element-plus/es/components/radio/index').then((module) => module.ElRadioGroup),
);
const ElSelect = defineAsyncComponent(() => import('element-plus/es/components/select/index'));
const ElSwitch = defineAsyncComponent(() => import('element-plus/es/components/switch/index'));
const ElTable = defineAsyncComponent(() => import('element-plus/es/components/table/index'));
const ElTableColumn = defineAsyncComponent(() =>
  import('element-plus/es/components/table/index').then((module) => module.ElTableColumn),
);

/** 注册项目实际使用的 Element Plus 组件与对应样式。 */
export function registerUiComponents(app: App): void {
  for (const [name, component] of Object.entries({
    ElAlert,
    ElButton,
    ElCheckbox,
    ElDatePicker,
    ElDrawer,
    ElEmpty,
    ElForm,
    ElFormItem,
    ElInput,
    ElInputNumber,
    ElOption,
    ElPagination,
    ElPopconfirm,
    ElRadioButton,
    ElRadioGroup,
    ElSelect,
    ElSkeleton,
    ElSwitch,
    ElTable,
    ElTableColumn,
  })) {
    app.component(name, component);
  }
}
