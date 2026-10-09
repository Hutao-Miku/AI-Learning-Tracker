<template>
  <div class="exam-page">
    <el-card class="exam-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">定期考核 · 周测 / 月测</span>
          <div class="gen-bar" v-if="phase !== 'exam'">
            <el-button type="primary" :loading="generating" @click="generate('weekly')">生成周测</el-button>
            <el-button type="success" :loading="generating" @click="generate('monthly')">生成月测</el-button>
          </div>
          <el-button v-else @click="reset">返回重选</el-button>
        </div>
      </template>

      <!-- 说明 -->
      <el-alert
        v-if="phase === 'idle'"
        type="info"
        :closable="false"
        show-icon
        title="题目全部来自真实来源，绝不 AI 编造"
        description="算法真题来自 Codeforces 正赛（不可达时退化为洛谷/牛客真实题库链接）；企业八股来自内置高频面试题库。同一周期只生成一次，重复点击直接加载已生成的试卷。"
        style="margin-bottom: 12px"
      />

      <!-- 生成中/空态 -->
      <el-empty v-if="phase === 'idle' && !generating" description="点击上方按钮生成一份定期考核试卷" />

      <!-- 答题阶段 -->
      <div v-if="phase === 'exam' && paper" class="questions-area">
        <el-alert
          :type="paper.cached ? 'warning' : 'success'"
          :closable="false"
          show-icon
          :title="paper.cached ? '已加载本周/本月已生成的试卷（未重复消耗外部接口）' : '试卷已生成，开始作答吧'"
          style="margin-bottom: 12px"
        />
        <div class="paper-meta">
          共 {{ paper.questions.length }} 题 ·
          客观题（八股）<span class="tag">自动批改</span> ·
          实战挑战题（算法真题）<span class="tag">不计分·附链接</span>
        </div>

        <div v-for="(q, idx) in paper.questions" :key="q.qid" class="question-block">
          <div class="q-head">
            <strong>第 {{ idx + 1 }} 题</strong>
            <el-tag size="small" :type="q.type === 'choice' ? 'primary' : 'warning'" style="margin-left: 8px">
              {{ q.type === 'choice' ? '八股选择题' : '算法实战挑战' }}
            </el-tag>
            <span class="q-kp">（{{ q.knowledgePoint || '—' }}）</span>
          </div>
          <div class="q-stem">{{ q.stem }}</div>

          <!-- 选择题 -->
          <el-radio-group v-if="q.type === 'choice'" v-model="answers[q.qid]" class="q-options">
            <el-radio
              v-for="opt in parseOptions(q.options)"
              :key="opt.value"
              :value="opt.value"
              class="q-option"
            >
              {{ opt.label }}
            </el-radio>
          </el-radio-group>

          <!-- 实战挑战题 -->
          <div v-else class="challenge-box">
            <el-input
              v-model="answers[q.qid]"
              type="textarea"
              :rows="3"
              placeholder="在此写下你的解法思路 / 代码框架（本题不计分，提交后可对照官方题解）"
            />
            <el-link v-if="q.url" type="primary" :href="q.url" target="_blank" rel="noopener" :underline="false">
              前往原题 / 题库 →
            </el-link>
          </div>
        </div>

        <div class="submit-bar">
          <el-button type="danger" size="large" :loading="submitting" @click="submitAll">提交试卷</el-button>
          <span class="hint">提交后统一打分并展示错题解析</span>
        </div>
      </div>

      <!-- 结果阶段 -->
      <div v-if="phase === 'result' && result" class="result-area">
        <el-result
          :icon="result.score >= 60 ? 'success' : (result.totalGradable === 0 ? 'info' : 'error')"
          :title="result.totalGradable > 0 ? ('客观题得分 ' + result.score + ' 分') : '本次试卷无客观计分题'"
          :sub-title="resultSubTitle"
        >
          <template #extra>
            <el-button type="primary" @click="reset">再做一次 / 返回</el-button>
          </template>
        </el-result>

        <!-- 错题解析 -->
        <el-divider>错题解析（含实战挑战题）</el-divider>
        <div v-for="item in wrongItems" :key="item.qid" class="wrong-block">
          <div class="w-head">
            <strong>第 {{ item.qid }} 题</strong>
            <el-tag size="small" :type="item.type === 'choice' ? 'danger' : 'warning'">
              {{ item.type === 'choice' ? '八股选择题' : '算法实战挑战' }}
            </el-tag>
            <span class="w-kp">{{ item.knowledgePoint }}</span>
          </div>
          <div class="w-stem">{{ item.stem }}</div>
          <div v-if="item.type === 'choice'" class="w-line">
            <span class="w-label">你的答案：</span>
            <span :class="item.isCorrect ? 'ok' : 'bad'">{{ item.userAnswer || '（未作答）' }}</span>
          </div>
          <div v-if="item.type === 'choice' && item.correctAnswer" class="w-line">
            <span class="w-label">正确答案：</span><span class="ok">{{ item.correctAnswer }}</span>
          </div>
          <div v-if="item.type === 'challenge'" class="w-line">
            <span class="w-label">本题为实战挑战：</span>
            <span>不计客观分，请前往链接提交并对照官方题解。</span>
          </div>
          <div v-if="item.explanation" class="w-explain">解析：{{ item.explanation }}</div>
          <el-link v-if="item.url" type="primary" :href="item.url" target="_blank" rel="noopener" :underline="false">
            原题 / 题库链接 →
          </el-link>
        </div>
        <el-empty v-if="!wrongItems.length" description="本轮没有需要解析的题目 🎉" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'

