<script setup lang="ts">
import { InfoFilled } from "@element-plus/icons-vue";
import type { StatDim } from "@/api/types";

/**
 * 统一时间口径切换器（学期 / 自然月）
 * ---------------------------------------------------------------------------
 * 硬约束（docs/04 §1.8、docs/14 AC-DD1-2、docs/01 AC-A5-1）：
 *  1. 默认值必须是「学期」，任何页面都不得默认自然月。
 *  2. 自然月仅为展示维度：切换后可见数据集合、聚合结果与越权判定都不变。
 *     前端不重新取数口径、不本地重算，只把 dim 传给服务端由服务端重排。
 *  3. 组件必须带口径说明（tooltip + 副标题），避免用户/财务误读为"换口径"。
 */
const props = withDefaults(
  defineProps<{
    modelValue: StatDim;
    /** 当前生效学期文案，如「2025-2026 学年 第一学期」 */
    termText?: string;
    disabled?: boolean;
  }>(),
  { modelValue: "TERM", termText: "", disabled: false }
);

const emit = defineEmits<{
  (e: "update:modelValue", v: StatDim): void;
  (e: "change", v: StatDim): void;
}>();

function onPick(v: StatDim) {
  if (v === props.modelValue) return;
  emit("update:modelValue", v);
  emit("change", v);
}
</script>

<template>
  <div class="dim">
    <el-radio-group
      :model-value="modelValue"
      size="small"
      :disabled="disabled"
      @change="onPick"
    >
      <el-radio-button value="TERM">学期</el-radio-button>
      <el-radio-button value="MONTH">自然月</el-radio-button>
    </el-radio-group>

    <el-tooltip
      placement="bottom-start"
      content="统计口径统一为「学期 + 周次」。自然月仅为展示维度，切换后可见数据与权限判定不变。"
    >
      <span class="dim__hint">
        <el-icon><InfoFilled /></el-icon>
        <template v-if="modelValue === 'TERM'">
          当前：{{ termText || "当前学期" }}
        </template>
        <template v-else>当前：自然月（仅展示维度）</template>
      </span>
    </el-tooltip>
  </div>
</template>

<style scoped>
.dim {
  display: inline-flex;
  align-items: center;
  gap: var(--sa-space-2);
}
.dim__hint {
  display: inline-flex;
  align-items: center;
  gap: var(--sa-space-1);
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
  cursor: help;
}
</style>
