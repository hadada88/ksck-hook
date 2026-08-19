#!/usr/bin/env python3
"""测试签名桥：正确算 sig（包含所有参数），请求快手广告接口"""
import json, urllib.request, sys, urllib.parse

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
    body = json.dumps({"path": path, "data": data, "salt": salt}).encode()
    req = urllib.request.Request(
        f"{SIGN_BRIDGE}/nssig",
        data=body, headers={"Content-Type": "application/json"},
    )
    resp = urllib.request.urlopen(req, timeout=15)
    return json.loads(resp.read())

def test_ad():
    # 合并所有参数（设备参数 + 业务参数）—— 快手 App 里 sig 是对全部参数算的
    all_params = {}
    all_params.update(DEVICE_PARAMS)
    all_params.update(AD_PARAMS)
    
    # 先不传 sig/nssig 等签名参数，让签名桥算
    sorted_items = sorted(all_params.items(), key=lambda x: x[0])
    data_str = "&".join(f"{k}={v}" for k, v in sorted_items)
    
    print(f"[参数] 共 {len(all_params)} 个参数")
    print(f"[data] {data_str[:200]}...")

    # 调用签名桥
    sig_result = call_nssig("/rest/e/reward/mixed/ad", data_str)
    sig = sig_result.get("sig", "")
    nssig3 = sig_result.get("nssig3", "")
    nstokensig = sig_result.get("nstokensig", "")
    print(f"[签名桥] sig={sig[:32]}... nssig3={nssig3[:20]}... nstokensig={nstokensig[:20]}...")

    # 构建完整参数（含签名参数）
    all_params["sig"] = sig
    all_params["__NS_sig3"] = nssig3
    all_params["__NS_xfalcon"] = ""
    all_params["__NStokensig"] = nstokensig
    
    # 构建 URL
    query = "&".join(f"{k}={v}" for k, v in sorted(all_params.items(), key=lambda x: x[0]))
    url = f"https://api.e.kuaishou.com/rest/e/reward/mixed/ad?{query}"

    headers = {
        "Host": "api.e.kuaishou.com",
        "User-Agent": "kwai-android aegon/4.26.0",
        "Cookie": build_cookie(),
        "Content-Type": "application/x-www-form-urlencoded",
    }

    print(f"\n[请求] 广告接口...")
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
        # 打印完整响应
        text = json.dumps(data, ensure_ascii=False, indent=2)
        print(text[:800])
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "ignore")
        print(f"[HTTP {e.code}]")
        try:
            print(json.dumps(json.loads(body), ensure_ascii=False, indent=2)[:500])
        except:
            print(body[:500])
    except Exception as e:
        print(f"[错误] {e}")

if __name__ == "__main__":
    test_ad()