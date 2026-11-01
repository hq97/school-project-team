<script setup lang="ts">
import { ref } from "vue";
import QuickApplyDrawer from "@/components/QuickApply/Drawer.vue";
import { schemas } from "@/components/QuickApply/schemas";

/** 调课 / 代课申请 M0801 /schedule-change/apply：2 步 */
const drawer = ref(false);
const schema = schemas.scheduleChangeApply;
</script>

<template>
  <div class="sc sa-page">
    <header class="sc__hd">
      <h2 class="sc__title">调课 / 代课申请</h2>
      <p class="sc__sub">{{ schema.stepText }} · 由教务主任审批（docs/07 流程模板配置）</p>
    </header>

    <section class="sc__entry sa-card">
      <div>
        <h3>发起调课 / 代课</h3>
        <p class="sc__desc">选原因 + 涉及节次 + 说明，提交后自动生成审批单。</p>
      </div>
      <el-button v-auth="schema.btnCode" type="primary" size="large" @click="drawer = true">
        调课 / 代课申请
      </el-button>
    </section>

    <QuickApplyDrawer v-model="drawer" :schema="schema" />
  </div>
</template>

<style scoped>
.sc__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.sc__sub {
  margin: var(--sa-space-1) 0 var(--sa-space-5);
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.sc__entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sa-space-4);
  padding: var(--sa-space-5);
}
.sc__entry h3 {
  margin: 0;
  font-size: var(--sa-fs-16);
  font-weight: var(--sa-fw-medium);
}
.sc__desc {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-13);
  color: var(--sa-text-2);
}
</style>
