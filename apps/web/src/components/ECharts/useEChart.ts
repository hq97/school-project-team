import * as echarts from "echarts";
import { onBeforeUnmount, onMounted, shallowRef, watch, type Ref } from "vue";

export type EChartsOption = echarts.EChartsOption;

/**
 * ECharts 5 通用 composable
 * ---------------------------------------------------------------------------
 * 约定：
 *  - 组件只负责「把服务端已聚合好的数据渲染成图」，不做任何计算 / 过滤 / 排序。
 *  - 容器尺寸变化用 ResizeObserver，不做定时轮询 resize。
 *  - 卸载即 dispose，避免大屏频繁切换模板导致实例泄漏。
 */
export function useEChart(
  el: Ref<HTMLDivElement | undefined>,
  option: Ref<EChartsOption>
) {
  const chart = shallowRef<echarts.ECharts>();

  const render = () => {
    if (!chart.value) return;
    // notMerge: true —— 切换学期/自然月维度时彻底重绘，避免残留旧 series
    chart.value.setOption(option.value, { notMerge: true });
  };

  let ro: ResizeObserver | undefined;

  onMounted(() => {
    if (!el.value) return;
    chart.value = echarts.init(el.value, undefined, {
      renderer: "canvas",
      // 办公浅色场景：不用深色挂墙主题
    });
    render();
    ro = new ResizeObserver(() => chart.value?.resize());
    ro.observe(el.value);
  });

  watch(option, render, { deep: true });

  onBeforeUnmount(() => {
    ro?.disconnect();
    ro = undefined;
    chart.value?.dispose();
    chart.value = undefined;
  });

  return { chart, render };
}

export { echarts };
