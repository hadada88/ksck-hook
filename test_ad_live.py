#!/usr/bin/env python3
"""测试签名桥：用 /nssig 算签名，请求快手广告接口"""
import json, urllib.request, sys, urllib.parse

# ====== 从 logcat 提取的 CK 参数 ======
CK_PARAMS = {
    "egid": "DFP6BE1B10EE66B5112686530EF7964172D291AF8B82284938014CE11AFBF685",
    "did": "ANDROID_f4f20e2f4a438388",
    "userId": "128449111",
    "kuaishou.api_st": "Cg9rdWFpc2hvdS5hcGkuc3QSoAHE3WZJQBm5-P2aHY5T4n_0Wvf21aBoIYHDvWgjr3ltd_Png2evP018shY3Gv1VDEpHVlO-Mk0QihMNt3OYzei3yX79_3FEja-dVuIwC9lXxob51hBxBkBTccjHk41Ri9zP_23haw3K8fG3Z6C0avnZqT5fkfjdrKrl-6Hjdw0qwhso3iPd0Z-_92WQwp20jsf2cKXRbwaDE1tHzIpAm0L3GhLaipd3wblK7a4OGNvi6qpZ62siIBEnVA0QsokkrL6k1MKWu7sP9SdIMvMcHse52BMX6mRFKAUwAQ",
    "oDid": "ANDROID_76b133ed9a29357e",
    "kpn": "NEBULA",
    "kpf": "ANDROID_PHONE",
    "rdid": "ANDROID_4c212b6629488c91",
    "client_key": "2ac2a76d",
    "os": "android",
}
# tokenClientSalt（用于算 __NStokensig，需要手动从 App 获取）
TOKEN_CLIENT_SALT = "sy5th908xb9bmgiz2ssy0cykzezkq1jf"  # 已知的快手 salt

# ====== 签名桥地址 ======
SIGN_BRIDGE = "http://127.0.0.1:3058"

# ====== 广告请求参数 ======
AD_PARAMS = {
    "businessId": "606",
    "posId": "20346",
    "subPageId": "100024064",
    "requestSceneType": "1",
    "taskType": "1",
    "source": "1",
    "scene": "1",
}

DEVICE_PARAMS = {
    "c": "a",
    "did": CK_PARAMS["did"],
    "oDid": CK_PARAMS["oDid"],
    "rdid": CK_PARAMS["rdid"],
    "did_gt": "1786927428340",
    "did_tag": "0",
    "cdid_tag": "2",
    "mod": "2107119DC",
    "sys": "android14",
    "kpn": CK_PARAMS["kpn"],
    "kpf": CK_PARAMS["kpf"],
    "sdkVersion": "2.0.6.14",
    "appver": "14.4.30.4277",
}

def build_cookie():
    return "; ".join(f"{k}={v}" for k, v in CK_PARAMS.items())

def call_nssig(path, data, salt=TOKEN_CLIENT_SALT):
    """调用签名桥 /nssig 计算签名"""
    body = json.dumps({"path": path, "data": data, "salt": salt}).encode()
    req = urllib.request.Request(
        f"{SIGN_BRIDGE}/nssig",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    resp = urllib.request.urlopen(req, timeout=15)
    return json.loads(resp.read())

def test_ad():
    # 构建请求数据（排序参数，用于 sig 计算）
    sorted_params = sorted(AD_PARAMS.items(), key=lambda x: x[0])
    data_str = "&".join(f"{k}={v}" for k, v in sorted_params)

    # 获取签名
    sig_result = call_nssig("/rest/e/reward/mixed/ad", data_str)
    print("[签名桥]", json.dumps(sig_result, ensure_ascii=False))
    sig = sig_result.get("sig", "")
    nssig3 = sig_result.get("nssig3", "")
    nstokensig = sig_result.get("nstokensig", "")

    # 构建请求 URL
    ad_query = "&".join(f"{k}={v}" for k, v in AD_PARAMS.items())
    ad_query += f"&sig={sig}&__NS_sig3={nssig3}&__NS_xfalcon=&__NStokensig={nstokensig}"

    device_query = "&".join(f"{k}={v}" for k, v in DEVICE_PARAMS.items())
    url = f"https://api.e.kuaishou.com/rest/e/reward/mixed/ad?{device_query}&{ad_query}"

    # 构建 Cookie
    cookie = build_cookie()

    headers = {
        "Host": "api.e.kuaishou.com",
        "User-Agent": "kwai-android aegon/4.26.0",
        "Cookie": cookie,
        "Content-Type": "application/x-www-form-urlencoded",
    }

    print(f"\n[请求] 广告接口...")
    print(f"Cookie: {cookie[:100]}...")
    print(f"sig={sig[:20]}... nssig3={nssig3[:20]}... nstokensig={nstokensig[:20]}...")

    req = urllib.request.Request(url, headers=headers)
    try:
        resp = urllib.request.urlopen(req, timeout=15)
        body = resp.read().decode("utf-8")
        print(f"[HTTP {resp.status}]")
        data = json.loads(body)
        # 只打印关键字段
        if "result" in data:
            print(f"  result={data['result']}")
        if "data" in data:
            d = data["data"]
            if isinstance(d, dict):
                print(f"  keys: {list(d.keys())[:10]}")
        print(json.dumps(data, ensure_ascii=False, indent=2)[:600])
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "ignore")
        print(f"[HTTP {e.code}]")
        print(body[:500])
    except Exception as e:
        print(f"[错误] {e}")

if __name__ == "__main__":
    test_ad()