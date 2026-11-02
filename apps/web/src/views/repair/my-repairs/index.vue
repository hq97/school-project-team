<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import { listMyRepairTickets, listSpaces, listDictItems } from "@/api/biz";
import type { RepairTicket, SpaceItem } from "@/api/types";

/** 我的报修 M0402 /repair/my-repairs：教师跟踪自己提交的报修工单进度 */

const loading = ref(false);
const list = ref<RepairTicket[]>([]);
const spaceMap = ref<Record<string, string>>({});
const faultMap = ref<Record<string, string>>({});

const STATUS: Record<string, { label: string; type: "warning" | "primary" | "info" | "success" | "danger" }> = {
  PENDING: { label: "待分派", type: "warning" },
  ASSIGNED: { label: "已分派", type: "primary" },
  PROCESSING: { label: "处理中", type: "info" },
  COMPLETED: { label: "已完成", type: "success" },
  CANCELLED: { label: "已取消", type: "danger" },
};

const statusLabel = (s: string) => STATUS[s]?.label ?? s;
const statusType = (s: string) => STATUS[s]?.type ?? "info";
const spaceName = (id?: string) => (id ? (spaceMap.value[id] ?? id) : "—");
const faultName = (code?: string) => (code ? (faultMap.value[code] ?? code) : "—");

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

/** 位置 / 故障类型的中文翻译（失败不阻塞列表，回退显示原始编码） */
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
}

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
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="维修人" min-width="100">
          <template #default="{ row }">{{ row.assignedTo || "—" }}</template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="170">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </section>
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
</style>
