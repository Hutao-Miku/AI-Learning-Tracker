<template>
  <el-container class="app-container">
    <el-header class="app-header">
      <span class="logo">🤖 AI 学习追踪网站</span>
      <nav class="nav">
        <router-link to="/">仪表盘</router-link>
        <router-link to="/courses">我的课程</router-link>
        <router-link to="/quiz">全部题目</router-link>
        <router-link to="/exam">定期考核</router-link>
      </nav>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
// 全局布局壳，页面内容由 router-view 渲染
</script>

<style>
body {
  margin: 0;
  font-family: 'Helvetica Neue', Helvetica, Arial, 'Microsoft YaHei', sans-serif;
}
.app-container {
  min-height: 100vh;
  background: #f5f7fa;
}
.app-header {
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  font-size: 18px;
  font-weight: 600;
  gap: 24px;
}
.nav {
  display: flex;
  gap: 18px;
  font-size: 14px;
  font-weight: 500;
}
.nav a {
  color: #eaf3ff;
  text-decoration: none;
  padding: 4px 6px;
  border-radius: 6px;
}
.nav a.router-link-active {
  color: #fff;
  background: rgba(255, 255, 255, 0.2);
}
</style>
