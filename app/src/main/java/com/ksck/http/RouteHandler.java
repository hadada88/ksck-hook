package com.ksck.http;

import com.ksck.signbridge.AtlasSign;
import com.ksck.signbridge.PureSign;

import org.json.JSONObject;

/**
 * 路由处理：/status, /nssig, /encsign
 */
public class RouteHandler {

    public static String handleStatus() {
        return AtlasSign.getStatus();
    }

    public static String handleNssig(String body) {
        try {
            JSONObject json = new JSONObject(body);
            String path = json.optString("path", "");
            String data = json.optString("data", "");
            String salt = json.optString("salt", "");

            // 纯算 sig
            String plainText = PureSign.buildPlainText(data);
            String sig = AtlasSign.accessSig(plainText);
            if (sig == null || sig.length() == 0) {
                sig = PureSign.computeSig(data);
            }
            // 纯算 __NStokensig
            String nstokensig = PureSign.computeTokenSig(sig, salt);
            // Atlas __NS_sig3 = KSecurity.atlasSign(path + sig)
            String nssig3 = AtlasSign.sig3(path, sig);

            JSONObject result = new JSONObject();
            result.put("sig", sig);
            result.put("nssig3", nssig3);
            result.put("nstokensig", nstokensig);
            return result.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    public static String handleEncsign(String body) {
        try {
            JSONObject json = new JSONObject(body);
            String base64Data = json.optString("data", "");
            String[] result = AtlasSign.encsign(base64Data);
            JSONObject jsonResult = new JSONObject();
            jsonResult.put("encdata", result[0]);
            jsonResult.put("sign", result[1]);
            return jsonResult.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}