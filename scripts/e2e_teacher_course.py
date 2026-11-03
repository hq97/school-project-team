#!/usr/bin/env python3
"""教务域 REQ-F1 教师任课表 E2E（dean 建任课→列表含它→teacher_li 仅本人→越权删除被拒）。

运行前提：后端已在 8080 启动、V14 已落库（teacher_course 种子 tc_demo_1..4 + dean_li 账号）。
用法：python3 scripts/e2e_teacher_course.py
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
    dean = login("dean_li")
    teacher = login("teacher_li")
    check("登录 dean_li / teacher_li", True)

    # ---- a. dean(R-DEAN/ALL) 建任课（归属 dean 本人） ----
    st, b = api("POST", "/api/schedule/teacher-courses", dean,
                {"teacherId": "user_dean_li", "classId": "dept_class2", "subject": "物理"})
    check("dean 建任课 200", st == 200, f"status={st} body={b if st != 200 else ''}")
    new_id = b["data"]["id"]
    check("dean 建任课返回 id + termId 当前学期",
          bool(new_id) and b["data"]["termId"] == "2025-2026-FIRST",
          f"id={new_id} termId={b['data'].get('termId')}")

    # ---- b. dean 列表含新任课（ALL 范围 = 4 种子 + 1 新） ----
    st, b = api("GET", "/api/schedule/teacher-courses?termId=2025-2026-FIRST", dean)
    ids = [x["id"] for x in b.get("data", [])]
    check("dean 列表含新建任课", st == 200 and new_id in ids,
          f"status={st} count={len(ids)} contains_new={new_id in ids}")

    # ---- c. teacher_li 列表仅本人任课（SELF），不含 dean 的任课 ----
    st, b = api("GET", "/api/schedule/teacher-courses?termId=2025-2026-FIRST", teacher)
    rows = b.get("data", [])
    owners = {x["teacherId"] for x in rows}
    check("teacher_li 列表仅本人任课", st == 200 and owners == {"user_teacher_li"},
          f"owners={owners} count={len(rows)}")
    check("teacher_li 列表不含 dean 任课", new_id not in {x["id"] for x in rows})

    # ---- d. teacher_li 越权删 dean 的任课 → 403（按钮门禁 40302 先行拦截） ----
    st, b = api("DELETE", f"/api/schedule/teacher-courses/{new_id}", teacher)
    check("teacher_li 越权删 dean 任课被拒(403)",
          st == 403 and isinstance(b.get("code"), int) and 40300 <= b["code"] <= 40399,
          f"status={st} code={b.get('code')}")

    # ---- e. dean 复删新建任课（清理，保持可重跑） ----
    st, b = api("DELETE", f"/api/schedule/teacher-courses/{new_id}", dean)
    check("dean 删除新建任课 200（清理）", st == 200, f"status={st} body={b if st != 200 else ''}")

    print(f"\n===== 汇总：PASS {len(PASS)} / FAIL {len(FAIL)} =====")
    if FAIL:
        print("失败项：" + ", ".join(FAIL))
        sys.exit(1)
    print("教师任课表 API 冒烟全部通过")


if __name__ == "__main__":
    main()
