<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import {
  listRepairTickets,
  listSpaces,
  listDictItems,
  assignRepair,
  listRepairmen,
} from "@/api/biz";
import type { RepairTicket, Repairman, SpaceItem } from "@/api/types";

/** 工单管理（报修审核）M0403 /repair/tickets：后勤处理视角，分派维修人 + 跟进状态 */

const loading = ref(false);
const list = ref<RepairTicket[]>([]);
const spaceMap = ref<Record<string, string>>({});
const faultMap = ref<Record<string, string>>({});
const repairmanMap = ref<Record<string, string>>({});
const repairmen = ref<Repairman[]>([]);
const query = reactive({ status: "" });

const STATUS: Record<string, { label: string; type: "warning" | "primary" | "info" | "success" | "danger" }> = {
  PENDING: { label: "待分派", type: "warning" },
  ASSIGNED: { label: "已分派·待维修", type: "primary" },
  PROCESSING: { label: "处理中", type: "info" },
  COMPLETED: { label: "已完成", type: "success" },
  REJECTED: { label: "已驳回", type: "danger" },
  CANCELLED: { label: "已取消", type: "danger" },
};
const STATUS_OPTIONS = Object.entries(STATUS).map(([value, v]) => ({ value, label: v.label }));

const statusLabel = (s: string) => STATUS[s]?.label ?? s;
const statusType = (s: string) => STATUS[s]?.type ?? "info";
const spaceName = (id?: string) => (id ? (spaceMap.value[id] ?? id) : "—");
const faultName = (code?: string) => (code ? (faultMap.value[code] ?? code) : "—");
const repairmanName = (id?: string) =>
  id ? (repairmanMap.value[id] ?? id) : "—";
const fmt = (t?: string) => (t ? new Date(t).toLocaleString("zh-CN", { hour12: false }) : "—");

async function load() {
  loading.value = true;
  try {
    list.value = await listRepairTickets(query.status || undefined);
  } catch {
    ElMessage.error("加载工单列表失败");
  } finally {
    loading.value = false;
  }
}

async function loadRefs() {
  try {
    const spaces: SpaceItem[] = await listSpaces();
    spaceMap.value = Object.fromEntries(spaces.map((s) => [s.id, s.spaceName]));
  } catch {
    /* 回退显示 spaceId */
  }
  try {
    const items = await listDictItems("DICT-FAULT-TYPE");
    faultMap.value = Object.fromEntries(items.map((i) => [i.value, i.label]));
  } catch {
    /* 回退显示编码 */
  }
  try {
    const rms = await listRepairmen();
    repairmen.value = rms;
    repairmanMap.value = Object.fromEntries(rms.map((r) => [r.id, r.realName]));
  } catch {
    /* 无 M0403 权限时回退显示 userId */
  }
}

function search() {
  load();
}

// ---------- 分派维修人（PENDING → ASSIGNED） ----------
const assignDialog = reactive({
  visible: false,
  id: "",
  ticketNo: "",
  repairmanId: "",
  saving: false
});
function openAssign(row: RepairTicket) {
  assignDialog.id = row.id;
  assignDialog.ticketNo = row.ticketNo;
  assignDialog.repairmanId = "";
  assignDialog.visible = true;
}
async function submitAssign() {
  if (!assignDialog.repairmanId) {
    ElMessage.warning("请选择维修人");
    return;
  }
  assignDialog.saving = true;
  try {
    await assignRepair(assignDialog.id, assignDialog.repairmanId);
    ElMessage.success("已分派");
    assignDialog.visible = false;
    load();
  } catch {
    ElMessage.error("分派失败");
  } finally {
    assignDialog.saving = false;
  }
}

onMounted(() => {
  load();
  loadRefs();
});
</script>

<template>
  <div class="tk sa-page">
    <header class="tk__hd">
      <div>
        <h2 class="tk__title">报修工单</h2>
        <p class="tk__sub">分派维修人并跟进工单处理状态（后勤处理视角）</p>
      </div>
      <div class="tk__tools">
        <el-select
          v-model="query.status"
          placeholder="全部状态"
          clearable
          style="width: 140px"
          @change="search"
        >
          <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button :loading="loading" @click="search">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
      </div>
    </header>

    <section class="sa-card tk__card">
      <el-table v-loading="loading" :data="list" row-key="id" empty-text="暂无工单">
        <el-table-column prop="ticketNo" label="工单号" min-width="150" />
        <el-table-column label="位置" min-width="140">
          <template #default="{ row }">{{ spaceName(row.spaceId) }}</template>
        </el-table-column>
        <el-table-column label="故障类型" min-width="110">
          <template #default="{ row }">{{ faultName(row.faultType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="维修人" min-width="100">
          <template #default="{ row }">
            {{ row.assignedToName || repairmanName(row.assignedTo) }}
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="165">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              v-perms="'BTN-REP-ASSIGN'"
              link
              type="primary"
              @click="openAssign(row)"
            >
              分派
            </el-button>
            <span v-else-if="row.status === 'ASSIGNED'" class="tk__hint">维修处理中</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 分派维修人 -->
    <el-dialog v-model="assignDialog.visible" :title="`分派维修人 - ${assignDialog.ticketNo}`" width="420px">
      <el-form label-width="70px">
        <el-form-item label="维修人" required>
          <el-select
            v-model="assignDialog.repairmanId"
            placeholder="请选择维修人"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="r in repairmen"
              :key="r.id"
              :label="`${r.realName}（${r.username}）`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="assignDialog.saving" @click="submitAssign">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tk__hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sa-space-4);
  margin-bottom: var(--sa-space-5);
}
.tk__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.tk__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.tk__tools {
  display: flex;
  gap: var(--sa-space-2);
}
.tk__card {
  padding: var(--sa-space-4);
}
.tk__hint {
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
</style>
