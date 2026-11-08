import { createApp } from 'vue';
import '@material/web/divider/divider.js';
import './theme/tokens.css';
import './style.css';
import App from './App.vue';
import { createAppRouter } from './router';

createApp(App).use(createAppRouter()).mount('#app');
