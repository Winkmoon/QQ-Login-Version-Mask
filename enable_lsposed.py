#!/usr/bin/env python3
import sqlite3
import sys

DB = "/data/adb/lspd/config/modules_config.db"
QQ = "com.tencent.mobileqq"
USER = 0

if len(sys.argv) < 3:
    print("usage: enable_qqversionfix.py <module_pkg> <apk_path>")
    sys.exit(1)

MODULE = sys.argv[1]
APK = sys.argv[2]

con = sqlite3.connect(DB)
cur = con.cursor()
cur.execute(
    "INSERT OR REPLACE INTO modules(module_pkg_name, apk_path) VALUES(?, ?)",
    (MODULE, APK),
)
cur.execute(
    "INSERT OR REPLACE INTO modules_state(module_pkg_name, user_id, enabled, scope_request_blocked) VALUES(?, ?, 1, 0)",
    (MODULE, USER),
)
cur.execute(
    "INSERT OR REPLACE INTO scope(module_pkg_name, app_pkg_name, user_id) VALUES(?, ?, ?)",
    (MODULE, QQ, USER),
)
con.commit()
print(cur.execute("SELECT * FROM modules WHERE module_pkg_name=?", (MODULE,)).fetchall())
print(cur.execute("SELECT * FROM modules_state WHERE module_pkg_name=?", (MODULE,)).fetchall())
print(cur.execute("SELECT * FROM scope WHERE module_pkg_name=?", (MODULE,)).fetchall())
con.close()
