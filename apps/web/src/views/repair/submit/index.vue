<script setup lang="ts">
import { ref } from "vue";
import QuickApplyDrawer from "@/components/QuickApply/Drawer.vue";
import { schemas } from "@/components/QuickApply/schemas";

/** 报修提交 M0401 /repair/submit：AC-C1-1 ≤3 步（实测 2 步） */
const drawer = ref(false);
const schema = schemas.repairSubmit;
</script>

<template>
  <div class="rp sa-page">
    <header class="rp__hd">
      <h2 class="rp__title">报修提交</h2>
      <p class="rp__sub">{{ schema.stepText }} · 提交后可在「我的报修」跟踪进度</p>
    </header>

    <section class="rp__entry sa-card">
      <div>
        <h3>哪儿坏了？</h3>
        <p class="rp__desc">选位置 + 故障类型 + 一句话描述，提交即生成工单号。</p>
      </div>
      <el-button v-perms="schema.btnCode" type="primary" size="large" @click="drawer = true">
        提交报修
      </el-button>
    </section>

    <QuickApplyDrawer v-model="drawer" :schema="schema" />
  </div>
</template>

<style scoped>
.rp__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.rp__sub {
  margin: var(--sa-space-1) 0 var(--sa-space-5);
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.rp__entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sa-space-4);
  padding: var(--sa-space-5);
}
.rp__entry h3 {
  margin: 0;
  font-size: var(--sa-fs-16);
  font-weight: var(--sa-fw-medium);
}
.rp__desc {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-13);
  color: var(--sa-text-2);
}
</style>
