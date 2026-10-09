<template>
  <div class="quiz-page">
    <el-card class="quiz-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">全部题目 · 自动追踪作答</span>
          <el-button @click="goBack">返回课程列表</el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="这里汇聚了「B站无感追踪」插件自动生成的题目，以及你在其它页面手动生成的题目。逐题作答即可。"
        style="margin-bottom: 12px"
      />

      <div v-if="questions.length" class="questions-area">
        <el-divider>题目（共 {{ questions.length }} 题）</el-divider>

        <div v-for="(q, idx) in questions" :key="q.id" class="question-block">
          <div class="q-head">
            <strong>第 {{ idx + 1 }} 题</strong>
            <el-tag size="small" :type="q.type === '选择题' ? 'primary' : 'success'" style="margin-left: 8px">
              {{ q.type }}
            </el-tag>
            <span class="q-kp">（知识点：{{ q.knowledgePoint || '—' }}）</span>
          </div>
          <div class="q-stem">{{ q.stem }}</div>

          <!-- 选择题 -->
          <el-radio-group v-if="q.type === '选择题'" v-model="answers[q.id]" class="q-options">
            <el-radio
              v-for="opt in parseOptions(q.options)"
              :key="opt.value"
              :value="opt.value"
              class="q-option"
            >
              {{ opt.label }}
            </el-radio>
          </el-radio-group>

          <!-- 简答题 -->
          <el-input
            v-else
            v-model="answers[q.id]"
            type="textarea"
            :rows="3"
            placeholder="请输入你的答案"
          />

          <!-- 单题结果 -->
          <div v-if="results[q.id]" class="q-result">
            <el-tag :type="results[q.id].isCorrect ? 'success' : 'danger'">
              {{ results[q.id].isCorrect ? '回答正确' : '回答错误' }}
            </el-tag>
            <span class="q-correct-answer">正确答案：{{ correctAnswerText(q) }}</span>
            <div v-if="q.explanation" class="q-explain">解析：{{ q.explanation }}</div>
            <div v-if="results[q.id].skipped" class="q-explain">（未作答，已跳过）</div>
            <div v-if="results[q.id].error" class="q-explain q-error">{{ results[q.id].error }}</div>
          </div>
        </div>

        <div class="submit-bar">
          <el-button type="success" :loading="submitting" @click="submitAll">提交答案</el-button>
          <span v-if="summaryText" class="summary">{{ summaryText }}</span>
        </div>
      </div>

      <el-empty v-else-if="!loading" description="暂无题目，去 B站看个视频试试「无感出题」吧" />
      <el-skeleton v-else :rows="6" animated />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()

const questions = ref([])
const answers = reactive({})
const results = reactive({})
const submitting = ref(false)
const loading = ref(false)
const summaryText = ref('')
// 进入页面即开始计时，用于统计真实学习时长
const startTime = Date.now()

function parseOptions(str) {
  let arr = []
  try {
    arr = typeof str === 'string' ? JSON.parse(str) : Array.isArray(str) ? str : []
  } catch {
    return []
  }
  return arr.map((o) => {
    const s = String(o)
    const m = s.match(/^\s*([A-Da-d])[\.、)]\s*([\s\S]*)$/)
    if (m) return { value: m[1].toUpperCase(), label: s }
    const first = s.trim().charAt(0).toUpperCase()
    return { value: first, label: s }
  })
}

function correctAnswerText(q) {
  if (q.type === '选择题') {
    const hit = parseOptions(q.options).find(
      (o) => o.value === String(q.answer || '').trim().charAt(0).toUpperCase()
    )
    if (hit) return hit.label
  }
  return q.answer || '—'
}

async function loadAll() {
  loading.value = true
  try {
    const res = await fetch('/api/ai/allQuestions')
    const json = await res.json()
    if (json.code === 200 && Array.isArray(json.data)) {
      questions.value = json.data
      Object.keys(answers).forEach((k) => delete answers[k])
      Object.keys(results).forEach((k) => delete results[k])
      summaryText.value = ''
    } else {
      ElMessage.error(json.message || '加载题目失败')
    }
  } catch (e) {
    ElMessage.error('请求失败：' + e.message + '（请确认后端已启动）')
  } finally {
    loading.value = false
  }
}

async function submitAll() {
  if (!questions.value.length) return
  submitting.value = true
  summaryText.value = ''
  let correctCount = 0
  try {
    for (const q of questions.value) {
      const userAnswer = answers[q.id]
      if (userAnswer === undefined || userAnswer === null || String(userAnswer).trim() === '') {
        results[q.id] = { isCorrect: false, skipped: true }
        continue
      }
      const res = await fetch('/api/answer/submit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          questionId: q.id,
          userAnswer: String(userAnswer),
          durationSeconds: Math.max(1, Math.round((Date.now() - startTime) / 1000))
        })
      })
      const json = await res.json()
      if (json.code === 200 && json.data) {
        const ok = !!json.data.isCorrect
        results[q.id] = { isCorrect: ok }
        if (ok) correctCount++
      } else {
        results[q.id] = { isCorrect: false, error: json.message || '提交失败' }
      }
    }
    const total = questions.value.length
    summaryText.value = `答题完成：正确 ${correctCount} / ${total}`
    ElMessage.success(summaryText.value)
  } catch (e) {
    ElMessage.error('提交失败：' + e.message)
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push('/')
}

onMounted(loadAll)
</script>

<style scoped>
.quiz-page {
  max-width: 880px;
  margin: 24px auto;
}
.quiz-card {
  width: 100%;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.questions-area {
  margin-top: 8px;
}
.question-block {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 14px;
}
.q-head {
  margin-bottom: 6px;
}
.q-kp {
  color: #909399;
  font-size: 13px;
}
.q-stem {
  margin: 6px 0 10px;
  line-height: 1.6;
}
.q-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.q-option {
  white-space: normal;
  height: auto;
  line-height: 1.5;
  margin-right: 0;
}
.q-result {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.q-correct-answer {
  color: #67c23a;
  font-weight: 600;
}
.q-explain {
  width: 100%;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}
.q-error {
  color: #f56c6c;
}
.submit-bar {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 14px;
}
.summary {
  font-weight: 600;
  color: #409eff;
}
</style>
