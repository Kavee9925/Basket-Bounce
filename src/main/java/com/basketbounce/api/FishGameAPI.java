package com.basketbounce.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/** Client for the official Marc Conrad Fish Game API. */
public class FishGameAPI {
    // The documentation specifies JSON output and says Base64 is disabled by default.
    private static final String HTTPS_ENDPOINT = "https://marcconrad.com/uob/fish/api.php?out=json&base64=no";
    private static final String HTTP_ENDPOINT = "http://marcconrad.com/uob/fish/api.php?out=json&base64=no";
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public Question fetch() throws Exception {
        Exception httpsFailure;
        try {
            return fetchFrom(HTTPS_ENDPOINT);
        } catch (Exception ex) {
            httpsFailure = ex;
        }
        try {
            return fetchFrom(HTTP_ENDPOINT);
        } catch (Exception httpFailure) {
            httpFailure.addSuppressed(httpsFailure);
            throw new IOException("Could not load the official Fish Game API. Check your internet connection and try again. " + safeMessage(httpFailure), httpFailure);
        }
    }

    private Question fetchFrom(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "BasketBounce/1.0")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Fish API returned HTTP " + response.statusCode());
        }
        String body = response.body() == null ? "" : response.body().trim();
        if (body.isEmpty()) throw new IOException("Fish API returned an empty response");
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) throw new IOException("Fish API response is not a JSON object");
            json = parsed.getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("Fish API returned invalid JSON: " + safeMessage(ex), ex);
        }
        // Official live response shape: { "question": "image URL", "fish": 6, "chest": 5 }.
        // The two numeric fields are the puzzle's answers, in fish-then-chest order.
        String answer = readAnswer(json);
        String imageValue = readPrimitive(json, "question", "image", "data");
        if (imageValue.isBlank()) throw new IOException("Fish API JSON is missing the question image URL");
        BufferedImage image = loadQuestionImage(imageValue);
        return new Question(answer, image);
    }

    static String readAnswer(JsonObject json) throws IOException {
        String fish = readPrimitive(json, "fish");
        String chest = readPrimitive(json, "chest");
        if (!fish.isBlank() && !chest.isBlank()) return fish.trim() + "," + chest.trim();
        // Compatibility with alternate documented/older response variants.
        String solution = readPrimitive(json, "solution", "answer", "result");
        if (!solution.isBlank()) return solution.trim();
        throw new IOException("Fish API JSON is missing the fish/chest solution values");
    }

    private static String readPrimitive(JsonObject json, String... keys) {
        for (String key : keys) {
            JsonElement value = json.get(key);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }
        return "";
    }

    private BufferedImage loadQuestionImage(String value) throws Exception {
        String imageValue = value.trim();
        if (imageValue.startsWith("data:")) {
            int comma = imageValue.indexOf(',');
            if (comma < 0) throw new IOException("Invalid Base64 image data returned by Fish API");
            byte[] bytes;
            try {
                bytes = Base64.getMimeDecoder().decode(imageValue.substring(comma + 1));
            } catch (IllegalArgumentException ex) {
                throw new IOException("Fish API returned invalid Base64 image data", ex);
            }
            return readImage(bytes);
        }
        URI imageUri;
        try {
            imageUri = URI.create(imageValue);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Fish API returned an invalid question image URL", ex);
        }
        if (!"https".equalsIgnoreCase(imageUri.getScheme()) && !"http".equalsIgnoreCase(imageUri.getScheme())) {
            throw new IOException("Fish API question image URL must use HTTP or HTTPS");
        }
        HttpRequest imageRequest = HttpRequest.newBuilder(imageUri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "image/png,image/jpeg,image/*,*/*")
                .header("User-Agent", "BasketBounce/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> imageResponse = client.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (imageResponse.statusCode() != 200) {
            throw new IOException("Fish API question image returned HTTP " + imageResponse.statusCode());
        }
        return readImage(imageResponse.body());
    }

    private BufferedImage readImage(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IOException("The Fish API question image is not a supported image format");
        return image;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
