<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import ConflictAlert from "@/components/ConflictAlert/index.vue";
import { listAssets, listDictItems, listSpaces, isConflict } from "@/api/biz";
import type { AssetItem, BizError, SpaceItem, TimeSlot } from "@/api/types";
import { CURRENT_WEEK, loadFreeSlots, type FieldDef, type QuickSchema } from "./schemas";

/**
 * QuickApplyDrawer —— 教师端高频事务统一申请面板
 * ---------------------------------------------------------------------------
 * 为什么是「抽屉 + 一屏表单」而不是「跳转页面 + 分步向导」：
 *   - 教师的高频事务是"想到就办"，跳转页面 = 丢失上下文 + 多一次等待；
 *   - 抽屉从工作台原地展开，表单字段 ≤5 个，教师不需要导航即可办完；
 *   - 步数 = ①点快捷卡（1） ②提交（2），场地类默认选中空闲时段后也是 2 步。
 *
 * 权限：v-auth 只隐藏入口（体验优化），服务端仍是安全边界（40302 / 40303）。
 */
const props = defineProps<{
  schema: QuickSchema;
  modelValue: boolean;
  /** 预填值（如从场地格子点进来时已带 spaceId / week / slot） */
  prefill?: Record<string, unknown>;
}>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submitted", res: Record<string, unknown>): void;
}>();

const form = reactive<Record<string, unknown>>({});
const submitting = ref(false);
const conflict = ref<BizError | null>(null);
const assets = ref<AssetItem[]>([]);
const spaces = ref<SpaceItem[]>([]);
const freeSlots = ref<{ slot: TimeSlot; slotText: string }[]>([]);
const loadingSlots = ref(false);
/** 字典选项缓存：dictCode -> options */
const dictMap = ref<Record<string, { label: string; value: string }[]>>({});

function optionsOf(f: FieldDef) {
  if (f.options?.length) return f.options;
  return f.dictCode ? (dictMap.value[f.dictCode] ?? []) : [];
}

/** 默认值：能带就带，减少教师填空 */
function reset() {
  conflict.value = null;
  freeSlots.value = [];
  for (const f of props.schema.fields) {
    if (f.type === "number" && f.key === "week") form[f.key] = CURRENT_WEEK.value;
    else if (f.type === "number") form[f.key] = f.min ?? 1;
    else form[f.key] = undefined;
  }
  // 预填（从场地格子 / 快捷入口带过来的上下文），减少教师重复选择
  if (props.prefill) Object.assign(form, props.prefill);
}
watch(() => [props.schema.code, props.modelValue], reset, { immediate: true });

const assetField = computed(() => props.schema.fields.find((f) => f.type === "asset"));
const spaceField = computed(() => props.schema.fields.find((f) => f.type === "space"));
const slotField = computed(() => props.schema.fields.find((f) => f.type === "slot"));

/** 资产 / 场地选项：按域隔离的 assetType、spaceType 拉取，服务端已过滤 */
async function ensureOptions() {
  if (assetField.value?.assetType) {
    assets.value = await listAssets(assetField.value.assetType);
  }
  if (spaceField.value?.spaceType) {
    spaces.value = await listSpaces(spaceField.value.spaceType);
  }
  const codes = [...new Set(props.schema.fields.map((f) => f.dictCode).filter(Boolean))] as string[];
  for (const c of codes) {
    if (!dictMap.value[c]) dictMap.value[c] = await listDictItems(c);
  }
}

/** 选中场地 + 周次后自动拉空闲时段，并默认选中第一个（把 3 步压回 2 步） */
async function ensureSlots() {
  const spaceId = form[spaceField.value?.key ?? "spaceId"] as string | undefined;
  const week = Number(form["week"] ?? CURRENT_WEEK.value);
  if (!slotField.value || !spaceId || !week) return;
  loadingSlots.value = true;
  try {
    freeSlots.value = await loadFreeSlots(spaceId, week);
    if (freeSlots.value.length > 0 && !form.slot) {
      form.slot = freeSlots.value[0].slot;
    }
  } finally {
    loadingSlots.value = false;
  }
}

