import { createApp } from 'vue';
import App from './App.vue';
import { router } from './router';
import { registerUiComponents } from './plugins/element-plus';
import './styles/app.css';

// 应用入口只负责装配路由、公共组件和全局样式。
const app = createApp(App).use(router);
registerUiComponents(app);
app.mount('#app');
