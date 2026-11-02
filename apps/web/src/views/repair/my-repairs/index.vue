<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import {
  listMyRepairTickets,
  listSpaces,
  listDictItems,
  listRepairmen,
  repairDetail,
} from "@/api/biz";
import type { RepairTicket, SpaceItem } from "@/api/types";
import type { FlowInstance } from "@/api/flow";

/** 我的报修 M0402 /repair/my-repairs：教师跟踪自己提交的报修工单进度 */

const loading = ref(false);
const list = ref<RepairTicket[]>([]);
const spaceMap = ref<Record<string, string>>({});
const faultMap = ref<Record<string, string>>({});
const repairmanMap = ref<Record<string, string>>({});

const STATUS: Record<string, { label: string; type: "warning" | "primary" | "info" | "success" | "danger" }> = {
  PENDING: { label: "待分派", type: "warning" },
  ASSIGNED: { label: "已分派·待维修", type: "primary" },
  PROCESSING: { label: "处理中", type: "info" },
  COMPLETED: { label: "已完成", type: "success" },
  REJECTED: { label: "已驳回", type: "danger" },
  CANCELLED: { label: "已取消", type: "danger" },
};

const statusLabel = (s: string) => STATUS[s]?.label ?? s;
const statusType = (s: string) => STATUS[s]?.type ?? "info";
const spaceName = (id?: string) => (id ? (spaceMap.value[id] ?? id) : "—");
const faultName = (code?: string) => (code ? (faultMap.value[code] ?? code) : "—");
const repairmanName = (id?: string) => (id ? (repairmanMap.value[id] ?? id) : "—");

const fmt = (t?: string) => {
  if (!t) return "—";
  return new Date(t).toLocaleString("zh-CN", { hour12: false });
};

async function load() {
  loading.value = true;
  try {
    list.value = await listMyRepairTickets();
  } catch {
    ElMessage.error("加载我的报修失败");
  } finally {
    loading.value = false;
  }
}

/** 位置 / 故障类型 / 维修人的中文翻译（失败不阻塞列表，回退显示原始值） */
async function loadRefs() {
  try {
    const spaces: SpaceItem[] = await listSpaces();
    spaceMap.value = Object.fromEntries(spaces.map((s) => [s.id, s.spaceName]));
  } catch {
    /* 无 M0401 权限时回退显示 spaceId */
  }
  try {
    const items = await listDictItems("DICT-FAULT-TYPE");
    faultMap.value = Object.fromEntries(items.map((i) => [i.value, i.label]));
  } catch {
    /* 字典不可用时回退显示故障类型编码 */
  }
  try {
    const rms = await listRepairmen();
    repairmanMap.value = Object.fromEntries(rms.map((r) => [r.id, r.realName]));
  } catch {
    /* 无权限时回退显示 userId */
  }
}

/* ===================== 详情对话框（审批进度） ===================== */
const detailDialog = reactive({ visible: false, loading: false });
const detail = ref<RepairTicket | null>(null);
const detailFlow = ref<FlowInstance | null>(null);

const ROLE_LABEL: Record<string, string> = {
  "R-PRINCIPAL": "校长",
  "R-VP": "副校长",
  "R-DEAN": "教务主任",
  "R-LOGISTICS": "后勤主任",
  "R-MORAL": "德育主任",
  "R-GRADE": "年级组长",
  "R-TEACHER": "教师",
  "R-REPAIRMAN": "维修人员"
};
const roleLabel = (c: string) => ROLE_LABEL[c] ?? c;

async function openDetail(row: RepairTicket) {
  detail.value = row;
  detailFlow.value = row.flow ?? null;
  detailDialog.visible = true;
  if (detailFlow.value) return;
  // 列表可能没下发 flow（旧数据/无实例），用详情接口兜底
  detailDialog.loading = true;
  try {
    const d = await repairDetail(row.id);
    detail.value = d;
    detailFlow.value = d.flow ?? null;
  } catch {
    /* 保持 null，展示「流程未生成」 */
  } finally {
    detailDialog.loading = false;
  }
}

