<template>
  <div class="practice-page">
    <el-card class="practice-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">去练真题 · 真实题库</span>
          <div class="gen-bar">
            <el-tag v-if="kp" type="info" effect="plain">{{ kp }}</el-tag>
            <el-button :loading="loading" @click="load">换一批</el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="success"
        :closable="false"
        show-icon
        title="所有题目均来自真实来源，绝无 AI 编造"
        description="理论/八股为公开权威面试题库真实 MCQ（后端判定对错）；算法题为 Codeforces 正赛真题，题面在本站实时抓取展示，作答全程在站内完成。"
        style="margin-bottom: 14px"
      />

      <el-skeleton v-if="loading" :rows="8" animated />
      <el-empty v-else-if="!theory.length && !algorithms.length" description="暂无题目，请点击「换一批」" />

      <template v-else>
        <!-- 理论 / 八股 -->
        <div v-if="theory.length" class="section">
          <div class="section-title">理论 / 八股（选择题 · 后端判分）</div>
          <div v-for="(q, idx) in theory" :key="'t' + q.questionId" class="question-block">
            <div class="q-head">
              <strong>第 {{ idx + 1 }} 题</strong>
              <el-tag size="small" type="primary">{{ q.knowledgePoint }}</el-tag>
            </div>
            <div class="q-stem">{{ q.stem }}</div>
            <el-radio-group v-model="theoryAnswers[q.questionId]" class="q-options">
              <el-radio
                v-for="opt in parseOptions(q.options)"
                :key="opt.value"
                :value="opt.value"
                class="q-option"
              >{{ opt.label }}</el-radio>
            </el-radio-group>
            <div class="q-action">
              <el-button
                type="primary"
                size="small"
                :disabled="!theoryAnswers[q.questionId]"
                :loading="theoryLoading[q.questionId]"
                @click="submitTheory(q)"
              >提交并判分</el-button>
              <span v-if="theoryResults[q.questionId]" class="q-result" :class="theoryResults[q.questionId].isCorrect ? 'ok' : 'bad'">
                {{ theoryResults[q.questionId].isCorrect ? '✓ 回答正确' : '✗ 回答错误' }}
                <span class="q-correct">正确答案：{{ theoryResults[q.questionId].correctAnswer }}</span>
              </span>
            </div>
            <div v-if="theoryResults[q.questionId] && theoryResults[q.questionId].explanation" class="q-explain">
              解析：{{ theoryResults[q.questionId].explanation }}
            </div>
          </div>
        </div>

        <!-- 算法真题 -->
        <div v-if="algorithms.length" class="section">
          <div class="section-title">算法真题（Codeforces 真实题 · 站内题面）</div>
          <div v-for="(q, idx) in algorithms" :key="'a' + q.contestId + q.index" class="question-block algo">
            <div class="q-head">
              <strong>第 {{ theory.length + idx + 1 }} 题</strong>
              <el-tag size="small" type="warning">算法真题</el-tag>
              <span class="q-kp">{{ q.knowledgePoint }}</span>
            </div>

            <!-- 题面：本地题直接展示，CF 题异步拉取 -->
            <div v-if="stmt(q).loading" class="stmt-loading">题面加载中…</div>
            <template v-else>
              <div class="q-stem stmt">{{ stmt(q).statement || q.stem }}</div>
              <div v-if="stmt(q).ioSamples && stmt(q).ioSamples.length" class="io-area">
                <div v-for="(s, si) in stmt(q).ioSamples" :key="si" class="io-item">
                  <div class="io-label">样例 {{ si + 1 }} 输入</div>
                  <pre class="io-pre">{{ s.input }}</pre>
                  <div class="io-label">样例 {{ si + 1 }} 输出</div>
                  <pre class="io-pre">{{ s.output }}</pre>
                </div>
              </div>
            </template>

            <el-input
              v-model="algoCode[q.key]"
              type="textarea"
              :rows="4"
              placeholder="在此书写你的解法 / 代码草稿"
              class="code-box"
            />
            <div class="q-action algo-action">
              <el-link
                v-if="q.url"
                type="primary"
                :href="q.url"
                target="_blank"
                rel="noopener"
                :underline="false"
              >去 Codeforces 评测 →</el-link>
              <el-radio-group v-model="algoPassed[q.key]" class="pass-group">
                <el-radio value="passed">已通过</el-radio>
                <el-radio value="failed">未通过</el-radio>
              </el-radio-group>
              <el-button
                type="success"
                size="small"
                :disabled="!algoPassed[q.key]"
                :loading="algoLoading[q.key]"
                @click="submitAlgorithm(q)"
              >提交记录</el-button>
              <span v-if="algoResults[q.key]" class="q-result ok">已记录：{{ algoResults[q.key] }}</span>
            </div>
          </div>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

const route = useRoute()
const kp = ref(route.query.kp ? String(route.query.kp) : '')

const loading = ref(false)
const theory = ref([])
const algorithms = ref([])
const startTime = Date.now()

const theoryAnswers = reactive({})
const theoryLoading = reactive({})
const theoryResults = reactive({})

const algoCode = reactive({})
const algoPassed = reactive({})
const algoLoading = reactive({})
const algoResults = reactive({})
// 算法题面缓存：key -> { loading, statement, ioSamples }
const stmtCache = reactive({})

function keyOf(q) {
  return 'a_' + (q.contestId != null ? q.contestId : 'L') + '_' + (q.index || 'x')
}

