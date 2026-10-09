<template>
  <div class="dash">
    <!-- 页面头部 -->
    <div class="dash-header">
      <div class="dash-header-left">
        <h1 class="dash-title">学习数据中心</h1>
        <p class="dash-sub">基于 SM-2 间隔重复算法的个性化学习看板 · Learning Analytics Dashboard</p>
      </div>
      <el-button class="refresh-btn" :icon="Refresh" @click="loadDashboard" :loading="loading">
        刷新数据
      </el-button>
    </div>

    <!-- 1. 顶部总览卡片区 -->
    <el-row :gutter="20" class="overview">
      <el-col v-for="card in cards" :key="card.key" :xs="24" :sm="12" :lg="6">
        <div class="ov-card">
          <div class="ov-icon" :class="card.cls">
            <el-icon><component :is="card.icon" /></el-icon>
          </div>
          <div class="ov-body">
            <div class="ov-label">{{ card.label }}</div>
            <div class="ov-value">
              {{ card.value }}<span class="ov-unit">{{ card.unit }}</span>
            </div>
            <div class="ov-foot">{{ card.sub }}</div>
            <div v-if="card.progress !== undefined" class="ov-mini">
              <el-progress
                :percentage="card.progress"
                :stroke-width="6"
                :show-text="false"
                :color="masteryColor(card.progress)"
              />
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 2. 核心功能区分栏：左右两列 -->
    <el-row :gutter="20" class="cols">
      <!-- 左列：今天要处理 / 复习提醒 -->
      <el-col :xs="24" :md="10">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">今天要处理</span>
            <span class="panel-badge" :class="{ active: reviewList.length }">
              {{ reviewList.length }} 项待复习
            </span>
          </div>
          <div class="panel-body">
            <el-skeleton v-if="loading" :rows="4" animated />
            <el-empty
              v-else-if="!reviewList.length"
              :image-size="90"
              description="🎉 太棒了！今天没有待复习任务，去学点新内容吧"
            />
            <div v-else class="review-list">
              <div v-for="item in reviewList" :key="item.knowledgePoint" class="review-card">
                <div class="rc-top">
                  <span class="rc-name">{{ item.knowledgePoint }}</span>
                  <el-tag size="small" :type="item.overdue ? 'danger' : 'warning'" effect="light">
                    {{ item.statusText }}
                  </el-tag>
                </div>
                <div class="rc-meta">
                  下次复习：<b>{{ item.nextReview }}</b> · 已作答 {{ item.answeredCount }} 次
                </div>
                <el-button class="review-btn" @click="openRecommend(item.knowledgePoint)">
                  立即复习
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右列：薄弱点 + 真题推荐 -->
      <el-col :xs="24" :md="14">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">薄弱点 · 真题推荐</span>
            <span class="panel-sub">掌握度最差的前 5 个知识点</span>
          </div>
          <div class="panel-body">
            <el-skeleton v-if="loading" :rows="5" animated />
            <el-empty v-else-if="!weakPoints.length" :image-size="90" description="暂无作答记录，去答几道题就会有数据啦" />
            <div v-else class="weak-list">
              <div v-for="wp in weakPoints" :key="wp.knowledgePoint" class="weak-row">
                <div class="weak-name">
                  <div class="wn-title">{{ wp.knowledgePoint }}</div>
                  <el-tag size="small" type="info" effect="plain">{{ wp.tag }}</el-tag>
                </div>
                <div class="weak-bar">
                  <el-progress
                    :percentage="wp.mastery"
                    :stroke-width="10"
                    :color="masteryColor(wp.mastery)"
                    :show-text="false"
                  />
                  <span class="weak-pct" :style="{ color: masteryColor(wp.mastery) }">
                    {{ wp.mastery }}%
                  </span>
                </div>
                <el-button type="primary" plain size="small" @click="openRecommend(wp.knowledgePoint)">
                  去练真题
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 3. 底部数据可视化：本周学习节奏 -->
    <div class="panel weekly">
      <div class="panel-head">
        <span class="panel-title">本周学习节奏</span>
        <span class="panel-sub">每日学习时长（分钟）</span>
      </div>
      <div class="bars">
        <div v-for="(d, i) in weekBars" :key="i" class="bar-col">
          <div class="bar-val" v-if="d.value > 0">{{ d.value }}</div>
          <div class="bar-track">
            <div
              class="bar-fill"
              :class="{ active: d.isToday, empty: d.value === 0 }"
              :style="{ height: d.height + '%' }"
            />
          </div>
          <div class="bar-label" :class="{ today: d.isToday }">{{ d.label }}</div>
        </div>
      </div>
      <div v-if="!hasWeekData" class="week-hint">
        仅今日有学习数据，随着积累将自动填充完整的一周节奏。
      </div>
    </div>

    <!-- 真题推荐弹窗 -->
    <el-dialog v-model="dialogVisible" :title="'真实竞赛真题推荐 · ' + recKp" width="640px" @closed="resetRec">
      <div v-if="rec" class="rec-dialog">
        <el-alert
          :type="rec.source === 'codeforces' ? 'success' : 'warning'"
          :closable="false"
          show-icon
          :title="rec.source === 'codeforces'
            ? '已从 Codeforces 正赛题库匹配到真实真题（非 AI 生成）'
            : 'Codeforces 暂无匹配，已为你准备好知名题库搜索入口，点击即可去刷'"
        />

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
import { ref, reactive, computed, onMounted, markRaw } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, EditPen, CircleCheck, Timer, DataLine } from '@element-plus/icons-vue'

