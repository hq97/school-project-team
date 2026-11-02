<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Refresh, Delete, ArrowUp, ArrowDown } from "@element-plus/icons-vue";
import {
  listFlowTemplates,
  createFlowTemplate,
  updateFlowTemplate,
  deleteFlowTemplate,
  type FlowTemplate,
  type RejectPolicy
} from "@/api/flow";
import { listRoles, type SysRole } from "@/api/system";

/**
 * 流程模板管理（M0507 /system/flow-templates）
 * 按钮权限：BTN-FLOW-TPL-EDIT（增删改统一按钮码）。
 * 节点固定线性（nodeType=LINEAR），列表顺序即流转顺序；审批角色取自现有角色码。
 */

const loading = ref(false);
const list = ref<FlowTemplate[]>([]);
const roles = ref<SysRole[]>([]);

const REJECT_POLICY: { label: string; value: RejectPolicy }[] = [
  { label: "退回发起人（RETURN）", value: "RETURN" },
  { label: "直接终止（TERMINATE）", value: "TERMINATE" }
];

async function load() {
  loading.value = true;
  try {
    list.value = await listFlowTemplates();
  } catch (e) {
    ElMessage.error("加载流程模板失败");
  } finally {
    loading.value = false;
  }
}

async function ensureRoles() {
  if (roles.value.length > 0) return;
  try {
    roles.value = await listRoles();
  } catch {
    ElMessage.error("加载角色列表失败");
  }
}

onMounted(() => {
  load();
  ensureRoles();
});

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
  businessType: string;
  rejectPolicy: RejectPolicy;
  enabled: boolean;
}>({ code: "", name: "", businessType: "", rejectPolicy: "RETURN", enabled: true });

/** 节点编辑列表（有序） */
const nodes = ref<{ name: string; approveRole: string; timeoutHours?: number }[]>([]);

function openCreate() {
  dialog.mode = "create";
  dialog.editId = "";
  Object.assign(form, {
    code: "",
    name: "",
    businessType: "",
    rejectPolicy: "RETURN",
    enabled: true
  });
  nodes.value = [{ name: "", approveRole: "", timeoutHours: null }];
  dialog.visible = true;
  ensureRoles();
}

function openEdit(row: FlowTemplate) {
  dialog.mode = "edit";
  dialog.editId = row.id;
  Object.assign(form, {
    code: row.code,
    name: row.name,
    businessType: row.businessType,
    rejectPolicy: row.rejectPolicy,
    enabled: row.enabled
  });
  nodes.value = row.nodes
    .slice()
    .sort((a, b) => a.seq - b.seq)
    .map(n => ({
      name: n.name,
      approveRole: n.approveRole,
      timeoutHours: n.timeoutHours ?? undefined
    }));
  dialog.visible = true;
  ensureRoles();
}

function addNode() {
  nodes.value.push({ name: "", approveRole: "", timeoutHours: undefined });
}

function removeNode(i: number) {
  if (nodes.value.length <= 1) {
    ElMessage.warning("流程至少需要一个审批节点");
    return;
  }
  nodes.value.splice(i, 1);
}

function moveNode(i: number, dir: -1 | 1) {
  const j = i + dir;
  if (j < 0 || j >= nodes.value.length) return;
  const tmp = nodes.value[i];
  nodes.value[i] = nodes.value[j];
  nodes.value[j] = tmp;
}

