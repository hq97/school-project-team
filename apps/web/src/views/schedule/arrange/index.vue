<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import {
  createScheduleEntry,
  listScheduleSpaces,
  listSchoolTimetable,
  listDictItems,
} from "@/api/biz";
import { pageUsers, listDepts, type SysUser, type SysDeptNode } from "@/api/system";
import type { SpaceItem, TimetableEntry } from "@/api/types";

/**
 * 课表编排 M0602 /schedule/arrange：手动排课 + 当前编排结果展示。
 * 提交按钮 BTN-SCH-ARRANGE；全校课表展示 BTN-SCH-VIEW-ALL。
 * 409 冲突时展示后端返回的冲突详情，不吞错误。
 */

const SUBJECTS = [
  "语文", "数学", "英语", "物理", "化学", "生物",
  "历史", "地理", "道德与法治", "体育", "音乐", "美术", "信息技术",
];
const DAY_OPTIONS = [
  { label: "周一", value: 1 },
  { label: "周二", value: 2 },
  { label: "周三", value: 3 },
  { label: "周四", value: 4 },
  { label: "周五", value: 5 },
];
const PERIOD_OPTIONS = Array.from({ length: 10 }, (_, i) => ({ label: `第 ${i + 1} 节`, value: i + 1 }));
const DAY_LABEL = ["", "周一", "周二", "周三", "周四", "周五"];

const classOptions = ref<{ label: string; value: string }[]>([]);
const teacherOptions = ref<{ label: string; value: string }[]>([]);
const spaceOptions = ref<{ label: string; value: string }[]>([]);
const termOptions = ref<{ label: string; value: string }[]>([]);
const spaceMap = ref<Record<string, string>>({});
const submitting = ref(false);

const form = reactive<{
  classId: string;
  subject: string;
  teacherId: string;
  spaceId: string;
  termId: string;
  week: number;
  dayOfWeek: number;
  period: number;
}>({
  classId: "",
  subject: "",
  teacherId: "",
  spaceId: "",
  termId: "",
  week: 1,
  dayOfWeek: 1,
  period: 1,
});

function flattenDepts(nodes: SysDeptNode[]): SysDeptNode[] {
  const out: SysDeptNode[] = [];
  const walk = (list: SysDeptNode[]) => {
    for (const n of list) {
      out.push(n);
      if (n.children?.length) walk(n.children);
    }
  };
  walk(nodes);
  return out;
}

async function loadRefs() {
  try {
    const res = await pageUsers({ roleId: "R-TEACHER", page: 1, pageSize: 500 });
    const users: SysUser[] = res.list ?? [];
    teacherOptions.value = users.map((u) => ({ label: `${u.realName}（${u.username}）`, value: u.id }));
  } catch {
    /* 忽略，可仍用空下拉 */
  }
  try {
    const depts = await listDepts();
    const classes = flattenDepts(depts).filter((d) => d.deptType === "CLASS");
    classOptions.value = classes.map((d) => ({ label: d.name, value: d.id }));
  } catch {
    /* 忽略 */
  }
  try {
    const spaces: SpaceItem[] = await listScheduleSpaces();
    spaceOptions.value = spaces.map((s) => ({ label: s.spaceName, value: s.id }));
    spaceMap.value = Object.fromEntries(spaces.map((s) => [s.id, s.spaceName]));
  } catch {
    /* 忽略 */
  }
  try {
    termOptions.value = await listDictItems("DICT-TERM");
  } catch {
    /* 忽略 */
  }
}

/** 从错误对象中尽力抽取后端返回的冲突详情/消息 */
function conflictText(err: unknown): string {
  const anyErr = err as any;
  const body = anyErr?.response?.data ?? anyErr?.data ?? anyErr;
  const payload = body?.data ?? body;
  if (payload?.conflict) {
    const c = payload.conflict;
    if (typeof c === "string") return c;
    const parts = [c.slotText, c.spaceName, c.message, c.subject, c.teacherName].filter(Boolean);
    if (parts.length) return parts.join(" · ");
    try {
      return JSON.stringify(c);
    } catch {
      return String(c);
    }
  }
  if (payload?.message) return String(payload.message);
  return "";
}

async function submit() {
  if (!form.classId || !form.subject || !form.teacherId || !form.termId) {
    ElMessage.warning("请填写班级、学科、教师与学期");
    return;
  }
  submitting.value = true;
  try {
    await createScheduleEntry({
      classId: form.classId,
      subject: form.subject,
      teacherId: form.teacherId,
      spaceId: form.spaceId || undefined,
      termId: form.termId,
      week: form.week,
      dayOfWeek: form.dayOfWeek,
      period: form.period,
    });
    ElMessage.success("排课成功");
    form.dayOfWeek = 1;
    form.period = 1;
    loadTimetable();
  } catch (err) {
    const msg = conflictText(err);
    if (msg) ElMessage.error(msg);
    else ElMessage.error("排课失败");
  } finally {
    submitting.value = false;
  }
}

