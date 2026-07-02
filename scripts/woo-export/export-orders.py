#!/usr/bin/env python3
"""Export ALL WooCommerce orders (legacy shop_order) from the local wp_db
container to JSON for the OrderImporter. Reads ONLY order/line tables — never
writes. Mirrors export.py / export-customers.py.

Usage:
    python3 scripts/woo-export/export-orders.py > /tmp/woo-orders.json
"""

import json
import subprocess
import sys
from collections import defaultdict
from datetime import datetime, timezone

CONTAINER = "wp_db"
DB = "client7002dbnem"
PREFIX = "guxdop_"


def rows(query):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", "-proot_password",
         "--default-character-set=utf8mb4", "-B", "-N", "--raw", DB, "-e", query],
        capture_output=True, text=True, check=True)
    return [json.loads(line) for line in result.stdout.splitlines() if line.strip()]


def to_huf(value):
    if value is None or value == "":
        return None
    return int(round(float(value)))


def line_gross(line_total, line_tax):
    return int(round(float(line_total or 0) + float(line_tax or 0)))


def unit_gross(line_gross_huf, qty):
    q = int(qty) if qty else 1
    return int(round(line_gross_huf / q)) if q else line_gross_huf


def tax_rate(line_tax, line_subtotal):
    sub = float(line_subtotal or 0)
    if sub <= 0:
        return 0
    return int(round(float(line_tax or 0) / sub * 100))


def iso(dt_str):
    if not dt_str or dt_str.startswith("0000"):
        return None
    return datetime.strptime(dt_str, "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc).isoformat()


def main():
    t = lambda name: PREFIX + name

    # 1) Orders with billing/total meta pulled via correlated subqueries.
    # LIMIT 1 guards against duplicate postmeta rows (seen in some Woo installs).
    def pm(key):
        return f"(SELECT meta_value FROM {t('postmeta')} pm WHERE pm.post_id = p.ID AND pm.meta_key = '{key}' LIMIT 1)"

    order_rows = rows(f"""
        SELECT JSON_OBJECT(
          'wooOrderId', p.ID,
          'orderKey',   {pm('_order_key')},
          'wooStatus',  p.post_status,
          'currency',   {pm('_order_currency')},
          'createdAt',  DATE_FORMAT(p.post_date_gmt, '%Y-%m-%d %H:%i:%s'),
          'paidAt',     {pm('_date_paid')},
          'wpUserId',   NULLIF({pm('_customer_user')}, '0'),
          'customerName', TRIM(CONCAT(
                            COALESCE({pm('_billing_first_name')}, ''), ' ',
                            COALESCE({pm('_billing_last_name')}, ''))),
          'email',      {pm('_billing_email')},
          'phone',      {pm('_billing_phone')},
          'postcode',   {pm('_billing_postcode')},
          'city',       {pm('_billing_city')},
          'addressLine',TRIM(CONCAT(
                            COALESCE({pm('_billing_address_1')}, ''), ' ',
                            COALESCE({pm('_billing_address_2')}, ''))),
          'orderShipping',    {pm('_order_shipping')},
          'orderShippingTax', {pm('_order_shipping_tax')},
          'orderTotal', {pm('_order_total')},
          'transactionId', {pm('_transaction_id')},
          'note',       {pm('_customer_note')}
        )
        FROM {t('posts')} p
        WHERE p.post_type = 'shop_order'
        ORDER BY p.ID
    """)

    # 2) Shipping method name per order (first shipping line item).
    # ANY_VALUE() is required because MySQL ONLY_FULL_GROUP_BY is on.
    ship_rows = rows(f"""
        SELECT JSON_OBJECT('orderId', i.order_id, 'name', ANY_VALUE(i.order_item_name))
        FROM {t('woocommerce_order_items')} i
        WHERE i.order_item_type = 'shipping'
        GROUP BY i.order_id
    """)
    ship_name = {int(r["orderId"]): r["name"] for r in ship_rows}

    # 3) Line items with product/variation ids, qty, totals, and resolved SKU.
    # LIMIT 1 guards against duplicate itemmeta rows.
    def im(key):
        return f"(SELECT meta_value FROM {t('woocommerce_order_itemmeta')} m WHERE m.order_item_id = i.order_item_id AND m.meta_key = '{key}' LIMIT 1)"

    line_rows = rows(f"""
        SELECT JSON_OBJECT(
          'orderId', i.order_id,
          'productName', i.order_item_name,
          'wooProductId',   {im('_product_id')},
          'wooVariationId', {im('_variation_id')},
          'qty',            {im('_qty')},
          'lineTotal',      {im('_line_total')},
          'lineTax',        {im('_line_tax')},
          'lineSubtotal',   {im('_line_subtotal')},
          'sku', (SELECT pm.meta_value FROM {t('postmeta')} pm
                  WHERE pm.meta_key = '_sku' AND pm.post_id =
                        COALESCE(NULLIF({im('_variation_id')}, '0'), {im('_product_id')}) LIMIT 1)
        )
        FROM {t('woocommerce_order_items')} i
        WHERE i.order_item_type = 'line_item'
        ORDER BY i.order_item_id
    """)

    items_by_order = defaultdict(list)
    for r in line_rows:
        lg = line_gross(r.get("lineTotal"), r.get("lineTax"))
        qty = int(float(r.get("qty") or 1))
        items_by_order[int(r["orderId"])].append({
            "wooProductId": to_huf(r.get("wooProductId")),
            "wooVariationId": to_huf(r.get("wooVariationId")),
            "sku": r.get("sku"),
            "productName": r.get("productName") or "—",
            "variantLabel": None,
            "quantity": qty,
            "unitGrossHuf": unit_gross(lg, qty),
            "taxRatePercent": tax_rate(r.get("lineTax"), r.get("lineSubtotal")),
            "lineGrossHuf": lg,
        })

    orders = []
    for o in order_rows:
        oid = int(o["wooOrderId"])
        items = items_by_order.get(oid, [])
        ship_gross = (to_huf(o.get("orderShipping")) or 0) + (to_huf(o.get("orderShippingTax")) or 0)
        paid = bool(o.get("paidAt")) or bool(o.get("transactionId"))
        orders.append({
            "wooOrderId": oid,
            "orderKey": o.get("orderKey"),
            "wooStatus": o.get("wooStatus"),
            "currency": o.get("currency"),
            "createdAt": iso(o.get("createdAt")),
            "paidAt": None,  # timestamp not preserved on payment; presence drives `paid`
            "wpUserId": to_huf(o.get("wpUserId")),
            "customerName": (o.get("customerName") or "").strip() or "—",
            "email": o.get("email") or "—",
            "phone": o.get("phone"),
            "postcode": o.get("postcode") or "—",
            "city": o.get("city") or "—",
            "addressLine": (o.get("addressLine") or "").strip() or "—",
            "note": o.get("note"),
            "shipMethodName": ship_name.get(oid),
            "shipGrossHuf": ship_gross,
            "itemsGrossHuf": sum(i["lineGrossHuf"] for i in items),
            "totalGrossHuf": to_huf(o.get("orderTotal")) or 0,
            "paid": paid,
            "transactionId": o.get("transactionId"),
            "items": items,
        })

    json.dump(orders, sys.stdout, ensure_ascii=False, indent=1)
    print(f"\nexported {len(orders)} orders", file=sys.stderr)


if __name__ == "__main__":
    main()
