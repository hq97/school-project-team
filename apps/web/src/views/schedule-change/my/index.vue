<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import { listMyScheduleChanges, scheduleChangeDetail } from "@/api/biz";
import type { ScheduleChange } from "@/api/types";
import type { FlowInstance } from "@/api/flow";

/** 我的调课 / 代课 M0802 /schedule-change/my：跟踪自己提交的调课/代课申请进度 */

const TYPE_LABEL: Record<string, string> = {
  CHANGE: "调课",
  SUBSTITUTE: "代课",
  LEAVE_SUBSTITUTE: "请假代课",
};
const STATUS: Record<string, { label: string; type: "warning" | "primary" | "info" | "success" | "danger" }> = {
  PENDING: { label: "待审批", type: "warning" },
  APPROVED: { label: "已通过", type: "success" },
  REJECTED: { label: "已驳回", type: "danger" },
  COMPLETED: { label: "已完成", type: "success" },
};

const loading = ref(false);
const list = ref<ScheduleChange[]>([]);

const statusLabel = (s: string) => STATUS[s]?.label ?? s;
const statusType = (s: string) => STATUS[s]?.type ?? "info";
const typeLabel = (t: string) => TYPE_LABEL[t] ?? t;
const DAY_LABEL = ["", "周一", "周二", "周三", "周四", "周五"];
const slotText = (slot?: { dayOfWeek?: number; period?: number }) => {
  if (!slot) return "—";
  const d = slot.dayOfWeek ? DAY_LABEL[slot.dayOfWeek] ?? `周${slot.dayOfWeek}` : "";
  const p = slot.period ? `第${slot.period}节` : "";
  return `${d}${p}` || "—";
};
const fmt = (t?: string) => (t ? new Date(t).toLocaleString("zh-CN", { hour12: false }) : "—");

async function load() {
  loading.value = true;
  try {
    list.value = await listMyScheduleChanges();
  } catch {
    ElMessage.error("加载我的调课/代课失败");
  } finally {
    loading.value = false;
  }
}

/* ===================== 详情抽屉（审批进度） ===================== */
const detailDialog = reactive({ visible: false, loading: false });
const detail = ref<ScheduleChange | null>(null);
const detailFlow = ref<FlowInstance | null>(null);

async function openDetail(row: ScheduleChange) {
  detail.value = row;
  detailFlow.value = row.flow ?? null;
  detailDialog.visible = true;
  if (detailFlow.value) return;
  detailDialog.loading = true;
  try {
    const d = await scheduleChangeDetail(row.id);
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
    { title: "提交申请", desc: "已提交调课/代课申请", status: "finish" },
  ];
  for (const n of nodes) {
    let st: StepStatus = "wait";
    if (flowStatus === "COMPLETED") st = "finish";
    else if (flowStatus === "REJECTED") st = n.seq < currentSeq ? "finish" : "wait";
    else st = n.seq < currentSeq ? "finish" : n.seq === currentSeq ? "process" : "wait";
    steps.push({ title: n.name, desc: `审批角色：${n.approveRole}`, status: st });
  }
  let finalStatus: StepStatus = "wait";
  if (flowStatus === "COMPLETED") finalStatus = "finish";
  else if (flowStatus === "REJECTED") finalStatus = "error";
  steps.push({ title: "完成", desc: "课表生效", status: finalStatus });
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

onMounted(load);
</script>

<template>
  <div class="sc sa-page">
    <header class="sc__hd">
      <div>
        <h2 class="sc__title">我的调课 / 代课</h2>
        <p class="sc__sub">跟踪你提交的调课/代课申请审批进度</p>
      </div>
      <div class="sc__tools">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
      </div>
    </header>

    <section class="sa-card sc__card">
      <el-table v-loading="loading" :data="list" row-key="id" empty-text="暂无申请">
        <el-table-column prop="applyNo" label="申请号" min-width="150" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标时段" width="130">
          <template #default="{ row }">{{ slotText(row.targetSlot) }}</template>
        </el-table-column>
        <el-table-column label="原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || "—" }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 详情：申请信息 + 审批进度 -->
    <el-drawer v-model="detailDialog.visible" title="调课 / 代课详情" size="520px">
      <div v-loading="detailDialog.loading">
        <el-descriptions v-if="detail" :column="1" border size="small">
          <el-descriptions-item label="申请号">{{ detail.applyNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeLabel(detail.type) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)" size="small">
              {{ statusLabel(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="目标时段">{{ slotText(detail.targetSlot) }}</el-descriptions-item>
          <el-descriptions-item label="原因">{{ detail.reason || "—" }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="sc__flow-title">审批进度</h4>
        <el-steps
          v-if="detailFlow"
          direction="vertical"
          :active="flowActive"
          finish-status="success"
          class="sc__flow"
        >
          <el-step v-for="(s, i) in flowSteps" :key="i" :title="s.title" :status="s.status">
            <template #description>{{ s.desc }}</template>
          </el-step>
        </el-steps>
        <div v-else class="sc__flow-empty">流程未生成</div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.sc__hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sa-space-4);
  margin-bottom: var(--sa-space-5);
}
.sc__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.sc__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.sc__tools {
  display: flex;
  gap: var(--sa-space-2);
}
.sc__card {
  padding: var(--sa-space-4);
}
.sc__flow-title {
  margin: var(--sa-space-5) 0 var(--sa-space-3);
  font-size: var(--sa-fs-14);
  font-weight: var(--sa-fw-medium);
}
.sc__flow {
  min-height: 80px;
}
.sc__flow-empty {
  padding: var(--sa-space-6) 0;
  text-align: center;
  color: var(--sa-text-3);
  font-size: var(--sa-fs-13);
}
</style>