// ---------- 当前编排结果（全校课表，BTN-SCH-VIEW-ALL） ----------
const ttLoading = ref(false);
const ttWeek = ref(1);
const ttList = ref<TimetableEntry[]>([]);

async function loadTimetable() {
  ttLoading.value = true;
  try {
    ttList.value = await listSchoolTimetable(ttWeek.value);
  } catch {
    ttList.value = [];
  } finally {
    ttLoading.value = false;
  }
}

onMounted(() => {
  loadRefs();
  loadTimetable();
});
</script>

<template>
  <div class="sa sa-page">
    <header class="sa__hd">
      <div>
        <h2 class="sa__title">课表编排</h2>
        <p class="sa__sub">手动排课（场地可选）；下方展示当前编排结果</p>
      </div>
    </header>

    <section class="sa-card sa__panel">
      <h3 class="sa__panel-title">新增排课</h3>
      <el-form label-position="top" @submit.prevent>
        <div class="sa__row">
          <el-form-item label="班级" required class="sa__col">
            <el-select v-model="form.classId" filterable placeholder="选择班级" style="width: 100%">
              <el-option v-for="o in classOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="学科" required class="sa__col">
            <el-select v-model="form.subject" filterable placeholder="选择学科" style="width: 100%">
              <el-option v-for="s in SUBJECTS" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item label="教师" required class="sa__col">
            <el-select v-model="form.teacherId" filterable placeholder="选择教师" style="width: 100%">
              <el-option v-for="o in teacherOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </div>
        <div class="sa__row">
          <el-form-item label="场地（可选）" class="sa__col">
            <el-select v-model="form.spaceId" filterable clearable placeholder="选择场地" style="width: 100%">
              <el-option v-for="o in spaceOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="学期" required class="sa__col">
            <el-select v-model="form.termId" placeholder="选择学期" style="width: 100%">
              <el-option v-for="t in termOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="周次" required class="sa__col">
            <el-select v-model="form.week" style="width: 100%">
              <el-option v-for="w in 30" :key="w" :label="`第 ${w} 周`" :value="w" />
            </el-select>
          </el-form-item>
        </div>
        <div class="sa__row">
          <el-form-item label="星期" required class="sa__col">
            <el-select v-model="form.dayOfWeek" style="width: 100%">
              <el-option v-for="o in DAY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="节次" required class="sa__col">
            <el-select v-model="form.period" style="width: 100%">
              <el-option v-for="o in PERIOD_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <div class="sa__col sa__col--btn">
            <el-button
              v-perms="'BTN-SCH-ARRANGE'"
              type="primary"
              size="large"
              :loading="submitting"
              @click="submit"
            >
              提交排课
            </el-button>
          </div>
        </div>
      </el-form>
    </section>

    <section v-perms="'BTN-SCH-VIEW-ALL'" class="sa-card sa__panel">
      <div class="sa__tt-head">
        <h3 class="sa__panel-title">当前编排结果</h3>
        <div class="sa__tt-tools">
          <el-select v-model="ttWeek" style="width: 140px" @change="loadTimetable">
            <el-option v-for="w in 30" :key="w" :label="`第 ${w} 周`" :value="w" />
          </el-select>
          <el-button :loading="ttLoading" @click="loadTimetable">
            <el-icon><Refresh /></el-icon>&nbsp;刷新
          </el-button>
        </div>
      </div>
      <el-table v-loading="ttLoading" :data="ttList" row-key="id" empty-text="暂无编排数据">
        <el-table-column label="星期" width="80">
          <template #default="{ row }">{{ DAY_LABEL[row.dayOfWeek] ?? row.dayOfWeek }}</template>
        </el-table-column>
        <el-table-column label="节次" width="80">
          <template #default="{ row }">第{{ row.period }}节</template>
        </el-table-column>
        <el-table-column prop="subject" label="学科" min-width="100" />
        <el-table-column prop="classId" label="班级" min-width="120" />
        <el-table-column prop="teacherId" label="教师" min-width="120" />
        <el-table-column label="场地" min-width="120">
          <template #default="{ row }">{{ spaceMap[row.spaceId] ?? row.spaceId ?? "—" }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.sa__hd {
  margin-bottom: var(--sa-space-5);
}
.sa__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.sa__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.sa__panel {
  padding: var(--sa-pad-card);
  margin-bottom: var(--sa-space-4);
}
.sa__panel-title {
  margin: 0 0 var(--sa-space-4);
  font-size: var(--sa-fs-16);
  font-weight: var(--sa-fw-medium);
}
.sa__row {
  display: flex;
  gap: var(--sa-space-3);
  align-items: flex-start;
}
.sa__col {
  flex: 1;
}
.sa__col--btn {
  display: flex;
  align-items: flex-end;
  padding-bottom: var(--sa-space-5);
}
.sa__tt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sa-space-3);
  margin-bottom: var(--sa-space-3);
}
.sa__tt-tools {
  display: flex;
  gap: var(--sa-space-2);
}
@media (max-width: 992px) {
  .sa__row {
    flex-direction: column;
  }
}
</style>
