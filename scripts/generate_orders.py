#!/usr/bin/env python3
"""
NL2SQL 测试数据生成脚本（M1 样例数据）。
对应章程 §测试数据规模：orders 表结构。
M1 先造 5000 条样例（region/channel/status 覆盖测试维度），150 万全量数据留 M8。

用法：
    python generate_orders.py [--count 5000]

依赖：pip install pymysql
"""
import argparse
import random
import sys
from datetime import datetime, timedelta

import pymysql

# 连接配置（与 application-dev.yml 环境变量一致）
DB_CONFIG = {
    "host": "127.0.0.1",
    "port": 3306,
    "user": "root",
    "password": "dili123",
    "database": "nl2sql",
    "charset": "utf8mb4",
}

REGIONS = ["华东", "华北", "华南", "西南", "东北", "西北"]
CHANNELS = ["app", "web", "miniapp", "offline"]
STATUSES = ["completed", "refunded", "cancelled"]
CATEGORY_COUNT = 20  # product_id 范围 1..20，模拟 20 个商品


def random_date(days_back=730):
    """近两年内的随机时间。"""
    now = datetime.now()
    delta = timedelta(days=random.randint(0, days_back), seconds=random.randint(0, 86399))
    return now - delta


def main(count):
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    # 清空旧数据（仅 orders 表，独立 nl2sql 库，不影响其他库）
    cursor.execute("TRUNCATE TABLE orders")

    rows = []
    for i in range(1, count + 1):
        quantity = random.randint(1, 10)
        price = round(random.uniform(10, 500), 2)
        amount = round(quantity * price, 2)
        rows.append((
            i,                          # order_id
            random.randint(1, 2000),    # user_id
            random.randint(1, CATEGORY_COUNT),  # product_id
            amount,
            quantity,
            price,
            random_date(),
            random.choice(REGIONS),
            random.choice(STATUSES),
            random.choice(CHANNELS),
        ))

    sql = (
        "INSERT INTO orders (order_id, user_id, product_id, amount, quantity, price, "
        "order_date, region, status, channel) "
        "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"
    )
    cursor.executemany(sql, rows)
    conn.commit()

    cursor.execute("SELECT COUNT(*) FROM orders")
    total = cursor.fetchone()[0]
    cursor.close()
    conn.close()
    print(f"OK: 写入 {total} 条 orders 数据到 nl2sql 库")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=5000)
    args = parser.parse_args()
    main(args.count)