watch(
  () => [form[spaceField.value?.key ?? "spaceId"], form["week"], props.modelValue],
  () => props.modelValue && ensureSlots()
);

watch(() => props.modelValue, (v) => v && ensureOptions());

const selectedAsset = computed(() => assets.value.find((a) => a.id === form.assetId));
const slotTextOf = (s?: TimeSlot) =>
  freeSlots.value.find((x) => JSON.stringify(x.slot) === JSON.stringify(s))?.slotText ?? "";

/**
 * M0703「全校课表总览」入口是否可见（菜单权限侧）。
 * ---------------------------------------------------------------------------
 * 动态路由由后端菜单权限下发：没权限的角色根本不会注册这条路由，
 * 所以用路由表判断即可，不需要另引权限 store。
 * 与服务端下发的 `hasOccupancyDetail` 取交集后才给「查看该场地课表」入口。
 *
 * ⚠️ **成立前提（架构已确认，改动前必读）**：
 * M0703 必须持续走「后端菜单权限下发的动态路由」。若哪天被改成静态路由
 * （预置进 constantRoutes），`router.getRoutes()` 会恒为 true，本判断失效，
 * 退化为"谁都看得见课表入口"——届时须改为读权限 store 的菜单权限列表。
 * 注意：即使误判也只是**入口可见性**出错，不会造成数据泄露
 * （真正的门是服务端 `hasOccupancyDetail` 布尔 + occupiedBy 脱敏）。
 */
const router = useRouter();
const canViewTimetable = computed(() =>
  router.getRoutes().some((r) => r.path === "/timetable/school")
);

async function submit() {
  const missing = props.schema.fields.find((f) => f.required && (form[f.key] === undefined || form[f.key] === ""));
  if (missing) {
    ElMessage.warning(`请填写「${missing.label}」`);
    return;
  }
  submitting.value = true;
  conflict.value = null;
  try {
    const res = await props.schema.submit({ ...form });
    ElMessage.success(props.schema.successText(res));
    emit("submitted", res);
    emit("update:modelValue", false);
  } catch (err) {
    if (isConflict(err)) {
      // 冲突/库存不足：就地展示「发生了什么 + 为什么 + 怎么办」，不弹通用报错
      conflict.value = (err as { data: BizError }).data;
    } else {
      // 40301~40304 由 axios 全局拦截器统一提示，这里不再重复弹窗
    }
  } finally {
    submitting.value = false;
  }
}

/** ConflictAlert 一键改约：直接用建议时段重提，不回退表单 */
async function retrySlot(slot: TimeSlot) {
  form.slot = slot;
  await submit();
}
function retryQty(qty: number) {
  form.qty = qty;
  submit();
}
function close() {
  emit("update:modelValue", false);
}

