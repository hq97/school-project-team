<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Refresh, Delete, Top, Bottom } from "@element-plus/icons-vue";
import {
  listFaultTypes,
  createFaultType,
  updateFaultType,
  deleteFaultType,
  listConfigSpaces,
  createConfigSpace,
  updateConfigSpace,
  deleteConfigSpace,
  listRepairFlow,
  updateRepairFlow,
} from "@/api/biz";
import type { FaultTypeItem, SpaceConfig } from "@/api/types";
import { listRoles, type SysRole } from "@/api/system";

/**
 * 报修配置 M0405 /repair/config：统一管理故障类型 / 报修位置 / 审批流程。
 * 按钮权限：BTN-REP-CONFIG（增删改统一按钮码，v-perms 仅隐藏入口，服务端 40302 为边界）。
 * 后端并行开发中，前端按契约 {success:true, data} 实现。
 */

const tab = ref("fault");

/* ===================== 故障类型 ===================== */
const ftLoading = ref(false);
const ftList = ref<FaultTypeItem[]>([]);
const ftDialog = reactive({
  visible: false,
  mode: "create" as "create" | "edit",
  saving: false,
  editId: ""
});
const ftForm = reactive<{
  itemCode: string;
  itemName: string;
  sort: number;
  enabled: boolean;
}>({ itemCode: "", itemName: "", sort: 0, enabled: true });

async function loadFaultTypes() {
  ftLoading.value = true;
  try {
    ftList.value = await listFaultTypes();
  } catch {
    ElMessage.error("加载故障类型失败");
  } finally {
    ftLoading.value = false;
  }
}

function openCreateFt() {
  ftDialog.mode = "create";
  ftDialog.editId = "";
  Object.assign(ftForm, { itemCode: "", itemName: "", sort: 0, enabled: true });
  ftDialog.visible = true;
}

function openEditFt(row: FaultTypeItem) {
  ftDialog.mode = "edit";
  ftDialog.editId = row.id;
  Object.assign(ftForm, {
    itemCode: row.itemCode,
    itemName: row.itemName,
    sort: row.sort ?? 0,
    enabled: row.enabled
  });
  ftDialog.visible = true;
}

async function submitFt() {
  if (!ftForm.itemCode.trim() || !ftForm.itemName.trim()) {
    ElMessage.warning("请填写项编码与项名称");
    return;
  }
  ftDialog.saving = true;
  try {
    if (ftDialog.mode === "create") {
      await createFaultType({
        itemCode: ftForm.itemCode.trim(),
        itemName: ftForm.itemName.trim(),
        sort: ftForm.sort
      });
      ElMessage.success("已新增故障类型");
    } else {
      await updateFaultType(ftDialog.editId, {
        itemName: ftForm.itemName.trim(),
        sort: ftForm.sort,
        enabled: ftForm.enabled
      });
      ElMessage.success("已保存");
    }
    ftDialog.visible = false;
    loadFaultTypes();
  } catch {
    ElMessage.error("保存失败");
  } finally {
    ftDialog.saving = false;
  }
}

