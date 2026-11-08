import { createRouter, createWebHistory, type RouteRecordRaw, type RouterHistory } from 'vue-router';
import { AREAS } from './navigation/areas';
import NotFoundPage from './navigation/NotFoundPage.vue';

// One route per existing area, plus the not-found view for every other path. The history is a parameter so that the
// tests can use a memory history. The browser history needs the host to serve index.html for deep links: the web
// image (nginx) and vite preview/dev do so.
export function createAppRouter(history: RouterHistory = createWebHistory()) {
  const routes: RouteRecordRaw[] = AREAS.flatMap((area) =>
    area.exists && area.component ? [{ path: area.path, component: area.component }] : [],
  );
  routes.push({ path: '/:pathMatch(.*)*', component: NotFoundPage });
  return createRouter({ history, routes });
}
