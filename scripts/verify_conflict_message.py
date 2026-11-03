#!/usr/bin/env python3
"""验证 GlobalExceptionHandler 修复：冲突/乐观锁具体原因透出（AC-F2-1）。

运行前提：后端已在 8080 启动、V13 演示课表已落库。
用法：python3 scripts/verify_conflict_message.py
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

    # ---- 场景1：同班级同时段冲突（班级已在该时段有课）。
    # te_demo_2 = teacher_li/dept_class1/space_c102 @ 周一第2节；换教师+换场地、保留同班同时段 → 触发班级冲突。
    st, b = api("POST", "/api/schedule/entries", dean,
                {"teacherId": "user_teacher_wang", "classId": "dept_class1",
                 "subject": "数学", "spaceId": "space_meet2",
                 "termId": "2025-2026-FIRST", "week": 1, "dayOfWeek": 1, "period": 2})
    check("排课冲突返回 409", st == 409, f"status={st}")
    check("排课冲突透出具体原因「班级已在该时段有课」",
          b.get("message") == "班级已在该时段有课",
          f"code={b.get('code')} message={b.get('message')!r}")

    # ---- 场景2：发布乐观锁 baseVersion=0 落后 → 具体提示 ----
    st, b = api("POST", "/api/schedule/publish", dean,
                {"termId": "2025-2026-FIRST", "effectiveWeek": 1, "baseVersion": 0})
    check("发布乐观锁返回 409", st == 409, f"status={st}")
    check("乐观锁透出「课表版本已更新，请刷新后重试」",
          b.get("message") == "课表版本已更新，请刷新后重试",
          f"code={b.get('code')} message={b.get('message')!r}")

    print(f"\n===== 汇总：PASS {len(PASS)} / FAIL {len(FAIL)} =====")
    if FAIL:
        print("失败项：" + ", ".join(FAIL))
        sys.exit(1)
    print("冲突原因透出验证全部通过")


if __name__ == "__main__":
    main()
