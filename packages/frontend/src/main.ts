import { VueQueryPlugin } from '@tanstack/vue-query';
import { createPinia } from 'pinia';
import { createApp } from 'vue';
import App from './App.vue';
import './index.css';

createApp(App)
  .use(createPinia())
  .use(VueQueryPlugin, {
    queryClientConfig: {
      defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
    },
  })
  .mount('#root');
