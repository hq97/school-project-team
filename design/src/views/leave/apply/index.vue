<script setup lang="ts">
import { ref } from "vue";
import QuickApplyDrawer from "@/components/QuickApply/Drawer.vue";
import { schemas } from "@/components/QuickApply/schemas";

/** 请假申请 M0901 /leave/apply（学生 / 教师）：2 步 */
const drawer = ref(false);
const schema = schemas.leaveApply;
</script>

<template>
  <div class="lv sa-page">
    <header class="lv__hd">
      <h2 class="lv__title">请假申请</h2>
      <p class="lv__sub">{{ schema.stepText }} · 审批结果发到消息中心</p>
    </header>

    <section class="lv__entry sa-card">
      <div>
        <h3>发起请假</h3>
        <p class="lv__desc">选类型 + 天数 + 事由，提交后由审批人处理。</p>
      </div>
      <el-button v-auth="schema.btnCode" type="primary" size="large" @click="drawer = true">
        请假申请
      </el-button>
    </section>

    <QuickApplyDrawer v-model="drawer" :schema="schema" />
  </div>
</template>

<style scoped>
.lv__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.lv__sub {
  margin: var(--sa-space-1) 0 var(--sa-space-5);
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.lv__entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sa-space-4);
  padding: var(--sa-space-5);
}
.lv__entry h3 {
  margin: 0;
  font-size: var(--sa-fs-16);
  font-weight: var(--sa-fw-medium);
}
.lv__desc {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-13);
  color: var(--sa-text-2);
}
</style>
