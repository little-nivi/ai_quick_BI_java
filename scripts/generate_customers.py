#!/usr/bin/env python3
"""
NL2SQL JOIN 测试数据生成脚本（阶段C配套）。
对应章程 §测试数据规模：customers 表结构。
灌 2000 条 customers，覆盖 orders.user_id 范围 1..2000，保证 JOIN 不返回空集。

用法：
    python generate_customers.py                       # 默认 2000 条
    python generate_customers.py --count 2000

依赖：pip install pymysql
"""
import argparse
import os
import random
from datetime import datetime, timedelta

import pymysql

DB_CONFIG = {
    "host": "127.0.0.1",
    "port": 3306,
    "user": "root",
    "password": os.environ.get("DB_PASSWORD", ""),
    "database": "nl2sql",
    "charset": "utf8mb4",
}

TYPES = ["个人", "企业"]
LEVELS = ["普通", "银卡", "金卡", "钻石"]


def random_date(days_back=730):
    now = datetime.now()
    delta = timedelta(days=random.randint(0, days_back), seconds=random.randint(0, 86399))
    return now - delta


def main(count):
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    cursor.execute("TRUNCATE TABLE customers")

    rows = []
    for i in range(1, count + 1):
        # 权重分布：个人 70%，企业 30%
        cust_type = random.choices(TYPES, weights=[7, 3])[0]
        # 等级分布：普通 50%，银卡 30%，金卡 15%，钻石 5%
        level = random.choices(LEVELS, weights=[50, 30, 15, 5])[0]
        rows.append((
            i,
            f"客户{i:04d}",
            cust_type,
            level,
            random_date(),
        ))

    sql = (
        "INSERT INTO customers (id, name, type, level, register_date) "
        "VALUES (%s, %s, %s, %s, %s)"
    )
    cursor.executemany(sql, rows)
    conn.commit()

    cursor.execute("SELECT COUNT(*) FROM customers")
    total = cursor.fetchone()[0]
    cursor.execute("SELECT type, COUNT(*) FROM customers GROUP BY type")
    type_dist = cursor.fetchall()
    cursor.execute("SELECT level, COUNT(*) FROM customers GROUP BY level")
    level_dist = cursor.fetchall()
    cursor.close()
    conn.close()

    print(f"OK: 写入 {total} 条 customers 数据到 nl2sql 库")
    print(f"  类型分布: {type_dist}")
    print(f"  等级分布: {level_dist}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=2000)
    args = parser.parse_args()
    main(args.count)
