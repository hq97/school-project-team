<script setup lang="ts">
import { computed } from "vue";
import { CaretTop, CaretBottom, Minus } from "@element-plus/icons-vue";
import type { KpiItem } from "@/api/types";

/**
 * KpiCard —— 大屏指标卡（REQ-DD3 / REQ-DD4 / REQ-DD6）
 * ---------------------------------------------------------------------------
 * 信息层级（校长端"扫视"优先，固定三段：标签 → 数值 → 对比）：
 *  ① 标签（13px 次级色）  ② 数值（32px tabular-nums，数字等宽不跳动）
 *  ③ 环比 / 同比（12px，箭头 + 百分比）
 * 状态：NORMAL 常规 / WARNING 橙色左边条 / DANGER 红色左边条 + 浅红底（超阈值自动标红）
 * 下钻：整卡可点（有 drillTo 时），hover 显示「查看明细」——不离开系统（硬指标①）
 */
const props = defineProps<{ item: KpiItem; compact?: boolean }>();
const emit = defineEmits<{ (e: "drill", item: KpiItem): void }>();

const statusClass = computed(() => `kpi--${props.item.status.toLowerCase()}`);
const trendIcon = computed(() => {
  if (props.item.trend === "UP") return CaretTop;
  if (props.item.trend === "DOWN") return CaretBottom;
  return Minus;
});
/**
 * 反向指标（如排课冲突数、超期审批数）上升是坏事：
 * 颜色跟随「好坏」而不是「升降」，避免"红色=下降"这种误读。
 */
const trendClass = computed(() => {
  const { trend, polarity = "POSITIVE" } = props.item;
  if (!trend || trend === "FLAT") return "kpi__trend--flat";
  const good = polarity === "POSITIVE" ? trend === "UP" : trend === "DOWN";
  return good ? "kpi__trend--good" : "kpi__trend--bad";
});

function fmtPct(v?: number) {
  if (v === undefined || v === null) return "—";
  const s = v > 0 ? "+" : "";
  return `${s}${Number(v).toFixed(1)}%`;
}
</script>

<template>
  <div
    class="kpi sa-card"
    :class="[statusClass, { 'kpi--compact': compact, 'is-clickable': !!item.drillTo }]"
    :title="item.thresholdText"
    @click="item.drillTo && emit('drill', item)"
  >
    <div class="kpi__label">
      <span class="kpi__label-text">{{ item.label }}</span>
      <el-tag v-if="item.status !== 'NORMAL'" :type="item.status === 'DANGER' ? 'danger' : 'warning'" size="small" effect="light" round>
        {{ item.status === "DANGER" ? "超阈值" : "预警" }}
      </el-tag>
    </div>

    <div class="kpi__value sa-num">
      {{ item.value }}<span v-if="item.unit" class="kpi__unit">{{ item.unit }}</span>
    </div>

    <div class="kpi__foot">
      <span class="kpi__trend" :class="trendClass">
        <el-icon><component :is="trendIcon" /></el-icon>
        <span>环比 {{ fmtPct(item.mom) }}</span>
      </span>
      <span class="kpi__sep" />
      <span class="kpi__yoy">同比 {{ fmtPct(item.yoy) }}</span>
    </div>
  </div>
</template>

<style scoped>
.kpi {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: var(--sa-h-kpi);
  padding: var(--sa-space-4) var(--sa-space-5);
  border-radius: var(--sa-radius-xl);
  transition: box-shadow var(--sa-dur-fast) var(--sa-ease);
}
.kpi--compact {
  height: 80px;
}
.kpi.is-clickable {
  cursor: pointer;
}
.kpi.is-clickable:hover {
  box-shadow: var(--sa-shadow-float);
}
/* 状态左边条：3px，不用整块底色（浅色办公场景要克制） */
.kpi::before {
  content: "";
  position: absolute;
  left: 0;
  top: var(--sa-space-4);
  bottom: var(--sa-space-4);
  width: 3px;
  border-radius: var(--sa-radius-pill);
  background: transparent;
}
.kpi--warning::before {
  background: var(--sa-warning);
}
.kpi--danger {
  background: linear-gradient(0deg, var(--sa-danger-bg) 0%, var(--sa-danger-bg) 100%), var(--sa-bg-card);
}
.kpi--danger::before {
  background: var(--sa-danger);
}

.kpi__label {
  display: flex;
  align-items: center;
  gap: var(--sa-space-2);
  min-height: 20px;
}
.kpi__label-text {
  font-size: var(--sa-fs-13);
  color: var(--sa-text-2);
}
.kpi__value {
  font-size: var(--sa-fs-32);
  font-weight: var(--sa-fw-bold);
  line-height: var(--sa-lh-tight);
  color: var(--sa-text-1);
}
.kpi__unit {
  margin-left: 2px;
  font-size: var(--sa-fs-13);
  font-weight: var(--sa-fw-normal);
  color: var(--sa-text-3);
}
.kpi__foot {
  display: flex;
  align-items: center;
  gap: var(--sa-space-2);
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.kpi__trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.kpi__trend--good {
  color: var(--sa-trend-up);
}
.kpi__trend--bad {
  color: var(--sa-trend-down);
}
.kpi__trend--flat {
  color: var(--sa-trend-flat);
}
.kpi__sep {
  width: 1px;
  height: 10px;
  background: var(--sa-border-1);
}
</style>
