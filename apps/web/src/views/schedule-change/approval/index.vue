<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import { listFlowTodos, approveFlowTask, type FlowTodo } from "@/api/flow";

/**
 * 调课 / 代课审批 M0803 /schedule-change/approval：
 * 待办来自通用 FlowEngine（GET /api/flow/todos），按 businessType='schedule_change' 过滤。
 * 权限：通过 BTN-SCH-CHANGE-APPROVE / 驳回 BTN-SCH-CHANGE-REJECT。
 */

const loading = ref(false);
const list = ref<FlowTodo[]>([]);

const fmt = (t?: string) => (t ? new Date(t).toLocaleString("zh-CN", { hour12: false }) : "—");

async function load() {
  loading.value = true;
  try {
    const todos = await listFlowTodos();
    list.value = todos.filter((t) => t.businessType === "schedule_change");
  } catch {
    ElMessage.error("加载待办失败");
  } finally {
    loading.value = false;
  }
}

async function approve(row: FlowTodo) {
  try {
    await approveFlowTask(row.taskId, "APPROVE");
    ElMessage.success("已通过");
    load();
  } catch {
    ElMessage.error("操作失败");
  }
}

async function reject(row: FlowTodo) {
  let comment = "";
  try {
    const { value } = await ElMessageBox.prompt("请输入驳回原因", "驳回", {
      inputType: "textarea",
      confirmButtonText: "确认驳回",
      cancelButtonText: "取消",
    });
    comment = value ?? "";
  } catch {
    return;
  }
  try {
    await approveFlowTask(row.taskId, "REJECT", comment);
    ElMessage.success("已驳回");
    load();
  } catch {
    ElMessage.error("操作失败");
  }
}

onMounted(load);
</script>

<template>
  <div class="sa sa-page">
    <header class="sa__hd">
      <div>
        <h2 class="sa__title">调课 / 代课审批</h2>
        <p class="sa__sub">处理待审批的调课/代课申请（通用审批流）</p>
      </div>
      <div class="sa__tools">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
      </div>
    </header>

    <section class="sa-card sa__card">
      <el-table v-loading="loading" :data="list" row-key="taskId" empty-text="暂无待审批事项">
        <el-table-column prop="nodeName" label="审批节点" min-width="140" />
        <el-table-column prop="templateName" label="流程" min-width="150" />
        <el-table-column prop="businessId" label="申请ID" min-width="160" />
        <el-table-column label="提交时间" min-width="165">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-perms="'BTN-SCH-CHANGE-APPROVE'"
              link
              type="success"
              @click="approve(row)"
            >
              通过
            </el-button>
            <el-button
              v-perms="'BTN-SCH-CHANGE-REJECT'"
              link
              type="danger"
              @click="reject(row)"
            >
              驳回
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.sa__hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sa-space-4);
  margin-bottom: var(--sa-space-5);
}
.sa__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.sa__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.sa__tools {
  display: flex;
  gap: var(--sa-space-2);
}
.sa__card {
  padding: var(--sa-space-4);
}
</style>
