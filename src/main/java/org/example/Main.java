package org.example;

import com.fastcgi.FCGIInterface;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

class Main {
    static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static int r;
    private static final String RESULT_JSON = """
            {
                "answer": %b,
                "executionTime": "%s",
                "serverTime": "%s"
            }
            """;
    private static final String HTTP_RESPONSE = """
        HTTP/1.1 200 OK
        Content-Type: application/json
        Content-Length: %d
        
        %s
        """;
    private static final String HTTP_ERROR = """
        HTTP/1.1 400 Bad Request
        Content-Type: application/json
        Content-Length: %d
        
        %s
        """;
    private static final String ERROR_JSON = """
        {
            "reason": "%s"
        }
        """;

    public static void main(String[] args) throws IOException {
        var fcgiInterface = new FCGIInterface();
        while (fcgiInterface.FCGIaccept() >= 0) {
            long startTime = System.nanoTime();
            try {
                String body = readRequestBody();
                Map<String, String> params = parse(body);

                float x = Float.parseFloat(params.get("x"));
                int y = Integer.parseInt(params.get("y"));
                r = Integer.parseInt(params.get("r"));

                boolean insideRectangle = isInsideRectangle(x, y, r, r, r);
                boolean insidePolygon = isInsidePolygon(x, y);
                boolean insidePath = isInsidePath(x, y);

                var json = String.format(RESULT_JSON, insideRectangle || insidePolygon || insidePath, System.nanoTime() - startTime, LocalDateTime.now().format(formatter));
                var responseBody = json.trim();
                var response = String.format(HTTP_RESPONSE, responseBody.getBytes(StandardCharsets.UTF_8).length, responseBody);
                try {
                    FCGIInterface.request.outStream.write(response.getBytes(StandardCharsets.UTF_8));
                    FCGIInterface.request.outStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } catch (Exception ex) {
                var json = String.format(ERROR_JSON, ex.getMessage());
                var responseBody = json.trim();
                var response = String.format(HTTP_ERROR, responseBody.getBytes(StandardCharsets.UTF_8).length, responseBody);
                FCGIInterface.request.outStream.write(response.getBytes(StandardCharsets.UTF_8));
                FCGIInterface.request.outStream.flush();
            }
        }
    }

    private static String readRequestBody() throws IOException {
        try {
            FCGIInterface.request.inStream.fill();
            int contentLength = FCGIInterface.request.inStream.available();
            var buffer = ByteBuffer.allocate(contentLength);
            var readBytes = FCGIInterface.request.inStream.read(buffer.array(), 0, contentLength);
            var requestBodyRaw = new byte[readBytes];
            buffer.get(requestBodyRaw);
            buffer.clear();
            return new String(requestBodyRaw, StandardCharsets.UTF_8);
        } catch (NullPointerException e){
            return "";
        }
    }

    private static Map<String, String> parse(String queryParams){
        Map<String, String> params = new HashMap<>();
        if (queryParams != null && !queryParams.isEmpty()) {
            String[] pairs = queryParams.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }

        } else {
            return params;
        }

        return params;
    }
    private static boolean isInsideRectangle(double px, double py, double rectY, double width, double height) {
        return (px >= (double) 0 && px <= (double) 0 + width) && (py >= rectY && py <= rectY + height);
    }

    private static boolean isInsidePolygon(double px, double py) {
        // Определим вершины многоугольника
        double[][] polygon = {
                {0, (double) -r /2},
                {0, 0},
                {r, 0}
        };

        int n = polygon.length;
        boolean inside = false;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon[i][0], yi = polygon[i][1];
            double xj = polygon[j][0], yj = polygon[j][1];

            boolean intersect = ((yi > py) != (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }

        return inside;
    }

    private static boolean isInsidePath(double x, double y) {
        double centerX = 0;
        double centerY = 0;
        double radius = r / 2;
        if (x <= centerX && y <= centerY && x >= (centerX - radius) && y >= (centerY - radius)) {
            double distanceSquared = Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2);
            if (distanceSquared <= Math.pow(radius, 2)) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}