<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Search } from "@element-plus/icons-vue";
import QuickApplyDrawer from "@/components/QuickApply/Drawer.vue";
import { schemas } from "@/components/QuickApply/schemas";
import { listAssets } from "@/api/biz";
import type { AssetItem } from "@/api/types";

/**
 * 物资领用申请（M0301 /material/apply）
 * ---------------------------------------------------------------------------
 * 步数（AC-B1-1 ≤3 步，实测 2 步）：
 *   第 1 步：在物资卡上点「领用」（卡上已带可用库存，不用先翻库存查询页）
 *   第 2 步：抽屉里确认数量与用途 → 提交
 * 库存不足（AC-B1-3 不超卖）走 ConflictAlert：明确"可用 N 件 / 你申请 M 件"，
 * 并给「改为申请 N 件」一键重提，不弹通用"操作失败"。
 *
 * 权限：领用按钮 BTN-MAT-APPLY（v-auth 体验优化）；数据范围 SELF（本人申请）
 * 由服务端保证，教师只能看到自己的申请记录（越权 40303）。
 */
const keyword = ref("");
const assets = ref<AssetItem[]>([]);
const loading = ref(false);
const drawer = ref(false);
const schema = schemas.materialApply;

/** 前端本地过滤（后端 /api/material/assets 不接 keyword，物资量小，本地过滤即可） */
const filteredAssets = computed(() => {
  const k = keyword.value.trim();
  if (!k) return assets.value;
  return assets.value.filter((a) => a.name.includes(k));
});

async function load() {
  loading.value = true;
  try {
    assets.value = await listAssets("MATERIAL");
  } finally {
    loading.value = false;
  }
}
onMounted(load);

/** 第 1 步：点卡片上的「领用」→ 预填该物资，抽屉里只填数量与用途 */
const prefill = ref<Record<string, unknown>>();
function openApply(asset: AssetItem) {
  prefill.value = { assetId: asset.id };
  drawer.value = true;
}
</script>

<template>
  <div class="ma sa-page">
    <header class="ma__hd">
      <div>
        <h2 class="ma__title">物资领用申请</h2>
        <p class="ma__sub">选物资 → 填数量用途 → 提交，{{ schema.stepText }}</p>
      </div>
      <div class="ma__search">
        <el-input v-model="keyword" placeholder="搜索物资名称" clearable @keyup.enter="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      </div>
    </header>

    <div v-loading="loading" class="ma__grid">
      <article v-for="a in filteredAssets" :key="a.id" class="ma__card sa-card">
        <div class="ma__card-hd">
          <h3 class="ma__name">{{ a.name }}</h3>
          <el-tag
            :type="a.availableQty === 0 ? 'danger' : a.availableQty < 10 ? 'warning' : 'success'"
            size="small"
            effect="light"
            round
          >
            {{ a.availableQty === 0 ? "无库存" : `可用 ${a.availableQty} 件` }}
          </el-tag>
        </div>
        <p class="ma__loc">{{ a.location || "—" }}</p>
        <div class="ma__card-ft">
          <!-- 第 1 步：直接领用。无库存时禁用，避免用户点了必然失败 -->
          <el-button
            v-auth="schema.btnCode"
            type="primary"
            size="large"
            :disabled="a.availableQty === 0"
            @click="openApply(a)"
          >
            领用
          </el-button>
        </div>
      </article>

      <el-empty v-if="!loading && filteredAssets.length === 0" description="没有可领用的物资" />
    </div>

    <QuickApplyDrawer v-model="drawer" :schema="schema" :prefill="prefill" />
  </div>
</template>

<style scoped>
.ma__hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sa-space-4);
  margin-bottom: var(--sa-space-5);
}
.ma__title {
  margin: 0;
  font-size: var(--sa-fs-20);
  font-weight: var(--sa-fw-bold);
}
.ma__sub {
  margin: var(--sa-space-1) 0 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.ma__search {
  display: flex;
  gap: var(--sa-space-2);
}
.ma__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--sa-space-4);
}
.ma__card {
  display: flex;
  flex-direction: column;
  gap: var(--sa-space-2);
  padding: var(--sa-space-4) var(--sa-space-5);
}
.ma__card-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sa-space-2);
}
.ma__name {
  margin: 0;
  font-size: var(--sa-fs-16);
  font-weight: var(--sa-fw-medium);
}
.ma__loc {
  margin: 0;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.ma__card-ft {
  margin-top: auto;
  display: flex;
  justify-content: flex-end;
}
</style>
