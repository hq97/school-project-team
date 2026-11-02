#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
审批流引擎（docs/07 REQ-D3）端到端验证脚本。
覆盖：模板 CRUD、发起、多级流转、完成、驳回(TERMINATE)、越权 40302/40303。
依赖：后端已启动于 http://localhost:8080；PostgreSQL 可经 psql 直连。
"""

import json
import subprocess
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080"
PSQL = r"D:\work\pgsql16\pgsql\bin\psql.exe"
PASSWORD = "Admin@123"

RESULTS = []


def check(name, cond, detail=""):
    RESULTS.append((name, bool(cond), detail))
    mark = "PASS" if cond else "FAIL"
    print(f"[{mark}] {name}" + (f"  -> {detail}" if detail and not cond else ""))


def psql(sql):
    r = subprocess.run(
        [PSQL, "-h", "localhost", "-U", "postgres", "-d", "school_affairs",
         "-t", "-A", "-c", sql],
        capture_output=True, text=True)
    if r.returncode != 0:
        return []
    return [ln for ln in r.stdout.splitlines() if ln.strip()]


def req(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw}


def login(username):
    st, j = req("POST", "/api/auth/login", body={"username": username, "password": PASSWORD})
    if st != 200 or not j.get("success"):
        raise RuntimeError(f"login {username} failed: {st} {j}")
    return j["data"]["token"]


def assign_roles(admin_tok, user_id, role_ids):
    st, j = req("PUT", f"/api/system/users/{user_id}/roles", token=admin_tok,
                body={"roleIds": role_ids})
    return st, j


def task_id_of(instance_id):
    rows = psql(f'SELECT "id" FROM "flow_task" WHERE "instanceId" = \'{instance_id}\' ORDER BY "seq";')
    return rows


def get_tpl(business_type, token):
    st, j = req("GET", "/api/system/flow-templates", token=token)
    for t in j.get("data", []):
        if t["businessType"] == business_type:
            return t
    return None


def sql_cleanup_e2e():
    """清理历史 E2E 测试数据（实例外键无级联，需按序删除）。"""
    psql("""
        DELETE FROM flow_task WHERE "instanceId" IN (
            SELECT id FROM flow_instance WHERE "templateId" IN (
                SELECT id FROM flow_template WHERE "businessType" LIKE 'E2E_%'));
        DELETE FROM flow_instance WHERE "templateId" IN (
            SELECT id FROM flow_template WHERE "businessType" LIKE 'E2E_%');
        DELETE FROM flow_node WHERE "templateId" IN (
            SELECT id FROM flow_template WHERE "businessType" LIKE 'E2E_%');
        DELETE FROM flow_template WHERE "businessType" LIKE 'E2E_%';
    """)


def main():
    admin = login("admin")

    # ---------- 清理历史 E2E 数据 ----------
    sql_cleanup_e2e()

    # ================= T1 多级审批 happy path（RETURN） =================
    print("\n=== T1 多级审批 happy path ===")
    st, j = req("POST", "/api/system/flow-templates", token=admin, body={
        "code": "E2E_HAPPY", "name": "审批流E2E多级", "businessType": "E2E_HAPPY_BIZ",
        "rejectPolicy": "RETURN",
        "nodes": [
            {"name": "教务主任审批", "approveRole": "R-DEAN"},
            {"name": "系统管理员终审", "approveRole": "R-ADMIN"}
        ]})
    check("T1 创建模板", st == 200 and j.get("success"), f"{st} {j}")
    tpl_id = j["data"]["id"]
    t1_view = get_tpl("E2E_HAPPY_BIZ", admin)
    check("T1 模板节点数=2", t1_view is not None and len(t1_view["nodes"]) == 2)

    # teacher001 授 R-DEAN
    st, _ = assign_roles(admin, "user_f1db29f78122", ["role_R_DEAN"])
    check("T1 teacher001 授 R-DEAN", st == 200, f"{st}")

    # admin 发起
    st, j = req("POST", "/api/flow/instances/start", token=admin,
                body={"businessType": "E2E_HAPPY_BIZ", "businessId": "biz-001"})
    check("T1 发起实例 PENDING", st == 200 and j["data"]["status"] == "PENDING", f"{st} {j}")
    inst_id = j["data"]["id"]
    check("T1 当前节点=1 教务主任审批", j["data"]["currentSeq"] == 1 and j["data"]["currentNodeName"] == "教务主任审批")

    # admin 无 R-DEAN，待办应为空
    st, j = req("GET", "/api/flow/todos", token=admin)
    check("T1 admin 待办为空(角色不匹配)", st == 200 and len(j["data"]) == 0, f"{j['data']}")

    # teacher001 待办应有 1 条
    teacher = login("teacher001")
    st, j = req("GET", "/api/flow/todos", token=teacher)
    check("T1 teacher001 待办=1", st == 200 and len(j["data"]) == 1, f"{j}")
    task1 = j["data"][0]["taskId"]

    # teacher001 通过第 1 级
    st, j = req("POST", f"/api/flow/tasks/{task1}/approve", token=teacher,
                body={"action": "APPROVE", "comment": "同意"})
    check("T1 第1级通过 -> currentSeq=2 PENDING",
          st == 200 and j["data"]["currentSeq"] == 2 and j["data"]["status"] == "PENDING", f"{st} {j}")

    # admin 待办应有第 2 级
    st, j = req("GET", "/api/flow/todos", token=admin)
    check("T1 admin 待办=1(第2级)", st == 200 and len(j["data"]) == 1 and j["data"][0]["seq"] == 2, f"{j}")
    task2 = j["data"][0]["taskId"]

    # admin 通过第 2 级 -> 完成
    st, j = req("POST", f"/api/flow/tasks/{task2}/approve", token=admin,
                body={"action": "APPROVE", "comment": "终审通过"})
    check("T1 第2级通过 -> COMPLETED", st == 200 and j["data"]["status"] == "COMPLETED", f"{st} {j}")

    # 已办：teacher001 与 admin 各 1 条
    st, j = req("GET", "/api/flow/done", token=teacher)
    check("T1 teacher001 已办=1", st == 200 and len(j["data"]) == 1, f"{j}")
    st, j = req("GET", "/api/flow/done", token=admin)
    check("T1 admin 已办>=1", st == 200 and len(j["data"]) >= 1, f"{j}")

    # 实例详情
    st, j = req("GET", f"/api/flow/instances/{inst_id}", token=admin)
    check("T1 实例详情 COMPLETED 且 2 节点", st == 200 and j["data"]["status"] == "COMPLETED" and len(j["data"]["nodes"]) == 2)

    # ================= T2 驳回 TERMINATE =================
    print("\n=== T2 驳回策略 TERMINATE ===")
    st, j = req("POST", "/api/system/flow-templates", token=admin, body={
        "code": "E2E_REJ", "name": "审批流E2E驳回", "businessType": "E2E_REJ_BIZ",
        "rejectPolicy": "TERMINATE",
        "nodes": [{"name": "管理员审批", "approveRole": "R-ADMIN"}]})
    check("T2 创建 TERMINATE 模板", st == 200 and j.get("success"), f"{st} {j}")

    st, j = req("POST", "/api/flow/instances/start", token=admin,
                body={"businessType": "E2E_REJ_BIZ", "businessId": "biz-002"})
    check("T2 发起实例", st == 200 and j["data"]["status"] == "PENDING", f"{st} {j}")
    inst2 = j["data"]["id"]

    st, j = req("GET", "/api/flow/todos", token=admin)
    task_rej = j["data"][0]["taskId"]

    st, j = req("POST", f"/api/flow/tasks/{task_rej}/approve", token=admin,
                body={"action": "REJECT", "comment": "材料不全"})
    check("T2 驳回 -> TERMINATED", st == 200 and j["data"]["status"] == "TERMINATED", f"{st} {j}")

    # ================= T3 越权 40302（角色不命中） =================
    print("\n=== T3 越权 40302 角色不命中 ===")
    st, j = req("POST", "/api/system/flow-templates", token=admin, body={
        "code": "E2E_ROLE", "name": "审批流E2E越权", "businessType": "E2E_ROLE_BIZ",
        "rejectPolicy": "RETURN",
        "nodes": [{"name": "教务主任审批", "approveRole": "R-DEAN"}]})
    check("T3 创建模板(节点 R-DEAN)", st == 200 and j.get("success"), f"{st} {j}")

    st, j = req("POST", "/api/flow/instances/start", token=admin,
                body={"businessType": "E2E_ROLE_BIZ", "businessId": "biz-003"})
    inst3 = j["data"]["id"]
    task3 = task_id_of(inst3)[0]

    # admin 无 R-DEAN，尝试审批 -> 40302
    st, j = req("POST", f"/api/flow/tasks/{task3}/approve", token=admin,
                body={"action": "APPROVE", "comment": "越权尝试"})
    check("T3 非角色审批 -> 403/40302", st == 403 and j.get("code") == 40302, f"{st} {j}")

    # ================= T4 越权 40303（数据范围不覆盖） =================
    print("\n=== T4 越权 40303 数据范围不覆盖 ===")
    st, j = req("POST", "/api/system/flow-templates", token=admin, body={
        "code": "E2E_SCOPE", "name": "审批流E2E范围", "businessType": "E2E_SCOPE_BIZ",
        "rejectPolicy": "RETURN",
        "nodes": [{"name": "年级组长审批", "approveRole": "R-GRADE"}]})
    check("T4 创建模板(节点 R-GRADE)", st == 200 and j.get("success"), f"{st} {j}")

    # teacher001 授 R-GRADE（GRADE 范围；teacher001 无年级归属，与 creator admin 的年级不重叠）
    st, _ = assign_roles(admin, "user_f1db29f78122", ["role_R_GRADE"])
    check("T4 teacher001 授 R-GRADE", st == 200, f"{st}")

    # admin 发起（creator=admin）
    st, j = req("POST", "/api/flow/instances/start", token=admin,
                body={"businessType": "E2E_SCOPE_BIZ", "businessId": "biz-004"})
    inst4 = j["data"]["id"]
    task4 = task_id_of(inst4)[0]

    teacher = login("teacher001")
    st, j = req("GET", "/api/flow/todos", token=teacher)
    check("T4 teacher001 待办为空(GRADE 不覆盖 creator)", st == 200 and len(j["data"]) == 0, f"{j}")

    st, j = req("POST", f"/api/flow/tasks/{task4}/approve", token=teacher,
                body={"action": "APPROVE", "comment": "越权尝试"})
    check("T4 范围不覆盖审批 -> 403/40303", st == 403 and j.get("code") == 40303, f"{st} {j}")

    # ================= 汇总 =================
    print("\n=== T5 删除保护（存在实例不可删） ===")
    st, j = req("POST", "/api/system/flow-templates", token=admin, body={
        "code": "E2E_DEL", "name": "审批流E2E删除", "businessType": "E2E_DEL_BIZ",
        "rejectPolicy": "RETURN",
        "nodes": [{"name": "管理员审批", "approveRole": "R-ADMIN"}]})
    del_tpl_id = j["data"]["id"]
    st, j = req("POST", "/api/flow/instances/start", token=admin,
                body={"businessType": "E2E_DEL_BIZ", "businessId": "biz-005"})
    st, j = req("DELETE", f"/api/system/flow-templates/{del_tpl_id}", token=admin)
    check("T5 有实例的模板删除 -> 409", st == 409 and j.get("code") == 40900, f"{st} {j}")

    # ================= 汇总 =================
    print("\n================ 汇总 ================")
    passed = sum(1 for _, ok, _ in RESULTS if ok)
    failed = [(n, d) for n, ok, d in RESULTS if not ok]
    print(f"通过 {passed}/{len(RESULTS)}")
    if failed:
        print("失败项：")
        for n, d in failed:
            print(f"  - {n}: {d}")
        sys.exit(1)
    print("ALL PASS")


if __name__ == "__main__":
    main()
