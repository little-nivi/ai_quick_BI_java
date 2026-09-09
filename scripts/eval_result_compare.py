#!/usr/bin/env python3
"""
阶段B：执行级结果比对脚本
——不只验"SQL 对"，还要验"数对不对"

原理：
    1. 调后端 API 拿到生成的 SQL 和查询结果（rows）
    2. 用 pymysql 直连 MySQL，跑预先写好的"参考 SQL"
    3. 比对两个结果集的数值

依赖：pip install requests pymysql

注意：
    必须在服务器上跑！pymysql 要连 Docker MySQL（127.0.0.1:3306），
    本地跑不了（MySQL 在服务器容器内，3306 不对外暴露）。

用法（服务器 SSH 执行）：
    cd /opt/nl2sql && python3 eval_result_compare.py
    cd /opt/nl2sql && python3 eval_result_compare.py --base http://127.0.0.1:8080
"""
import argparse
import json
import sys
import time
from dataclasses import dataclass
from typing import Optional, List, Tuple

import requests

try:
    import pymysql
except ImportError:
    print("ERROR: 缺少 pymysql，请先安装：pip install pymysql")
    sys.exit(1)

# ============================================================
# 20 条代表性用例 + 参考 SQL
# 选取覆盖：单值/多行/时间/JOIN/聚合
# 参考 SQL 是"标准答案"，和 LLM 生成的 SQL 比对结果集
# ============================================================

COMPARE_CASES = [
    # ---- 单值查询（SUM/COUNT/AVG/MAX/MIN）----
    (1, "总销售额是多少",
     "SELECT SUM(amount) FROM orders",
     "scalar"),
    (2, "一共有多少订单",
     "SELECT COUNT(*) FROM orders",
     "scalar"),
    (4, "订单的平均金额",
     "SELECT AVG(amount) FROM orders",
     "scalar"),
    (5, "最大的一笔订单金额",
     "SELECT MAX(amount) FROM orders",
     "scalar"),
    (6, "最小的一笔订单金额",
     "SELECT MIN(amount) FROM orders",
     "scalar"),
    # ---- 多行查询（GROUP BY）----
    (22, "每个地区的销售额",
     "SELECT region, SUM(amount) FROM orders GROUP BY region",
     "multi_row_sort"),
    (24, "各渠道的订单数量",
     "SELECT channel, COUNT(*) FROM orders GROUP BY channel",
     "multi_row_sort"),
    (26, "各状态的订单数",
     "SELECT status, COUNT(*) FROM orders GROUP BY status",
     "multi_row_sort"),
    # ---- 时间范围查询 ----
    (62, "最近7天订单数",
     "SELECT COUNT(*) FROM orders WHERE order_date >= DATE_SUB(NOW(), INTERVAL 7 DAY)",
     "scalar"),
    (68, "昨天销售额",
     "SELECT SUM(amount) FROM orders WHERE DATE(order_date) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)",
     "scalar"),
    # ---- JOIN 查询 ----
    (201, "企业类型客户下了多少笔交易",
     "SELECT COUNT(*) FROM orders o JOIN customers c ON o.user_id = c.id WHERE c.type = '企业'",
     "scalar"),
    (207, "数码类商品总共卖出去了多少件",
     "SELECT SUM(o.quantity) FROM orders o JOIN products p ON o.product_id = p.id WHERE p.category = '数码'",
     "scalar"),
    (213, "各等级客户的交易金额",
     "SELECT c.level, SUM(o.amount) FROM orders o JOIN customers c ON o.user_id = c.id GROUP BY c.level",
     "multi_row_sort"),
    (216, "数码类商品的销售额",
     "SELECT SUM(o.amount) FROM orders o JOIN products p ON o.product_id = p.id WHERE p.category = '数码'",
     "scalar"),
    # ---- 模糊语义 ----
    (84, "完成的订单",
     "SELECT COUNT(*) FROM orders WHERE status = 'completed'",
     "scalar"),
    (86, "退款金额",
     "SELECT SUM(amount) FROM orders WHERE status = 'refunded'",
     "scalar"),
    # ---- 复杂嵌套 ----
    (93, "销售额超过10万的地区",
     "SELECT region FROM orders GROUP BY region HAVING SUM(amount) > 100000",
     "multi_row_sort"),
    # ---- 大数据量 ----
    (121, "所有订单的总金额",
     "SELECT SUM(amount) FROM orders",
     "scalar"),
    (123, "各地区订单量汇总",
     "SELECT region, COUNT(*) FROM orders GROUP BY region",
     "multi_row_sort"),
    # ---- JOIN 三表 ----
    (218, "各品类商品的销售额",
     "SELECT p.category, SUM(o.amount) FROM orders o JOIN products p ON o.product_id = p.id GROUP BY p.category",
     "multi_row_sort"),
]


