import { createApp } from 'vue';
import { createPinia } from 'pinia';
import Antd from 'ant-design-vue';
import App from './App.vue';
import router from './router';
import { setupPermissionDirective } from './directives/permission';
import { Can } from './components/permission';
import 'ant-design-vue/dist/reset.css';
import './styles/index.css';

const app = createApp(App);

app.use(createPinia());
app.use(router);
app.use(Antd);

// 注册权限指令和组件
setupPermissionDirective(app);
app.component('Can', Can);

app.mount('#app');
