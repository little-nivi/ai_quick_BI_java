#!/usr/bin/env python3
"""
NL2SQL 准确率回归评测脚本（章程 M7/M8：150 条用例批量回归）。

批量发送测试问题到 /api/v1/query，统计成功率，定位失败环节。

用法：
    python scripts/eval_accuracy.py [--base http://127.0.0.1:8080] [--username admin] [--password admin123]

依赖：pip install requests

注意：
    1. 需先启动后端应用 + 已造数据；
    2. 会真实调用 qwen，产生 Token 费用；
    3. 用例集在下方 TEST_CASES，M7 起步先内置少量，后续补到 150 条。
"""
import argparse
import json
import time
from dataclasses import dataclass
from typing import Optional

import requests

# 内置用例集：M7 起步少量，后续按章程 §测试用例设计补到 150 条
TEST_CASES = [
    # (问题, 类别, 期望要点, 预期code)
    ("总销售额是多少", "单表简单查询", "SELECT SUM(amount)", 2000),
    ("各地区的订单数量排名", "单表聚合查询", "GROUP BY region", 2000),
    ("上个月销售额", "时间范围查询", "order_date", 2000),
    ("卖得最好的商品是什么", "模糊语义查询", "销量最高", 2000),
    ("删除所有订单", "边界/异常测试", None, 4003),
    ("今天天气怎么样", "意图识别", None, 4001),
]

CATEGORIES = {}


@dataclass
class Result:
    question: str
    category: str
    code: Optional[int]
    sql: Optional[str]
    latency_ms: Optional[int]
    cache_hit: Optional[bool]
    passed: bool
    fail_reason: str = ""


def login(base: str, username: str, password: str) -> str:
    resp = requests.post(f"{base}/api/v1/auth/login", json={"username": username, "password": password}, timeout=10)
    data = resp.json()
    if data.get("code") != 2000:
        raise RuntimeError(f"login failed: {data}")
    return data["data"]["token"]


def run_one(base: str, token: str, question: str, expected_code: int) -> Result:
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    start = time.time()
    try:
        resp = requests.post(f"{base}/api/v1/query", json={"question": question}, headers=headers, timeout=60)
        data = resp.json()
        latency_ms = int((time.time() - start) * 1000)
        code = data.get("code")
        inner = data.get("data") or {}
        return Result(
            question=question, category="", code=code, sql=inner.get("sql"),
            latency_ms=latency_ms, cache_hit=inner.get("cache_hit"),
            passed=(code == expected_code), fail_reason=f"期望 {expected_code} 实际 {code}")
    except Exception as e:
        return Result(question=question, category="", code=None, sql=None,
                      latency_ms=int((time.time() - start) * 1000), cache_hit=None,
                      passed=False, fail_reason=f"异常 {e}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin123")
    args = parser.parse_args()

    token = login(args.base, args.username, args.password)

    results = []
    for question, category, expected_sql_hint, expected_code in TEST_CASES:
        r = run_one(args.base, token, question, expected_code)
        r.category = category
        results.append(r)
        status = "✓" if r.passed else "✗"
        print(f"{status} [{r.category}] {r.question} → code={r.code} {r.fail_reason}")
        time.sleep(0.3)  # 轻限流，避免打爆 qwen

    total = len(results)
    passed = sum(1 for r in results if r.passed)
    print("\n" + "=" * 50)
    print(f"总计 {total} 条，通过 {passed} 条，成功率 {passed / total * 100:.1f}%")

    # 按类别统计
    by_cat = {}
    for r in results:
        by_cat.setdefault(r.category, []).append(r)
    for cat, rs in by_cat.items():
        cp = sum(1 for x in rs if x.passed)
        print(f"  {cat}: {cp}/{len(rs)} ({cp / len(rs) * 100:.1f}%)")

    # 失败明细
    fails = [r for r in results if not r.passed]
    if fails:
        print("\n失败用例：")
        for r in fails:
            print(f"  ✗ {r.question} — {r.fail_reason}")


if __name__ == "__main__":
    main()
