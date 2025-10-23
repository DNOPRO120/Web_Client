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
        System.out.println("🌐 Server đang chạy tại: http://localhost:" + port);

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

            while (!(in.readLine()).isEmpty()) { }

            if (path.equals("/")) {
                sendHtml(out, getHomePage());
                return;
            }

            if (path.startsWith("/search")) {
                Map<String, String> query = parseQuery(path);
                String targetUrl = query.get("url");
                String reqMethod = query.get("method");

                if (targetUrl == null || targetUrl.isEmpty()) {
                    sendHtml(out, "<h2>❌ Vui lòng nhập URL hợp lệ!</h2>");
                    return;
                }

                String resultHtml = handleWebRequest(targetUrl, reqMethod);
                sendHtml(out, resultHtml);
                return;
            }

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
        result.append("""
        <html>
        <head>
        <meta charset='UTF-8'>
        <title>Kết quả phân tích</title>
        <style>
            body { font-family: Arial, sans-serif; background: #f5f7fa; margin: 40px; }
            a { text-decoration: none; color: #0077cc; }
            h2 { color: #333; }
            table { border-collapse: collapse; margin-top: 20px; width: 60%; background: white; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
            th, td { border: 1px solid #ccc; padding: 10px; text-align: left; }
            th { background-color: #0077cc; color: white; }
            tr:nth-child(even) { background-color: #f2f2f2; }
            .preview { border: 1px solid #ccc; background: white; padding: 10px; margin-top: 20px; max-height: 250px; overflow-y: auto; }
            details { margin-top: 20px; background: #fff; border: 1px solid #ddd; padding: 10px; border-radius: 6px; }
            summary { cursor: pointer; font-weight: bold; color: #0077cc; }
        </style>
        </head>
        <body>
        <a href='/'>← Quay lại trang chủ</a><br><br>
        """);

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            result.append("<h2>🌍 Kết quả cho: ").append(method).append(" ").append(targetUrl).append("</h2>");
            result.append("<p><b>Mã phản hồi:</b> ").append(conn.getResponseCode()).append("</p>");

            if (method.equals("HEAD")) {
                result.append("<h3>📄 Header của tài nguyên:</h3><pre>");
                for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                    result.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                result.append("</pre>");
            } else {
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

                result.append("<h3>📊 Bảng thống kê HTML</h3>");
                result.append("<table>")
                        .append("<tr><th>Thông tin</th><th>Giá trị</th></tr>")
                        .append("<tr><td>Chiều dài HTML</td><td>").append(len).append(" ký tự</td></tr>")
                        .append("<tr><td>Số thẻ &lt;p&gt;</td><td>").append(pCount).append("</td></tr>")
                        .append("<tr><td>Số thẻ &lt;div&gt;</td><td>").append(divCount).append("</td></tr>")
                        .append("<tr><td>Số thẻ &lt;span&gt;</td><td>").append(spanCount).append("</td></tr>")
                        .append("<tr><td>Số thẻ &lt;img&gt;</td><td>").append(imgCount).append("</td></tr>")
                        .append("</table>");

                result.append("<h3>📝 Xem trước nội dung văn bản:</h3>");
                result.append("<div class='preview'>")
                        .append(doc.text().substring(0, Math.min(600, doc.text().length())))
                        .append("...</div>");

                result.append("<details><summary>🔍 Xem toàn bộ mã HTML</summary>")
                        .append("<pre style='white-space: pre-wrap; max-height: 400px; overflow-y:auto;'>")
                        .append(htmlContent.toString()
                                .replace("<", "&lt;")
                                .replace(">", "&gt;"))
                        .append("</pre></details>");
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
        <head>
        <meta charset='UTF-8'>
        <title>Mini Browser</title>
        <style>
            body { font-family: Arial, sans-serif; background-color: #f7f9fc; color: #333; margin: 50px; }
            h2 { color: #0077cc; }
            form { background: white; padding: 20px; border-radius: 10px; box-shadow: 0 3px 8px rgba(0,0,0,0.1); width: 450px; }
            input[type=text], select { width: 100%; padding: 10px; margin-top: 10px; border: 1px solid #ccc; border-radius: 6px; }
            input[type=submit] { background-color: #0077cc; color: white; border: none; padding: 10px 15px; border-radius: 6px; margin-top: 15px; cursor: pointer; }
            input[type=submit]:hover { background-color: #005fa3; }
        </style>
        </head>
        <body>
        <h2>🌐 Trình duyệt thu nhỏ (Mini Browser)</h2>
        <form action='/search' method='get'>
            <label>🔗 Nhập URL:</label>
            <input type='text' name='url' placeholder='https://example.com' required>
            <label>⚙️ Chọn phương thức:</label>
            <select name='method'>
                <option>GET</option>
                <option>POST</option>
                <option>HEAD</option>
            </select>
            <input type='submit' value='Gửi yêu cầu'>
        </form>
        </body>
        </html>
        """;
    }
}
