package org.example;

import com.fastcgi.FCGIInterface;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

class Main {
    static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static int r;
    static int x;
    static float y;
    private static final String RESULT_JSON = """
            {
                "x":"%s",
                "y": "%s",
                "r": "%s",
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
            "x":"%s",
            "y": "%s",
            "r": "%s",
            "reason": "%s"
        }
        """;

    public static void main(String[] args) throws IOException {
        FCGIInterface fcgiInterface = new FCGIInterface();
        while (fcgiInterface.FCGIaccept() >= 0) {
            long startTime = System.nanoTime();
            boolean correctX = false;
            boolean correctY = false;
            boolean correctR = false;
            try {
                String body = readRequestBody();
                Map<String, String> params = parse(body);

                if (params.containsKey("x")) {
                    x = Integer.parseInt(params.get("x"));
                    correctX = true;
                }
                if (params.containsKey("y")) {
                    BigDecimal yValue = new BigDecimal(params.get("y"));
                    y = yValue.setScale(2, RoundingMode.HALF_UP).floatValue();
                    if (y > -5 && y < 3) {
                        correctY = true;
                    }
                }
                if (params.containsKey("r")) {
                    r = Integer.parseInt(params.get("r"));
                    correctR = true;
                }

                if (!(correctY && correctX && correctR)) {
                    throw new Exception("incorrect parameters");
                }
                boolean insideRectangle = isInsidePolygon(x, y, r);
                boolean insidePolygon = isInsideRectangle(x, y, r);
                boolean insidePath = isInsidePath(x, y, r);

                var json = String.format(RESULT_JSON, x, y, r, insideRectangle || insidePolygon || insidePath, System.nanoTime() - startTime, LocalDateTime.now().format(formatter));
                var responseBody = json.trim();
                var response = String.format(HTTP_RESPONSE, responseBody.getBytes(StandardCharsets.UTF_8).length, responseBody);
                try {
                    FCGIInterface.request.outStream.write(response.getBytes(StandardCharsets.UTF_8));
                    FCGIInterface.request.outStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } catch (Exception ex) {
                Function<Boolean, String> xCheck = status -> {
                    if (status){
                        return String.valueOf(x);
                    }
                    return "incorrect Value";
                };
                Function<Boolean, String> yCheck = status -> {
                    if (status){
                        return String.valueOf(y);
                    }
                    return "incorrect Value";
                };
                Function<Boolean, String> rCheck = status -> {
                    if (status){
                        return String.valueOf(r);
                    }
                    return "incorrect Value";
                };
                var json = String.format(ERROR_JSON, xCheck.apply(correctX), yCheck.apply(correctY), rCheck.apply(correctR), ex.getMessage());
                var responseBody = json.trim();
                var response = String.format(HTTP_ERROR, responseBody.getBytes(StandardCharsets.UTF_8).length, responseBody);
                FCGIInterface.request.outStream.write(response.getBytes(StandardCharsets.UTF_8));
                FCGIInterface.request.outStream.flush();

                //как работают потоки в джаве
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
           return e.getMessage();
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
        }
        return params;
    }
    private static boolean isInsidePolygon(double x, double y, double r) {
        return x <= r && y <= r && x >= 0 && y >= 0;
    }

    private static boolean isInsideRectangle(double x, double y, double r) {
        return y >= 0.5*x - r/2 && y <= 0 && y >= -r/2 && x >= 0 && x <= r;
    }

    private static boolean isInsidePath(double x, double y, double r) {
        double radius = r / 2;
        return x <= 0 && y <= 0 && x >= (0 - radius) && y >= (0 - radius) && Math.pow(x, 2) + Math.pow(y, 2) <= Math.pow(radius, 2);
    }
}