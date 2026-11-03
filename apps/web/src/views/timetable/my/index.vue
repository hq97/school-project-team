<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import { listMyTimetable, listScheduleSpaces } from "@/api/biz";
import type { SpaceItem, TimetableEntry } from "@/api/types";

/**
 * 我的课表 M0701 /timetable/my：教师周课表（1 步即见）。
 * 行=节次 1..10，列=周一..周五，格内显示学科 + 教室（代课标记）。
 * 读本人课表，无需按钮权限。
 */

const DAYS = ["周一", "周二", "周三", "周四", "周五"];
const PERIODS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

const loading = ref(false);
const week = ref(1);
const entries = ref<TimetableEntry[]>([]);
const spaceMap = ref<Record<string, string>>({});

const spaceName = (id?: string) => (id ? (spaceMap.value[id] ?? id) : "");

const grid = computed(() => {
  const m: Record<string, TimetableEntry> = {};
  for (const e of entries.value) m[`${e.dayOfWeek}-${e.period}`] = e;
  return m;
});

const rows = computed(() =>
  PERIODS.map((p) => ({
    period: p,
    cells: DAYS.map((_, idx) => grid.value[`${idx + 1}-${p}`] ?? null)
  }))
);

async function load() {
  loading.value = true;
  try {
    entries.value = await listMyTimetable(week.value);
  } catch {
    ElMessage.error("加载课表失败");
  } finally {
    loading.value = false;
  }
}

async function loadSpaces() {
  try {
    const spaces: SpaceItem[] = await listScheduleSpaces();
    spaceMap.value = Object.fromEntries(spaces.map((s) => [s.id, s.spaceName]));
  } catch {
    /* 回退显示 spaceId */
  }
}

onMounted(() => {
  load();
  loadSpaces();
});
</script>

<template>
  <div class="tt sa-page">
    <header class="tt__hd">
      <div>
        <h2 class="tt__title">我的课表</h2>
        <p class="tt__sub">按周查看本人课节；代课标记表示本课由他人代上</p>
      </div>
      <div class="tt__tools">
        <el-select v-model="week" style="width: 140px" @change="load">
          <el-option v-for="w in 30" :key="w" :label="`第 ${w} 周`" :value="w" />
        </el-select>
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
      </div>
    </header>

    <section class="sa-card tt__card">
      <table v-loading="loading" class="tt__grid">
        <thead>
          <tr>
            <th class="tt__cell tt__head tt__period-col">节次</th>
            <th v-for="(d, i) in DAYS" :key="d" class="tt__cell tt__head">
              {{ d }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.period">
            <td class="tt__cell tt__period">第{{ r.period }}节</td>
            <td v-for="(c, i) in r.cells" :key="i" class="tt__cell">
              <template v-if="c">
                <div class="tt__subject">{{ c.subject }}</div>
                <div class="tt__space">{{ spaceName(c.spaceId) }}</div>
                <div v-if="c.substituteTeacherId" class="tt__sub">代课</div>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.tt__hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sa-space-4);
  margin-bottom: var(--sa-space-5);
}
.tt__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.tt__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.tt__tools {
  display: flex;
  gap: var(--sa-space-2);
}
.tt__card {
  padding: var(--sa-space-4);
}
.tt__grid {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.tt__cell {
  border: 1px solid var(--sa-border-2);
  padding: var(--sa-space-2);
  vertical-align: top;
  text-align: center;
  min-height: 44px;
  font-size: var(--sa-fs-13);
}
.tt__head {
  background: var(--sa-bg-hover);
  color: var(--sa-text-2);
  font-weight: var(--sa-fw-medium);
  font-size: var(--sa-fs-13);
}
.tt__period-col {
  width: 64px;
}
.tt__period {
  background: var(--sa-bg-hover);
  color: var(--sa-text-2);
  font-size: var(--sa-fs-12);
}
.tt__subject {
  font-weight: var(--sa-fw-medium);
  color: var(--sa-text-1);
}
.tt__space {
  margin-top: var(--sa-space-1);
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.tt__sub {
  margin-top: var(--sa-space-1);
  display: inline-block;
  padding: 0 var(--sa-space-2);
  border-radius: var(--sa-radius-sm);
  background: var(--sa-warning-bg);
  color: var(--sa-warning);
  font-size: var(--sa-fs-12);
}
</style>
