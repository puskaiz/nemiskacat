#!/usr/bin/env python3
"""Export WordPress/WooCommerce users from the local wp_db container to JSON for
the CustomerImporter. Exports the phpass ($P$) hash so login upgrades to bcrypt
on first use. Reads ONLY user + name fields — no order/billing PII beyond name.

Usage:
    python3 scripts/woo-export/export-customers.py [role]   # default role: customer
    # role is the WordPress capability key: customer | subscriber | administrator
"""

import json
import subprocess
import sys

CONTAINER = "wp_db"
DB = "client7002dbnem"
PREFIX = "guxdop_"


def rows(query):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", "-proot_password",
         "--default-character-set=utf8mb4", "-B", "-N", "--raw", DB, "-e", query],
        capture_output=True, text=True, check=True)
    return [json.loads(line) for line in result.stdout.splitlines() if line.strip()]


def main():
    t = lambda name: PREFIX + name

    # Export ALL users with their role parsed from the capabilities meta.
    customers = rows(f"""
        SELECT JSON_OBJECT(
          'wpUserId', u.ID,
          'username', u.user_login,
          'email', u.user_email,
          'passwordHash', u.user_pass,
          'displayName', NULLIF(u.display_name, ''),
          'firstName', (SELECT meta_value FROM {t('usermeta')} m
                        WHERE m.user_id = u.ID AND m.meta_key = 'first_name' LIMIT 1),
          'lastName',  (SELECT meta_value FROM {t('usermeta')} m
                        WHERE m.user_id = u.ID AND m.meta_key = 'last_name' LIMIT 1),
          'role', CASE
              WHEN caps.meta_value LIKE '%administrator%' THEN 'administrator'
              WHEN caps.meta_value LIKE '%shop_manager%'  THEN 'administrator'
              WHEN caps.meta_value LIKE '%subscriber%'    THEN 'subscriber'
              ELSE 'customer' END)
        FROM {t('users')} u
        JOIN {t('usermeta')} caps ON caps.user_id = u.ID AND caps.meta_key = '{PREFIX}capabilities'
        WHERE u.user_email <> '' AND u.user_pass LIKE '$P$%'""")

    json.dump(customers, sys.stdout, ensure_ascii=False, indent=1)
    from collections import Counter
    by_role = Counter(c.get("role") for c in customers)
    print(f"\nexported {len(customers)} users: {dict(by_role)}", file=sys.stderr)


if __name__ == "__main__":
    main()
