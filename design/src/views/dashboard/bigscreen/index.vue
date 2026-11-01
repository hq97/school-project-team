<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { Refresh, WarningFilled } from "@element-plus/icons-vue";
import KpiCard from "@/components/KpiCard/index.vue";
import ChartFrame from "@/components/ECharts/ChartFrame.vue";
import DimensionSwitcher from "@/components/DimensionSwitcher/index.vue";
import { getOverview } from "@/api/bigscreen";
import { DEFAULT_DIM } from "@/api/biz";
import type { AlertItem, KpiItem, OverviewResp, StatDim } from "@/api/types";
import { TEMPLATES } from "./templates";

/**
 * 数据大屏（M15，/dashboard/bigscreen）
 * ---------------------------------------------------------------------------
 * 硬指标①落点（AC-DD2-1 + AC-DD5-1）：校长登录后首屏即见
 *   「六域核心指标 + 异常预警」，任一指标都能在系统内点开下钻，不必去微信/电话追问。
 *
 * 设计约束：
 *  1. 浅色办公场景：白底卡片 + 1px 描边 + 一级阴影。禁止深色挂墙风格、
 *     禁止轮播动效、禁止流光/闪烁，不做 7×24 常驻视觉。
 *  2. 服务端聚合 + 数据权限过滤：本页不发任何"全量拉取再本地算"的请求，
 *     切换角色看到的内容不同是服务端决定的（AC-DD5-1）。
 *  3. 默认学期口径；自然月仅切换展示维度，前端只改 dim 参数（AC-DD1-2）。
 *  4. 平台级配置对象（字典/流程模板/菜单/角色/部门）不出现在指标与下钻（AC-DD5-2）。
 *  4b. 【权限主路径】本页只对**有 M15 菜单权限**的角色注册路由并渲染。
 *      教师 / 学生 / 家长无 M15 菜单（docs/03 矩阵「—」），服务端对他们的 overview
 *      请求直接 40301；`templateKey = NONE` 仅用于「有菜单但无预置模板」的自建角色
 *      作优雅降级，**不是**"无大屏角色也能调 overview"的依据。
 *  5. 定时刷新默认 60s（REQ-DD7），每个区块显示服务端下发的「数据截止时间」。
 */
const router = useRouter();

const dim = ref<StatDim>(DEFAULT_DIM); // 默认「学期」，禁止默认自然月
const loading = ref(false);
const data = ref<OverviewResp>();
const refreshSec = 60;
const countdown = ref(refreshSec);
let timer: ReturnType<typeof setInterval> | undefined;

const layout = computed(() => TEMPLATES[data.value?.templateKey ?? "NONE"]);
const kpis = computed(() => (data.value?.kpis ?? []).slice(0, layout.value.kpiMax));
const alerts = computed(() => (data.value?.alerts ?? []).slice(0, layout.value.alertMax));
const charts = computed(() => {
  const list = [...(data.value?.charts ?? [])];
  const order = layout.value.chartOrder;
  return list.sort((a, b) => {
    const i = order.indexOf(a.code);
    const j = order.indexOf(b.code);
    return (i < 0 ? 99 : i) - (j < 0 ? 99 : j);
  });
});
const spanOf = (code: string) => layout.value.charts[code]?.span ?? 12;
const heightOf = (code: string) => layout.value.charts[code]?.height ?? 288;

async function load() {
  loading.value = true;
  try {
    data.value = await getOverview({ dim: dim.value });
  } finally {
    loading.value = false;
    countdown.value = refreshSec;
  }
}

/** 维度切换：只重发请求，前端不重算、不本地重排 */
watch(dim, load);

/** 定时刷新（REQ-DD7）：大屏是办公查看场景，60s 足够，不做 WebSocket 实时流 */
onMounted(() => {
  load();
  timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) load();
  }, 1000);
});
onBeforeUnmount(() => timer && clearInterval(timer));

/** 下钻（REQ-DD6）：跳业务明细页；越权由服务端拒绝 40303/40304，前端不预筛 */
function drill(item: KpiItem) {
  if (item.drillTo) router.push(item.drillTo);
}
function gotoAlert(a: AlertItem) {
  router.push(a.linkTo);
}
function drillChart(code: string) {
  const c = data.value?.charts.find((x) => x.code === code);
  if (c?.drillTo) router.push(c.drillTo);
}
</script>

