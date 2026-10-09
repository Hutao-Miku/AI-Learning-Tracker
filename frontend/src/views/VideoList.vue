<template>
  <div class="video-list-page">
    <el-card class="video-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button @click="goBack">返回课程列表</el-button>
            <span class="course-title">课程：{{ courseName }}</span>
          </div>
          <el-button type="primary" @click="openAddDialog">添加视频记录</el-button>
        </div>
      </template>

      <el-table
        :data="videos"
        v-loading="loading"
        stripe
        border
        empty-text="还没有视频记录，点击「添加视频记录」开始吧"
        style="width: 100%"
      >
        <el-table-column prop="episodeNumber" label="集数" width="80" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="时长" width="100">
          <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
        </el-table-column>
        <el-table-column label="观看进度" min-width="200">
          <template #default="{ row }">
            <el-progress
              :percentage="Number(row.watchProgress) || 0"
              :stroke-width="14"
              :text-inside="true"
            />
          </template>
        </el-table-column>
        <el-table-column prop="notes" label="笔记" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.notes || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <div class="op-buttons">
              <el-button type="primary" size="small" @click="goQuiz(row)">AI出题</el-button>
              <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加视频记录弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      title="添加视频记录"
      width="460px"
      @closed="resetForm"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="集数" prop="episodeNumber">
          <el-input-number v-model="form.episodeNumber" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="如：第1集 变量与数据类型" />
        </el-form-item>
        <el-form-item label="时长(秒)" prop="duration">
          <el-input-number v-model="form.duration" :min="0" :max="100000" />
        </el-form-item>
        <el-form-item label="进度" prop="watchProgress">
          <el-slider v-model="form.watchProgress" :min="0" :max="100" show-input />
        </el-form-item>
        <el-form-item label="笔记" prop="notes">
          <el-input v-model="form.notes" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAdd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()

const courseId = ref(Number(route.params.id))
const courseName = ref('加载中...')
const videos = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = ref({
  episodeNumber: 1,
  title: '',
  duration: 0,
  watchProgress: 0,
  notes: ''
})

const rules = {
  episodeNumber: [{ required: true, message: '请输入集数', trigger: 'blur' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  watchProgress: [{ required: true, message: '请输入进度', trigger: 'blur' }]
}

function formatDuration(sec) {
  const s = Number(sec) || 0
  if (s <= 0) return '—'
  const m = Math.floor(s / 60)
  const r = s % 60
  return m + ':' + String(r).padStart(2, '0')
}

async function fetchCourse() {
  try {
    const res = await fetch(`/api/course/${courseId.value}`)
    const json = await res.json()
    if (json.code === 200 && json.data) {
      courseName.value = json.data.name || ('课程 ' + courseId.value)
    } else {
      courseName.value = '未知课程 (id=' + courseId.value + ')'
    }
  } catch (e) {
    courseName.value = '未知课程 (id=' + courseId.value + ')'
  }
}

async function fetchVideos() {
  loading.value = true
  try {
    const res = await fetch(`/api/video/list/${courseId.value}`)
    const json = await res.json()
    if (json.code === 200) {
      videos.value = json.data || []
    } else {
      ElMessage.error(json.message || '获取视频记录失败')
    }
  } catch (e) {
    ElMessage.error('请求失败：' + e.message + '（请确认后端已启动）')
  } finally {
    loading.value = false
  }
}

function loadAll() {
  courseId.value = Number(route.params.id)
  fetchCourse()
  fetchVideos()
}

function goBack() {
  router.push('/courses')
}

function goQuiz(row) {
  // 记录来源课程 id，方便答题页“返回视频列表”能跳回正确的课程
  router.push('/video/' + row.id + '/quiz?courseId=' + courseId.value)
}

function openAddDialog() {
  dialogVisible.value = true
}

function resetForm() {
  form.value = { episodeNumber: 1, title: '', duration: 0, watchProgress: 0, notes: '' }
  formRef.value?.clearValidate()
}

async function handleAdd() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = {
      courseId: courseId.value,
      episodeNumber: Number(form.value.episodeNumber),
      title: form.value.title,
      duration: Number(form.value.duration),
      watchProgress: Number(form.value.watchProgress),
      notes: form.value.notes
    }
    const res = await fetch('/api/video/add', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    const json = await res.json()
    if (json.code === 200) {
      ElMessage.success('添加成功')
      dialogVisible.value = false
      await fetchVideos()
    } else {
      ElMessage.error(json.message || '添加失败')
    }
  } catch (e) {
    ElMessage.error('请求失败：' + e.message)
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  const label = row.title || ('第' + row.episodeNumber + '集')
  try {
    await ElMessageBox.confirm(`确定删除「${label}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    const res = await fetch(`/api/video/${row.id}`, { method: 'DELETE' })
    const json = await res.json()
    if (json.code === 200) {
      ElMessage.success('删除成功')
      await fetchVideos()
    } else {
      ElMessage.error(json.message || '删除失败')
    }
  } catch (e) {
    ElMessage.error('请求失败：' + e.message)
  }
}

onMounted(loadAll)
watch(() => route.params.id, () => loadAll())
</script>

<style scoped>
.video-list-page {
  max-width: 1080px;
  margin: 24px auto;
}
.video-card {
  width: 100%;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.course-title {
  font-size: 16px;
  font-weight: 600;
}
.op-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}
</style>
