<script setup lang="ts">
import { computed } from "vue";
import { WarningFilled, CircleCloseFilled } from "@element-plus/icons-vue";
import type { BizError, TimeSlot } from "@/api/types";

/**
 * ConflictAlert —— 冲突 / 并发失败的用户侧反馈（统一错误态）
 * ---------------------------------------------------------------------------
 * 反模式（禁止）：只弹一个「操作失败」或把后端 message 直接甩给用户。
 * 本组件强制三段式文案：发生了什么 → 为什么 → 怎么办。
 *
 * 覆盖场景（409 段资源冲突类，错误码为数字，ADR §4.1）：
 *  40901 场地被课表占用（排课权威源，docs/04 §1.12）→ 给替代时段 + 查看课表
 *  40902 并发预约竞争失败（C9 时段格唯一性）        → 给替代时段
 *  40903 库存不足（C9 availableQty 原子扣减）       → 给"改为申请 N 件"
 *  40904 名额已满（评优/竞赛/赛事）                 → 给"查看其它场次"
 *
 * 【越权红线 · 架构裁决】`occupiedBy` 是课表数据，服务端按请求人数据范围分级脱敏：
 *  范围够 → 下发「班级 + 学科 + 教师」；范围不够 → 只下发占位文案「已被课表占用」。
 *  因此本组件必须兼容 `occupiedBy` 缺失 / 降级，禁止渲染 undefined（见 occupiedText）。
 */
const props = withDefaults(
  defineProps<{
    error: BizError;
    /**
     * 当前用户是否有 M0703「全校课表总览」菜单权限（页面侧传入）。
     * 与服务端 `hasOccupancyDetail` 取交集后，才渲染「查看该场地课表」入口。
     */
    canViewTimetable?: boolean;
  }>(),
  { canViewTimetable: true }
);
const emit = defineEmits<{
  (e: "retrySlot", slot: TimeSlot): void;
  (e: "retryQty", qty: number): void;
  (e: "goto", url: string): void;
  (e: "close"): void;
}>();

const isScheduleConflict = computed(() => props.error.code === 40901);
const isBookingRace = computed(() => props.error.code === 40902);

/**
 * 占用来源文案（分级脱敏兜底）：
 *  occupiedBy 有值 → 直接用；缺失 / 被降级 → 一律用占位文案，绝不拼 undefined。
 */
const occupiedText = computed(() => {
  const t = props.error.conflict?.occupiedBy;
  return t && t.trim() ? t : "已被课表占用";
});

const title = computed(() => {
  switch (props.error.code) {
    case 40901:
      return "该时段已被课表占用，无法预约";
    case 40902:
      return "该时段刚被他人预约";
    case 40903:
      return "库存不足，本次申请未提交";
    case 40904:
      return "名额已满，本次报名未成功";
    default:
      return "操作未完成";
  }
});

const why = computed(() => {
  const e = props.error;
  if (isScheduleConflict.value)
    return `排课是场地占用的权威来源：${e.conflict?.spaceName ?? "该场地"} ${e.conflict?.slotText ?? ""}（${occupiedText.value}）。预约不能覆盖排课，请另选时段。`;
  if (isBookingRace.value)
    return `${e.conflict?.spaceName ?? "该场地"} ${e.conflict?.slotText ?? ""} 在你提交的同时被其他老师预约成功，同一时段只能有一个预约。`;
  if (e.code === 40903)
    return `${e.stock?.name ?? "该物资"} 当前可用 ${e.stock?.availableQty ?? 0} 件，你申请了 ${e.stock?.requestedQty ?? 0} 件。`;
  if (e.code === 40904)
    return `${e.quota?.name ?? "该活动"} 名额 ${e.quota?.quota ?? 0} 人，已全部报满。`;
  return "";
});

const hasSuggestions = computed(() => (props.error.conflict?.suggestions?.length ?? 0) > 0);

/**
 * 是否拿到了真实占用明细 —— **直接读服务端下发的布尔**，不做字符串比较。
 * ---------------------------------------------------------------------------
 * 为什么不判断 `occupiedBy` 是否为空 / 是否等于占位文案：占位文案会随字典与文案
 * 微调变化，字符串比较迟早失效；布尔标志是唯一稳定的契约。
 *
 * 「查看该场地课表」入口的可见性 = 服务端布尔（管数据范围）
 *                            ∩ 页面传入的 M0703 菜单权限（管入口），两者取交集。
 */
const hasOccupancyDetail = computed(
  () => props.error.conflict?.hasOccupancyDetail === true && props.canViewTimetable
);
</script>

<template>
  <div class="ca" :class="isScheduleConflict ? 'ca--warn' : 'ca--error'">
    <div class="ca__hd">
      <el-icon class="ca__icon">
        <component :is="isScheduleConflict ? WarningFilled : CircleCloseFilled" />
      </el-icon>
      <span class="ca__title">{{ title }}</span>
      <el-button link size="small" @click="emit('close')">知道了</el-button>
    </div>

    <p class="ca__why">{{ why }}</p>

    <!-- 怎么办：替代时段（一键重提，不回到表单重填） -->
    <template v-if="hasSuggestions">
      <div class="ca__label">可改约这些空闲时段：</div>
      <div class="ca__slots">
        <el-button
          v-for="s in error.conflict!.suggestions"
          :key="`${s.slot.week}-${s.slot.dayOfWeek}-${s.slot.period}`"
          size="small"
          @click="emit('retrySlot', s.slot)"
        >
          {{ s.slotText }}
        </el-button>
      </div>
    </template>

    <div class="ca__acts">
      <el-button
        v-if="error.code === 40903 && error.stock"
        size="small"
        type="primary"
        @click="emit('retryQty', error.stock.availableQty)"
      >
        改为申请 {{ error.stock.availableQty }} 件
      </el-button>
      <el-button
        v-if="isScheduleConflict && hasOccupancyDetail"
        size="small"
        @click="emit('goto', '/timetable/school')"
      >
        查看该场地课表
      </el-button>
      <el-button v-if="isBookingRace && !hasSuggestions" size="small" @click="emit('close')">
        重新选择时段
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.ca {
  border-radius: var(--sa-radius-md);
  padding: var(--sa-space-3) var(--sa-space-4);
  border: 1px solid var(--sa-border-1);
}
.ca--warn {
  background: var(--sa-warning-bg);
  border-color: #f4d9b0;
}
.ca--error {
  background: var(--sa-danger-bg);
  border-color: #f6c9c4;
}
.ca__hd {
  display: flex;
  align-items: center;
  gap: var(--sa-space-2);
}
.ca__icon {
  font-size: 16px;
}
.ca--warn .ca__icon {
  color: var(--sa-warning);
}
.ca--error .ca__icon {
  color: var(--sa-danger);
}
.ca__title {
  flex: 1;
  font-size: var(--sa-fs-14);
  font-weight: var(--sa-fw-medium);
  color: var(--sa-text-1);
}
.ca__why {
  margin: var(--sa-space-2) 0 0;
  font-size: var(--sa-fs-13);
  line-height: var(--sa-lh-normal);
  color: var(--sa-text-2);
}
.ca__label {
  margin-top: var(--sa-space-3);
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.ca__slots {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sa-space-2);
  margin-top: var(--sa-space-2);
}
.ca__acts {
  display: flex;
  gap: var(--sa-space-2);
  margin-top: var(--sa-space-3);
}
</style>