<template>
  <div v-loading="loading" class="screen sa-page">
    <!-- ① 页头：角色模板标题 + 口径 + 维度切换器 + 数据截止时间 + 刷新 -->
    <header class="screen__hd">
      <div>
        <h2 class="screen__title">{{ layout.title || "我的视图" }}</h2>
        <p class="screen__sub">
          {{ layout.subtitle }}
          <template v-if="data"> · {{ data.scopeText }}</template>
        </p>
      </div>

      <div class="screen__ops">
        <DimensionSwitcher v-model="dim" :term-text="data?.currentTerm" />
        <span class="screen__asof">
          数据截止 {{ data?.dataAsOf || "—" }} · {{ countdown }}s 后刷新
        </span>
        <el-button :icon="Refresh" size="small" @click="load">刷新</el-button>
      </div>
    </header>

    <!-- ② 异常预警：校长首屏必须先看异常，再读指标（硬指标①） -->
    <section v-if="alerts.length" class="screen__alert sa-card">
      <div class="screen__alert-hd">
        <el-icon class="screen__alert-icon"><WarningFilled /></el-icon>
        <h3>{{ layout.alertTitle }}</h3>
        <el-tag type="danger" size="small" effect="dark" round>{{ alerts.length }}</el-tag>
      </div>
      <ul class="screen__alert-list">
        <li v-for="a in alerts" :key="a.id" class="screen__alert-item" @click="gotoAlert(a)">
          <el-tag :type="a.level === 'DANGER' ? 'danger' : 'warning'" size="small" effect="light">
            {{ a.level === "DANGER" ? "严重" : "预警" }}
          </el-tag>
          <span class="screen__alert-title">{{ a.title }}</span>
          <span v-if="a.ownerName" class="screen__alert-owner">责任人 {{ a.ownerName }}</span>
          <span v-if="a.overdueText" class="screen__alert-overdue">{{ a.overdueText }}</span>
          <span class="screen__alert-go">去处理 →</span>
        </li>
      </ul>
    </section>

    <!-- ③ KPI 区：六域核心指标，一屏扫完，点击可下钻 -->
    <section class="screen__kpi">
      <el-row :gutter="16">
        <el-col v-for="k in kpis" :key="k.code" :span="layout.kpiSpan">
          <KpiCard :item="k" @drill="drill" />
        </el-col>
      </el-row>
    </section>

    <!-- ④ 图表区：趋势折线 / 分布柱状 / 对比条形 / 达标雷达（AC-DD1-1） -->
    <section class="screen__charts">
      <el-row :gutter="16">
        <el-col v-for="c in charts" :key="c.code" :span="spanOf(c.code)">
          <ChartFrame :item="c" :height="heightOf(c.code)" @drill="drillChart" />
        </el-col>
      </el-row>
    </section>

    <el-empty v-if="!loading && !kpis.length" description="当前角色没有可查看的大屏数据" />
  </div>
</template>

<style scoped>
.screen {
  background: var(--sa-bg-page);
}
.screen__hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sa-space-4);
  margin-bottom: var(--sa-space-4);
}
.screen__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
  color: var(--sa-text-1);
}
.screen__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.screen__ops {
  display: flex;
  align-items: center;
  gap: var(--sa-space-3);
  flex-wrap: wrap;
}
.screen__asof {
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}

/* 异常预警：浅红底 + 左侧红色竖条，醒目但不刺眼（办公场景） */
.screen__alert {
  padding: var(--sa-space-4) var(--sa-space-5);
  margin-bottom: var(--sa-space-4);
  background: var(--sa-danger-bg);
  border-color: #f6c9c4;
}
.screen__alert-hd {
  display: flex;
  align-items: center;
  gap: var(--sa-space-2);
  margin-bottom: var(--sa-space-3);
}
.screen__alert-hd h3 {
  margin: 0;
  font-size: var(--sa-fs-14);
  font-weight: var(--sa-fw-medium);
  color: var(--sa-text-1);
}
.screen__alert-icon {
  color: var(--sa-danger);
}
.screen__alert-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: var(--sa-space-2);
}
.screen__alert-item {
  display: flex;
  align-items: center;
  gap: var(--sa-space-2);
  padding: var(--sa-space-2) var(--sa-space-3);
  background: var(--sa-bg-card);
  border: 1px solid var(--sa-border-1);
  border-radius: var(--sa-radius-md);
  cursor: pointer;
  font-size: var(--sa-fs-13);
}
.screen__alert-item:hover {
  box-shadow: var(--sa-shadow-float);
}
.screen__alert-title {
  color: var(--sa-text-1);
}
.screen__alert-owner,
.screen__alert-overdue {
  color: var(--sa-text-3);
  font-size: var(--sa-fs-12);
}
.screen__alert-overdue {
  color: var(--sa-danger);
}
.screen__alert-go {
  margin-left: auto;
  color: var(--sa-primary);
  font-size: var(--sa-fs-12);
}

.screen__kpi {
  margin-bottom: var(--sa-space-4);
}
.screen__charts :deep(.el-col) {
  margin-bottom: var(--sa-space-4);
}
</style>
