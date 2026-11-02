<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import {
  Box,
  Tools,
  Calendar,
  Clock,
  OfficeBuilding,
  MagicStick,
} from "@element-plus/icons-vue";
import QuickApplyDrawer from "@/components/QuickApply/Drawer.vue";
import { schemas, TEACHER_QUICK_ACTIONS } from "@/components/QuickApply/schemas";

/**
 * 教师个人工作台（M0101 /dashboard/my-affairs）
 * ---------------------------------------------------------------------------
 * 教师端不是大屏（docs/14 第一节明确）：这里是「个人事务视图」——
 * 我的课表 / 待办 / 我的申请 / 我的报修 + 六个高频事务快捷入口。
 *
 * 硬指标②落点（高频事务 ≤3 步）：
 *   快捷卡（第 1 步）→ 抽屉内填完提交（第 2 步）= 2 步；
 *   场地类默认已选中最近空闲时段，同样是 2 步（上限 3 步）。
 *
 * 权限：卡片用 v-auth 隐藏无权限入口（避免让用户点到必然 403 的入口），
 *      但服务端校验仍是唯一安全边界（docs/01 AC-E5-1）。
 */
const router = useRouter();
const currentSchema = ref<(typeof schemas)[string]>();
const drawer = ref(false);

const ICONS: Record<string, unknown> = {
  materialApply: Box,
  repairSubmit: Tools,
  leaveApply: Calendar,
  scheduleChangeApply: Clock,
  meetingRoomBook: OfficeBuilding,
  labBook: MagicStick,
};

const actions = computed(() =>
  TEACHER_QUICK_ACTIONS.map((code) => ({
    code,
    title: schemas[code].title,
    btnCode: schemas[code].btnCode,
    stepText: schemas[code].stepText,
    icon: ICONS[code],
  }))
);

function open(code: string) {
  currentSchema.value = schemas[code];
  drawer.value = true;
}
</script>

<template>
  <div class="wb sa-page">
    <header class="wb__hd">
      <h2 class="wb__title">我的工作台</h2>
      <p class="wb__sub">常用事务一步进入，办完即走</p>
    </header>

    <!-- 高频事务：大按钮卡片（教师端按钮要大、路径要短） -->
    <section class="wb__quick">
      <h3 class="wb__sec-title">高频事务</h3>
      <div class="wb__cards">
        <button
          v-for="a in actions"
          :key="a.code"
          v-perms="a.btnCode"
          class="wb__card sa-card"
          type="button"
          @click="open(a.code)"
        >
          <el-icon class="wb__card-icon"><component :is="a.icon" /></el-icon>
          <span class="wb__card-title">{{ a.title }}</span>
          <span class="wb__card-step">{{ a.stepText }}</span>
        </button>
      </div>
    </section>

    <!-- 个人事务：课表 / 待办 / 我的申请 / 我的报修 -->
    <section class="wb__mine">
      <div class="wb__mine-card sa-card" @click="router.push('/timetable/my')">
        <h4>今日课表</h4>
        <p class="wb__mine-desc">查看本人今日课节与调课结果</p>
      </div>
      <div class="wb__mine-card sa-card" @click="router.push('/dashboard/my-applications')">
        <h4>我的申请</h4>
        <p class="wb__mine-desc">领用 / 请假 / 调课，状态与驳回原因</p>
      </div>
      <div class="wb__mine-card sa-card" @click="router.push('/dashboard/my-repairs')">
        <h4>我的报修</h4>
        <p class="wb__mine-desc">工单进度与处理结果</p>
      </div>
    </section>

    <QuickApplyDrawer
      v-if="currentSchema"
      v-model="drawer"
      :schema="currentSchema"
    />
  </div>
</template>

<style scoped>
.wb__hd {
  margin-bottom: var(--sa-space-5);
}
.wb__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.wb__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.wb__sec-title {
  margin: 0 0 var(--sa-space-3);
  font-size: var(--sa-fs-16);
  font-weight: var(--sa-fw-medium);
}
.wb__cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--sa-space-4);
}
.wb__card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--sa-space-2);
  min-height: 104px;
  padding: var(--sa-space-4);
  border: 1px solid var(--sa-border-1);
  border-radius: var(--sa-radius-lg);
  background: var(--sa-bg-card);
  cursor: pointer;
  text-align: left;
  transition: box-shadow var(--sa-dur-fast) var(--sa-ease),
    border-color var(--sa-dur-fast) var(--sa-ease);
}
.wb__card:hover {
  border-color: var(--sa-primary-border);
  box-shadow: var(--sa-shadow-float);
}
.wb__card-icon {
  font-size: 20px;
  color: var(--sa-primary);
}
.wb__card-title {
  font-size: var(--sa-fs-16);
  font-weight: var(--sa-fw-medium);
  color: var(--sa-text-1);
}
.wb__card-step {
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.wb__mine {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--sa-space-4);
  margin-top: var(--sa-space-6);
}
.wb__mine-card {
  padding: var(--sa-space-4) var(--sa-space-5);
  cursor: pointer;
}
.wb__mine-card h4 {
  margin: 0;
  font-size: var(--sa-fs-14);
  font-weight: var(--sa-fw-medium);
}
.wb__mine-desc {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
</style>
