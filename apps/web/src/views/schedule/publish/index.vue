<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { publishSchedule, listDictItems } from "@/api/biz";

/**
 * 课表发布与版本 M0604 /schedule/publish。
 * 选择学期 + 生效周，发布走 POST /api/schedule/publish；展示发布结果。
 * 按钮权限：BTN-SCH-PUBLISH。
 */

const termOptions = ref<{ label: string; value: string }[]>([]);
const publishing = ref(false);
const form = reactive<{ termId: string; effectiveWeek: number }>({
  termId: "",
  effectiveWeek: 1,
});

const lastResult = ref<{
  versionId: string;
  version: number;
  effectiveWeek: number;
  status: string;
} | null>(null);

async function loadTerms() {
  try {
    termOptions.value = await listDictItems("DICT-TERM");
  } catch {
    termOptions.value = [];
  }
}

async function publish() {
  if (!form.termId) {
    ElMessage.warning("请选择学期");
    return;
  }
  publishing.value = true;
  try {
    const res = await publishSchedule({
      termId: form.termId,
      effectiveWeek: form.effectiveWeek,
    });
    lastResult.value = res;
    ElMessage.success("课表发布成功");
  } catch {
    ElMessage.error("发布失败");
  } finally {
    publishing.value = false;
  }
}

onMounted(loadTerms);
</script>

<template>
  <div class="sp sa-page">
    <header class="sp__hd">
      <h2 class="sp__title">课表发布与版本</h2>
      <p class="sp__sub">选择学期与生效周，发布当前编排的课表版本</p>
    </header>

    <section class="sa-card sp__panel">
      <h3 class="sp__panel-title">发布课表</h3>
      <el-form label-position="top" @submit.prevent>
        <div class="sp__row">
          <el-form-item label="学期" required class="sp__col">
            <el-select v-model="form.termId" placeholder="选择学期" style="width: 100%">
              <el-option v-for="t in termOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="生效周" required class="sp__col">
            <el-select v-model="form.effectiveWeek" style="width: 100%">
              <el-option v-for="w in 30" :key="w" :label="`第 ${w} 周`" :value="w" />
            </el-select>
          </el-form-item>
          <div class="sp__col sp__col--btn">
            <el-button
              v-perms="'BTN-SCH-PUBLISH'"
              type="primary"
              size="large"
              :loading="publishing"
              @click="publish"
            >
              发布
            </el-button>
          </div>
        </div>
      </el-form>
    </section>

    <section class="sa-card sp__panel">
      <h3 class="sp__panel-title">发布结果</h3>
      <el-descriptions v-if="lastResult" :column="2" border size="small">
        <el-descriptions-item label="版本号">{{ lastResult.version }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ lastResult.status }}</el-descriptions-item>
        <el-descriptions-item label="生效周">第 {{ lastResult.effectiveWeek }} 周</el-descriptions-item>
        <el-descriptions-item label="版本ID">{{ lastResult.versionId }}</el-descriptions-item>
      </el-descriptions>
      <div v-else class="sp__empty">尚未发布；版本历史列表接口待后端提供</div>
    </section>
  </div>
</template>

<style scoped>
.sp__hd {
  margin-bottom: var(--sa-space-5);
}
.sp__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.sp__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.sp__panel {
  padding: var(--sa-pad-card);
  margin-bottom: var(--sa-space-4);
}
.sp__panel-title {
  margin: 0 0 var(--sa-space-4);
  font-size: var(--sa-fs-16);
  font-weight: var(--sa-fw-medium);
}
.sp__row {
  display: flex;
  gap: var(--sa-space-3);
  align-items: flex-start;
}
.sp__col {
  flex: 1;
  max-width: 320px;
}
.sp__col--btn {
  display: flex;
  align-items: flex-end;
  padding-bottom: var(--sa-space-5);
}
.sp__empty {
  padding: var(--sa-space-6) 0;
  text-align: center;
  color: var(--sa-text-3);
  font-size: var(--sa-fs-13);
}
</style>
