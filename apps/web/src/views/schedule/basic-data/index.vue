<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Refresh } from "@element-plus/icons-vue";
import {
  listTeacherCourses,
  createTeacherCourse,
  updateTeacherCourse,
  deleteTeacherCourse,
  listDictItems,
} from "@/api/biz";
import { pageUsers, listDepts, type SysUser, type SysDeptNode } from "@/api/system";
import type { TeacherCourse } from "@/api/types";

/**
 * 排课基础数据 M0601 /schedule/basic-data：教师任课表 CRUD。
 * 按钮权限：BTN-SCH-BASIC-EDIT（新增/编辑/删除共用）。
 */

const SUBJECTS = [
  "语文", "数学", "英语", "物理", "化学", "生物",
  "历史", "地理", "道德与法治", "体育", "音乐", "美术", "信息技术",
];

const loading = ref(false);
const list = ref<TeacherCourse[]>([]);
const termFilter = ref("");

const teacherOptions = ref<{ label: string; value: string }[]>([]);
const teacherMap = ref<Record<string, string>>({});
const classOptions = ref<{ label: string; value: string }[]>([]);
const classMap = ref<Record<string, string>>({});
const termOptions = ref<{ label: string; value: string }[]>([]);
const termMap = ref<Record<string, string>>({});

const teacherName = (id?: string) => (id ? (teacherMap.value[id] ?? id) : "—");
const className = (id?: string) => (id ? (classMap.value[id] ?? id) : "—");
const termName = (id?: string) => (id ? (termMap.value[id] ?? id) : "—");

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

async function load() {
  loading.value = true;
  try {
    list.value = await listTeacherCourses(termFilter.value || undefined);
  } catch {
    ElMessage.error("加载任课表失败");
  } finally {
    loading.value = false;
  }
}

async function loadRefs() {
  try {
    const res = await pageUsers({ roleId: "R-TEACHER", page: 1, pageSize: 500 });
    const users: SysUser[] = res.list ?? [];
    teacherOptions.value = users.map((u) => ({ label: `${u.realName}（${u.username}）`, value: u.id }));
    teacherMap.value = Object.fromEntries(users.map((u) => [u.id, u.realName]));
  } catch {
    /* 回退显示 teacherId */
  }
  try {
    const depts = await listDepts();
    const classes = flattenDepts(depts).filter((d) => d.deptType === "CLASS");
    classOptions.value = classes.map((d) => ({ label: d.name, value: d.id }));
    classMap.value = Object.fromEntries(classes.map((d) => [d.id, d.name]));
  } catch {
    /* 回退显示 classId */
  }
  try {
    const terms = await listDictItems("DICT-TERM");
    termOptions.value = terms;
    termMap.value = Object.fromEntries(terms.map((t) => [t.value, t.label]));
  } catch {
    /* 回退显示 termId */
  }
}

onMounted(() => {
  load();
  loadRefs();
});

// ---------- 新增 / 编辑 ----------
const dialog = reactive({
  visible: false,
  mode: "create" as "create" | "edit",
  saving: false,
  editId: "",
});
const form = reactive<{
  teacherId: string;
  classId: string;
  subject: string;
  termId: string;
}>({ teacherId: "", classId: "", subject: "", termId: "" });

function openCreate() {
  dialog.mode = "create";
  dialog.editId = "";
  Object.assign(form, {
    teacherId: "",
    classId: "",
    subject: "",
    termId: termFilter.value || "",
  });
  dialog.visible = true;
}

function openEdit(row: TeacherCourse) {
  dialog.mode = "edit";
  dialog.editId = row.id;
  Object.assign(form, {
    teacherId: row.teacherId,
    classId: row.classId,
    subject: row.subject,
    termId: row.termId,
  });
  dialog.visible = true;
}

async function submit() {
  if (!form.teacherId || !form.classId || !form.subject || !form.termId) {
    ElMessage.warning("请填写教师、班级、学科与学期");
    return;
  }
  dialog.saving = true;
  try {
    if (dialog.mode === "create") {
      await createTeacherCourse({ ...form });
      ElMessage.success("已新增任课");
    } else {
      await updateTeacherCourse(dialog.editId, { ...form });
      ElMessage.success("已保存");
    }
    dialog.visible = false;
    load();
  } catch {
    ElMessage.error("保存失败");
  } finally {
    dialog.saving = false;
  }
}

async function remove(row: TeacherCourse) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${teacherName(row.teacherId)} - ${className(row.classId)} - ${row.subject}」这条任课？`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  try {
    await deleteTeacherCourse(row.id);
    ElMessage.success("已删除");
    load();
  } catch {
    ElMessage.error("删除失败");
  }
}
</script>

<template>
  <div class="sb sa-page">
    <header class="sb__hd">
      <div>
        <h2 class="sb__title">排课基础数据</h2>
        <p class="sb__sub">维护教师任课表（教师 / 班级 / 学科 / 学期）</p>
      </div>
      <div class="sb__tools">
        <el-select
          v-model="termFilter"
          placeholder="全部学期"
          clearable
          style="width: 180px"
          @change="load"
        >
          <el-option v-for="t in termOptions" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
        <el-button v-perms="'BTN-SCH-BASIC-EDIT'" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>&nbsp;新增任课
        </el-button>
      </div>
    </header>

    <section class="sa-card sb__card">
      <el-table v-loading="loading" :data="list" row-key="id" empty-text="暂无任课数据">
        <el-table-column label="教师" min-width="130">
          <template #default="{ row }">{{ teacherName(row.teacherId) }}</template>
        </el-table-column>
        <el-table-column label="班级" min-width="130">
          <template #default="{ row }">{{ className(row.classId) }}</template>
        </el-table-column>
        <el-table-column prop="subject" label="学科" min-width="120" />
        <el-table-column label="学期" min-width="160">
          <template #default="{ row }">{{ termName(row.termId) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-perms="'BTN-SCH-BASIC-EDIT'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-perms="'BTN-SCH-BASIC-EDIT'" link type="danger" @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'create' ? '新增任课' : '编辑任课'"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="70px">
        <el-form-item label="教师" required>
          <el-select v-model="form.teacherId" filterable placeholder="选择教师" style="width: 100%">
            <el-option v-for="o in teacherOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级" required>
          <el-select v-model="form.classId" filterable placeholder="选择班级" style="width: 100%">
            <el-option v-for="o in classOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="学科" required>
          <el-select v-model="form.subject" filterable placeholder="选择学科" style="width: 100%">
            <el-option v-for="s in SUBJECTS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期" required>
          <el-select v-model="form.termId" placeholder="选择学期" style="width: 100%">
            <el-option v-for="t in termOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sb__hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sa-space-4);
  margin-bottom: var(--sa-space-5);
}
.sb__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.sb__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.sb__tools {
  display: flex;
  gap: var(--sa-space-2);
  flex-wrap: wrap;
  justify-content: flex-end;
}
.sb__card {
  padding: var(--sa-space-4);
}
</style>
