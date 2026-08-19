#!/usr/bin/env python3
"""测试签名桥：按快手真实结构请求广告接口
device_params 放 URL 前部（不参与签名），业务参数参与签名
"""
import json, urllib.request, urllib.parse, hashlib

# ====== CK 参数 ======
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
TOKEN_CLIENT_SALT = "sy5th908xb9bmgiz2ssy0cykzezkq1jf"
SIGN_BRIDGE = "http://127.0.0.1:3058"

# ====== 业务参数（参与签名）====== 
AD_PARAMS = {
    "businessId": "606",
    "posId": "20346",
    "subPageId": "100024064",
    "requestSceneType": "1",
    "taskType": "1",
    "source": "1",
    "scene": "1",
    "os": "android",
    "client_key": "2ac2a76d",
    "kuaishou.api_st": CK_PARAMS["kuaishou.api_st"],
    "egid": CK_PARAMS["egid"],
}

# ====== 设备参数（不参与签名，放 URL 前部）====== 
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
    body = json.dumps({"path": path, "data": data, "salt": salt}).encode()
    req = urllib.request.Request(
        f"{SIGN_BRIDGE}/nssig",
        data=body, headers={"Content-Type": "application/json"},
    )
    resp = urllib.request.urlopen(req, timeout=15)
    return json.loads(resp.read())

def test_ad():
    # 业务参数排序拼接（只对业务参数签名）
    sorted_items = sorted(AD_PARAMS.items(), key=lambda x: x[0])
    data_str = "&".join(f"{k}={v}" for k, v in sorted_items)

    # 本地纯算验证
    salt = "772867c19925"
    s = ""
    last_key = None
    for k, v in sorted_items:
        if k.startswith("__NS"): continue
        if k != last_key:
            s += f"{k}={v}"
            last_key = k
    local_sig = hashlib.md5((s + salt).encode()).hexdigest()
    print(f"[本地纯算] sig={local_sig}")

    # 签名桥计算
    sig_result = call_nssig("/rest/e/reward/mixed/ad", data_str)
    sig = sig_result.get("sig", "")
    nssig3 = sig_result.get("nssig3", "")
    nstokensig = sig_result.get("nstokensig", "")
    print(f"[签名桥]   sig={sig[:32]}... nssig3={nssig3[:20]}... nstokensig={nstokensig[:20]}...")

    # 构建 URL：设备参数 + 业务参数（含签名）——与 test_ks_ad.py 结构一致
    ad_query = "&".join(f"{k}={v}" for k, v in sorted_items)
    ad_query += f"&sig={sig}&__NS_sig3={nssig3}&__NS_xfalcon=&__NStokensig={nstokensig}"

    device_query = "&".join(f"{k}={v}" for k, v in sorted(DEVICE_PARAMS.items(), key=lambda x: x[0]))
    url = f"https://api.e.kuaishou.com/rest/e/reward/mixed/ad?{device_query}&{ad_query}"

    headers = {
        "Host": "api.e.kuaishou.com",
        "User-Agent": "kwai-android aegon/4.26.0",
        "Cookie": build_cookie(),
        "Content-Type": "application/x-www-form-urlencoded",
    }

    print(f"\n[请求] 广告接口 (sig 前32位: {sig[:32]})")
    req = urllib.request.Request(url, headers=headers)
    try:
        resp = urllib.request.urlopen(req, timeout=15)
        body = resp.read().decode("utf-8")
        print(f"[HTTP {resp.status}]")
        data = json.loads(body)
        if "result" in data:
            print(f"  result={data['result']}")
        if "error_msg" in data:
            print(f"  error_msg={data['error_msg']}")
        print(json.dumps(data, ensure_ascii=False, indent=2)[:600])
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "ignore")
        print(f"[HTTP {e.code}]")
        print(body[:500])
    except Exception as e:
        print(f"[错误] {e}")

if __name__ == "__main__":
    test_ad()