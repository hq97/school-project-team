<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Refresh } from "@element-plus/icons-vue";
import type { ElTree } from "element-plus";
import {
  listRoles,
  createRole,
  updateRole,
  deleteRole,
  listMenus,
  assignRoleMenus,
  type SysRole,
  type SysMenuNode,
  type ScopeLevel
} from "@/api/system";

/**
 * 系统角色管理（M0502 /system/roles）
 * 按钮权限：BTN-SYS-ROLE-ADD / EDIT / DEL / PERM。
 * 数据范围三级（SELF/GRADE/ALL），预置角色 builtin 不可删（服务端 409 保护）。
 */

const loading = ref(false);
const list = ref<SysRole[]>([]);

const SCOPE_LEVEL: { label: string; value: ScopeLevel }[] = [
  { label: "本人（SELF）", value: "SELF" },
  { label: "本年级（GRADE）", value: "GRADE" },
  { label: "全校（ALL）", value: "ALL" }
];

async function load() {
  loading.value = true;
  try {
    list.value = await listRoles();
  } catch (e) {
    ElMessage.error("加载角色列表失败");
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
  dataScope: ScopeLevel;
  scopeComplete: boolean;
}>({ code: "", name: "", dataScope: "SELF", scopeComplete: false });

function openCreate() {
  dialog.mode = "create";
  dialog.editId = "";
  Object.assign(form, { code: "", name: "", dataScope: "SELF", scopeComplete: false });
  dialog.visible = true;
}

function openEdit(row: SysRole) {
  dialog.mode = "edit";
  dialog.editId = row.id;
  Object.assign(form, {
    code: row.code,
    name: row.name,
    dataScope: row.dataScope,
    scopeComplete: row.scopeComplete
  });
  dialog.visible = true;
}

async function submit() {
  await formRef.value?.validate();
  dialog.saving = true;
  try {
    if (dialog.mode === "create") {
      await createRole({ ...form });
      ElMessage.success("已新增角色");
    } else {
      await updateRole(dialog.editId, {
        name: form.name,
        dataScope: form.dataScope,
        scopeComplete: form.scopeComplete
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

// ---------- 删除 ----------
async function remove(row: SysRole) {
  if (row.builtin) {
    ElMessage.warning("预置角色不可删除");
    return;
  }
  try {
    await ElMessageBox.confirm(`确认删除角色「${row.name}」？`, "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  try {
    await deleteRole(row.id);
    ElMessage.success("已删除");
    load();
  } catch (e) {
    ElMessage.error("删除失败（可能仍被用户引用）");
  }
}

// ---------- 分配菜单 ----------
const menuTree = ref<SysMenuNode[]>([]);
const menuTreeRef = ref<InstanceType<typeof ElTree>>();
const permDialog = reactive({ visible: false, id: "", name: "", saving: false });

async function openPerm(row: SysRole) {
  permDialog.id = row.id;
  permDialog.name = row.name;
  if (menuTree.value.length === 0) {
    try {
      menuTree.value = await listMenus();
    } catch {
      ElMessage.error("加载菜单树失败");
      return;
    }
  }
  permDialog.visible = true;
  await nextTick();
  menuTreeRef.value?.setCheckedKeys([]);
}

async function submitPerm() {
  const menuIds = (menuTreeRef.value?.getCheckedKeys(false) ?? []) as string[];
  const halfChecked = (menuTreeRef.value?.getHalfCheckedKeys() ?? []) as string[];
  const all = [...new Set([...menuIds, ...halfChecked])];
  permDialog.saving = true;
  try {
    await assignRoleMenus(permDialog.id, all, []);
    ElMessage.success("菜单授权已更新");
    permDialog.visible = false;
  } catch (e) {
    ElMessage.error("授权失败");
  } finally {
    permDialog.saving = false;
  }
}
</script>

<template>
  <div class="sys sa-page">
    <header class="sys__hd">
      <div>
        <h2 class="sys__title">角色管理</h2>
        <p class="sys__sub">角色编码 + 三级数据范围，预置角色不可删除</p>
      </div>
      <div class="sys__tools">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
        <el-button v-perms="'BTN-SYS-ROLE-ADD'" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>&nbsp;新增角色
        </el-button>
      </div>
    </header>

    <section class="sa-card sys__card">
      <el-table v-loading="loading" :data="list" row-key="id">
        <el-table-column prop="code" label="角色编码" min-width="120" />
        <el-table-column prop="name" label="角色名" min-width="120" />
        <el-table-column label="数据范围" width="130">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.dataScope }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="范围完整" width="100">
          <template #default="{ row }">{{ row.scopeComplete ? "是" : "否" }}</template>
        </el-table-column>
        <el-table-column label="预置" width="80">
          <template #default="{ row }">
            <el-tag :type="row.builtin ? 'warning' : 'info'" size="small">
              {{ row.builtin ? "预置" : "自建" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-perms="'BTN-SYS-ROLE-EDIT'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-perms="'BTN-SYS-ROLE-PERM'" link type="primary" @click="openPerm(row)">
              分配菜单
            </el-button>
            <el-button
              v-perms="'BTN-SYS-ROLE-DEL'"
              link
              type="danger"
              :disabled="row.builtin"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'create' ? '新增角色' : '编辑角色'"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="角色编码" required>
          <el-input
            v-model="form.code"
            :disabled="dialog.mode === 'edit'"
            placeholder="如 R-CUSTOM-XXX"
          />
        </el-form-item>
        <el-form-item label="角色名" required>
          <el-input v-model="form.name" placeholder="角色显示名" />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="s in SCOPE_LEVEL" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="范围完整">
          <el-switch v-model="form.scopeComplete" />
          <span class="sys__hint">开启表示该范围下无需按组织对象再细分</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配菜单 -->
    <el-dialog
      v-model="permDialog.visible"
      :title="`分配菜单 - ${permDialog.name}`"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="勾选后提交将覆盖该角色当前菜单授权"
        style="margin-bottom: 12px"
      />
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        node-key="id"
        show-checkbox
        default-expand-all
        :props="{ label: 'name', children: 'children' }"
      />
      <template #footer>
        <el-button @click="permDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="permDialog.saving" @click="submitPerm">保存授权</el-button>
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
.sys__hint {
  margin-left: var(--sa-space-2);
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
</style>
