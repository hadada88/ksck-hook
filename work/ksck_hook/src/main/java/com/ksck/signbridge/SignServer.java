package com.ksck.signbridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 极简 HTTP 签名服务器（运行在手机端，替换 Windows 上的 ks_local_bridge.py）
 * 监听 127.0.0.1:3058，提供：
 *   /nssig   - POST, body: {path, data, salt} -> {sig, nssig3, nstokensig}
 *   /encsign - POST, body: {data} -> {encdata, sign}
 *   /status  - GET -> {initialized, wrapper, ...}
 */
public class SignServer {

    private static final int PORT = 3058;
    private static ServerSocket serverSocket;
    private static volatile boolean running = false;

    public static synchronized void start() {
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT, 50, java.net.InetAddress.getByName("127.0.0.1"));
                XposedBridge.log("[SignServer] listening on 127.0.0.1:" + PORT);
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        handleClient(client);
                    } catch (IOException e) {
                        if (running) XposedBridge.log("[SignServer] accept error: " + e);
                    }
                }
            } catch (Exception e) {
                XposedBridge.log("[SignServer] start failed: " + e);
            }
        }, "SignServer").start();
    }

    public static synchronized void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
    }

    private static void handleClient(Socket client) {
        try (Socket s = client;
             BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
             OutputStream out = s.getOutputStream()) {

            String requestLine = r.readLine();
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String path = parts.length > 1 ? parts[1].split("\\?")[0] : "/";

            // 读取请求头
            Map<String, String> headers = new HashMap<>();
            String line;
            int contentLength = 0;
            while ((line = r.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String k = line.substring(0, colon).trim().toLowerCase();
                    String v = line.substring(colon + 1).trim();
                    headers.put(k, v);
                    if (k.equals("content-length")) contentLength = Integer.parseInt(v);
                }
            }

            // 读取请求体
            String body = "";
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                int read = r.read(buf, 0, contentLength);
                if (read > 0) body = new String(buf, 0, read);
            }

            String response;
            if ("GET".equals(method) && "/status".equals(path)) {
                response = StatusHandler.handle();
            } else if ("POST".equals(method) && "/nssig".equals(path)) {
                response = NssigHandler.handle(body);
            } else if ("POST".equals(method) && "/encsign".equals(path)) {
                response = EncsignHandler.handle(body);
            } else {
                response = "{\"error\":\"not_found\"}";
            }

            byte[] respBytes = response.getBytes("UTF-8");
            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: " + respBytes.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(header.getBytes("UTF-8"));
            out.write(respBytes);
            out.flush();
        } catch (Exception e) {
            XposedBridge.log("[SignServer] handle error: " + e);
        }
    }
}