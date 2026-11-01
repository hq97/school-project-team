<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Refresh, Search } from "@element-plus/icons-vue";
import {
  listDicts,
  createDict,
  updateDict,
  deleteDict,
  type SysDict
} from "@/api/system";

/**
 * 数据字典管理（M0505 /system/dicts）
 * 平台级字典（枚举中文名唯一来源，DICT-* 前缀）。
 * 按钮权限：BTN-DICT-EDIT（新增/编辑/删除共用）。
 */

const loading = ref(false);
const list = ref<SysDict[]>([]);
const keyword = ref("");

async function load() {
  loading.value = true;
  try {
    list.value = await listDicts(keyword.value || undefined);
  } catch (e) {
    ElMessage.error("加载字典失败");
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
  dictCode: string;
  itemCode: string;
  itemName: string;
  sort?: number;
  enabled: boolean;
  isCurrent: boolean;
}>({ dictCode: "", itemCode: "", itemName: "", sort: 0, enabled: true, isCurrent: false });

function openCreate() {
  dialog.mode = "create";
  dialog.editId = "";
  Object.assign(form, {
    dictCode: keyword.value || "",
    itemCode: "",
    itemName: "",
    sort: 0,
    enabled: true,
    isCurrent: false
  });
  dialog.visible = true;
}

function openEdit(row: SysDict) {
  dialog.mode = "edit";
  dialog.editId = row.id;
  Object.assign(form, {
    dictCode: row.dictCode,
    itemCode: row.itemCode,
    itemName: row.itemName,
    sort: row.sort ?? 0,
    enabled: row.enabled,
    isCurrent: row.isCurrent
  });
  dialog.visible = true;
}

async function submit() {
  await formRef.value?.validate();
  dialog.saving = true;
  try {
    if (dialog.mode === "create") {
      await createDict({ ...form });
      ElMessage.success("已新增字典项");
    } else {
      await updateDict(dialog.editId, {
        itemName: form.itemName,
        sort: form.sort,
        enabled: form.enabled,
        isCurrent: form.isCurrent
      });
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

async function remove(row: SysDict) {
  try {
    await ElMessageBox.confirm(`确认删除字典项「${row.itemName}」？`, "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  try {
    await deleteDict(row.id);
    ElMessage.success("已删除");
    load();
  } catch (e) {
    ElMessage.error("删除失败");
  }
}
</script>

<template>
  <div class="sys sa-page">
    <header class="sys__hd">
      <div>
        <h2 class="sys__title">数据字典管理</h2>
        <p class="sys__sub">枚举中文名的唯一来源（DICT-*），禁止在库内与 API 写中文字面量</p>
      </div>
      <div class="sys__tools">
        <el-input
          v-model="keyword"
          placeholder="字典编码，如 DICT-FAULT-TYPE"
          clearable
          style="width: 240px"
          @keyup.enter="load"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>&nbsp;查询
        </el-button>
        <el-button v-perms="'BTN-DICT-EDIT'" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>&nbsp;新增字典项
        </el-button>
      </div>
    </header>

    <section class="sa-card sys__card">
      <el-table v-loading="loading" :data="list" row-key="id">
        <el-table-column prop="dictCode" label="字典编码" min-width="180" />
        <el-table-column prop="itemCode" label="项编码" min-width="140" />
        <el-table-column prop="itemName" label="项名称" min-width="140" />
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? "启用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前" width="80">
          <template #default="{ row }">
            {{ row.isCurrent ? "是" : "否" }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-perms="'BTN-DICT-EDIT'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-perms="'BTN-DICT-EDIT'" link type="danger" @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'create' ? '新增字典项' : '编辑字典项'"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="字典编码" required>
          <el-input
            v-model="form.dictCode"
            :disabled="dialog.mode === 'edit'"
            placeholder="如 DICT-FAULT-TYPE"
          />
        </el-form-item>
        <el-form-item label="项编码" required>
          <el-input
            v-model="form.itemCode"
            :disabled="dialog.mode === 'edit'"
            placeholder="英文码，如 ELECTRICAL"
          />
        </el-form-item>
        <el-form-item label="项名称" required>
          <el-input v-model="form.itemName" placeholder="中文名" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="当前项">
          <el-switch v-model="form.isCurrent" />
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
  flex-wrap: wrap;
  justify-content: flex-end;
}
.sys__card {
  padding: var(--sa-space-4);
}
</style>
