import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import CourseList from '../views/CourseList.vue'
import VideoList from '../views/VideoList.vue'
import QuizView from '../views/QuizView.vue'
import QuizAllView from '../views/QuizAllView.vue'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: Dashboard
  },
  {
    path: '/courses',
    name: 'CourseList',
    component: CourseList
  },
  {
    path: '/course/:id/videos',
    name: 'VideoList',
    component: VideoList,
    props: true
  },
  {
    path: '/video/:videoRecordId/quiz',
    name: 'QuizView',
    component: QuizView,
    props: true
  },
  {
    path: '/quiz',
    name: 'QuizAllView',
    component: QuizAllView
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