@dataclass
class CompareResult:
    case_id: int
    question: str
    api_code: int
    api_sql: str
    api_rows: list
    ref_sql: str
    ref_rows: list
    match: bool
    reason: str


def login(base: str, username: str = "admin", password: str = "admin123") -> str:
    resp = requests.post(f"{base}/api/v1/auth/login",
                         json={"username": username, "password": password}, timeout=15)
    data = resp.json()
    if data.get("code") != 2000:
        raise RuntimeError(f"登录失败: {data}")
    return data["data"]["token"]


def ask(base: str, token: str, question: str, timeout: int = 120) -> Tuple[int, dict]:
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    resp = requests.post(f"{base}/api/v1/query", json={"question": question},
                         headers=headers, timeout=timeout)
    body = resp.json()
    return body.get("code"), (body.get("data") or {})


def run_ref_sql(mysql_config: dict, sql: str) -> Tuple[list, str]:
    """跑参考 SQL，返回 (rows, error_msg)"""
    try:
        conn = pymysql.connect(**mysql_config)
        with conn.cursor() as cur:
            cur.execute(sql)
            rows = cur.fetchall()
        conn.close()
        return list(rows), ""
    except Exception as e:
        return [], str(e)


def normalize_value(v):
    """归一化数值：Decimal/float 转 float，int 保持，None 保持"""
    if v is None:
        return None
    # Decimal
    try:
        from decimal import Decimal
        if isinstance(v, Decimal):
            return float(v)
    except:
        pass
    if isinstance(v, (int, float)):
        return float(v)
    return v


def compare_results(api_rows: list, ref_rows: list, mode: str) -> Tuple[bool, str]:
    """比对 API 返回的结果集 和 参考 SQL 的结果集"""
    if not api_rows and not ref_rows:
        return True, "两者都为空"
    if not api_rows:
        return False, f"API 返回空，参考 SQL 有 {len(ref_rows)} 行"
    if not ref_rows:
        return False, f"参考 SQL 为空，API 返回 {len(api_rows)} 行"

    if mode == "scalar":
        # 单值查询：取第一个单元的值，比对数值（容差 0.01）
        api_val = normalize_value(api_rows[0][0]) if api_rows else None
        ref_val = normalize_value(ref_rows[0][0]) if ref_rows else None
        if api_val is None and ref_val is None:
            return True, f"两者都是 None"
        if api_val is None or ref_val is None:
            return False, f"API={api_val} vs REF={ref_val}（一方为空）"
        if abs(api_val - ref_val) < 0.01:
            return True, f"API={api_val} vs REF={ref_val} ✓"
        return False, f"API={api_val} vs REF={ref_val} 差异>0.01"

    elif mode == "multi_row_sort":
        # 多行查询：排序后逐行比对
        if len(api_rows) != len(ref_rows):
            return False, f"行数不同 API={len(api_rows)} vs REF={len(ref_rows)}"
        # 排序（按第一列）
        api_sorted = sorted([[normalize_value(c) for c in row] for row in api_rows],
                            key=lambda r: str(r[0]))
        ref_sorted = sorted([[normalize_value(c) for c in row] for row in ref_rows],
                            key=lambda r: str(r[0]))
        for i, (a, r) in enumerate(zip(api_sorted, ref_sorted)):
            for j, (av, rv) in enumerate(zip(a, r)):
                if av is None and rv is None:
                    continue
                if av is None or rv is None:
                    return False, f"第{i+1}行第{j+1}列 API={av} vs REF={rv}（一方为空）"
                if isinstance(av, (int, float)) and isinstance(rv, (int, float)):
                    if abs(av - rv) > 0.01:
                        return False, f"第{i+1}行第{j+1}列 API={av} vs REF={rv} 差异>0.01"
                elif str(av) != str(rv):
                    return False, f"第{i+1}行第{j+1}列 API={av} vs REF={rv} 不相等"
        return True, f"{len(api_rows)} 行全部匹配"

    return False, f"未知比对模式 {mode}"


