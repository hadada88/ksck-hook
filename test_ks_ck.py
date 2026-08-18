#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试快手 ck 是否有效 - basicInfo 接口"""
import json, sys, urllib.request

COOKIE = sys.argv[1] if len(sys.argv) > 1 else None
if not COOKIE:
    print("用法: python test_ks_ck.py 'COOKIE'")
    sys.exit(1)

# 从参数中移除 #salt 部分（如果有）
if "#=" in COOKIE:
    COOKIE = COOKIE.split("#=")[0].rstrip(";# ")

url = "https://nebula.kuaishou.com/rest/n/nebula/activity/earn/overview/basicInfo?source=bottom_guide_first"
headers = {
    "Host": "nebula.kuaishou.com",
    "User-Agent": "kwai-android aegon/4.26.0",
    "Cookie": COOKIE,
    "Content-Type": "application/x-www-form-urlencoded",
}

print("[*] 请求 basicInfo ...")
req = urllib.request.Request(url, headers=headers)
try:
    resp = urllib.request.urlopen(req, timeout=15)
    body = resp.read().decode("utf-8")
    print("[HTTP]", resp.status)
    data = json.loads(body)
    print("[响应] result =", data.get("result"))
    if data.get("result") == 1 and data.get("data"):
        u = data["data"].get("userData") or {}
        print("  昵称:", u.get("nickname"))
        print("  金币:", data["data"].get("totalCoin"))
        print("  现金:", data["data"].get("allCash"))
        print("  ✅ CK 有效！")
    else:
        print(json.dumps(data, ensure_ascii=False)[:800])
except Exception as e:
    print("[错误]", e)
    if hasattr(e, "read"):
        print(e.read().decode("utf-8", "ignore")[:800])