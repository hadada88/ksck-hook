#!/usr/bin/env python3
"""测试快手 ck 能否请求广告任务接口"""
import json, urllib.request, sys

COOKIE = sys.argv[1] if len(sys.argv) > 1 else None
if not COOKIE:
    print("用法: python test_ks_ad.py 'COOKIE'")
    sys.exit(1)

# 从参数中移除 #salt 部分
if "#=" in COOKIE:
    COOKIE = COOKIE.split("#=")[0].rstrip(";# ")

# 固定的签名参数（从 node 计算得到）
sig = "d40b0858dcfcb6f375e8bdcc659935a0"
nstokensig = "f57a43e00fd720e2c55958f604cfa89d75a8567a607377e82833115cac45ea77"
# __NS_sig3 需要 KSecurity，用空值或固定 48 字符测试
ns_sig3 = "000000000000000000000000000000000000000000000000"

# 测试广告请求 - 宝箱广告
ad_params = {
    "businessId": "606",
    "posId": "20346",
    "subPageId": "100024064",
    "requestSceneType": "1",
    "taskType": "1",
    "source": "1",
    "scene": "1",
    "os": "android",
    "client_key": "2ac2a76d",
    "kuaishou.api_st": "Cg9rdWFpc2hvdS5hcGkuc3QSoAHE3WZJQBm5-P2aHY5T4n_0Wvf21aBoIYHDvWgjr3ltd_Png2evP018shY3Gv1VDEpHVlO-Mk0QihMNt3OYzei3yX79_3FEja-dVuIwC9lXxob51hBxBkBTccjHk41Ri9zP_23haw3K8fG3Z6C0avnZqT5fkfjdrKrl-6Hjdw0qwhso3iPd0Z-_92WQwp20jsf2cKXRbwaDE1tHzIpAm0L3GhLaipd3wblK7a4OGNvi6qpZ62siIBEnVA0QsokkrL6k1MKWu7sP9SdIMvMcHse52BMX6mRFKAUwAQ",
    "egid": "DFP6BE1B10EE66B5112686530EF7964172D291AF8B82284938014CE11AFBF685",
}

# 构建 query string
query = "&".join(f"{k}={v}" for k, v in ad_params.items())
query += f"&sig={sig}&__NS_sig3={ns_sig3}&__NS_xfalcon=&__NStokensig={nstokensig}"

# 设备参数
device_params = {
    "c": "a",
    "did": "ANDROID_f4f20e2f4a438388",
    "oDid": "ANDROID_76b133ed9a29357e",
    "rdid": "ANDROID_4c212b6629488c91",
    "did_gt": "1786927428340",
    "did_tag": "0",
    "cdid_tag": "2",
    "mod": "2107119DC",
    "sys": "android14",
    "kpn": "NEBULA",
    "kpf": "ANDROID_PHONE",
    "sdkVersion": "2.0.6.14",
    "appver": "14.4.30.4277",
}

url = "https://api.e.kuaishou.com/rest/e/reward/mixed/ad?" + "&".join(
    f"{k}={v}" for k, v in device_params.items()
) + "&" + query

headers = {
    "Host": "api.e.kuaishou.com",
    "User-Agent": "kwai-android aegon/4.26.0",
    "Cookie": COOKIE,
    "Content-Type": "application/x-www-form-urlencoded",
}

print("[*] 请求广告接口...")
print("URL:", url[:200] + "...")
req = urllib.request.Request(url, headers=headers)
try:
    resp = urllib.request.urlopen(req, timeout=15)
    body = resp.read().decode("utf-8")
    print("[HTTP]", resp.status)
    data = json.loads(body)
    print(json.dumps(data, ensure_ascii=False, indent=2)[:1000])
except urllib.error.HTTPError as e:
    print("[HTTP]", e.code)
    body = e.read().decode("utf-8", "ignore")
    try:
        data = json.loads(body)
        print(json.dumps(data, ensure_ascii=False, indent=2)[:600])
    except:
        print(body[:600])
except Exception as e:
    print("[错误]", e)