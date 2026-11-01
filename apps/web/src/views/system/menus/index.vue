<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Refresh } from "@element-plus/icons-vue";
import {
  listMenus,
  createMenu,
  updateMenu,
  deleteMenu,
  type SysMenuNode
} from "@/api/system";

/**
 * 系统菜单管理（M0503 /system/menus）
 * 菜单树维护：编码/名称/路由 path/组件 component/图标 icon/排序。
 * 按钮权限：BTN-SYS-MENU-ADD / EDIT / DEL；删除有子菜单或已被角色引用时服务端 409 保护。
 */

const loading = ref(false);
const tree = ref<SysMenuNode[]>([]);

async function load() {
  loading.value = true;
  try {
    tree.value = await listMenus();
  } catch (e) {
    ElMessage.error("加载菜单树失败");
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
  code: string;
  name: string;
  path: string;
  parentId?: string;
  sort?: number;
  component?: string;
  icon?: string;
  listLike: boolean;
}>({
  code: "",
  name: "",
  path: "",
  parentId: undefined,
  sort: 0,
  component: "",
  icon: "",
  listLike: false
});

function resetForm() {
  Object.assign(form, {
    code: "",
    name: "",
    path: "",
    parentId: undefined,
    sort: 0,
    component: "",
    icon: "",
    listLike: false
  });
}

function openCreate(parent?: SysMenuNode) {
  dialog.mode = "create";
  dialog.editId = "";
  resetForm();
  form.parentId = parent?.id;
  dialog.visible = true;
}

function openEdit(row: SysMenuNode) {
  dialog.mode = "edit";
  dialog.editId = row.id;
  Object.assign(form, {
    code: row.code,
    name: row.name,
    path: row.path ?? "",
    parentId: row.parentId,
    sort: row.sort ?? 0,
    component: row.component ?? "",
    icon: row.icon ?? "",
    listLike: row.listLike ?? false
  });
  dialog.visible = true;
}

async function submit() {
  await formRef.value?.validate();
  dialog.saving = true;
  const payload = {
    name: form.name,
    path: form.path || undefined,
    parentId: form.parentId || undefined,
    sort: form.sort,
    component: form.component || undefined,
    icon: form.icon || undefined,
    listLike: form.listLike
  };
  try {
    if (dialog.mode === "create") {
      await createMenu({ code: form.code, ...payload });
      ElMessage.success("已新增菜单");
    } else {
      await updateMenu(dialog.editId, payload);
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

async function remove(row: SysMenuNode) {
  try {
    await ElMessageBox.confirm(`确认删除菜单「${row.name}」？`, "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  try {
    await deleteMenu(row.id);
    ElMessage.success("已删除");
    load();
  } catch (e) {
    ElMessage.error("删除失败（存在子菜单或已被角色引用）");
  }
}
</script>

<template>
  <div class="sys sa-page">
    <header class="sys__hd">
      <div>
        <h2 class="sys__title">菜单管理</h2>
        <p class="sys__sub">维护系统菜单树，前端动态路由据此渲染</p>
      </div>
      <div class="sys__tools">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
        <el-button v-perms="'BTN-SYS-MENU-ADD'" type="primary" @click="openCreate()">
          <el-icon><Plus /></el-icon>&nbsp;新增菜单
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
        <el-table-column prop="name" label="菜单名称" min-width="180" />
        <el-table-column prop="code" label="编码" min-width="100" />
        <el-table-column prop="path" label="路由路径" min-width="180">
          <template #default="{ row }">{{ row.path || "—" }}</template>
        </el-table-column>
        <el-table-column prop="component" label="组件" min-width="180">
          <template #default="{ row }">{{ row.component || "—" }}</template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="130">
          <template #default="{ row }">{{ row.icon || "—" }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-perms="'BTN-SYS-MENU-ADD'" link type="primary" @click="openCreate(row)">
              加子菜单
            </el-button>
            <el-button v-perms="'BTN-SYS-MENU-EDIT'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-perms="'BTN-SYS-MENU-DEL'" link type="danger" @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'create' ? '新增菜单' : '编辑菜单'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="菜单编码" required>
          <el-input v-model="form.code" :disabled="dialog.mode === 'edit'" placeholder="如 M0501" />
        </el-form-item>
        <el-form-item label="菜单名称" required>
          <el-input v-model="form.name" placeholder="显示名" />
        </el-form-item>
        <el-form-item label="父级菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="tree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            check-strictly
            clearable
            placeholder="留空为顶级目录"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input v-model="form.path" placeholder="如 /system/users" />
        </el-form-item>
        <el-form-item label="组件路径">
          <el-input v-model="form.component" placeholder="叶子填相对路径，如 system/users/index" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="Element Plus 图标，如 ep:user" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="列表类">
          <el-switch v-model="form.listLike" />
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