type StepStatus = "wait" | "process" | "finish" | "error" | "success";
const flowSteps = computed<{ title: string; desc: string; status: StepStatus }[]>(() => {
  const nodes = (detailFlow.value?.nodes ?? []).slice().sort((a, b) => a.seq - b.seq);
  const flowStatus = detailFlow.value?.status;
  const currentSeq = detailFlow.value?.currentSeq ?? 0;
  const steps: { title: string; desc: string; status: StepStatus }[] = [
    { title: "提交报修", desc: "已提交工单", status: "finish" }
  ];
  for (const n of nodes) {
    let st: StepStatus = "wait";
    if (flowStatus === "COMPLETED") st = "finish";
    else if (flowStatus === "REJECTED") st = n.seq < currentSeq ? "finish" : "wait";
    else st = n.seq < currentSeq ? "finish" : n.seq === currentSeq ? "process" : "wait";
    steps.push({ title: n.name, desc: `审批角色：${roleLabel(n.approveRole)}`, status: st });
  }
  let finalStatus: StepStatus = "wait";
  if (flowStatus === "COMPLETED") finalStatus = "finish";
  else if (flowStatus === "REJECTED") finalStatus = "error";
  steps.push({ title: "完成", desc: "工单办结", status: finalStatus });
  return steps;
});

const flowActive = computed(() => {
  const p = flowSteps.value.findIndex((s) => s.status === "process");
  if (p >= 0) return p;
  if (flowSteps.value.every((s) => s.status === "finish")) return flowSteps.value.length;
  let last = -1;
  flowSteps.value.forEach((s, i) => {
    if (s.status === "finish") last = i;
  });
  return last + 1;
});

onMounted(() => {
  load();
  loadRefs();
});
</script>

<template>
  <div class="rp sa-page">
    <header class="rp__hd">
      <div>
        <h2 class="rp__title">我的报修</h2>
        <p class="rp__sub">跟踪你提交的报修工单处理进度</p>
      </div>
      <div class="rp__tools">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
      </div>
    </header>

    <section class="sa-card rp__card">
      <el-table v-loading="loading" :data="list" row-key="id" empty-text="暂无报修记录">
        <el-table-column prop="ticketNo" label="工单号" min-width="150" />
        <el-table-column label="位置" min-width="140">
          <template #default="{ row }">{{ spaceName(row.spaceId) }}</template>
        </el-table-column>
        <el-table-column label="故障类型" min-width="120">
          <template #default="{ row }">{{ faultName(row.faultType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="维修人" min-width="100">
          <template #default="{ row }">
            {{ row.assignedToName || repairmanName(row.assignedTo) }}
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="170">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 详情：工单信息 + 审批进度 -->
    <el-dialog v-model="detailDialog.visible" title="报修详情" width="560px">
      <div v-loading="detailDialog.loading">
        <el-descriptions v-if="detail" :column="2" border size="small">
          <el-descriptions-item label="工单号">{{ detail.ticketNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)" size="small">
              {{ statusLabel(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="位置">{{ spaceName(detail.spaceId) }}</el-descriptions-item>
          <el-descriptions-item label="故障类型">{{ faultName(detail.faultType) }}</el-descriptions-item>
          <el-descriptions-item label="维修人" :span="2">
            {{ detail.assignedToName || repairmanName(detail.assignedTo) }}
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="rp__flow-title">审批进度</h4>
        <el-steps
          v-if="detailFlow"
          direction="vertical"
          :active="flowActive"
          finish-status="success"
          class="rp__flow"
        >
          <el-step
            v-for="(s, i) in flowSteps"
            :key="i"
            :title="s.title"
            :status="s.status"
          >
            <template #description>{{ s.desc }}</template>
          </el-step>
        </el-steps>
        <div v-else class="rp__flow-empty">流程未生成</div>
      </div>
      <template #footer>
        <el-button @click="detailDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.rp__hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sa-space-4);
  margin-bottom: var(--sa-space-5);
}
.rp__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.rp__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.rp__tools {
  display: flex;
  gap: var(--sa-space-2);
}
.rp__card {
  padding: var(--sa-space-4);
}
.rp__flow-title {
  margin: var(--sa-space-5) 0 var(--sa-space-3);
  font-size: var(--sa-fs-14);
  font-weight: var(--sa-fw-medium);
}
.rp__flow {
  min-height: 80px;
}
.rp__flow-empty {
  padding: var(--sa-space-6) 0;
  text-align: center;
  color: var(--sa-text-3);
  font-size: var(--sa-fs-13);
}
</style>
