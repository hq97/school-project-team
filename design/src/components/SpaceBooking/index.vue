<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import QuickApplyDrawer from "@/components/QuickApply/Drawer.vue";
import { schemas } from "@/components/QuickApply/schemas";
import { listOccupancies, listSpaces } from "@/api/biz";
import { CURRENT_WEEK } from "@/components/QuickApply/schemas";
import type { OccupancyState, SpaceItem, TimeSlot } from "@/api/types";

/**
 * 场地预约通用视图（会议室 M1204 / 实验室 M1302 / 体育场地共用）
 * ---------------------------------------------------------------------------
 * 步数（上限 3 步）：
 *   ①选场地（默认选中第一个）→ ②点空闲格子（时段已预填）→ ③提交
 *
 * 冲突用户侧反馈（docs/04 §1.12）：
 *   格子颜色直接表达占用来源——
 *     深灰 = 被课表占用（排课是权威源，不可预约，点了会 40901）
 *     浅蓝 = 已被他人预约（竞争失败会 40902）
 *     白   = 空闲
 *   这样用户在第②步就看得见冲突，而不是提交后才被拒；
 *   真被拒时（并发）由 ConflictAlert 给「替代时段」一键改约。
 */
const props = defineProps<{
  title: string;
  spaceType: SpaceItem["spaceType"];
  schemaCode: "meetingRoomBook" | "labBook";
}>();

const spaces = ref<SpaceItem[]>([]);
const spaceId = ref<string>();
const week = ref<number>(CURRENT_WEEK.value);
const drawer = ref(false);
const schema = computed(() => schemas[props.schemaCode]);

/**
 * 占用矩阵：服务端下发（SCHEDULE 排课占用 / RESERVED 预约占用 / FREE 空闲）
 * ---------------------------------------------------------------------------
 * 【越权红线】格子**只承载状态，不承载占用人信息**：
 * RESERVED 格子对 SELF 范围用户不得下发预约人（防借占用矩阵反查他人行程），
 * SCHEDULE 格子同理不得下发班级/教师。所以这里没有 text 字段，
 * tooltip 只用固定的状态文案，不渲染服务端返回的任何人名/班级。
 */
const matrix = ref<Record<string, OccupancyState>>({});
const STATE_TEXT: Record<OccupancyState, string> = {
  FREE: "空闲，可预约",
  SCHEDULE: "已被课表占用，不可预约",
  RESERVED: "已被他人预约",
};
const days = [1, 2, 3, 4, 5, 6, 7];
const periods = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
const dayText = ["一", "二", "三", "四", "五", "六", "日"];
const keyOf = (d: number, p: number) => `${d}-${p}`;

async function loadSpaces() {
  spaces.value = await listSpaces(props.spaceType);
  if (spaces.value.length && !spaceId.value) spaceId.value = spaces.value[0].spaceId;
}

/** 占用查询：GET /api/spaces/:id/occupancies?week=N（服务端已过滤 + 不含占用人身份） */
async function loadMatrix() {
  if (!spaceId.value) return;
  matrix.value = await listOccupancies(spaceId.value, week.value);
}

onMounted(loadSpaces);
watch([spaceId, week], loadMatrix);

/** 点空格子后预填给抽屉的时段（省掉用户在表单里再选一次） */
const prefill = ref<Record<string, unknown>>();

/** 点空闲格子 → 打开抽屉并预填时段 */
function pickCell(d: number, p: number) {
  const cell = matrix.value[keyOf(d, p)];
  if (cell && cell !== "FREE") return; // 已占用：不进入必然失败的路径
  const slot: TimeSlot = { week: week.value, dayOfWeek: d, period: p };
  prefill.value = { spaceId: spaceId.value, week: week.value, slot };
  drawer.value = true;
}
</script>

<template>
  <div class="sb sa-page">
    <header class="sb__hd">
      <div>
        <h2 class="sb__title">{{ title }}</h2>
        <p class="sb__sub">{{ schema.stepText }}</p>
      </div>
      <div class="sb__ops">
        <el-select v-model="spaceId" placeholder="选择场地" style="width: 200px">
          <el-option v-for="s in spaces" :key="s.spaceId" :label="s.spaceName" :value="s.spaceId" />
        </el-select>
        <el-select v-model="week" placeholder="周次" style="width: 120px">
          <el-option v-for="w in 20" :key="w" :label="`第 ${w} 周`" :value="w" />
        </el-select>
      </div>
    </header>

    <div class="sb__legend">
      <span><i class="sb__dot sb__dot--free" />空闲（可预约）</span>
      <span><i class="sb__dot sb__dot--sch" />被课表占用（不可预约）</span>
      <span><i class="sb__dot sb__dot--res" />已被预约</span>
    </div>

    <div class="sb__grid sa-card">
      <table class="sb__table">
        <thead>
          <tr>
            <th class="sb__th-period" />
            <th v-for="d in days" :key="d">周{{ dayText[d - 1] }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in periods" :key="p">
            <th class="sb__th-period">第 {{ p }} 节</th>
            <td v-for="d in days" :key="d">
              <button
                class="sb__cell"
                :class="{
                  'is-sch': matrix[keyOf(d, p)] === 'SCHEDULE',
                  'is-res': matrix[keyOf(d, p)] === 'RESERVED',
                }"
                type="button"
                :disabled="!!matrix[keyOf(d, p)] && matrix[keyOf(d, p)] !== 'FREE'"
                :title="STATE_TEXT[matrix[keyOf(d, p)] ?? 'FREE']"
                @click="pickCell(d, p)"
              >
                <span v-if="matrix[keyOf(d, p)] === 'SCHEDULE'">课</span>
                <span v-else-if="matrix[keyOf(d, p)] === 'RESERVED'">约</span>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <QuickApplyDrawer v-model="drawer" :schema="schema" :prefill="prefill" />
  </div>
</template>

<style scoped>
.sb__hd {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--sa-space-4);
  margin-bottom: var(--sa-space-4);
}
.sb__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.sb__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.sb__ops {
  display: flex;
  gap: var(--sa-space-2);
}
.sb__legend {
  display: flex;
  gap: var(--sa-space-4);
  margin-bottom: var(--sa-space-2);
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.sb__dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin-right: var(--sa-space-1);
  vertical-align: -1px;
}
.sb__dot--free {
  background: var(--sa-bg-card);
  border: 1px solid var(--sa-border-1);
}
.sb__dot--sch {
  background: var(--sa-border-1);
}
.sb__dot--res {
  background: var(--sa-primary-bg);
  border: 1px solid var(--sa-primary-border);
}
.sb__grid {
  padding: var(--sa-space-4);
  overflow-x: auto;
}
.sb__table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 4px;
}
.sb__table th {
  font-size: var(--sa-fs-12);
  font-weight: var(--sa-fw-normal);
  color: var(--sa-text-3);
}
.sb__th-period {
  width: 64px;
  text-align: right;
  padding-right: var(--sa-space-2);
}
.sb__cell {
  width: 100%;
  height: 34px;
  border: 1px solid var(--sa-border-1);
  border-radius: var(--sa-radius-sm);
  background: var(--sa-bg-card);
  cursor: pointer;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.sb__cell:hover:not(:disabled) {
  border-color: var(--sa-primary);
  background: var(--sa-primary-bg);
  color: var(--sa-primary);
}
.sb__cell.is-sch {
  background: var(--sa-border-2);
  color: var(--sa-text-3);
  cursor: not-allowed;
}
.sb__cell.is-res {
  background: var(--sa-primary-bg);
  border-color: var(--sa-primary-border);
  color: var(--sa-primary);
  cursor: not-allowed;
}
</style>