async function removeFt(row: FaultTypeItem) {
  try {
    await ElMessageBox.confirm(`确认删除故障类型「${row.itemName}」？`, "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  try {
    await deleteFaultType(row.id);
    ElMessage.success("已删除");
    loadFaultTypes();
  } catch {
    ElMessage.error("删除失败");
  }
}

/* ===================== 报修位置 ===================== */
const SPACE_TYPE_LABEL: Record<string, string> = {
  CLASSROOM: "教室",
  LAB: "实验室",
  MEETING_ROOM: "会议室",
  SPORT_VENUE: "体育场馆"
};
const SPACE_TYPE_OPTIONS = Object.entries(SPACE_TYPE_LABEL).map(([value, label]) => ({
  value,
  label
}));
const SPACE_STATUS: Record<
  string,
  { label: string; type: "success" | "info" | "warning" }
> = {
  AVAILABLE: { label: "可用", type: "success" },
  DISABLED: { label: "停用", type: "info" },
  REPAIRING: { label: "维修中", type: "warning" }
};
const SPACE_STATUS_OPTIONS = Object.entries(SPACE_STATUS).map(([value, v]) => ({
  value,
  label: v.label
}));
const SCOPE_LABEL: Record<string, string> = { SELF: "仅本人", GRADE: "本年级", ALL: "全校" };
const SCOPE_OPTIONS = Object.entries(SCOPE_LABEL).map(([value, label]) => ({ value, label }));

const spLoading = ref(false);
const spList = ref<SpaceConfig[]>([]);
const spDialog = reactive({
  visible: false,
  mode: "create" as "create" | "edit",
  saving: false,
  editId: ""
});
const spForm = reactive<{
  spaceName: string;
  spaceType: SpaceConfig["spaceType"];
  capacity: number;
  status: SpaceConfig["status"];
  openScope: string;
}>({
  spaceName: "",
  spaceType: "CLASSROOM",
  capacity: 30,
  status: "AVAILABLE",
  openScope: "ALL"
});

async function loadSpaces() {
  spLoading.value = true;
  try {
    spList.value = await listConfigSpaces();
  } catch {
    ElMessage.error("加载报修位置失败");
  } finally {
    spLoading.value = false;
  }
}

function openCreateSp() {
  spDialog.mode = "create";
  spDialog.editId = "";
  Object.assign(spForm, {
    spaceName: "",
    spaceType: "CLASSROOM",
    capacity: 30,
    status: "AVAILABLE",
    openScope: "ALL"
  });
  spDialog.visible = true;
}

function openEditSp(row: SpaceConfig) {
  spDialog.mode = "edit";
  spDialog.editId = row.id;
  Object.assign(spForm, {
    spaceName: row.spaceName,
    spaceType: row.spaceType,
    capacity: row.capacity,
    status: row.status,
    openScope: row.openScope ?? "ALL"
  });
  spDialog.visible = true;
}

async function submitSp() {
  if (!spForm.spaceName.trim()) {
    ElMessage.warning("请填写位置名称");
    return;
  }
  spDialog.saving = true;
  try {
    if (spDialog.mode === "create") {
      await createConfigSpace({
        spaceName: spForm.spaceName.trim(),
        spaceType: spForm.spaceType,
        capacity: spForm.capacity,
        openScope: spForm.openScope
      });
      ElMessage.success("已新增报修位置");
    } else {
      await updateConfigSpace(spDialog.editId, {
        spaceName: spForm.spaceName.trim(),
        spaceType: spForm.spaceType,
        capacity: spForm.capacity,
        status: spForm.status,
        openScope: spForm.openScope
      });
      ElMessage.success("已保存");
    }
    spDialog.visible = false;
    loadSpaces();
  } catch {
    ElMessage.error("保存失败");
  } finally {
    spDialog.saving = false;
  }
}

async function removeSp(row: SpaceConfig) {
  try {
    await ElMessageBox.confirm(`确认删除报修位置「${row.spaceName}」？`, "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  try {
    await deleteConfigSpace(row.id);
    ElMessage.success("已删除");
    loadSpaces();
  } catch {
    ElMessage.error("删除失败");
  }
}

/* ===================== 审批流程 ===================== */
const flowLoading = ref(false);
const savingFlow = ref(false);
const flowName = ref("");
const flowNodes = ref<{ name: string; approveRole: string; timeoutHours?: number }[]>([]);
const roles = ref<SysRole[]>([]);

async function loadFlow() {
  flowLoading.value = true;
  try {
    const flow = await listRepairFlow();
    flowName.value = flow?.name ?? "";
    flowNodes.value = (flow?.nodes ?? [])
      .slice()
      .sort((a, b) => a.seq - b.seq)
      .map((n) => ({
        name: n.name,
        approveRole: n.approveRole,
        timeoutHours: n.timeoutHours ?? undefined
      }));
  } catch {
    flowName.value = "";
    flowNodes.value = [];
  } finally {
    flowLoading.value = false;
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

function addNode() {
  flowNodes.value.push({ name: "", approveRole: "", timeoutHours: undefined });
}

function removeNode(i: number) {
  if (flowNodes.value.length <= 1) {
    ElMessage.warning("流程至少需要一个审批节点");
    return;
  }
  flowNodes.value.splice(i, 1);
}

function moveNode(i: number, dir: -1 | 1) {
  const j = i + dir;
  if (j < 0 || j >= flowNodes.value.length) return;
  const tmp = flowNodes.value[i];
  flowNodes.value[i] = flowNodes.value[j];
  flowNodes.value[j] = tmp;
}

async function saveFlow() {
  for (const n of flowNodes.value) {
    if (!n.name.trim() || !n.approveRole) {
      ElMessage.warning("每个节点都需填写名称与审批角色");
      return;
    }
  }
  savingFlow.value = true;
  try {
    await updateRepairFlow(
      flowNodes.value.map((n) => ({
        name: n.name.trim(),
        approveRole: n.approveRole,
        timeoutHours: n.timeoutHours
      }))
    );
    ElMessage.success("报修审批流已保存");
    loadFlow();
  } catch {
    ElMessage.error("保存流程失败");
  } finally {
    savingFlow.value = false;
  }
}

onMounted(() => {
  loadFaultTypes();
  loadSpaces();
  loadFlow();
  ensureRoles();
});
</script>

<template>
  <div class="rc sa-page">
    <header class="rc__hd">
      <div>
        <h2 class="rc__title">报修配置</h2>
        <p class="rc__sub">统一管理故障类型 / 报修位置 / 报修审批流程</p>
      </div>
    </header>

    <section class="sa-card rc__card">
      <el-tabs v-model="tab">
        <!-- ================= 故障类型 ================= -->
        <el-tab-pane label="故障类型" name="fault">
          <div class="rc__tab-tools">
            <el-button :loading="ftLoading" @click="loadFaultTypes">
              <el-icon><Refresh /></el-icon>&nbsp;刷新
            </el-button>
            <el-button v-perms="'BTN-REP-CONFIG'" type="primary" @click="openCreateFt">
              <el-icon><Plus /></el-icon>&nbsp;新增故障类型
            </el-button>
          </div>
          <el-table v-loading="ftLoading" :data="ftList" row-key="id" empty-text="暂无故障类型">
            <el-table-column prop="itemCode" label="项编码" min-width="150" />
            <el-table-column prop="itemName" label="项名称" min-width="140" />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column label="启用" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? "启用" : "停用" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button v-perms="'BTN-REP-CONFIG'" link type="primary" @click="openEditFt(row)">
                  编辑
                </el-button>
                <el-button v-perms="'BTN-REP-CONFIG'" link type="danger" @click="removeFt(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ================= 报修位置 ================= -->
        <el-tab-pane label="报修位置" name="space">
          <div class="rc__tab-tools">
            <el-button :loading="spLoading" @click="loadSpaces">
              <el-icon><Refresh /></el-icon>&nbsp;刷新
            </el-button>
            <el-button v-perms="'BTN-REP-CONFIG'" type="primary" @click="openCreateSp">
              <el-icon><Plus /></el-icon>&nbsp;新增位置
            </el-button>
          </div>
          <el-table v-loading="spLoading" :data="spList" row-key="id" empty-text="暂无报修位置">
            <el-table-column prop="spaceName" label="位置名称" min-width="150" />
            <el-table-column label="类型" min-width="110">
              <template #default="{ row }">{{ SPACE_TYPE_LABEL[row.spaceType] ?? row.spaceType }}</template>
            </el-table-column>
            <el-table-column prop="capacity" label="容量" width="90" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="SPACE_STATUS[row.status]?.type ?? 'info'" size="small">
                  {{ SPACE_STATUS[row.status]?.label ?? row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="开放范围" width="100">
              <template #default="{ row }">{{ SCOPE_LABEL[row.openScope] ?? row.openScope ?? "—" }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button v-perms="'BTN-REP-CONFIG'" link type="primary" @click="openEditSp(row)">
                  编辑
                </el-button>
                <el-button v-perms="'BTN-REP-CONFIG'" link type="danger" @click="removeSp(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ================= 审批流程 ================= -->
        <el-tab-pane label="审批流程" name="flow">
          <div class="rc__tab-tools">
            <div class="rc__flow-name">
              当前流程：<strong>{{ flowName || "未配置" }}</strong>
            </div>
            <div class="rc__tab-tools-right">
              <el-button :loading="flowLoading" @click="loadFlow">
                <el-icon><Refresh /></el-icon>&nbsp;刷新
              </el-button>
              <el-button v-perms="'BTN-REP-CONFIG'" type="primary" :loading="savingFlow" @click="saveFlow">
                保存流程
              </el-button>
            </div>
          </div>

          <div v-loading="flowLoading" class="rc__nodes">
            <div class="rc__node-head">
              <span class="rc__node-col rc__node-col--seq">顺序</span>
              <span class="rc__node-col rc__node-col--name">节点名称</span>
              <span class="rc__node-col rc__node-col--role">审批角色</span>
              <span class="rc__node-col rc__node-col--timeout">超时(h)</span>
              <span class="rc__node-col rc__node-col--ops">操作</span>
            </div>
            <div v-for="(n, i) in flowNodes" :key="i" class="rc__node-row">
              <span class="rc__node-col rc__node-col--seq">
                <span class="rc__seq">{{ i + 1 }}</span>
              </span>
              <span class="rc__node-col rc__node-col--name">
                <el-input v-model="n.name" placeholder="节点名称，如 部门审批" />
              </span>
              <span class="rc__node-col rc__node-col--role">
                <el-select v-model="n.approveRole" placeholder="审批角色" filterable style="width: 100%">
                  <el-option
                    v-for="r in roles"
                    :key="r.code"
                    :label="`${r.name}（${r.code}）`"
                    :value="r.code"
                  />
                </el-select>
              </span>
              <span class="rc__node-col rc__node-col--timeout">
                <el-input-number
                  v-model="n.timeoutHours"
                  :min="1"
                  :max="720"
                  controls-position="right"
                  placeholder="h"
                  style="width: 100%"
                />
              </span>
              <span class="rc__node-col rc__node-col--ops">
                <el-button link @click="moveNode(i, -1)" :disabled="i === 0">
                  <el-icon><Top /></el-icon>
                </el-button>
                <el-button link @click="moveNode(i, 1)" :disabled="i === flowNodes.length - 1">
                  <el-icon><Bottom /></el-icon>
                </el-button>
                <el-button v-perms="'BTN-REP-CONFIG'" link type="danger" @click="removeNode(i)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </span>
            </div>
            <div v-if="flowNodes.length === 0" class="rc__nodes-empty">
              流程未配置，请先添加审批节点
            </div>
            <el-button v-perms="'BTN-REP-CONFIG'" link type="primary" @click="addNode">
              <el-icon><Plus /></el-icon>&nbsp;添加节点
            </el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- 故障类型：新增 / 编辑 -->
    <el-dialog
      v-model="ftDialog.visible"
      :title="ftDialog.mode === 'create' ? '新增故障类型' : '编辑故障类型'"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form :model="ftForm" label-width="90px">
        <el-form-item label="项编码" required>
          <el-input
            v-model="ftForm.itemCode"
            :disabled="ftDialog.mode === 'edit'"
            placeholder="英文码，如 ELECTRICAL"
          />
        </el-form-item>
        <el-form-item label="项名称" required>
          <el-input v-model="ftForm.itemName" placeholder="中文名，如 电路故障" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="ftForm.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item v-if="ftDialog.mode === 'edit'" label="启用">
          <el-switch v-model="ftForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ftDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="ftDialog.saving" @click="submitFt">保存</el-button>
      </template>
    </el-dialog>

    <!-- 报修位置：新增 / 编辑 -->
    <el-dialog
      v-model="spDialog.visible"
      :title="spDialog.mode === 'create' ? '新增报修位置' : '编辑报修位置'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form :model="spForm" label-width="90px">
        <el-form-item label="位置名称" required>
          <el-input v-model="spForm.spaceName" placeholder="如 教学楼 301 教室" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="spForm.spaceType" style="width: 100%">
            <el-option v-for="o in SPACE_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="容量">
          <el-input-number v-model="spForm.capacity" :min="1" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开放范围">
          <el-select v-model="spForm.openScope" style="width: 100%">
            <el-option v-for="o in SCOPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="spDialog.mode === 'edit'" label="状态">
          <el-select v-model="spForm.status" style="width: 100%">
            <el-option v-for="o in SPACE_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="spDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="spDialog.saving" @click="submitSp">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.rc__hd {
  margin-bottom: var(--sa-space-5);
}
.rc__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.rc__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.rc__card {
  padding: var(--sa-space-4);
}
.rc__tab-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sa-space-2);
  margin-bottom: var(--sa-space-3);
}
.rc__tab-tools-right {
  display: flex;
  gap: var(--sa-space-2);
}
.rc__flow-name {
  font-size: var(--sa-fs-13);
  color: var(--sa-text-2);
}
.rc__flow-name strong {
  color: var(--sa-text-1);
}
.rc__nodes {
  display: flex;
  flex-direction: column;
  gap: var(--sa-space-2);
}
.rc__node-head,
.rc__node-row {
  display: flex;
  align-items: center;
  gap: var(--sa-space-2);
}
.rc__node-head {
  padding-bottom: var(--sa-space-1);
  border-bottom: 1px solid var(--sa-border-2);
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.rc__node-col--seq {
  width: 48px;
  flex-shrink: 0;
  text-align: center;
}
.rc__node-col--name {
  width: 220px;
  flex-shrink: 0;
}
.rc__node-col--role {
  flex: 1;
  min-width: 160px;
}
.rc__node-col--timeout {
  width: 140px;
  flex-shrink: 0;
}
.rc__node-col--ops {
  width: 110px;
  flex-shrink: 0;
  display: flex;
  gap: 0;
  justify-content: flex-end;
}
.rc__seq {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--sa-primary);
  color: var(--sa-text-inverse);
  font-size: var(--sa-fs-12);
}
.rc__nodes-empty {
  padding: var(--sa-space-5);
  text-align: center;
  color: var(--sa-text-3);
  font-size: var(--sa-fs-13);
}
</style>