def main():
    parser = argparse.ArgumentParser(description="阶段B：执行级结果比对")
    parser.add_argument("--base", default="http://127.0.0.1:8080",
                       help="后端地址（默认 http://127.0.0.1:8080）")
    parser.add_argument("--mysql-host", default="127.0.0.1")
    parser.add_argument("--mysql-port", type=int, default=3306)
    parser.add_argument("--mysql-user", default="root")
    parser.add_argument("--mysql-password", default="dili123")
    parser.add_argument("--mysql-db", default="nl2sql")
    args = parser.parse_args()

    mysql_config = {
        "host": args.mysql_host,
        "port": args.mysql_port,
        "user": args.mysql_user,
        "password": args.mysql_password,
        "database": args.mysql_db,
        "charset": "utf8mb4",
    }

    print(f"=== 阶段B：执行级结果比对 === 目标: {args.base}")
    print(f"MySQL: {args.mysql_host}:{args.mysql_port}/{args.mysql_db}")

    # 测试 MySQL 连通性
    try:
        test_rows, test_err = run_ref_sql(mysql_config, "SELECT 1")
        if test_err:
            print(f"MySQL 连接失败: {test_err}")
            return
        print(f"MySQL 连通 ✓")
    except Exception as e:
        print(f"MySQL 连接异常: {e}")
        return

    # 登录
    token = login(args.base)
    print(f"登录成功: admin (token len={len(token)})")
    print()

    # 跑用例
    results = []
    for cid, question, ref_sql, mode in COMPARE_CASES:
        print(f"[#{cid}] {question}")

        # 1. 调 API 拿生成的 SQL 和结果
        code, data = ask(args.base, token, question)
        api_sql = data.get("sql") or ""
        api_rows = data.get("rows") or []
        print(f"  API: code={code} sql={api_sql[:80]}")
        print(f"  API rows: {api_rows[:3]}{'...' if len(api_rows)>3 else ''}")

        if code != 2000:
            results.append(CompareResult(cid, question, code, api_sql, api_rows,
                                        ref_sql, [], False, f"API code={code} 非 2000"))
            print(f"  [FAIL] API 非 2000，跳过比对")
            print()
            continue

        # 2. 跑参考 SQL
        ref_rows, ref_err = run_ref_sql(mysql_config, ref_sql)
        if ref_err:
            results.append(CompareResult(cid, question, code, api_sql, api_rows,
                                        ref_sql, [], False, f"参考 SQL 执行失败: {ref_err}"))
            print(f"  [FAIL] 参考 SQL 错误: {ref_err}")
            print()
            continue
        print(f"  REF rows: {ref_rows[:3]}{'...' if len(ref_rows)>3 else ''}")

        # 3. 比对
        match, reason = compare_results(api_rows, ref_rows, mode)
        results.append(CompareResult(cid, question, code, api_sql, api_rows,
                                    ref_sql, ref_rows, match, reason))
        status = "PASS" if match else "FAIL"
        print(f"  [{status}] {reason}")
        print()

        time.sleep(0.5)  # 避免 LLM 限流

    # 汇总
    total = len(results)
    passed = sum(1 for r in results if r.match)
    accuracy = round(passed / total * 100, 1) if total else 0

    print("=" * 60)
    print(f"阶段B 结果比对完成")
    print(f"总计 {total} 条，通过 {passed} 条，结果一致率 {accuracy}%")
    print()

    print("失败明细：")
    for r in results:
        if not r.match:
            print(f"  #{r.case_id} {r.question}")
            print(f"    API: {r.api_sql[:80]}")
            print(f"    REF: {r.ref_sql[:80]}")
            print(f"    原因: {r.reason}")
            print()

    # 生成报告
    import os
    report_dir = "/opt/nl2sql/reports" if os.path.exists("/opt/nl2sql") else "scripts/reports"
    os.makedirs(report_dir, exist_ok=True)
    ts = time.strftime("%Y%m%d_%H%M%S")
    report_path = os.path.join(report_dir, f"result_compare_{ts}.json")
    report_data = {
        "summary": {"total": total, "passed": passed, "accuracy": accuracy},
        "results": [{"case_id": r.case_id, "question": r.question,
                     "api_code": r.api_code, "api_sql": r.api_sql,
                     "api_rows": r.api_rows, "ref_sql": r.ref_sql,
                     "ref_rows": r.ref_rows, "match": r.match, "reason": r.reason}
                    for r in results],
    }
    def json_default(o):
        from decimal import Decimal
        from datetime import datetime, date
        if isinstance(o, Decimal):
            return float(o)
        if isinstance(o, (datetime, date)):
            return o.isoformat()
        return str(o)

    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report_data, f, ensure_ascii=False, indent=2, default=json_default)
    print(f"报告已生成：{report_path}")


if __name__ == "__main__":
    main()