async function submit() {
  if (!form.code || !form.name || !form.businessType) {
    ElMessage.warning("请填写模板编码、名称与业务类型");
    return;
  }
  for (const n of nodes.value) {
    if (!n.name || !n.approveRole) {
      ElMessage.warning("每个节点都需填写名称与审批角色");
      return;
    }
  }
  dialog.saving = true;
  try {
    if (dialog.mode === "create") {
      await createFlowTemplate({ ...form, nodes: nodes.value });
      ElMessage.success("已新增流程模板");
    } else {
      await updateFlowTemplate(dialog.editId, {
        name: form.name,
        rejectPolicy: form.rejectPolicy,
        enabled: form.enabled,
        nodes: nodes.value
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
async function remove(row: FlowTemplate) {
  try {
    await ElMessageBox.confirm(`确认删除流程模板「${row.name}」？`, "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  try {
    await deleteFlowTemplate(row.id);
    ElMessage.success("已删除");
    load();
  } catch (e) {
    ElMessage.error("删除失败");
  }
}

const STATUS_MAP: Record<string, { label: string; type: "success" | "info" | "danger" }> = {
  RETURN: { label: "退回发起人", type: "info" },
  TERMINATE: { label: "直接终止", type: "danger" }
};
</script>

<template>
  <div class="sys sa-page">
    <header class="sys__hd">
      <div>
        <h2 class="sys__title">流程模板管理</h2>
        <p class="sys__sub">固定线性流转，可配置多级审批节点与各节点审批角色</p>
      </div>
      <div class="sys__tools">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
        <el-button v-perms="'BTN-FLOW-TPL-EDIT'" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>&nbsp;新增模板
        </el-button>
      </div>
    </header>

    <section class="sa-card sys__card">
      <el-table v-loading="loading" :data="list" row-key="id">
        <el-table-column prop="code" label="模板编码" min-width="130" />
        <el-table-column prop="name" label="模板名称" min-width="140" />
        <el-table-column prop="businessType" label="业务类型" min-width="130" />
        <el-table-column label="节点数" width="80">
          <template #default="{ row }">{{ row.nodes?.length ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="驳回策略" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_MAP[row.rejectPolicy]?.type ?? 'info'">
              {{ STATUS_MAP[row.rejectPolicy]?.label ?? row.rejectPolicy }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? "启用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column label="审批链" min-width="240">
          <template #default="{ row }">
            <div v-if="row.nodes?.length" class="sys__chain">
              <template v-for="(n, i) in row.nodes" :key="n.id ?? i">
                <span class="sys__node">{{ n.name }}</span>
                <span v-if="i < row.nodes.length - 1" class="sys__arrow">→</span>
              </template>
            </div>
            <span v-else class="sys__empty">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-perms="'BTN-FLOW-TPL-EDIT'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button
              v-perms="'BTN-FLOW-TPL-EDIT'"
              link
              type="danger"
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
      :title="dialog.mode === 'create' ? '新增流程模板' : '编辑流程模板'"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模板编码" required>
              <el-input
                v-model="form.code"
                :disabled="dialog.mode === 'edit'"
                placeholder="如 PROCURE_APPROVE"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板名称" required>
              <el-input v-model="form.name" placeholder="流程显示名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="业务类型" required>
              <el-input
                v-model="form.businessType"
                :disabled="dialog.mode === 'edit'"
                placeholder="绑定业务标识，唯一"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="驳回策略">
              <el-select v-model="form.rejectPolicy" style="width: 100%">
                <el-option
                  v-for="p in REJECT_POLICY"
                  :key="p.value"
                  :label="p.label"
                  :value="p.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="dialog.mode === 'edit'" label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <el-divider content-position="left">审批节点（自上而下依次流转）</el-divider>
        <div class="sys__nodes">
          <div v-for="(n, i) in nodes" :key="i" class="sys__node-row">
            <span class="sys__seq">{{ i + 1 }}</span>
            <el-input v-model="n.name" placeholder="节点名称，如 部门审批" class="sys__node-name" />
            <el-select
              v-model="n.approveRole"
              placeholder="审批角色"
              class="sys__node-role"
              filterable
            >
              <el-option
                v-for="r in roles"
                :key="r.code"
                :label="`${r.name}（${r.code}）`"
                :value="r.code"
              />
            </el-select>
            <el-input-number
              v-model="n.timeoutHours"
              :min="1"
              :max="720"
              placeholder="超时h"
              class="sys__node-timeout"
              controls-position="right"
            />
            <div class="sys__node-ops">
              <el-button link @click="moveNode(i, -1)" :disabled="i === 0">
                <el-icon><Top /></el-icon>
              </el-button>
              <el-button link @click="moveNode(i, 1)" :disabled="i === nodes.length - 1">
                <el-icon><Bottom /></el-icon>
              </el-button>
              <el-button link type="danger" @click="removeNode(i)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <el-button link type="primary" @click="addNode">
            <el-icon><Plus /></el-icon>&nbsp;添加节点
          </el-button>
        </div>
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
.sys__chain {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sa-space-1);
}
.sys__node {
  padding: 1px 8px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-size: var(--sa-fs-12);
}
.sys__arrow {
  color: var(--sa-text-3);
  font-size: var(--sa-fs-12);
}
.sys__empty {
  color: var(--sa-text-3);
}
.sys__nodes {
  display: flex;
  flex-direction: column;
  gap: var(--sa-space-2);
}
.sys__node-row {
  display: flex;
  align-items: center;
  gap: var(--sa-space-2);
}
.sys__seq {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: var(--sa-fs-12);
  flex-shrink: 0;
}
.sys__node-name {
  width: 180px;
}
.sys__node-role {
  flex: 1;
}
.sys__node-timeout {
  width: 130px;
}
.sys__node-ops {
  display: flex;
  gap: 0;
  flex-shrink: 0;
}
</style>
