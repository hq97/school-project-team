#!/usr/bin/env python3
"""后勤业务 P0 核心闭环 E2E（采购/领用/报修/资产 + 三级数据范围）。

运行前提：后端已在 8080 启动、V8 种子已落库。无第三方依赖（仅标准库）。
用法：python3 scripts/logistics_e2e.py
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
    admin = login("admin")
    teacher = login("teacher_li")
    logistics = login("logistics_wang")
    principal = login("principal_zhao")
    grade = login("grade_zhang")
    check("5 账号登录成功", True)

    print("\n== 采购闭环（admin 发起 -> logistics 审批通过 -> APPROVED）==")
    st, b = api("POST", "/api/procurement/projects", admin, {"name": "采购A4纸一批", "budget": 5000})
    check("admin 发起采购", st == 200, f"status={st}")
    proc_id = b["data"]["id"]
    check("采购初始 PENDING", b["data"]["status"] == "PENDING", b["data"]["status"])

    st, b = api("GET", "/api/flow/todos", logistics)
    task = next((t for t in b["data"] if t.get("businessType") == "procurement" and t.get("businessId") == proc_id), None)
    check("logistics 待办含采购", task is not None, f"taskId={task['taskId'] if task else None}")
    st, b = api("POST", f"/api/flow/tasks/{task['taskId']}/approve", logistics,
                {"action": "APPROVE", "comment": "同意"})
    check("审批动作成功", st == 200, f"status={st}")
    st, b = api("GET", f"/api/procurement/projects/{proc_id}", admin)
    check("采购审批后 APPROVED", b["data"]["status"] == "APPROVED", b["data"]["status"])

    print("\n== 领用闭环-通过（teacher 提交 -> 库存原子扣减 -> logistics 通过 -> COMPLETED）==")
    st, b = api("GET", "/api/material/assets", teacher)
    check("可领用资产列表", st == 200 and len(b["data"]) > 0, f"count={len(b.get('data', []))}")
    asset_id = "asset_a4"
    st, b = api("POST", "/api/material/requisitions", teacher, {"assetId": asset_id, "quantity": 2})
    check("teacher 提交领用", st == 200, f"status={st}")
    mat_id = b["data"]["id"]
    st, b = api("GET", "/api/asset/list", logistics)
    a4 = next((x for x in b["data"] if x["id"] == asset_id), None)
    check("库存原子扣减 100->98", a4 is not None and a4["availableQty"] == 98, f"availableQty={a4['availableQty'] if a4 else None}")

    st, b = api("GET", "/api/flow/todos", logistics)
    task = next((t for t in b["data"] if t.get("businessType") == "material" and t.get("businessId") == mat_id), None)
    check("logistics 待办含领用", task is not None)
    st, b = api("POST", f"/api/flow/tasks/{task['taskId']}/approve", logistics, {"action": "APPROVE", "comment": "同意"})
    check("领用审批通过", st == 200, f"status={st}")
    st, b = api("GET", "/api/material/requisitions/my", teacher)
    mine = next((x for x in b["data"] if x["id"] == mat_id), None)
    check("领用状态 COMPLETED", mine is not None and mine["status"] == "COMPLETED", mine["status"] if mine else None)

    print("\n== 领用闭环-驳回（库存回滚）==")
    st, b = api("POST", "/api/material/requisitions", teacher, {"assetId": asset_id, "quantity": 3})
    mat2 = b["data"]["id"]
    st, b = api("GET", "/api/asset/list", logistics)
    a4 = next((x for x in b["data"] if x["id"] == asset_id), None)
    check("再次扣减 98->95", a4["availableQty"] == 95, f"availableQty={a4['availableQty']}")
    st, b = api("GET", "/api/flow/todos", logistics)
    task = next((t for t in b["data"] if t.get("businessType") == "material" and t.get("businessId") == mat2), None)
    st, b = api("POST", f"/api/flow/tasks/{task['taskId']}/approve", logistics, {"action": "REJECT", "comment": "驳回"})
    check("领用审批驳回", st == 200, f"status={st}")
    st, b = api("GET", "/api/material/requisitions/my", teacher)
    mine = next((x for x in b["data"] if x["id"] == mat2), None)
    check("驳回后 REJECTED", mine["status"] == "REJECTED", mine["status"])
    st, b = api("GET", "/api/asset/list", logistics)
    a4 = next((x for x in b["data"] if x["id"] == asset_id), None)
    check("库存回滚 95->98", a4["availableQty"] == 98, f"availableQty={a4['availableQty']}")

    print("\n== 库存不足 -> 40903 ==")
    st, b = api("POST", "/api/material/requisitions", teacher, {"assetId": asset_id, "quantity": 99999})
    check("库存不足 40903", st == 409 and b.get("code") == 40903, f"code={b.get('code')}")

    print("\n== 报修闭环（提交 -> 分派 -> 处理 -> 完成）==")
    st, b = api("GET", "/api/repair/spaces", teacher)
    check("可选场地列表", st == 200 and len(b["data"]) > 0, f"count={len(b.get('data', []))}")
    st, b = api("POST", "/api/repair/tickets", teacher, {"spaceId": "space_c101", "faultType": "MULTIMEDIA"})
    check("提交报修 PENDING", st == 200 and b["data"]["status"] == "PENDING", f"status={st}")
    rep_id = b["data"]["id"]
    st, b = api("PUT", f"/api/repair/tickets/{rep_id}/assign", logistics, {"assignedTo": "王后勤"})
    check("分派 ASSIGNED", st == 200 and b["data"]["status"] == "ASSIGNED", b["data"]["status"])
    st, b = api("PUT", f"/api/repair/tickets/{rep_id}/status", logistics, {"status": "PROCESSING"})
    check("处理中 PROCESSING", st == 200 and b["data"]["status"] == "PROCESSING", b["data"]["status"])
    st, b = api("PUT", f"/api/repair/tickets/{rep_id}/status", logistics, {"status": "COMPLETED"})
    check("完成 COMPLETED", st == 200 and b["data"]["status"] == "COMPLETED", b["data"]["status"])

    print("\n== 三级数据范围（SELF/GRADE/ALL）==")
    st, b = api("POST", "/api/procurement/projects", grade, {"name": "七年级采购", "budget": 1000})
    grade_proc = b["data"]["id"]
    st, b = api("POST", "/api/procurement/projects", principal, {"name": "全校采购", "budget": 9000})
    principal_proc = b["data"]["id"]

    st, b = api("GET", "/api/procurement/projects", grade)
    ids = [x["id"] for x in b["data"]]
    check("R-GRADE 只看到本年级", grade_proc in ids and principal_proc not in ids, f"ids={ids}")

    st, b = api("GET", "/api/procurement/projects", principal)
    ids = [x["id"] for x in b["data"]]
    check("R-PRINCIPAL 看到全校", grade_proc in ids and principal_proc in ids, f"count={len(ids)}")

    st, b = api("GET", "/api/procurement/projects", teacher)
    check("R-TEACHER 无采购菜单 -> 40301", st == 403 and b.get("code") == 40301, f"code={b.get('code')}")

    st, b = api("GET", f"/api/procurement/projects/{principal_proc}", grade)
    check("跨范围详情 -> 40304", st == 403 and b.get("code") == 40304, f"code={b.get('code')}")

    st, b = api("GET", "/api/material/requisitions/my", teacher)
    check("teacher 领用 my 仅本人", st == 200 and all(x["applicantId"] == "user_teacher_li" for x in b["data"]), f"count={len(b.get('data', []))}")

    print(f"\n===== 汇总：PASS {len(PASS)} / FAIL {len(FAIL)} =====")
    if FAIL:
        print("失败项：" + ", ".join(FAIL))
        sys.exit(1)
    print("全部通过")


if __name__ == "__main__":
    main()
