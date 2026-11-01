<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Refresh } from "@element-plus/icons-vue";
import {
  listDepts,
  createDept,
  updateDept,
  deleteDept,
  type SysDeptNode,
  type DeptType
} from "@/api/system";

/**
 * 系统部门管理（M0504 /system/depts）
 * 组织树维护：学校/年级/班级/学科组/办公室。
 * 按钮权限：BTN-SYS-DEPT-ADD / EDIT / DEL；删除有子部门或已被引用时服务端 409 保护。
 */

const loading = ref(false);
const tree = ref<SysDeptNode[]>([]);

const DEPT_TYPE: { label: string; value: DeptType }[] = [
  { label: "学校", value: "SCHOOL" },
  { label: "年级", value: "GRADE" },
  { label: "班级", value: "CLASS" },
  { label: "学科组", value: "SUBJECT" },
  { label: "办公室", value: "OFFICE" }
];

async function load() {
  loading.value = true;
  try {
    tree.value = await listDepts();
  } catch (e) {
    ElMessage.error("加载部门树失败");
  } finally {
    loading.value = false;
  }
}
onMounted(load);

// ---------- 新增 / 编辑 ----------
const dialog = reactive({
  visible: false,
  mode: "create" as "create" | "edit",
  saving: false,
  editId: ""
});
const formRef = ref();
const form = reactive<{
  name: string;
  deptType: DeptType;
  parentId?: string;
  sort?: number;
}>({ name: "", deptType: "SCHOOL", parentId: undefined, sort: 0 });

function openCreate(parent?: SysDeptNode) {
  dialog.mode = "create";
  dialog.editId = "";
  Object.assign(form, { name: "", deptType: "SCHOOL", parentId: parent?.id, sort: 0 });
  dialog.visible = true;
}

function openEdit(row: SysDeptNode) {
  dialog.mode = "edit";
  dialog.editId = row.id;
  Object.assign(form, {
    name: row.name,
    deptType: row.deptType,
    parentId: row.parentId,
    sort: row.sort ?? 0
  });
  dialog.visible = true;
}

async function submit() {
  await formRef.value?.validate();
  dialog.saving = true;
  const payload = {
    name: form.name,
    deptType: form.deptType,
    parentId: form.parentId || undefined,
    sort: form.sort
  };
  try {
    if (dialog.mode === "create") {
      await createDept(payload);
      ElMessage.success("已新增部门");
    } else {
      await updateDept(dialog.editId, payload);
      ElMessage.success("已保存");
    }
    dialog.visible = false;
    load();
  } catch (e) {
    ElMessage.error("保存失败");
  } finally {
    dialog.saving = false;
  }
}

async function remove(row: SysDeptNode) {
  try {
    await ElMessageBox.confirm(`确认删除部门「${row.name}」？`, "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  try {
    await deleteDept(row.id);
    ElMessage.success("已删除");
    load();
  } catch (e) {
    ElMessage.error("删除失败（存在子部门或已被引用）");
  }
}
</script>

<template>
  <div class="sys sa-page">
    <header class="sys__hd">
      <div>
        <h2 class="sys__title">部门管理</h2>
        <p class="sys__sub">学校组织树（学校/年级/班级/学科组/办公室）</p>
      </div>
      <div class="sys__tools">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
        <el-button v-perms="'BTN-SYS-DEPT-ADD'" type="primary" @click="openCreate()">
          <el-icon><Plus /></el-icon>&nbsp;新增部门
        </el-button>
      </div>
    </header>

    <section class="sa-card sys__card">
      <el-table
        v-loading="loading"
        :data="tree"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="name" label="部门名称" min-width="180" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">
              {{ DEPT_TYPE.find(t => t.value === row.deptType)?.label ?? row.deptType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="220">
          <template #default="{ row }">{{ row.path || "—" }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-perms="'BTN-SYS-DEPT-ADD'" link type="primary" @click="openCreate(row)">
              加子部门
            </el-button>
            <el-button v-perms="'BTN-SYS-DEPT-EDIT'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-perms="'BTN-SYS-DEPT-DEL'" link type="danger" @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'create' ? '新增部门' : '编辑部门'"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="部门名称" required>
          <el-input v-model="form.name" placeholder="如 高一（1）班" />
        </el-form-item>
        <el-form-item label="部门类型">
          <el-select v-model="form.deptType" style="width: 100%">
            <el-option v-for="t in DEPT_TYPE" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="tree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            check-strictly
            clearable
            placeholder="留空为顶级"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
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
.sys__hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sa-space-4);
  margin-bottom: var(--sa-space-5);
}
.sys__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.sys__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.sys__tools {
  display: flex;
  gap: var(--sa-space-2);
}
.sys__card {
  padding: var(--sa-space-4);
}
</style>