const phase = ref('idle') // idle | exam | result
const generating = ref(false)
const submitting = ref(false)
const paper = ref(null)
const result = ref(null)
const answers = reactive({})

const wrongItems = computed(() => {
  if (!result.value) return []
  // 错题 + 实战挑战题都展示解析
  return result.value.items.filter(it => it.type === 'challenge' || it.isCorrect === false)
})

const resultSubTitle = computed(() => {
  if (!result.value) return ''
  const r = result.value
  if (r.totalGradable > 0) {
    return `客观题答对 ${r.correctCount} / ${r.totalGradable}；实战挑战题 ${r.challengeCount} 道（不计分）`
  }
  return `实战挑战题 ${r.challengeCount} 道（不计分）`
})

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

function friendlyError(msg) {
  const s = String(msg || '')
  if (/429|Too Many Requests|访问量过大|1305|繁忙/.test(s)) return '服务器当前繁忙，请稍后重试'
  if (/timeout|timed out|超时/i.test(s)) return '接口响应超时，请稍后重试'
  if (/Failed to fetch|NetworkError|连接失败/.test(s)) return '后端连接失败，请确认后端已启动'
  return s || '操作失败'
}

async function generate(period) {
  generating.value = true
  clearAnswers()
  result.value = null
  try {
    const res = await fetch('/api/exam/generate?period=' + period, { method: 'POST' })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      paper.value = json.data
      phase.value = 'exam'
      ElMessage.success(paper.value.cached ? '已加载已生成的试卷' : `已生成 ${paper.value.questions.length} 道题目`)
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
  if (!paper.value) return
  submitting.value = true
  const ans = []
  for (const q of paper.value.questions) {
    ans.push({ qid: q.qid, userAnswer: answers[q.qid] ?? '' })
  }
  try {
    const res = await fetch('/api/exam/submit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ paperId: paper.value.paperId, answers: ans })
    })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      result.value = json.data
      phase.value = 'result'
    } else {
      ElMessage.error(friendlyError(json.message))
    }
  } catch (e) {
    ElMessage.error('提交失败：' + friendlyError(e.message))
  } finally {
    submitting.value = false
  }
}

function clearAnswers() {
  Object.keys(answers).forEach(k => delete answers[k])
}

function reset() {
  phase.value = 'idle'
  paper.value = null
  result.value = null
  clearAnswers()
}
</script>

<style scoped>
.exam-page {
  max-width: 880px;
  margin: 24px auto;
}
.exam-card {
  width: 100%;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.paper-meta {
  font-size: 13px;
  color: #909399;
  margin-bottom: 14px;
}
.paper-meta .tag {
  display: inline-block;
  background: #ecf5ff;
  color: #409eff;
  border-radius: 4px;
  padding: 0 6px;
  margin-left: 4px;
  font-size: 12px;
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
.challenge-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.submit-bar {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 14px;
}
.hint {
  color: #909399;
  font-size: 13px;
}
.result-area {
  margin-top: 8px;
}
.wrong-block {
  border: 1px solid #fde2e2;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 12px;
  background: #fef0f0;
}
.w-head {
  margin-bottom: 4px;
}
.w-kp {
  color: #909399;
  font-size: 13px;
  margin-left: 6px;
}
.w-stem {
  margin: 4px 0 8px;
  line-height: 1.6;
}
.w-line {
  font-size: 14px;
  margin: 3px 0;
}
.w-label {
  color: #606266;
}
.ok {
  color: #67c23a;
  font-weight: 600;
}
.bad {
  color: #f56c6c;
  font-weight: 600;
}
.w-explain {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  margin-top: 4px;
}
</style>
