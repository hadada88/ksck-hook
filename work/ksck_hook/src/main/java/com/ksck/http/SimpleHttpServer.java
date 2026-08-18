package com.ksck.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * 极简 HTTP 服务器（单线程，只处理本机请求）
 * 监听 127.0.0.1:3058
 */
public class SimpleHttpServer {
    private static final int PORT = 3058;
    private static ServerSocket serverSocket;
    private static volatile boolean running = false;
    private static volatile boolean available = false;

    public static synchronized void start() {
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT, 50, java.net.InetAddress.getByName("127.0.0.1"));
                available = true;
                android.util.Log.i("SignBridge", "listening on 127.0.0.1:" + PORT);
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        handleClient(client);
                    } catch (IOException e) {
                        if (running) android.util.Log.e("SignBridge", "accept: " + e);
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("SignBridge", "start: " + e);
            }
        }, "SignBridge-HTTP").start();
    }

    public static boolean isAvailable() { return available; }

    public static synchronized void stop() {
        running = false;
        available = false;
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

            String body = "";
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                int read = r.read(buf, 0, contentLength);
                if (read > 0) body = new String(buf, 0, read);
            }

            String response;
            if ("GET".equals(method) && "/status".equals(path)) {
                response = RouteHandler.handleStatus();
            } else if ("POST".equals(method) && "/nssig".equals(path)) {
                response = RouteHandler.handleNssig(body);
            } else if ("POST".equals(method) && "/encsign".equals(path)) {
                response = RouteHandler.handleEncsign(body);
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
            android.util.Log.e("SignBridge", "handle: " + e);
        }
    }
}