const loading = ref(false)
const overallMastery = ref(0)
const today = reactive({ answerCount: 0, correctCount: 0, accuracy: 0, studyMinutes: 0 })
const weakPoints = ref([])

const dialogVisible = ref(false)
const rec = ref(null)
const recKp = ref('')
const recLoading = ref(false)

// 最近 7 天学习节奏（后端真实数据）
const weekly = ref({ days: [] })

function masteryColor(pct) {
  if (pct >= 70) return '#22c55e'
  if (pct >= 40) return '#f59e0b'
  return '#ef4444'
}

// 顶部总览卡片（今日答题数 / 正确率 / 时长 / 整体掌握度）
const cards = computed(() => [
  { key: 'answer', label: '今日答题数', value: today.answerCount, unit: ' 题', icon: markRaw(EditPen), cls: 'ic-blue', sub: '今日累计作答' },
  { key: 'acc', label: '今日正确率', value: today.accuracy, unit: ' %', icon: markRaw(CircleCheck), cls: 'ic-green', sub: '答题正确比例' },
  { key: 'time', label: '今日学习时长', value: today.studyMinutes, unit: ' 分', icon: markRaw(Timer), cls: 'ic-orange', sub: '专注学习分钟' },
  { key: 'mastery', label: '整体掌握度', value: overallMastery.value, unit: ' %', icon: markRaw(DataLine), cls: 'ic-purple', sub: '全知识点均值', progress: overallMastery.value }
])

// 左列：SM-2 到期（今天或逾期）的复习任务
const reviewList = computed(() => {
  const td = todayStr()
  return weakPoints.value
    .filter(w => w.nextReview && w.nextReview <= td)
    .map(w => {
      const overdue = w.nextReview < td
      return {
        knowledgePoint: w.knowledgePoint,
        nextReview: w.nextReview,
        answeredCount: w.answeredCount,
        overdue,
        statusText: overdue ? '已逾期' : '今天到期'
      }
    })
})

// 底部：本周学习节奏（真实 7 天数据，按后端返回的 studyMinutes 决定柱高；无数据天高度为 0）
const weekBars = computed(() => {
  const days = weekly.value.days || []
  const max = Math.max(1, ...days.map(d => d.studyMinutes || 0))
  return days.map(d => {
    const v = d.studyMinutes || 0
    return {
      label: d.weekday,
      date: d.date,
      isToday: d.date === todayStr(),
      value: v,
      height: v > 0 ? Math.max(12, Math.round((v / max) * 100)) : 0
    }
  })
})
const hasWeekData = computed(() => (weekly.value.days || []).some(d => (d.studyMinutes || 0) > 0))

function todayStr() {
  const d = new Date()
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
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
  }
  // 周数据独立加载，失败不影响主面板
  try {
    const wres = await fetch('/api/mastery/weekly')
    const wjson = await wres.json()
    if (wjson.code === 200 && wjson.data) {
      weekly.value = wjson.data
    }
  } catch (e) {
    /* 周数据异常时保持空态，不阻断主面板 */
  } finally {
    loading.value = false
  }
}

async function openRecommend(knowledgePoint) {
  dialogVisible.value = true
  recKp.value = knowledgePoint
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
.dash {
  max-width: 1440px;
  margin: 0 auto;
  padding: 28px 32px 48px;
  min-height: 100vh;
  background: linear-gradient(180deg, #f5f7fb 0%, #eef1f8 100%);
}

/* 页面头部 */
.dash-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}
.dash-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: #1f2937;
  letter-spacing: 0.5px;
}
.dash-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: #8a93a6;
}
.refresh-btn {
  border-radius: 10px;
  padding: 10px 18px;
  font-weight: 600;
}