function stmt(q) {
  const k = keyOf(q)
  const c = stmtCache[k]
  if (c) return c
  // 初始化：本地题直接有题面，CF 题需拉取
  if (q.statement) {
    stmtCache[k] = { loading: false, statement: q.statement, ioSamples: q.ioSamples || [] }
    return stmtCache[k]
  }
  const init = { loading: true, statement: '', ioSamples: [] }
  stmtCache[k] = init
  loadStatement(q, k)
  return init
}

async function loadStatement(q, k) {
  try {
    const res = await fetch(`/api/practice/algorithm/statement?contestId=${q.contestId}&index=${encodeURIComponent(q.index)}`)
    const json = await res.json()
    if (json.code === 200 && json.data) {
      stmtCache[k] = {
        loading: false,
        statement: json.data.statement || q.stem,
        ioSamples: json.data.ioSamples || []
      }
    } else {
      stmtCache[k] = { loading: false, statement: q.stem, ioSamples: [] }
    }
  } catch (e) {
    stmtCache[k] = { loading: false, statement: q.stem, ioSamples: [] }
  }
}

function parseOptions(arr) {
  if (!Array.isArray(arr)) return []
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
  if (/429|Too Many Requests|繁忙/.test(s)) return '服务器繁忙，请稍后重试'
  if (/timeout|超时/i.test(s)) return '接口响应超时，请稍后重试'
  if (/Failed to fetch|连接失败/.test(s)) return '后端连接失败，请确认后端已启动'
  return s || '操作失败'
}

async function load() {
  loading.value = true
  try {
    const url = '/api/practice/recommend' + (kp.value ? '?knowledgePoint=' + encodeURIComponent(kp.value) : '')
    const res = await fetch(url)
    const json = await res.json()
    if (json.code === 200 && json.data) {
      theory.value = json.data.theory || []
      algorithms.value = json.data.algorithms || []
      // 重置作答状态
      Object.keys(theoryAnswers).forEach((k) => delete theoryAnswers[k])
      Object.keys(theoryResults).forEach((k) => delete theoryResults[k])
      Object.keys(algoCode).forEach((k) => delete algoCode[k])
      Object.keys(algoPassed).forEach((k) => delete algoPassed[k])
      Object.keys(algoResults).forEach((k) => delete algoResults[k])
      Object.keys(stmtCache).forEach((k) => delete stmtCache[k])
    } else {
      ElMessage.error(json.message || '加载题目失败')
    }
  } catch (e) {
    ElMessage.error('请求失败：' + friendlyError(e.message) + '（请确认后端已启动）')
  } finally {
    loading.value = false
  }
}

async function submitTheory(q) {
  const ans = theoryAnswers[q.questionId]
  if (!ans) return
  theoryLoading[q.questionId] = true
  try {
    const res = await fetch('/api/practice/submit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        kind: 'theory',
        questionId: q.questionId,
        userAnswer: ans,
        durationSeconds: Math.max(1, Math.round((Date.now() - startTime) / 1000))
      })
    })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      theoryResults[q.questionId] = {
        isCorrect: json.data.isCorrect,
        correctAnswer: json.data.correctAnswer,
        explanation: json.data.explanation
      }
    } else {
      ElMessage.error(json.message || '提交失败')
    }
  } catch (e) {
    ElMessage.error('提交失败：' + friendlyError(e.message))
  } finally {
    theoryLoading[q.questionId] = false
  }
}

async function submitAlgorithm(q) {
  const k = keyOf(q)
  const passed = algoPassed[k]
  if (!passed) return
  algoLoading[k] = true
  try {
    const res = await fetch('/api/practice/submit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        kind: 'algorithm',
        contestId: q.contestId,
        index: q.index,
        passed: passed === 'passed',
        code: algoCode[k] || '',
        durationSeconds: Math.max(1, Math.round((Date.now() - startTime) / 1000))
      })
    })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      algoResults[k] = json.data.message || (passed === 'passed' ? '已通过' : '未通过')
    } else {
      ElMessage.error(json.message || '提交失败')
    }
  } catch (e) {
    ElMessage.error('提交失败：' + friendlyError(e.message))
  } finally {
    algoLoading[k] = false
  }
}

onMounted(load)
</script>

<style scoped>
.practice-page {
  max-width: 920px;
  margin: 24px auto;
}
.practice-card {
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
.gen-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.section {
  margin-top: 8px;
}
.section-title {
  font-size: 15px;
  font-weight: 700;
  color: #303133;
  margin: 18px 0 12px;
  padding-left: 10px;
  border-left: 4px solid #409eff;
}
.question-block {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 14px;
}
.q-head {
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.q-kp {
  color: #909399;
  font-size: 13px;
}
.q-stem {
  margin: 6px 0 10px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.stmt {
  background: #fafbff;
  border: 1px solid #eef0f7;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 14px;
}
.stmt-loading {
  color: #909399;
  padding: 12px 0;
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
.q-action {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.q-result {
  font-weight: 600;
}
.q-result.ok { color: #67c23a; }
.q-result.bad { color: #f56c6c; }
.q-correct {
  color: #909399;
  font-weight: 400;
  margin-left: 8px;
}
.q-explain {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  margin-top: 8px;
  background: #fdf6ec;
  border-radius: 6px;
  padding: 8px 10px;
}
.io-area {
  margin: 10px 0;
}
.io-item {
  margin-bottom: 8px;
}
.io-label {
  font-size: 12px;
  color: #909399;
  margin: 4px 0 2px;
}
.io-pre {
  background: #2d2d2d;
  color: #f5f5f5;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
.code-box {
  margin-top: 10px;
}
.algo-action {
  margin-top: 10px;
}
.pass-group {
  margin-left: 4px;
}
</style>
