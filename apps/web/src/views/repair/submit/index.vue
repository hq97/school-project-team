<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { listDictItems, listRepairFlow, listSpaces, submitRepair } from "@/api/biz";
import type { RepairFlowNode, SpaceItem } from "@/api/types";

/**
 * 报修提交 M0401 /repair/submit：AC-C1-1 ≤3 步（实测 2 步：填写 → 提交）。
 * 左右分栏：左半展示报修审批流程（可配置审批流的节点链），右半内嵌提交表单。
 * 权限：提交按钮 BTN-REP-SUBMIT（v-perms 仅隐藏入口，服务端 40302 仍为安全边界）。
 */

const flowNodes = ref<RepairFlowNode[]>([]);
const flowLoading = ref(false);
const flowLoaded = ref(false);

const spaces = ref<SpaceItem[]>([]);
const faultOptions = ref<{ label: string; value: string }[]>([]);
const submitting = ref(false);

const form = reactive<{ spaceId: string; faultType: string; description: string }>({
  spaceId: "",
  faultType: "",
  description: ""
});

/** 角色码 → 中文（简单映射；未知码回退显示原码，避免渲染 undefined） */
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

async function loadFlow() {
  flowLoading.value = true;
  try {
    const flow = await listRepairFlow();
    flowNodes.value = (flow?.nodes ?? []).slice().sort((a, b) => a.seq - b.seq);
  } catch {
    flowNodes.value = [];
  } finally {
    flowLoading.value = false;
    flowLoaded.value = true;
  }
}

/** 位置 / 故障类型下拉选项（失败不阻塞表单，但提示可降级） */
async function loadOptions() {
  try {
    spaces.value = await listSpaces();
  } catch {
    spaces.value = [];
  }
  try {
    faultOptions.value = await listDictItems("DICT-FAULT-TYPE");
  } catch {
    faultOptions.value = [];
  }
}

function resetForm() {
  form.spaceId = "";
  form.faultType = "";
  form.description = "";
}

async function submit() {
  if (!form.spaceId) {
    ElMessage.warning("请选择报修位置");
    return;
  }
  if (!form.faultType) {
    ElMessage.warning("请选择故障类型");
    return;
  }
  submitting.value = true;
  try {
    const res = await submitRepair({
      spaceId: form.spaceId,
      faultType: form.faultType,
      description: form.description.trim() || undefined
    });
    ElMessage.success(`已提交，工单号 ${res.ticketNo}，可在「我的报修」跟踪进度`);
    resetForm();
    loadFlow();
  } catch {
    // 40301~40304 由 axios 全局拦截器统一提示，这里不再重复弹窗
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  loadFlow();
  loadOptions();
});
</script>

<template>
  <div class="rp sa-page">
    <header class="rp__hd">
      <h2 class="rp__title">报修提交</h2>
      <p class="rp__sub">2 步：填写位置与故障 → 提交 · 提交后可在「我的报修」跟踪进度</p>
    </header>

    <div class="rp__grid">
      <!-- 左半：报修处理流程（可配置审批流的节点链） -->
      <section class="sa-card rp__panel">
        <h3 class="rp__panel-title">报修处理流程</h3>
        <div v-loading="flowLoading" class="rp__flow">
          <el-steps
            v-if="flowNodes.length"
            direction="vertical"
            :active="flowNodes.length"
            finish-status="success"
            class="rp__steps"
          >
            <el-step
              v-for="n in flowNodes"
              :key="n.seq"
              :title="n.name"
            >
              <template #description>
                <div class="rp__step-desc">
                  <span>审批角色：{{ roleLabel(n.approveRole) }}</span>
                  <span v-if="n.timeoutHours" class="rp__step-timeout">超时 {{ n.timeoutHours }}h</span>
                </div>
              </template>
            </el-step>
          </el-steps>
          <div v-else-if="flowLoaded" class="rp__empty">
            流程未配置<span v-if="!flowLoading">，请联系管理员在「流程模板管理」中设置审批流</span>
          </div>
        </div>
      </section>

      <!-- 右半：内嵌提交表单 -->
      <section class="sa-card rp__panel">
        <h3 class="rp__panel-title">填写报修信息</h3>
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="报修位置" required>
            <el-select v-model="form.spaceId" placeholder="请选择报修位置" filterable class="rp__ctl">
              <el-option
                v-for="s in spaces"
                :key="s.id"
                :label="`${s.spaceName}（${s.capacity}人）`"
                :value="s.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="故障类型" required>
            <el-select v-model="form.faultType" placeholder="请选择故障类型" class="rp__ctl">
              <el-option v-for="o in faultOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="故障描述">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              placeholder="选填，简要描述故障现象（200 字以内）"
            />
          </el-form-item>

          <el-button
            v-perms="'BTN-REP-SUBMIT'"
            type="primary"
            size="large"
            class="rp__submit"
            :loading="submitting"
            @click="submit"
          >
            提交报修
          </el-button>
        </el-form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.rp__hd {
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
.rp__grid {
  display: flex;
  gap: var(--sa-gap-card);
  align-items: flex-start;
}
.rp__panel {
  padding: var(--sa-pad-card);
}
.rp__panel:first-child {
  flex: 0 0 40%;
}
.rp__panel:last-child {
  flex: 1;
}
.rp__panel-title {
  margin: 0 0 var(--sa-space-4);
  font-size: var(--sa-fs-16);
  font-weight: var(--sa-fw-medium);
}
.rp__flow {
  min-height: 120px;
}
.rp__step-desc {
  display: flex;
  align-items: center;
  gap: var(--sa-space-2);
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.rp__step-timeout {
  padding: 0 var(--sa-space-2);
  border-radius: var(--sa-radius-sm);
  background: var(--sa-warning-bg);
  color: var(--sa-warning);
}
.rp__empty {
  padding: var(--sa-space-8) 0;
  text-align: center;
  color: var(--sa-text-3);
  font-size: var(--sa-fs-13);
}
.rp__ctl {
  width: 100%;
}
.rp__submit {
  width: 100%;
}
@media (max-width: 992px) {
  .rp__grid {
    flex-direction: column;
  }
  .rp__panel:first-child {
    flex: 1 1 auto;
    width: 100%;
  }
  .rp__panel:last-child {
    width: 100%;
  }
}
</style>
