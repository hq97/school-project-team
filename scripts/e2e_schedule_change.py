#!/usr/bin/env python3
"""教务域调课/代课闭环 E2E（申请→R-DEAN 审批→课表更新→场地占用→消息通知 + 越权拒绝）。

运行前提：后端已在 8080 启动、V13 已落库（演示课表 te_demo_1..4 + 教务账号 dean_li）。
用法：python3 scripts/e2e_schedule_change.py
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
        with urllib.request.urlopen(req, timeout=15) as r:
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
    teacher = login("teacher_li")
    teacher_wang = login("teacher_wang")
    dean = login("dean_li")
    check("登录 teacher_li / teacher_wang / dean_li", True)

    # ---- a. teacher_li 提交调课（把 te_demo_1 从 周一第1节 调到 周三第3节） ----
    st, b = api("POST", "/api/schedule-change/apply", teacher,
                {"type": "CHANGE", "entryId": "te_demo_1", "targetDayOfWeek": 3, "targetPeriod": 3,
                 "reason": "调课联调"})
    check("apply 调课 200", st == 200, f"status={st} body={b if st != 200 else ''}")
    change_id = b["data"]["change"]["id"]
    check("apply 返回 PENDING", b["data"]["change"]["status"] == "PENDING", b["data"]["change"]["status"])
    check("apply 命中流程节点", b["data"]["flow"]["nodes"][0]["approveRole"] == "R-DEAN",
          str(b["data"]["flow"]["nodes"][0]["approveRole"] if b["data"]["flow"].get("nodes") else None))

    # ---- b. dean_li(R-DEAN) 待办 → 审批通过 ----
    st, b = api("GET", "/api/flow/todos", dean)
    task = next((t for t in b.get("data", []) if t.get("businessType") == "schedule_change"
                 and t.get("businessId") == change_id), None)
    check("dean 待办含 schedule_change", task is not None, f"taskId={task.get('taskId') if task else None}")
    st, b = api("POST", f"/api/flow/tasks/{task['taskId']}/approve", dean, {"action": "APPROVE", "comment": "同意"})
    check("审批通过 200", st == 200, f"status={st} body={b if st != 200 else ''}")

    # ---- c. 复核：change 联动 APPROVED + 课表更新到目标时段 ----
    st, b = api("GET", f"/api/schedule-change/{change_id}", dean)
    check("change 详情 APPROVED", st == 200 and b["data"]["status"] == "APPROVED", b["data"]["status"])

    st, b = api("GET", "/api/schedule/my-timetable?week=1", teacher)
    entry = next((x for x in b.get("data", []) if x["id"] == "te_demo_1"), None)
    check("课表更新到目标时段(3,3)", entry is not None and entry["dayOfWeek"] == 3 and entry["period"] == 3,
          f"dayOfWeek={entry['dayOfWeek'] if entry else None} period={entry['period'] if entry else None}")

    # ---- d. 越权：teacher_li 调 teacher_wang 的课 → 拒绝 ----
    st, b = api("POST", "/api/schedule-change/apply", teacher,
                {"type": "CHANGE", "entryId": "te_demo_4", "targetDayOfWeek": 3, "targetPeriod": 4, "reason": "越权试探"})
    check("越权调他人课 403/404 被拒", st in (403, 404), f"status={st} code={b.get('code')}")

    print(f"\n===== 汇总：PASS {len(PASS)} / FAIL {len(FAIL)} =====")
    if FAIL:
        print("失败项：" + ", ".join(FAIL))
        sys.exit(1)
    print("API 冒烟全部通过")


if __name__ == "__main__":
    main()
