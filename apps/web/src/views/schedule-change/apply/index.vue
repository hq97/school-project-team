<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { applyScheduleChange, listMyTimetable, listScheduleSpaces } from "@/api/biz";
import type { SpaceItem, TimetableEntry } from "@/api/types";

/**
 * 调课 / 代课申请 M0801 /schedule-change/apply：≤3 步
 * ①选「我的课」→ ②选类型 + 目标时段/代课教师 → ③提交。
 * 左半展示审批流提示（FT-SCHEDULE-CHANGE 走教务主任审批，流程由流程模板管理配置）。
 * 权限：提交按钮 BTN-SCH-CHANGE-APPLY。
 */

const TYPE_OPTIONS = [
  { label: "调课（换到其它时段）", value: "CHANGE" },
  { label: "代课（指定代课教师）", value: "SUBSTITUTE" },
  { label: "请假代课", value: "LEAVE_SUBSTITUTE" },
] as const;

const DAY_OPTIONS = [
  { label: "周一", value: 1 },
  { label: "周二", value: 2 },
  { label: "周三", value: 3 },
  { label: "周四", value: 4 },
  { label: "周五", value: 5 },
];
const PERIOD_OPTIONS = Array.from({ length: 10 }, (_, i) => ({
  label: `第 ${i + 1} 节`,
  value: i + 1,
}));

const week = ref(1);
const myEntries = ref<TimetableEntry[]>([]);
const spaceMap = ref<Record<string, string>>({});
const loadingEntries = ref(false);
const submitting = ref(false);

const form = reactive<{
  entryId: string;
  type: "CHANGE" | "SUBSTITUTE" | "LEAVE_SUBSTITUTE";
  targetDayOfWeek: number;
  targetPeriod: number;
  substituteTeacherId: string;
  reason: string;
}>({
  entryId: "",
  type: "CHANGE",
  targetDayOfWeek: 1,
  targetPeriod: 1,
  substituteTeacherId: "",
  reason: "",
});

const spaceName = (id?: string) => (id ? (spaceMap.value[id] ?? id) : "");
const entryLabel = (e: TimetableEntry) =>
  `${e.subject} · 周${e.dayOfWeek} 第${e.period}节 · ${spaceName(e.spaceId)}`;

async function loadEntries() {
  loadingEntries.value = true;
  try {
    myEntries.value = await listMyTimetable(week.value);
  } catch {
    myEntries.value = [];
  } finally {
    loadingEntries.value = false;
  }
}

async function loadSpaces() {
  try {
    const spaces: SpaceItem[] = await listScheduleSpaces();
    spaceMap.value = Object.fromEntries(spaces.map((s) => [s.id, s.spaceName]));
  } catch {
    /* 回退显示 spaceId */
  }
}

async function submit() {
  if (!form.entryId) {
    ElMessage.warning("请选择要调整的课");
    return;
  }
  if (form.type === "CHANGE" && (!form.targetDayOfWeek || !form.targetPeriod)) {
    ElMessage.warning("请选择目标时段");
    return;
  }
  if (form.type !== "CHANGE" && !form.substituteTeacherId.trim()) {
    ElMessage.warning("请填写代课教师");
    return;
  }
  if (!form.reason.trim()) {
    ElMessage.warning("请填写调整原因");
    return;
  }
  submitting.value = true;
  try {
    const res = await applyScheduleChange({
      type: form.type,
      entryId: form.entryId,
      targetDayOfWeek: form.type === "CHANGE" ? form.targetDayOfWeek : undefined,
      targetPeriod: form.type === "CHANGE" ? form.targetPeriod : undefined,
      substituteTeacherId: form.type !== "CHANGE" ? form.substituteTeacherId.trim() : undefined,
      reason: form.reason.trim(),
    });
    ElMessage.success(`已提交，单号 ${res.applyNo}，可在「我的调课/代课」跟踪进度`);
    form.entryId = "";
    form.type = "CHANGE";
    form.targetDayOfWeek = 1;
    form.targetPeriod = 1;
    form.substituteTeacherId = "";
    form.reason = "";
  } catch {
    // 40301~40304 由 axios 全局拦截器统一提示，这里不再重复弹窗
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  loadEntries();
  loadSpaces();
});
</script>

