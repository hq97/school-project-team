<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Refresh, Search } from "@element-plus/icons-vue";
import {
  pageUsers,
  createUser,
  updateUser,
  deleteUser,
  resetUserPassword,
  listRoles,
  assignUserRoles,
  getUserRoles,
  type SysUser,
  type SysRole,
  type AccountStatus,
  type StudentStatus
} from "@/api/system";

/**
 * 系统用户管理（M0501 /system/users）
 * 按钮权限：BTN-SYS-USER-ADD / EDIT / DEL（v-perms 体验优化，服务端 @RequireButton 强校验）
 */

const loading = ref(false);
const list = ref<SysUser[]>([]);
const total = ref(0);
const query = reactive({
  keyword: "",
  status: "",
  roleId: "",
  studentId: "",
  page: 1,
  pageSize: 10
});

const ACCOUNT_STATUS: { label: string; value: AccountStatus }[] = [
  { label: "启用", value: "ACTIVE" },
  { label: "停用", value: "DISABLED" }
];
const STUDENT_STATUS: { label: string; value: StudentStatus }[] = [
  { label: "在读", value: "ENROLLED" },
  { label: "毕业", value: "GRADUATED" },
  { label: "转学", value: "TRANSFERRED" },
  { label: "离校", value: "LEFT" },
  { label: "休学", value: "SUSPENDED" }
];

async function load() {
  loading.value = true;
  try {
    const res = await pageUsers(query);
    list.value = res.list;
    total.value = res.total;
  } catch (e) {
    ElMessage.error("加载用户列表失败");
  } finally {
    loading.value = false;
  }
}
onMounted(() => {
  load();
  loadRoles();
});

async function loadRoles() {
  try {
    roles.value = await listRoles();
  } catch {
    // 角色加载失败不阻塞用户列表
  }
}

function search() {
  query.page = 1;
  load();
}

// ---------- 新增 / 编辑 ----------
const dialog = reactive({
  visible: false,
  mode: "create" as "create" | "edit",
  saving: false,
  editId: ""
});
const formRef = ref();
const form = reactive<{
  username: string;
  password: string;
  realName: string;
  accountStatus: AccountStatus;
  studentStatus?: StudentStatus;
  studentId?: string;
}>({
  username: "",
  password: "",
  realName: "",
  accountStatus: "ACTIVE",
  studentStatus: undefined,
  studentId: ""
});

function openCreate() {
  dialog.mode = "create";
  dialog.editId = "";
  Object.assign(form, {
    username: "",
    password: "",
    realName: "",
    accountStatus: "ACTIVE",
    studentStatus: undefined,
    studentId: ""
  });
  dialog.visible = true;
}

function openEdit(row: SysUser) {
  dialog.mode = "edit";
  dialog.editId = row.id;
  Object.assign(form, {
    username: row.username,
    password: "",
    realName: row.realName,
    accountStatus: row.accountStatus,
    studentStatus: row.studentStatus,
    studentId: row.studentId ?? ""
  });
  dialog.visible = true;
}

