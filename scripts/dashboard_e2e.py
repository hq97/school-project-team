#!/usr/bin/env python3
"""校长端看板聚合 + 详情端点 E2E（采购总览/异常清单/趋势/报修统计 + 三级范围收窄）。

运行前提：后端已在 8080 启动、V8/V9 已落库。无第三方依赖（仅标准库）。
用法：python3 scripts/dashboard_e2e.py
"""
import json
import sys
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
PASS, FAIL = [], []


def check(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print(f"  [{'PASS' if cond else 'FAIL'}] {name}" + (f"  -> {detail}" if detail else ""))


def api(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw}


def login(username, password="Admin@123"):
    st, b = api("POST", "/api/auth/login", body={"username": username, "password": password})
    assert st == 200, f"login {username} failed: {st} {b}"
    return b["data"]["token"]


def main():
    print("== 登录 ==")
    teacher_li = login("teacher_li")
    teacher_wang = login("teacher_wang")
    logistics = login("logistics_wang")
    principal = login("principal_zhao")
    grade = login("grade_zhang")
    check("5 账号登录成功", True)

    # ---- 基线：grade 当前可见采购（用已证实的列表端点） ----
    st, b = api("GET", "/api/procurement/projects", grade)
    grade_visible_before = len(b["data"]) if st == 200 else 0
    grade_budget_before = sum(x.get("budget", 0) or 0 for x in b["data"]) if st == 200 else 0

    # ---- 造数：grade 一条（dept_grade1），principal 一条（dept_school） ----
    st, b = api("POST", "/api/procurement/projects", grade, {"name": "七年级看板采购", "budget": 3000})
    check("grade 发起采购", st == 200, f"status={st}")
    grade_proc = b["data"]["id"]

    st, b = api("POST", "/api/procurement/projects", principal, {"name": "全校看板采购", "budget": 8000})
    check("principal 发起采购", st == 200, f"status={st}")
    principal_proc = b["data"]["id"]

    # ---- 造数：teacher_li 领用 + 报修（用于详情/响应统计） ----
    st, b = api("POST", "/api/material/requisitions", teacher_li, {"assetId": "asset_pen", "quantity": 1})
    check("teacher_li 提交领用", st == 200, f"status={st}")
    mat_id = b["data"]["id"]

    st, b = api("POST", "/api/repair/tickets", teacher_li, {"spaceId": "space_lab1", "faultType": "MULTIMEDIA"})
    check("teacher_li 提交报修", st == 200, f"status={st}")
    rep_id = b["data"]["id"]
    st, b = api("PUT", f"/api/repair/tickets/{rep_id}/assign", logistics, {"assignedTo": "王后勤"})
    check("logistics 分派报修", st == 200 and b["data"]["status"] == "ASSIGNED", f"status={st}")

    # ---- 1. 采购总览（M0201）：形状 + ALL/GRADE 收窄 ----
    st, b = api("GET", "/api/dashboard/procurement/summary", principal)
    check("principal summary 200", st == 200, f"status={st}")
    pd = b["data"]
    check("summary 字段齐全", all(k in pd for k in ("projectCount", "budgetTotal", "executedAmount", "statusCounts")),
          str(sorted(pd.keys())))

    st, b = api("GET", "/api/dashboard/procurement/summary", grade)
    check("grade summary 200", st == 200, f"status={st}")
    gd = b["data"]
    check("GRADE 收窄：只含本年级项目数", gd["projectCount"] == grade_visible_before + 1,
          f"grade={gd['projectCount']} vs expect={grade_visible_before + 1}")
    check("GRADE 收窄：预算只含本年级", gd["budgetTotal"] == grade_budget_before + 3000,
          f"gradeBudget={gd['budgetTotal']} vs expect={grade_budget_before + 3000}")
    check("ALL 覆盖全校：principal 数 >= grade 数 + 1（含全校项目）", pd["projectCount"] >= gd["projectCount"] + 1,
          f"principal={pd['projectCount']} grade={gd['projectCount']}")
    check("ALL 预算 >= grade 预算 + 8000", pd["budgetTotal"] >= gd["budgetTotal"] + 8000,
          f"principalBudget={pd['budgetTotal']} gradeBudget={gd['budgetTotal']}")

    # ---- 2. 异常清单（M0101） ----
    st, b = api("GET", "/api/dashboard/alerts", principal)
    check("alerts 200 list", st == 200 and isinstance(b["data"], list), f"status={st} count={len(b.get('data', []))}")

    # ---- 3. 趋势（M0204，principal 有 / grade、logistics 无） ----
    st, b = api("GET", "/api/dashboard/trends?dim=TERM", principal)
    check("trends TERM 200", st == 200 and all(k in b.get("data", {}) for k in ("dim", "procurement", "material", "repair")),
          f"status={st}")
    st, b = api("GET", "/api/dashboard/trends?dim=MONTH", principal)
    check("trends MONTH 200", st == 200 and b.get("data", {}).get("dim") == "MONTH", f"status={st}")
    st, b = api("GET", "/api/dashboard/trends?dim=TERM", grade)
    check("grade 无 M0204 -> 40301", st == 403 and b.get("code") == 40301, f"code={b.get('code')}")
    st, b = api("GET", "/api/dashboard/trends?dim=TERM", logistics)
    check("logistics 无 M0204 -> 40301", st == 403 and b.get("code") == 40301, f"code={b.get('code')}")

    # ---- 4. 报修响应统计（M0404，logistics） ----
    st, b = api("GET", "/api/repair/stats", logistics)
    check("repair stats 200", st == 200 and all(k in b.get("data", {}) for k in ("avgResponseSeconds", "overdueCount", "overdueTickets")),
          f"status={st}")
    check("avgResponseSeconds >= 0", st == 200 and b["data"]["avgResponseSeconds"] >= 0,
          str(b.get("data", {}).get("avgResponseSeconds")))
    st, b = api("GET", "/api/repair/stats", grade)
    check("grade 无 M0404 -> 40301", st == 403 and b.get("code") == 40301, f"code={b.get('code')}")

    # ---- 5. 详情端点（matchRecord 判归属，跨本人 40304 防枚举） ----
    st, b = api("GET", f"/api/material/requisitions/{mat_id}", teacher_li)
    check("material detail owner 200", st == 200 and b["data"]["id"] == mat_id, f"status={st}")
    st, b = api("GET", f"/api/material/requisitions/{mat_id}", teacher_wang)
    check("material detail 跨本人 40304", st == 403 and b.get("code") == 40304, f"code={b.get('code')}")

    st, b = api("GET", f"/api/repair/tickets/{rep_id}", teacher_li)
    check("repair detail owner 200", st == 200 and b["data"]["id"] == rep_id, f"status={st}")
    st, b = api("GET", f"/api/repair/tickets/{rep_id}", grade)
    check("repair detail 本年级可见 200", st == 200 and b["data"]["id"] == rep_id, f"status={st}")
    st, b = api("GET", f"/api/repair/tickets/{rep_id}", teacher_wang)
    check("repair detail 跨本人 40304", st == 403 and b.get("code") == 40304, f"code={b.get('code')}")

    print(f"\n===== 汇总：PASS {len(PASS)} / FAIL {len(FAIL)} =====")
    if FAIL:
        print("失败项：" + ", ".join(FAIL))
        sys.exit(1)
    print("全部通过")


if __name__ == "__main__":
    main()
