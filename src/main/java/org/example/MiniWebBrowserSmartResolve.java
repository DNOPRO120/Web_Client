package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MiniWebBrowserSmartResolve {
    public static void main(String[] args) throws IOException {
        int port = 8080;
        ServerSocket server = new ServerSocket(port);
        System.out.println("Server đang chạy tại http://localhost:" + port);

        while (true) {
            Socket client = server.accept();
            new Thread(() -> handleClient(client)).start();
        }
    }

    private static void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream out = socket.getOutputStream()) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            String path = parts[1];

            while (!(in.readLine()).isEmpty()) { }

            if (path.equals("/")) {
                sendHtml(out, getHomePage(null, null));
                return;
            }

            if (path.startsWith("/view")) {
                Map<String, String> query = parseQuery(path);
                String raw = query.get("url");
                String resolved = resolveUrl(raw);
                sendHtml(out, getHomePage(resolved, null));
                return;
            }

            if (path.startsWith("/analyze")) {
                Map<String, String> query = parseQuery(path);
                String raw = query.get("url");
                String method = query.get("method");
                String resolved = resolveUrl(raw);
                String result = analyze(resolved, method);
                sendHtml(out, getHomePage(resolved, result));
                return;
            }

            sendHtml(out, "<h2>404 Not Found</h2>");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========================
    // URL / search resolving
    // ========================
    private static String resolveUrl(String raw) {
        if (raw == null) return "";
        raw = raw.trim();
        if (raw.isEmpty()) return "";

        if (raw.contains(" ")) {
            try {
                String q = URLEncoder.encode(raw, "UTF-8");
                return "https://www.google.com/search?q=" + q;
            } catch (UnsupportedEncodingException e) {
                return "https://www.google.com/search?q=" + raw.replace(" ", "+");
            }
        }

        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }

        if (raw.contains(".")) {
            String candidate = "https://" + raw;
            if (checkUrlAvailable(candidate)) return candidate;
            candidate = "http://" + raw;
            if (checkUrlAvailable(candidate)) return candidate;
            return "https://" + raw;
        }

        String lc = raw.toLowerCase();
        Map<String, String> shortcuts = getShortcutsMap();
        if (shortcuts.containsKey(lc)) {
            String mapped = shortcuts.get(lc);
            String cand = "https://" + mapped;
            if (checkUrlAvailable(cand)) return cand;
            if (checkUrlAvailable("http://" + mapped)) return "http://" + mapped;
        }

        String[] tlds = {".com", ".vn", ".net", ".org", ".edu", ".com.vn"};
        for (String tld : tlds) {
            String candidate = "https://" + raw + tld;
            if (checkUrlAvailable(candidate)) return candidate;
        }
        for (String tld : tlds) {
            String candidate = "http://" + raw + tld;
            if (checkUrlAvailable(candidate)) return candidate;
        }

        return "https://" + raw;
    }

    private static Map<String, String> getShortcutsMap() {
        Map<String, String> m = new HashMap<>();
        m.put("fb", "facebook.com");
        m.put("facebook", "facebook.com");
        m.put("yt", "youtube.com");
        m.put("youtube", "youtube.com");
        m.put("gg", "google.com");
        m.put("g", "google.com");
        m.put("gh", "github.com");
        m.put("git", "github.com");
        m.put("in", "instagram.com");
        m.put("tt", "twitter.com");
        m.put("tn", "thanhnien.vn");
        m.put("thanhnien", "thanhnien.vn");
        return m;
    }

    private static boolean checkUrlAvailable(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36");
            int code = conn.getResponseCode();
            return (code >= 200 && code < 400);
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ========================
    // HTTP + analysis
    // ========================
    private static String analyze(String targetUrl, String method) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='padding:10px;background:#fff;border-radius:10px;box-shadow:0 0 5px #ccc;'>");
        sb.append("<h3>Phân tích ").append(method).append(" ").append(targetUrl).append("</h3>");

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            conn.setInstanceFollowRedirects(true);

            // Nếu là POST -> gửi dữ liệu form mẫu
            if ("POST".equalsIgnoreCase(method)) {
                conn.setDoOutput(true);
                String postData = "name=Dat&project=MiniBrowser";
                byte[] postBytes = postData.getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Content-Length", String.valueOf(postBytes.length));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postBytes);
                }

                sb.append("<h4>📤 Request gửi đi:</h4><pre>")
                        .append(method).append(" ").append(targetUrl).append("\n")
                        .append("Content-Type: application/x-www-form-urlencoded\n")
                        .append("Body: ").append(postData)
                        .append("</pre>");
            }

            int code = conn.getResponseCode();
            sb.append("<p><b>Mã phản hồi:</b> ").append(code).append(" - ").append(conn.getResponseMessage()).append("</p>");

            if ("HEAD".equalsIgnoreCase(method)) {
                sb.append("<h4>Header:</h4><pre>");
                for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                    sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                sb.append("</pre>");
            } else {
                InputStream is;
                try {
                    is = conn.getInputStream();
                } catch (IOException ioe) {
                    is = conn.getErrorStream();
                }
                if (is == null) {
                    sb.append("<p style='color:red;'>Không lấy được nội dung.</p>");
                } else {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder html = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) html.append(line);
                    reader.close();

                    sb.append("<h4>📥 Phản hồi từ Server:</h4><pre style='max-height:300px;overflow:auto;background:#f0f0f0;padding:5px;border-radius:5px;'>")
                            .append(html.toString().replace("<", "&lt;").replace(">", "&gt;"))
                            .append("</pre>");

                    if (!"POST".equalsIgnoreCase(method)) {
                        Document doc = Jsoup.parse(html.toString());
                        sb.append("<ul>");
                        sb.append("<li>Độ dài HTML: ").append(html.length()).append("</li>");
                        sb.append("<li>Số thẻ &lt;p&gt;: ").append(doc.select("p").size()).append("</li>");
                        sb.append("<li>Số thẻ &lt;div&gt;: ").append(doc.select("div").size()).append("</li>");
                        sb.append("<li>Số thẻ &lt;span&gt;: ").append(doc.select("span").size()).append("</li>");
                        sb.append("<li>Số thẻ &lt;img&gt;: ").append(doc.select("img").size()).append("</li>");
                        sb.append("</ul>");
                    }
                }
            }
        } catch (Exception e) {
            sb.append("<p style='color:red;'>Lỗi khi phân tích: ").append(e.getMessage()).append("</p>");
        }

        sb.append("</div>");
        return sb.toString();
    }

    // ========================
    // HTTP server helpers
    // ========================
    private static Map<String, String> parseQuery(String path) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        if (path.contains("?")) {
            String queryStr = path.substring(path.indexOf("?") + 1);
            for (String pair : queryStr.split("&")) {
                String[] kv = pair.split("=");
                if (kv.length == 2)
                    map.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
            }
        }
        return map;
    }

    private static void sendHtml(OutputStream out, String html) throws IOException {
        byte[] bytes = html.getBytes("UTF-8");
        PrintWriter pw = new PrintWriter(out);
        pw.println("HTTP/1.1 200 OK");
        pw.println("Content-Type: text/html; charset=UTF-8");
        pw.println("Content-Length: " + bytes.length);
        pw.println();
        pw.flush();
        out.write(bytes);
        out.flush();
    }

    private static String getHomePage(String url, String info) {
        if (url == null) url = "";

        String iframeSection = "";
        if (!url.isEmpty()) {
            iframeSection = "<iframe src='" + url + "' style='width:100%;height:500px;border:1px solid #ccc;'></iframe>";
        }

        String resultSection = info != null ? "<div style='margin-top:20px;'>" + info + "</div>" : "";

        return """
        <html>
        <head>
        <meta charset='UTF-8'>
        <title>Mini Web Browser</title>
        <style>
            body { font-family: Arial; margin: 30px; background: #f9f9f9; }
            .bar { background: #fff; padding: 10px; border-radius: 10px; box-shadow: 0 0 5px #ccc; margin-bottom: 15px; }
            input[type=text] { width: 400px; padding: 5px; }
            button { padding: 6px 12px; margin-left: 5px; cursor: pointer; }
            iframe { background: white; border-radius: 10px; }
            pre { white-space: pre-wrap; word-wrap: break-word; }
        </style>
        </head>
        <body>
            <div class='bar'>
                <form action='/view' method='get' style='display:inline;'>
                    <input type='text' name='url' placeholder='ví dụ: thanhnien.vn hoặc facebook.com' value='%s'>
                    <button type='submit'>🔍 Tìm kiếm</button>
                </form>
                %s
            </div>
            %s
            %s
        </body>
        </html>
        """.formatted(
                url,
                url.isEmpty() ? "" :
                        "<button onclick=\"window.location='/analyze?method=GET&url=" + url + "'\">GET</button>" +
                                "<button onclick=\"window.location='/analyze?method=POST&url=" + url + "'\">POST</button>" +
                                "<button onclick=\"window.location='/analyze?method=HEAD&url=" + url + "'\">HEAD</button>",
                iframeSection,
                resultSection
        );
    }
}