/* 1. 顶部总览卡片 */
.overview {
  margin-bottom: 20px;
}
.ov-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px 22px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 10px rgba(31, 41, 55, 0.06);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  height: 100%;
}
.ov-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(79, 70, 229, 0.12);
}
.ov-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  flex-shrink: 0;
}
.ov-icon :deep(svg) {
  width: 26px;
  height: 26px;
}
.ic-blue { background: #e8f0fe; color: #3b82f6; }
.ic-green { background: #e6f7ec; color: #22c55e; }
.ic-orange { background: #fef3e2; color: #f59e0b; }
.ic-purple { background: #f1ecfe; color: #8b5cf6; }

.ov-body { flex: 1; min-width: 0; }
.ov-label {
  font-size: 13px;
  color: #8a93a6;
  font-weight: 500;
}
.ov-value {
  font-size: 32px;
  font-weight: 800;
  color: #1f2937;
  line-height: 1.25;
  margin-top: 2px;
}
.ov-unit {
  font-size: 14px;
  font-weight: 600;
  color: #aab2c5;
  margin-left: 2px;
}
.ov-foot {
  font-size: 12px;
  color: #aab2c5;
  margin-top: 2px;
}
.ov-mini {
  margin-top: 8px;
}

/* 通用面板 */
.panel {
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 2px 10px rgba(31, 41, 55, 0.06);
  margin-bottom: 20px;
  overflow: hidden;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
  border-bottom: 1px solid #f0f2f7;
}
.panel-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  position: relative;
  padding-left: 12px;
}
.panel-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: linear-gradient(135deg, #667eea, #764ba2);
}
.panel-sub {
  font-size: 12px;
  color: #aab2c5;
}
.panel-badge {
  font-size: 12px;
  font-weight: 600;
  color: #aab2c5;
  background: #f3f4f8;
  padding: 3px 10px;
  border-radius: 20px;
}
.panel-badge.active {
  color: #fff;
  background: linear-gradient(135deg, #667eea, #764ba2);
}
.panel-body {
  padding: 18px 22px;
}

/* 左列：复习卡片 */
.review-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.review-card {
  border: 1px solid #eef0f5;
  border-radius: 12px;
  padding: 14px 16px;
  background: #fcfcfe;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.review-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 4px 14px rgba(102, 126, 234, 0.12);
}
.rc-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.rc-name {
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
}
.rc-meta {
  font-size: 12px;
  color: #8a93a6;
  margin: 8px 0;
}
.rc-meta b { color: #4b5563; }
.review-btn {
  width: 100%;
  border: none;
  border-radius: 10px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #667eea, #764ba2);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}
.review-btn:hover {
  filter: brightness(1.06);
}

/* 右列：薄弱点 */
.weak-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.weak-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.weak-name {
  width: 170px;
  flex-shrink: 0;
}
.wn-title {
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 4px;
}
.weak-bar {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}
.weak-bar :deep(.el-progress) {
  flex: 1;
}
.weak-pct {
  width: 46px;
  text-align: right;
  font-weight: 700;
  font-size: 14px;
}
.weak-row .el-button {
  flex-shrink: 0;
  border-radius: 9px;
}

/* 底部：本周节奏柱状图 */
.bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  gap: 10px;
  height: 200px;
  padding: 8px 4px 0;
}
.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}
.bar-val {
  font-size: 13px;
  font-weight: 700;
  color: #6d28d9;
  margin-bottom: 6px;
  min-height: 18px;
}
.bar-track {
  flex: 1;
  width: 60%;
  max-width: 46px;
  background: #f1f3f8;
  border-radius: 10px;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
}
.bar-fill {
  width: 100%;
  border-radius: 10px;
  background: linear-gradient(180deg, #a5b4fc, #818cf8);
  transition: height 0.4s ease;
}
.bar-fill.active {
  background: linear-gradient(180deg, #8b5cf6, #6d28d9);
  box-shadow: 0 4px 12px rgba(109, 40, 217, 0.35);
}
.bar-fill.empty {
  height: 0 !important;
}
.bar-label {
  margin-top: 8px;
  font-size: 12px;
  color: #aab2c5;
}
.bar-label.today {
  color: #6d28d9;
  font-weight: 700;
}
.week-hint {
  text-align: center;
  font-size: 12px;
  color: #aab2c5;
  padding: 10px 0 2px;
}

/* 弹窗 */
.rec-section {
  margin-top: 14px;
}
.rec-section-title {
  font-weight: 700;
  margin-bottom: 8px;
  color: #374151;
}
.rec-problem {
  padding: 8px 0;
  border-bottom: 1px dashed #ebeef5;
}
.rec-rating {
  margin-left: 10px;
  color: #f59e0b;
  font-size: 13px;
}
.rec-tags {
  margin-top: 4px;
}
.rec-fallback {
  padding: 4px 0;
}

@media (max-width: 768px) {
  .dash { padding: 18px 14px 36px; }
  .weak-name { width: 120px; }
  .weak-row { gap: 10px; }
}
</style>