async function submit() {
  await formRef.value?.validate();
  dialog.saving = true;
  try {
    if (dialog.mode === "create") {
      await createUser({ ...form });
      ElMessage.success("已新增用户");
    } else {
      await updateUser(dialog.editId, {
        realName: form.realName,
        accountStatus: form.accountStatus,
        studentStatus: form.studentStatus,
        studentId: form.studentId
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
async function remove(row: SysUser) {
  try {
    await ElMessageBox.confirm(
      `确认删除用户「${row.realName}（${row.username}）」？`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  try {
    await deleteUser(row.id);
    ElMessage.success("已删除");
    load();
  } catch (e) {
    ElMessage.error("删除失败");
  }
}

// ---------- 重置密码 ----------
const pwdDialog = reactive({ visible: false, id: "", pwd: "", saving: false });
function openReset(row: SysUser) {
  pwdDialog.id = row.id;
  pwdDialog.pwd = "";
  pwdDialog.visible = true;
}
async function submitReset() {
  if (!pwdDialog.pwd) {
    ElMessage.warning("请输入新密码");
    return;
  }
  pwdDialog.saving = true;
  try {
    await resetUserPassword(pwdDialog.id, pwdDialog.pwd);
    ElMessage.success("密码已重置");
    pwdDialog.visible = false;
  } catch (e) {
    ElMessage.error("重置失败");
  } finally {
    pwdDialog.saving = false;
  }
}

// ---------- 分配角色 ----------
const roles = ref<SysRole[]>([]);
const roleDialog = reactive({ visible: false, id: "", checked: [] as string[], saving: false });
async function openRoles(row: SysUser) {
  roleDialog.id = row.id;
  roleDialog.checked = [];
  if (roles.value.length === 0) {
    try {
      roles.value = await listRoles();
    } catch {
      ElMessage.error("加载角色失败");
      return;
    }
  }
  roleDialog.visible = true;
  // 回显该用户已分配的角色（后端 GET /{id}/roles）
  try {
    roleDialog.checked = (await getUserRoles(row.id)) ?? [];
  } catch {
    roleDialog.checked = [];
  }
}
async function submitRoles() {
  roleDialog.saving = true;
  try {
    await assignUserRoles(roleDialog.id, roleDialog.checked);
    ElMessage.success("角色已分配");
    roleDialog.visible = false;
  } catch (e) {
    ElMessage.error("分配失败");
  } finally {
    roleDialog.saving = false;
  }
}
</script>

<template>
  <div class="sys sa-page">
    <header class="sys__hd">
      <div>
        <h2 class="sys__title">用户管理</h2>
        <p class="sys__sub">维护平台账号、启停状态与角色分配</p>
      </div>
      <div class="sys__tools">
        <el-input
          v-model="query.keyword"
          placeholder="搜索用户名 / 姓名"
          clearable
          style="width: 180px"
          @keyup.enter="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-input
          v-model="query.studentId"
          placeholder="学号"
          clearable
          style="width: 140px"
          @keyup.enter="search"
        />
        <el-select
          v-model="query.status"
          placeholder="状态"
          clearable
          style="width: 110px"
          @change="search"
        >
          <el-option v-for="s in ACCOUNT_STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-select
          v-model="query.roleId"
          placeholder="角色"
          clearable
          filterable
          style="width: 150px"
          @change="search"
        >
          <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
        <el-button :loading="loading" @click="search">
          <el-icon><Refresh /></el-icon>&nbsp;查询
        </el-button>
        <el-button v-perms="'BTN-SYS-USER-ADD'" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>&nbsp;新增用户
        </el-button>
      </div>
    </header>

    <section class="sa-card sys__card">
      <el-table v-loading="loading" :data="list" row-key="id">
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.accountStatus === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.accountStatus === "ACTIVE" ? "启用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="studentId" label="学号" min-width="100">
          <template #default="{ row }">{{ row.studentId || "—" }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <template v-if="row.roles && row.roles.length">
              <el-tag
                v-for="r in row.roles"
                :key="r.id"
                size="small"
                effect="plain"
                style="margin-right: 4px"
              >
                {{ r.name }}
              </el-tag>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-perms="'BTN-SYS-USER-EDIT'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-perms="'BTN-SYS-USER-EDIT'" link type="primary" @click="openRoles(row)">
              分配角色
            </el-button>
            <el-button v-perms="'BTN-SYS-USER-EDIT'" link type="primary" @click="openReset(row)">
              重置密码
            </el-button>
            <el-button v-perms="'BTN-SYS-USER-DEL'" link type="danger" @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="sys__pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </section>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'create' ? '新增用户' : '编辑用户'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="dialog.mode === 'edit'" placeholder="登录账号" />
        </el-form-item>
        <el-form-item v-if="dialog.mode === 'create'" label="密码" required>
          <el-input v-model="form.password" type="password" placeholder="初始密码" show-password />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.accountStatus">
            <el-radio v-for="s in ACCOUNT_STATUS" :key="s.value" :value="s.value">
              {{ s.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="学生状态">
          <el-select v-model="form.studentStatus" placeholder="非学生可不填" clearable style="width: 100%">
            <el-option v-for="s in STUDENT_STATUS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="学号">
          <el-input v-model="form.studentId" placeholder="学生选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdDialog.visible" title="重置密码" width="420px">
      <el-form label-width="80px">
        <el-form-item label="新密码" required>
          <el-input v-model="pwdDialog.pwd" type="password" show-password placeholder="输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="pwdDialog.saving" @click="submitReset">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色 -->
    <el-dialog v-model="roleDialog.visible" title="分配角色" width="420px">
      <el-checkbox-group v-model="roleDialog.checked">
        <el-checkbox v-for="r in roles" :key="r.id" :value="r.id">
          {{ r.name }}（{{ r.code }}）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="roleDialog.saving" @click="submitRoles">确定</el-button>
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
.sys__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sa-space-4);
}
</style>
