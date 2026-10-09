<template>
  <div class="quiz-page">
    <el-card class="quiz-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">AI 智能出题</span>
          <el-button @click="goBack">返回视频列表</el-button>
        </div>
      </template>

      <!-- 手动粘贴兜底输入框 -->
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="B站视频简介/字幕常常为空，无法自动抓取时，请在此手动粘贴内容后再出题"
        style="margin-bottom: 12px"
      />
      <el-input
        v-model="pasteText"
        type="textarea"
        :rows="6"
        placeholder="请粘贴B站AI总结、字幕或视频简介"
      />
      <div class="generate-bar">
        <el-button type="primary" :loading="generating" :disabled="!videoRecordId" @click="generate">
          开始出题
        </el-button>
        <span v-if="!videoRecordId" class="hint-error">未获取到视频记录ID</span>
        <span v-else-if="!pasteText.trim()" class="hint-warn">未粘贴内容，将回退使用该视频记录的笔记</span>
      </div>

      <!-- 题目区 -->
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

      <el-empty v-else-if="!generating" description="点击「开始出题」生成题目" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const videoRecordId = ref(Number(route.params.videoRecordId) || null)
const courseId = ref(route.query.courseId ? Number(route.query.courseId) : null)

const pasteText = ref('')
const questions = ref([])
const answers = reactive({})
const results = reactive({})
const generating = ref(false)
const submitting = ref(false)
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

// 把后端返回的报错统一翻译为用户能看懂的中文提示
function friendlyError(msg) {
  const s = String(msg || '')
  if (/429|Too Many Requests|访问量过大|1305|繁忙/.test(s)) {
    return 'AI 服务器当前繁忙，请稍后重试（多点几次或等一两分钟即可）'
  }
  if (/timeout|timed out|超时/i.test(s)) {
    return 'AI 接口响应超时，请稍后重试'
  }
  if (/Failed to fetch|NetworkError|连接失败/i.test(s)) {
    return '后端连接失败，请确认后端已启动'
  }
  return s || '生成失败'
}

async function generate() {
  if (!videoRecordId.value) {
    ElMessage.error('未获取到视频记录ID')
    return
  }
  generating.value = true
  summaryText.value = ''
  try {
    const payload = {
      videoRecordId: videoRecordId.value,
      text: pasteText.value
    }
    const res = await fetch('/api/ai/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    const json = await res.json()
    if (json.code === 200 && Array.isArray(json.data)) {
      questions.value = json.data
      // 重置作答与结果
      Object.keys(answers).forEach((k) => delete answers[k])
      Object.keys(results).forEach((k) => delete results[k])
      ElMessage.success(`已生成 ${json.data.length} 道题目`)
    } else {
      ElMessage.error(friendlyError(json.message))
    }
  } catch (e) {
    ElMessage.error(friendlyError(e.message) + '（请确认后端已启动）')
  } finally {
    generating.value = false
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
  if (courseId.value) {
    router.push('/course/' + courseId.value + '/videos')
  } else {
    router.push('/')
  }
}

onMounted(() => {
  if (!videoRecordId.value) {
    ElMessage.warning('未检测到视频记录ID，请通过视频列表进入')
  }
})
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
.generate-bar {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.hint-warn {
  color: #e6a23c;
  font-size: 13px;
}
.hint-error {
  color: #f56c6c;
  font-size: 13px;
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
