package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.net.*;
import java.util.*;

public class MiniBrowserServer {
    public static void main(String[] args) throws IOException {
        int port = 8080;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server đang chạy tại http://localhost:" + port);

        while (true) {
            Socket client = serverSocket.accept();
            new Thread(() -> handleClient(client)).start();
        }
    }

    private static void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream out = socket.getOutputStream()) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String path = parts[1];

            // Bỏ qua các header
            while (!(in.readLine()).isEmpty()) { }

            // Trang chủ (form tìm kiếm)
            if (path.equals("/")) {
                sendHtml(out, getHomePage());
                return;
            }

            // Khi người dùng bấm "Tìm kiếm"
            if (path.startsWith("/search")) {
                // Phân tích query (vd: /search?url=https://example.com&method=GET)
                Map<String, String> query = parseQuery(path);
                String targetUrl = query.get("url");
                String reqMethod = query.get("method");

                if (targetUrl == null || targetUrl.isEmpty()) {
                    sendHtml(out, "<h2>Vui lòng nhập URL!</h2>");
                    return;
                }

                String resultHtml = handleWebRequest(targetUrl, reqMethod);
                sendHtml(out, resultHtml);
                return;
            }

            // Không tìm thấy
            sendHtml(out, "<h2>404 Not Found</h2>");

        } catch (Exception e) {
            e.printStackTrace();
        }
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

    private static String handleWebRequest(String targetUrl, String method) {
        StringBuilder result = new StringBuilder();
        result.append("<html><head><title>Kết quả</title></head><body>");
        result.append("<a href='/'>← Quay lại</a><br><br>");

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            result.append("<h2>Request: ").append(method).append(" ").append(targetUrl).append("</h2>");
            result.append("<p>Mã phản hồi: ").append(conn.getResponseCode()).append("</p>");

            if (method.equals("HEAD")) {
                result.append("<h3>Header:</h3><pre>");
                for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                    result.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                result.append("</pre>");
            } else { // GET hoặc POST
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder htmlContent = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) htmlContent.append(line);
                reader.close();

                Document doc = Jsoup.parse(htmlContent.toString());
                int len = htmlContent.length();
                int pCount = doc.select("p").size();
                int divCount = doc.select("div").size();
                int spanCount = doc.select("span").size();
                int imgCount = doc.select("img").size();

                result.append("<h3>Phân tích HTML:</h3>");
                result.append("<ul>")
                        .append("<li>Chiều dài: ").append(len).append("</li>")
                        .append("<li><p>Thẻ &lt;p&gt;: ").append(pCount).append("</p></li>")
                        .append("<li><p>Thẻ &lt;div&gt;: ").append(divCount).append("</p></li>")
                        .append("<li><p>Thẻ &lt;span&gt;: ").append(spanCount).append("</p></li>")
                        .append("<li><p>Thẻ &lt;img&gt;: ").append(imgCount).append("</p></li>")
                        .append("</ul>");

                result.append("<h3>Xem trước văn bản:</h3>");
                result.append("<div style='border:1px solid #ccc;padding:10px;'>");
                result.append(doc.text().substring(0, Math.min(500, doc.text().length())));
                result.append("...</div>");
            }
        } catch (Exception e) {
            result.append("<p style='color:red;'>Lỗi: ").append(e.getMessage()).append("</p>");
        }

        result.append("</body></html>");
        return result.toString();
    }

    private static String getHomePage() {
        return """
        <html>
        <head><title>Mini Browser</title></head>
        <body style='font-family:sans-serif;'>
        <h2>Trình duyệt thu nhỏ (Mini Browser)</h2>
        <form action='/search' method='get'>
            <label>Nhập URL:</label><br>
            <input type='text' name='url' style='width:400px;' placeholder='https://example.com' required><br><br>
            <label>Phương thức:</label>
            <select name='method'>
                <option>GET</option>
                <option>POST</option>
                <option>HEAD</option>
            </select><br><br>
            <input type='submit' value='Tìm kiếm'>
        </form>
        </body>
        </html>
        """;
    }
}
