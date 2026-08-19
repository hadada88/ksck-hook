#!/usr/bin/env python3
"""????????????

?????result=50 ?? URL ????????????? MD5 sig / ??
TOKEN_CLIENT_SALT ??????????????????? URL ??????
??? /nssig ????????????????? KS_CK ???? salt/token?
"""
import argparse
import hashlib
import json
import os
import urllib.parse
import urllib.request
import urllib.error

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

AD_PARAMS = {
    "businessId": "606",
    "posId": "20346",
    "subPageId": "100024064",
    "requestSceneType": "1",
    "taskType": "1",
    "source": "1",
    "scene": "1",
    "os": CK_PARAMS["os"],
    "client_key": CK_PARAMS["client_key"],
    "kuaishou.api_st": CK_PARAMS["kuaishou.api_st"],
    "egid": CK_PARAMS["egid"],
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

def parse_kv_blob(blob: str):
    out = {}
    salt = None
    if not blob:
        return out, salt
    if "#=" in blob:
        blob, salt = blob.split("#=", 1)
        salt = salt.strip().strip(";")
    for part in blob.replace("\n", ";").split(";"):
        part = part.strip()
        if not part or "=" not in part:
            continue
        k, v = part.split("=", 1)
        if k.strip() == "tokenClientSalt":
            salt = v.strip()
        else:
            out[k.strip()] = v.strip()
    return out, salt

def apply_live_ck(blob: str):
    global TOKEN_CLIENT_SALT
    params, salt = parse_kv_blob(blob)
    for k, v in params.items():
        if k in CK_PARAMS:
            CK_PARAMS[k] = v
    if salt:
        TOKEN_CLIENT_SALT = salt
    AD_PARAMS.update({
        "os": CK_PARAMS["os"],
        "client_key": CK_PARAMS["client_key"],
        "kuaishou.api_st": CK_PARAMS["kuaishou.api_st"],
        "egid": CK_PARAMS["egid"],
    })
    for k in ("did", "oDid", "rdid", "kpn", "kpf"):
        if k in CK_PARAMS:
            DEVICE_PARAMS[k] = CK_PARAMS[k]

def build_cookie():
    return "; ".join(f"{k}={v}" for k, v in CK_PARAMS.items())

def encode_query(params):
    return urllib.parse.urlencode(sorted(params.items()), safe="")

def signing_data(params):
    return "&".join(f"{k}={v}" for k, v in sorted(params.items()))

def call_nssig(path, data, salt):
    body = json.dumps({"path": path, "data": data, "salt": salt}).encode()
    req = urllib.request.Request(
        f"{SIGN_BRIDGE}/nssig",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read())

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ck", default=os.environ.get("KS_CK", ""), help="MainHook ??? KS_CK ??")
    parser.add_argument("--salt", default="", help="?? tokenClientSalt")
    args = parser.parse_args()
    if args.ck:
        apply_live_ck(args.ck)
    if args.salt:
        globals()["TOKEN_CLIENT_SALT"] = args.salt

    request_params = {}
    request_params.update(DEVICE_PARAMS)
    request_params.update(AD_PARAMS)
    data_str = signing_data(request_params)

    legacy_md5 = hashlib.md5(("".join(f"{k}={v}" for k, v in sorted(request_params.items())) + "772867c19925").encode()).hexdigest()
    sig_result = call_nssig("/rest/e/reward/mixed/ad", data_str, TOKEN_CLIENT_SALT)
    sig = sig_result.get("sig", "")
    nssig3 = sig_result.get("nssig3", "")
    nstokensig = sig_result.get("nstokensig", "")

    print(f"[????] params={len(request_params)} salt_len={len(TOKEN_CLIENT_SALT)}")
    print(f"[?MD5??] {legacy_md5}")
    print(f"[???] sig={sig[:32]} len={len(sig)} nssig3={nssig3[:20]}... nstokensig={nstokensig[:20]}...")
    if sig == legacy_md5:
        print("[??] ???????? MD5 ???????? AtlasSign.accessSig ?????????")

    request_params.update({
        "sig": sig,
        "__NS_sig3": nssig3,
        "__NS_xfalcon": "",
        "__NStokensig": nstokensig,
    })
    url = "https://api.e.kuaishou.com/rest/e/reward/mixed/ad?" + encode_query(request_params)
    headers = {
        "Host": "api.e.kuaishou.com",
        "User-Agent": "kwai-android aegon/4.26.0",
        "Cookie": build_cookie(),
        "Content-Type": "application/x-www-form-urlencoded",
    }

    print("\n[??] ????")
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = resp.read().decode("utf-8")
            print(f"[HTTP {resp.status}]")
            data = json.loads(body)
            if "result" in data:
                print(f"  result={data['result']}")
            if "error_msg" in data:
                print(f"  error_msg={data['error_msg']}")
            print(json.dumps(data, ensure_ascii=False, indent=2)[:800])
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "ignore")
        print(f"[HTTP {e.code}]")
        print(body[:500])
    except Exception as e:
        print(f"[??] {e}")

if __name__ == "__main__":
    main()
