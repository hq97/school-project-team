<script setup lang="ts">
import { computed, ref } from "vue";
import { useEChart, type EChartsOption } from "./useEChart";
import { chart as chartToken, chartTextStyle, domainColor } from "@/design/tokens";
import type { ChartItem } from "@/api/types";

/**
 * ChartFrame —— 大屏图表容器（四类图表统一入口）
 * ---------------------------------------------------------------------------
 * 设计约束：
 *  - 服务端只给「已聚合好的 categories + series」，本组件负责渲染，不计算口径。
 *  - 图例、网格、轴线的间距/颜色全部取自 design token，禁止在页面里改样式。
 *  - 不做装饰动效（无涟漪/无流光/无自动轮播）；仅保留必要的 tooltip 与 hover 高亮。
 */
const props = defineProps<{
  item: ChartItem;
  height?: number;
}>();

const emit = defineEmits<{ (e: "drill", code: string): void }>();

const el = ref<HTMLDivElement>();

/** 系列取色：优先用服务端给的色 key（六域固定色板），否则退回中性色 */
function pickColor(s: { colorKey?: string }, i: number) {
  const key = s.colorKey as keyof typeof domainColor | undefined;
  if (key && domainColor[key]) return domainColor[key];
  return chartToken.neutral;
}

const option = computed<EChartsOption>(() => {
  const { item } = props;
  const base: EChartsOption = {
    textStyle: chartTextStyle,
    grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
    tooltip: {
      trigger: item.type === "RADAR" ? "item" : "axis",
      backgroundColor: "#fff",
      borderColor: chartToken.axis,
      borderWidth: 1,
      textStyle: { ...chartTextStyle, fontSize: 12 },
      axisPointer: { type: "line", lineStyle: { color: chartToken.axis } },
    },
  };

  if (item.type === "LINE") {
    return {
      ...base,
      legend: {
        show: item.series.length > 1,
        top: 0,
        right: 0,
        icon: "roundRect",
        itemWidth: 8,
        itemHeight: 8,
        textStyle: chartTextStyle,
      },
      xAxis: {
        type: "category",
        data: item.categories,
        boundaryGap: false,
        axisLine: { lineStyle: { color: chartToken.axis } },
        axisTick: { show: false },
        axisLabel: chartTextStyle,
      },
      yAxis: {
        type: "value",
        splitLine: { lineStyle: { color: chartToken.split } },
        axisLabel: chartTextStyle,
      },
      series: item.series.map((s, i) => ({
        name: s.name,
        type: "line",
        smooth: false, // 不做平滑曲线：趋势要读得准，不要"好看"
        symbol: "circle",
        symbolSize: 6,
        showSymbol: item.categories.length <= 20,
        lineStyle: { width: 2, color: pickColor(s, i) },
        itemStyle: { color: pickColor(s, i) },
        areaStyle: item.series.length === 1 ? { color: pickColor(s, i), opacity: 0.08 } : undefined,
        data: s.data,
      })),
    };
  }

  if (item.type === "BAR") {
    return {
      ...base,
      legend: { show: item.series.length > 1, top: 0, right: 0, icon: "roundRect", itemWidth: 8, itemHeight: 8, textStyle: chartTextStyle },
      xAxis: {
        type: "category",
        data: item.categories,
        axisLine: { lineStyle: { color: chartToken.axis } },
        axisTick: { show: false },
        axisLabel: { ...chartTextStyle, interval: 0, hideOverlap: true },
      },
      yAxis: {
        type: "value",
        splitLine: { lineStyle: { color: chartToken.split } },
        axisLabel: chartTextStyle,
      },
      series: item.series.map((s, i) => ({
        name: s.name,
        type: "bar",
        barMaxWidth: 24,
        itemStyle: { color: pickColor(s, i), borderRadius: [4, 4, 0, 0] },
        data: s.data,
      })),
    };
  }

  if (item.type === "BAR_H") {
    // 对比条形：类目名较长（如年级/班级/物资名），用横向条形避免标签旋转
    return {
      ...base,
      grid: { left: 8, right: 24, top: 8, bottom: 8, containLabel: true },
      xAxis: {
        type: "value",
        splitLine: { lineStyle: { color: chartToken.split } },
        axisLabel: chartTextStyle,
      },
      yAxis: {
        type: "category",
        data: item.categories,
        inverse: true, // 第一名在顶部
        axisLine: { lineStyle: { color: chartToken.axis } },
        axisTick: { show: false },
        axisLabel: chartTextStyle,
      },
      series: item.series.map((s, i) => ({
        name: s.name,
        type: "bar",
        barMaxWidth: 16,
        itemStyle: { color: pickColor(s, i), borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: "right", ...chartTextStyle, color: chartToken.text },
        data: s.data,
      })),
    };
  }

  // RADAR：达标雷达（如体测达标率 / 六域健康度）
  return {
    ...base,
    radar: {
      indicator: item.indicators ?? item.categories.map((c) => ({ name: c, max: 100 })),
      radius: "68%",
      center: ["50%", "54%"],
      axisName: { ...chartTextStyle, color: chartToken.text },
      splitLine: { lineStyle: { color: chartToken.split } },
      splitArea: { show: false },
      axisLine: { lineStyle: { color: chartToken.axis } },
    },
    series: item.series.map((s, i) => ({
      name: s.name,
      type: "radar",
      symbolSize: 4,
      lineStyle: { width: 2, color: pickColor(s, i) },
      itemStyle: { color: pickColor(s, i) },
      areaStyle: { color: pickColor(s, i), opacity: 0.12 },
      data: [{ value: s.data, name: s.name }],
    })),
  };
});

useEChart(el, option);
</script>

<template>
  <section class="chart-card sa-card" :style="{ height: (height ?? 288) + 'px' }">
    <header class="chart-card__hd">
      <h3 class="chart-card__title">{{ item.title }}</h3>
      <el-button
        v-if="item.drillTo"
        link
        type="primary"
        size="small"
        @click="emit('drill', item.code)"
      >
        查看明细
      </el-button>
    </header>
    <div ref="el" class="chart-card__body" />
  </section>
</template>

<style scoped>
.chart-card {
  display: flex;
  flex-direction: column;
  padding: var(--sa-space-4) var(--sa-space-5) var(--sa-space-3);
}
.chart-card__hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--sa-space-2);
}
.chart-card__title {
  margin: 0;
  font-size: var(--sa-fs-14);
  font-weight: var(--sa-fw-medium);
  color: var(--sa-text-1);
}
.chart-card__body {
  flex: 1;
  min-height: 0;
  width: 100%;
}
</style>
