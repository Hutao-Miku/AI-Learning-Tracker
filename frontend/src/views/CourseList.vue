<template>
  <div class="course-list-page">
    <el-card class="course-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">我的课程</span>
          <el-button type="primary" @click="openAddDialog">添加课程</el-button>
        </div>
      </template>

      <el-table
        :data="courses"
        v-loading="loading"
        stripe
        border
        empty-text="还没有课程，点击「添加课程」开始吧"
        style="width: 100%"
      >
        <el-table-column prop="name" label="课程名称" min-width="160" />
        <el-table-column prop="platform" label="平台" min-width="120" />
        <el-table-column label="进度" min-width="240">
          <template #default="{ row }">
            <el-progress
              :percentage="progressOf(row)"
              :stroke-width="14"
              :text-inside="true"
            />
            <div class="progress-text">
              {{ row.completedEpisodes }} / {{ row.totalEpisodes }} 集
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openVideos(row)">查看记录</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加课程弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      title="添加课程"
      width="440px"
      @closed="resetForm"
    >
      <el-form
        :model="form"
        :rules="rules"
        ref="formRef"
        label-width="80px"
      >
        <el-form-item label="课程名" prop="name">
          <el-input v-model="form.name" placeholder="请输入课程名称" />
        </el-form-item>
        <el-form-item label="平台" prop="platform">
          <el-input v-model="form.platform" placeholder="如：B站 / 慕课网 / Coursera" />
        </el-form-item>
        <el-form-item label="总集数" prop="totalEpisodes">
          <el-input-number v-model="form.totalEpisodes" :min="1" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAdd">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

const courses = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = ref({
  name: '',
  platform: '',
  totalEpisodes: 10
})

const rules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  platform: [{ required: true, message: '请输入平台', trigger: 'blur' }],
  totalEpisodes: [{ required: true, message: '请输入总集数', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await fetch('/api/course/list')
    const json = await res.json()
    if (json.code === 200) {
      courses.value = json.data || []
    } else {
      ElMessage.error(json.message || '获取课程列表失败')
    }
  } catch (e) {
    ElMessage.error('请求失败：' + e.message + '（请确认后端已启动）')
  } finally {
    loading.value = false
  }
}

function progressOf(row) {
  const total = Number(row.totalEpisodes) || 0
  const done = Number(row.completedEpisodes) || 0
  if (total <= 0) return 0
  return Math.min(100, Math.round((done / total) * 100))
}

function openAddDialog() {
  dialogVisible.value = true
}

function openVideos(row) {
  router.push('/course/' + row.id + '/videos')
}

function resetForm() {
  form.value = { name: '', platform: '', totalEpisodes: 10 }
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
      userId: 1,
      name: form.value.name,
      platform: form.value.platform,
      totalEpisodes: Number(form.value.totalEpisodes),
      completedEpisodes: 0
    }
    const res = await fetch('/api/course/add', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    const json = await res.json()
    if (json.code === 200) {
      ElMessage.success('添加成功')
      dialogVisible.value = false
      await fetchList()
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
  try {
    await ElMessageBox.confirm(`确定删除课程「${row.name}」吗？`, '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    const res = await fetch(`/api/course/${row.id}`, { method: 'DELETE' })
    const json = await res.json()
    if (json.code === 200) {
      ElMessage.success('删除成功')
      await fetchList()
    } else {
      ElMessage.error(json.message || '删除失败')
    }
  } catch (e) {
    ElMessage.error('请求失败：' + e.message)
  }
}

onMounted(fetchList)
</script>

<style scoped>
.course-list-page {
  max-width: 960px;
  margin: 24px auto;
}
.course-card {
  width: 100%;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header .title {
  font-size: 16px;
  font-weight: 600;
}
.progress-text {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
</style>