<template>
  <div class="sc sa-page">
    <header class="sc__hd">
      <h2 class="sc__title">调课 / 代课申请</h2>
      <p class="sc__sub">3 步：选我的课 → 选类型与目标 → 提交 · 由教务主任审批</p>
    </header>

    <div class="sc__grid">
      <!-- 左半：审批流程提示 -->
      <section class="sa-card sc__panel">
        <h3 class="sc__panel-title">审批流程</h3>
        <el-steps direction="vertical" :active="1" finish-status="success" class="sc__flow">
          <el-step title="提交申请" description="教师提交调课/代课申请" />
          <el-step title="教务主任审批" description="FT-SCHEDULE-CHANGE 流程模板" />
          <el-step title="完成" description="审批通过后课表生效" />
        </el-steps>
        <p class="sc__hint">审批流由「流程模板管理」统一配置，业务标识 FT-SCHEDULE-CHANGE</p>
      </section>

      <!-- 右半：申请表单 -->
      <section class="sa-card sc__panel">
        <h3 class="sc__panel-title">填写申请信息</h3>
        <el-form label-position="top" @submit.prevent>
          <div class="sc__row">
            <el-form-item label="周次" class="sc__week">
              <el-select v-model="week" style="width: 100%" @change="loadEntries">
                <el-option v-for="w in 30" :key="w" :label="`第 ${w} 周`" :value="w" />
              </el-select>
            </el-form-item>
            <el-form-item label="我的课" required class="sc__entry">
              <el-select
                v-model="form.entryId"
                placeholder="选择要调整的课"
                filterable
                :loading="loadingEntries"
                style="width: 100%"
              >
                <el-option v-for="e in myEntries" :key="e.id" :label="entryLabel(e)" :value="e.id" />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item label="类型" required>
            <el-radio-group v-model="form.type">
              <el-radio-button v-for="o in TYPE_OPTIONS" :key="o.value" :value="o.value">
                {{ o.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>

          <div v-if="form.type === 'CHANGE'" class="sc__row">
            <el-form-item label="目标星期" required class="sc__half">
              <el-select v-model="form.targetDayOfWeek" style="width: 100%">
                <el-option v-for="o in DAY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标节次" required class="sc__half">
              <el-select v-model="form.targetPeriod" style="width: 100%">
                <el-option v-for="o in PERIOD_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item v-else label="代课教师" required>
            <el-input
              v-model="form.substituteTeacherId"
              placeholder="代课教师工号/ID（如 T1001）"
            />
          </el-form-item>

          <el-form-item label="调整原因" required>
            <el-input
              v-model="form.reason"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              placeholder="简要说明调整原因"
            />
          </el-form-item>

          <el-button
            v-perms="'BTN-SCH-CHANGE-APPLY'"
            type="primary"
            size="large"
            class="sc__submit"
            :loading="submitting"
            @click="submit"
          >
            提交申请
          </el-button>
        </el-form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.sc__hd {
  margin-bottom: var(--sa-space-5);
}
.sc__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.sc__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.sc__grid {
  display: flex;
  gap: var(--sa-gap-card);
  align-items: flex-start;
}
.sc__panel {
  padding: var(--sa-pad-card);
}
.sc__panel:first-child {
  flex: 0 0 36%;
}
.sc__panel:last-child {
  flex: 1;
}
.sc__panel-title {
  margin: 0 0 var(--sa-space-4);
  font-size: var(--sa-fs-16);
  font-weight: var(--sa-fw-medium);
}
.sc__flow {
  margin-bottom: var(--sa-space-3);
}
.sc__hint {
  margin: 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.sc__row {
  display: flex;
  gap: var(--sa-space-3);
}
.sc__week {
  width: 160px;
  flex-shrink: 0;
}
.sc__entry {
  flex: 1;
}
.sc__half {
  flex: 1;
}
.sc__submit {
  width: 100%;
}
@media (max-width: 992px) {
  .sc__grid {
    flex-direction: column;
  }
  .sc__panel:first-child,
  .sc__panel:last-child {
    flex: 1 1 auto;
    width: 100%;
  }
}
</style>
