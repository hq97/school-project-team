const Layout = () => import("@/layout/index.vue");

/**
 * 中学校务数字化平台 · 业务路由（按 docs/03 菜单树 path 注册）
 * ---------------------------------------------------------------------------
 * 当前为「门禁4 框架整合」阶段：仅挂载 design/ 已有原型的 8 个页面，
 * 其余菜单（M02 采购 / M05 系统 / M06 排课 / M07 课表 / M10 违纪 / M11 评优 /
 * M14 体育等）在 B runway 业务开发阶段逐菜单补齐。
 *
 * 约定：
 *  - path / name 严格对应 docs/03 菜单编码，勿随意改动（越权审计依赖）；
 *  - 组件指向 design/ 原型页面（后续业务开发替换为真实实现）；
 *  - meta.icon 用 Element Plus 图标集（"ep/xxx"），meta.title 用 docs/03 菜单名。
 */
export default [
  // M01 工作台
  {
    path: "/dashboard",
    name: "Dashboard",
    component: Layout,
    redirect: "/dashboard/my-affairs",
    meta: {
      icon: "ep/odometer",
      title: "工作台",
      rank: 1
    },
    children: [
      {
        path: "/dashboard/my-affairs",
        name: "MyAffairs",
        component: () => import("@/views/dashboard/workbench/index.vue"),
        meta: { title: "我的事务" }
      },
      {
        path: "/dashboard/bigscreen",
        name: "BigScreen",
        component: () => import("@/views/dashboard/bigscreen/index.vue"),
        meta: { title: "数据大屏" }
      }
    ]
  },
  // M03 物资领用
  {
    path: "/material",
    name: "Material",
    component: Layout,
    redirect: "/material/apply",
    meta: {
      icon: "ep/box",
      title: "物资领用",
      rank: 2
    },
    children: [
      {
        path: "/material/apply",
        name: "MaterialApply",
        component: () => import("@/views/material/apply/index.vue"),
        meta: { title: "领用申请" }
      }
    ]
  },
  // M04 报修预约
  {
    path: "/repair",
    name: "Repair",
    component: Layout,
    redirect: "/repair/submit",
    meta: {
      icon: "ep/tools",
      title: "报修预约",
      rank: 3
    },
    children: [
      {
        path: "/repair/submit",
        name: "RepairSubmit",
        component: () => import("@/views/repair/submit/index.vue"),
        meta: { title: "报修提交" }
      }
    ]
  },
  // M08 调课管理
  {
    path: "/schedule-change",
    name: "ScheduleChange",
    component: Layout,
    redirect: "/schedule-change/apply",
    meta: {
      icon: "ep/sort",
      title: "调课管理",
      rank: 4
    },
    children: [
      {
        path: "/schedule-change/apply",
        name: "ScheduleChangeApply",
        component: () => import("@/views/schedule-change/apply/index.vue"),
        meta: { title: "调课/代课申请" }
      }
    ]
  },
  // M09 请假管理
  {
    path: "/leave",
    name: "Leave",
    component: Layout,
    redirect: "/leave/apply",
    meta: {
      icon: "ep/calendar",
      title: "请假管理",
      rank: 5
    },
    children: [
      {
        path: "/leave/apply",
        name: "LeaveApply",
        component: () => import("@/views/leave/apply/index.vue"),
        meta: { title: "请假申请" }
      }
    ]
  },
  // M12 党政办公
  {
    path: "/party",
    name: "Party",
    component: Layout,
    redirect: "/party/meeting-room",
    meta: {
      icon: "ep/office-building",
      title: "党政办公",
      rank: 6
    },
    children: [
      {
        path: "/party/meeting-room",
        name: "MeetingRoom",
        component: () => import("@/views/party/meeting-room/index.vue"),
        meta: { title: "会议室预约" }
      }
    ]
  },
  // M13 科学教育
  {
    path: "/science",
    name: "Science",
    component: Layout,
    redirect: "/science/lab",
    meta: {
      icon: "ep/school",
      title: "科学教育",
      rank: 7
    },
    children: [
      {
        path: "/science/lab",
        name: "ScienceLab",
        component: () => import("@/views/science/lab/index.vue"),
        meta: { title: "实验预约" }
      }
    ]
  }
] satisfies Array<RouteConfigsTable>;
