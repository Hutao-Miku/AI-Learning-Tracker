<template>
  <div class="dashboard-page">
    <el-card class="dash-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">📊 每日学习仪表盘</span>
          <el-button text :icon="Refresh" @click="loadDashboard" :loading="loading">刷新</el-button>
        </div>
      </template>

      <!-- 今日学习情况 -->
      <el-row :gutter="16" class="stat-row">
        <el-col :span="8">
          <el-card shadow="hover" class="stat-box">
            <el-statistic title="今日答题数" :value="today.answerCount" />
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="stat-box">
            <el-statistic
              title="今日正确率"
              :value="today.accuracy"
              suffix="%"
              :value-style="{ color: today.accuracy >= 60 ? '#67c23a' : '#e6a23c' }"
            />
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="stat-box">
            <el-statistic title="今日学习时长" :value="today.studyMinutes" suffix="分钟" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 整体掌握度 -->
      <div class="mastery-block">
        <div class="mastery-label">
          整体掌握度
          <span class="mastery-num">{{ overallMastery }}%</span>
        </div>
        <el-progress
          :percentage="overallMastery"
          :stroke-width="18"
          :color="masteryColor"
          :show-text="false"
        />
      </div>
    </el-card>

    <!-- 薄弱知识点 -->
    <el-card class="dash-card" shadow="never">
      <template #header>
        <span class="title">🎯 掌握度最差的 5 个知识点（点「去练真题」做针对性练习）</span>
      </template>

      <el-empty v-if="!weakPoints.length && !loading" description="暂无作答记录，去答几道题就会有数据啦" />
      <el-skeleton v-else-if="loading" :rows="5" animated />

      <div v-else class="weak-list">
        <div v-for="wp in weakPoints" :key="wp.knowledgePoint" class="weak-item">
          <div class="weak-main">
            <div class="weak-kp">{{ wp.knowledgePoint }}</div>
            <el-tag size="small" type="info" effect="plain">{{ wp.tag }}</el-tag>
          </div>
          <div class="weak-bar">
            <el-progress
              :percentage="wp.mastery"
              :stroke-width="12"
              :color="masteryColor(wp.mastery)"
              :show-text="false"
            />
            <span class="weak-pct">{{ wp.mastery }}%</span>
          </div>
          <div class="weak-meta">
            <span>已作答 {{ wp.answeredCount }} 次</span>
            <span v-if="wp.nextReview">下次复习：{{ wp.nextReview }}</span>
          </div>
          <el-button type="warning" size="small" @click="openRecommend(wp.knowledgePoint)">
            去练真题
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 真题推荐弹窗 -->
    <el-dialog v-model="dialogVisible" title="真实竞赛真题推荐" width="620px" @closed="resetRec">
      <div v-if="rec" class="rec-dialog">
        <el-alert
          :type="rec.source === 'codeforces' ? 'success' : 'warning'"
          :closable="false"
          show-icon
          :title="rec.source === 'codeforces'
            ? '已从 Codeforces 正赛题库匹配到真实真题（非 AI 生成）'
            : 'Codeforces 暂无匹配，已为你准备好知名题库搜索入口，点击即可去刷'"
        />

        <!-- 真实真题列表 -->
        <div v-if="rec.problems && rec.problems.length" class="rec-section">
          <div class="rec-section-title">Codeforces 真题</div>
          <div v-for="(p, i) in rec.problems" :key="i" class="rec-problem">
            <el-link type="primary" :href="p.url" target="_blank" rel="noopener">
              {{ p.name }}
            </el-link>
            <span class="rec-rating">rating {{ p.rating }}</span>
            <div class="rec-tags">
              <el-tag v-for="t in p.tags" :key="t" size="small" effect="plain">{{ t }}</el-tag>
            </div>
          </div>
        </div>

        <!-- 兜底题库链接（始终展示，方便自行拓展） -->
        <div class="rec-section">
          <div class="rec-section-title">其它知名题库（自行刷题）</div>
          <div v-for="(f, i) in rec.fallbackLinks" :key="i" class="rec-fallback">
            <el-link type="info" :href="f.url" target="_blank" rel="noopener">{{ f.name }}</el-link>
          </div>
        </div>
      </div>
      <el-skeleton v-else :rows="4" animated />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const overallMastery = ref(0)
const today = reactive({ answerCount: 0, correctCount: 0, accuracy: 0, studyMinutes: 0 })
const weakPoints = ref([])

const dialogVisible = ref(false)
const rec = ref(null)
const recLoading = ref(false)

function masteryColor(pct) {
  if (pct >= 70) return '#67c23a'
  if (pct >= 40) return '#e6a23c'
  return '#f56c6c'
}

async function loadDashboard() {
  loading.value = true
  try {
    const res = await fetch('/api/mastery/dashboard')
    const json = await res.json()
    if (json.code === 200 && json.data) {
      const d = json.data
      overallMastery.value = d.overallMastery || 0
      Object.assign(today, d.today || {})
      weakPoints.value = d.weakPoints || []
    } else {
      ElMessage.error(json.message || '加载仪表盘失败')
    }
  } catch (e) {
    ElMessage.error('请求失败：' + e.message + '（请确认后端已启动）')
  } finally {
    loading.value = false
  }
}

async function openRecommend(knowledgePoint) {
  dialogVisible.value = true
  rec.value = null
  recLoading.value = true
  try {
    const res = await fetch('/api/mastery/recommend?knowledgePoint=' + encodeURIComponent(knowledgePoint))
    const json = await res.json()
    if (json.code === 200 && json.data) {
      rec.value = json.data
    } else {
      ElMessage.error(json.message || '获取推荐失败')
    }
  } catch (e) {
    ElMessage.error('请求失败：' + e.message)
  } finally {
    recLoading.value = false
  }
}

function resetRec() {
  rec.value = null
}

onMounted(loadDashboard)
</script>

<style scoped>
.dashboard-page {
  max-width: 1000px;
  margin: 24px auto;
}
.dash-card {
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.stat-row {
  margin-bottom: 8px;
}
.stat-box {
  text-align: center;
}
.mastery-block {
  margin-top: 18px;
}
.mastery-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
}
.mastery-num {
  font-size: 18px;
  font-weight: 700;
  color: #409eff;
}
.weak-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.weak-item {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px 16px;
}
.weak-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.weak-kp {
  font-size: 15px;
  font-weight: 600;
}
.weak-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.weak-bar .el-progress {
  flex: 1;
}
.weak-pct {
  width: 44px;
  text-align: right;
  font-weight: 600;
  color: #909399;
}
.weak-meta {
  display: flex;
  gap: 18px;
  font-size: 12px;
  color: #909399;
  margin: 8px 0;
}
.rec-section {
  margin-top: 14px;
}
.rec-section-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.rec-problem {
  padding: 8px 0;
  border-bottom: 1px dashed #ebeef5;
}
.rec-rating {
  margin-left: 10px;
  color: #e6a23c;
  font-size: 13px;
}
.rec-tags {
  margin-top: 4px;
}
.rec-fallback {
  padding: 4px 0;
}
</style>