const cols = (f: FieldDef) => (f.cols === 24 ? "is-full" : "is-half");
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="schema.title"
    size="480px"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div class="qa">
      <!-- 步数自证：把「几步」直接写在界面上，教师不用猜 -->
      <div class="qa__steps">预计 {{ schema.stepText }}</div>

      <el-form label-position="top" @submit.prevent>
        <div class="qa__grid">
          <template v-for="f in schema.fields" :key="f.key">
            <!-- 资产（统一 asset 台账） -->
            <el-form-item v-if="f.type === 'asset'" :class="cols(f)" :label="f.label" required>
              <el-select v-model="form.assetId" :placeholder="f.placeholder" filterable class="qa__ctl">
                <el-option
                  v-for="a in assets"
                  :key="a.id"
                  :label="`${a.name}（可用 ${a.availableQty}${a.unit}）`"
                  :value="a.id"
                />
              </el-select>
              <div v-if="selectedAsset" class="qa__tip">
                库存可用 {{ selectedAsset.availableQty }}{{ selectedAsset.unit }}<template v-if="selectedAsset.location"> · {{ selectedAsset.location }}</template>
              </div>
            </el-form-item>

            <!-- 场地（统一 space） -->
            <el-form-item v-else-if="f.type === 'space'" :class="cols(f)" :label="f.label" required>
              <el-select v-model="form[f.key]" placeholder="请选择" filterable class="qa__ctl">
                <el-option
                  v-for="s in spaces"
                  :key="s.spaceId"
                  :label="`${s.spaceName}（${s.capacity}人）`"
                  :value="s.spaceId"
                />
              </el-select>
            </el-form-item>

            <!-- 时段：服务端已排除排课占用，默认选中第一个空闲时段 -->
            <el-form-item v-else-if="f.type === 'slot'" :class="cols(f)" :label="f.label" required>
              <div v-loading="loadingSlots" class="qa__slots">
                <el-radio-group v-model="form.slot">
                  <el-radio-button v-for="s in freeSlots" :key="s.slotText" :value="s.slot">
                    {{ s.slotText }}
                  </el-radio-button>
                </el-radio-group>
                <div v-if="!loadingSlots && freeSlots.length === 0" class="qa__tip">
                  本周该场地已排满，请换一个周次。
                </div>
              </div>
              <div v-if="f.tip" class="qa__tip">{{ f.tip }}</div>
            </el-form-item>

            <!-- 数字 -->
            <el-form-item v-else-if="f.type === 'number'" :class="cols(f)" :label="f.label" required>
              <el-input-number
                v-model="form[f.key]"
                :min="f.min"
                :max="f.max"
                class="qa__ctl"
                controls-position="right"
              />
            </el-form-item>

            <!-- 下拉（字典项只读消费：字典是业务选项来源，不作为大屏指标） -->
            <el-form-item v-else-if="f.type === 'select'" :class="cols(f)" :label="f.label" required>
              <el-select v-model="form[f.key]" placeholder="请选择" class="qa__ctl">
                <el-option v-for="o in optionsOf(f)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>

            <!-- 多行文本 -->
            <el-form-item v-else-if="f.type === 'textarea'" :class="cols(f)" :label="f.label" required>
              <el-input
                v-model="form[f.key]"
                type="textarea"
                :rows="3"
                :maxlength="f.max"
                show-word-limit
                :placeholder="f.placeholder"
              />
            </el-form-item>

            <!-- 单行文本 -->
            <el-form-item v-else :class="cols(f)" :label="f.label" required>
              <el-input v-model="form[f.key]" :maxlength="f.max" :placeholder="f.placeholder" />
            </el-form-item>
          </template>
        </div>
      </el-form>

      <!-- 冲突 / 库存不足 / 名额已满：三段式反馈 + 一键重提 -->
      <ConflictAlert
        v-if="conflict"
        class="qa__conflict"
        :error="conflict"
        :can-view-timetable="canViewTimetable"
        @retry-slot="retrySlot"
        @retry-qty="retryQty"
        @close="conflict = null"
      />

      <div v-if="slotField && form.slot" class="qa__tip">
        已选：{{ slotTextOf(form.slot as TimeSlot) }}
      </div>
    </div>

    <template #footer>
      <div class="qa__footer">
        <el-button @click="close">取消</el-button>
        <el-button
          v-auth="schema.btnCode"
          type="primary"
          size="large"
          :loading="submitting"
          @click="submit"
        >
          提交
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.qa__steps {
  margin-bottom: var(--sa-space-4);
  padding: var(--sa-space-2) var(--sa-space-3);
  background: var(--sa-primary-bg);
  border-radius: var(--sa-radius-md);
  font-size: var(--sa-fs-12);
  color: var(--sa-primary);
}
.qa__grid {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--sa-space-4);
}
.qa__grid :deep(.is-half) {
  width: calc(50% - var(--sa-space-2));
}
.qa__grid :deep(.is-full) {
  width: 100%;
}
.qa__ctl {
  width: 100%;
}
.qa__slots {
  width: 100%;
  min-height: 32px;
}
.qa__tip {
  margin-top: var(--sa-space-1);
  font-size: var(--sa-fs-12);
  color: var(--sa-text-3);
  line-height: var(--sa-lh-normal);
}
.qa__conflict {
  margin-top: var(--sa-space-4);
}
.qa__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--sa-space-2);
}
</style>
