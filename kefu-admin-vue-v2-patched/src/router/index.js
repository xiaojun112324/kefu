import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/store/auth'

import Login from '@/views/Login.vue'
import Layout from '@/views/Layout.vue'
import Dashboard from '@/views/Dashboard.vue'
import Customers from '@/views/Customers.vue'
import Conversations from '@/views/Conversations.vue'
import Users from '@/views/Users.vue'
import Agents from '@/views/Agents.vue'
import OpLogs from '@/views/OpLogs.vue'
import PlatformSettings from '@/views/PlatformSettings.vue'
import Subscribe from '@/views/Subscribe.vue'
import ActivationCodes from '@/views/ActivationCodes.vue'

const routes = [
  { path: '/login', component: Login, meta: { public: true } },
  {
    path: '/',
    component: Layout,
    children: [
      { path: '', component: Dashboard },
      { path: 'customers', component: Customers },
      { path: 'conversations', component: Conversations },
      { path: 'users', component: Users },
      { path: 'agents', component: Agents },
      { path: 'op-logs', component: OpLogs },
      { path: 'platform', component: PlatformSettings },
      { path: 'subscribe', component: Subscribe },
      { path: 'activationCodes', component: ActivationCodes },
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuth()
  if (to.path === '/login' && auth.token) {
    return '/'
  }
  if (!to.meta.public && !auth.token) {
    return '/login'
  }
})


export default router
