<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Bell } from "@element-plus/icons-vue";
import { http } from "@/utils/http";

/**
 * 消息铃铛（M0105 消息中心入口，AC-MS6-1：查看最新消息 ≤2 步 = 登录 → 点铃铛）
 * ---------------------------------------------------------------------------
 * docs/15 交互硬约束（必守）：
 *  ① 业务类消息（公告 / 审批单 / 请假单 / 会议通知）一律不做站内正文预览。
 *     点击 = 直接跳转业务对象详情页，已读回执由业务端记录（AC-MS8-1）。
 *  ② 未读角标只反映「站内信自身」的已读/未读，不代表业务对象已读状态。
 *     列表里不放"业务已读/未读"字段，避免双轨口径。
 *  ③ 点击后先标记站内信已读再跳走；跳转失败（40303/40304）由全局错误处理器提示。
 */
interface MsgItem {
  id: string;
  title: string;
  category: string;
  categoryText: string;
  sentAt: string;
  read: boolean;
  /** 业务对象详情页路由，由服务端下发 */
  linkTo: string;
}

const router = useRouter();
const unread = ref(0);
const list = ref<MsgItem[]>([]);
const drawer = ref(false);
const loading = ref(false);

async function load() {
  const res = await http.request<{ list: MsgItem[]; unread: number }>(
    "get",
    "/api/messages",
    { params: { page: 1, size: 20 } }
  );
  list.value = res.list;
  unread.value = res.unread;
}

async function open() {
  drawer.value = true;
  loading.value = true;
  try {
    await load();
  } finally {
    loading.value = false;
  }
}

async function go(msg: MsgItem) {
  // 先置站内信已读（仅影响角标），再跳业务详情
  if (!msg.read) {
    await http.request<void>("post", `/api/messages/${msg.id}/read`);
    msg.read = true;
    unread.value = Math.max(0, unread.value - 1);
  }
  drawer.value = false;
  router.push(msg.linkTo);
}

onMounted(load);
defineExpose({ reload: load });
</script>

<template>
  <div class="bell">
    <el-badge :value="unread" :hidden="unread === 0" :max="99" type="danger">
      <el-button :icon="Bell" circle text @click="open" aria-label="消息中心" />
    </el-badge>

    <el-drawer v-model="drawer" title="消息中心" size="420px">
      <el-empty v-if="!loading && list.length === 0" description="暂无消息" />
      <ul v-else v-loading="loading" class="bell__list">
        <li
          v-for="m in list"
          :key="m.id"
          class="bell__item"
          :class="{ 'is-unread': !m.read }"
          @click="go(m)"
        >
          <div class="bell__row">
            <el-tag size="small" effect="light" round>{{ m.categoryText }}</el-tag>
            <span v-if="!m.read" class="bell__dot" />
            <time class="bell__time">{{ m.sentAt }}</time>
          </div>
          <!-- 只展示标题 + 分类 + 时间：不做正文预览（docs/15 排除项） -->
          <p class="bell__title">{{ m.title }}</p>
          <span class="bell__go">打开业务详情 →</span>
        </li>
      </ul>
      <template #footer>
        <el-button link type="primary" @click="router.push('/dashboard/messages')">
          查看全部消息
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.bell__list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.bell__item {
  padding: var(--sa-space-3) var(--sa-space-3);
  border-bottom: 1px solid var(--sa-border-2);
  cursor: pointer;
  border-radius: var(--sa-radius-md);
}
.bell__item:hover {
  background: var(--sa-bg-hover);
}
.bell__row {
  display: flex;
  align-items: center;
  gap: var(--sa-space-2);
}
.bell__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sa-danger);
}
.bell__time {
  margin-left: auto;
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
}
.bell__title {
  margin: var(--sa-space-2) 0 var(--sa-space-1);
  font-size: var(--sa-fs-14);
  color: var(--sa-text-1);
  line-height: var(--sa-lh-normal);
}
.bell__item.is-unread .bell__title {
  font-weight: var(--sa-fw-medium);
}
.bell__go {
  font-size: var(--sa-fs-12);
  color: var(--sa-primary);
}
</style>
