package io.kestra.plugin.telegram;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.serializers.JacksonMapper;

import io.micronaut.http.HttpStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TelegramBotApiService {

    public static void send(HttpClient client, String destinationId, String apiToken, String message, String url, HttpRequest.HttpRequestBuilder requestBuilder, String parseMode)
        throws ErrorSendingMessageException {

        TelegramMessage payload = new TelegramMessage(destinationId, message, parseMode);

        // The Telegram Bot API requires the token in the URL path (/bot<token>/sendMessage).
        // This is mandated by the API design and cannot be replaced with an Authorization header.
        // CWE-598: To reduce log-based exposure, the token is inserted only into the outbound
        // URI and never referenced in log statements. Treat any HTTP-client DEBUG logs as sensitive.
        String uri = url + "/bot" + apiToken + "/sendMessage";

        requestBuilder
            .addHeader("Content-Type", "application/json")
            .uri(URI.create(uri))
            .method("POST")
            .body(
                HttpRequest.JsonRequestBody.builder()
                    .content(payload)
                    .build()
            );

        sendRequest(client, requestBuilder.build());
    }

    public static void sendDocument(HttpClient client, String destinationId, String apiToken, InputStream document, String filename, String caption, String url,
        HttpRequest.HttpRequestBuilder requestBuilder, String parseMode) throws ErrorSendingMessageException {

        var textContentType = ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8);
        // Use UTF-8 multipart headers to preserve non-ASCII filenames.
        var multipart = MultipartEntityBuilder.create()
            .setMode(HttpMultipartMode.LEGACY)
            .setCharset(StandardCharsets.UTF_8)
            .setContentType(ContentType.MULTIPART_FORM_DATA.withCharset(StandardCharsets.UTF_8))
            .addTextBody("chat_id", destinationId, textContentType)
            .addBinaryBody("document", document, ContentType.APPLICATION_OCTET_STREAM, filename);
        if (caption != null) {
            multipart.addTextBody("caption", caption, textContentType);
        }
        if (parseMode != null) {
            multipart.addTextBody("parse_mode", parseMode, textContentType);
        }
        var entity = multipart.build();

        // multipart entity supplies its own Content-Type boundary
        requestBuilder
            .uri(URI.create(url + "/bot" + apiToken + "/sendDocument"))
            .method("POST")
            .body(
                HttpRequest.PassthroughRequestBody.builder()
                    .entity(entity)
                    .contentType(entity.getContentType())
                    .charset(StandardCharsets.UTF_8)
                    .build()
            );

        sendRequest(client, requestBuilder.build());
    }

    private static void sendRequest(HttpClient client, HttpRequest request) throws ErrorSendingMessageException {

        try {
            HttpResponse<TelegramBotApiResponse> exchange = client.request(request, TelegramBotApiResponse.class);

            if (
                exchange.getStatus().getCode() != HttpStatus.OK.getCode()
                    || exchange.getBody() == null
                    || !Objects.requireNonNull(exchange.getBody()).ok()
            ) {
                var description = exchange.getBody() != null ? exchange.getBody().description() : null;
                throw new ErrorSendingMessageException(exchange.getStatus(), description, null);
            }
        } catch (HttpClientResponseException e) {
            var response = Objects.requireNonNull(e.getResponse());
            throw new ErrorSendingMessageException(response.getStatus(), errorDescription(response.getBody()), e);
        } catch (IllegalVariableEvaluationException | HttpClientException e) {
            throw new RuntimeException(e);
        }
    }

    // Telegram explains rejections in the "description" field of its JSON error body.
    private static String errorDescription(Object body) {
        var raw = body instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : body instanceof String text ? text : null;
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JacksonMapper.ofJson().readTree(raw).path("description").textValue();
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramBotApiResponse(boolean ok, TelegramMessage result, String description) {
    }

    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramMessage {
        @JsonProperty("chat_id")
        private String chatId;

        private String text;

        @JsonProperty("parse_mode")
        private String parseMode;

        @JsonProperty("message_id")
        private Integer messageId;

        public TelegramMessage(String chatId, String text, String parseMode) {
            this.chatId = chatId;
            this.text = text;
            this.parseMode = parseMode;
        }
    }

    public static class ErrorSendingMessageException extends Exception {
        public final HttpResponse.Status httpStatus;

        public ErrorSendingMessageException(HttpResponse.Status httpStatus, Throwable e) {
            this(httpStatus, null, e);
        }

        public ErrorSendingMessageException(HttpResponse.Status httpStatus, String description, Throwable e) {
            super(buildMessage(httpStatus, description), e);
            this.httpStatus = httpStatus;
        }

        private static String buildMessage(HttpResponse.Status httpStatus, String description) {
            var message = new StringBuilder("Unable to send Telegram message");
            if (httpStatus != null) {
                message.append(": HTTP ").append(httpStatus.getCode());
            }
            if (description != null && !description.isBlank()) {
                message.append(" - ").append(description);
            } else if (httpStatus != null && httpStatus.getReason() != null) {
                message.append(" ").append(httpStatus.getReason());
            }
            if (httpStatus != null && httpStatus.getCode() == HttpStatus.REQUEST_ENTITY_TOO_LARGE.getCode()) {
                message.append(" (the file likely exceeds Telegram's 50 MB upload limit for bots)");
            }
            return message.toString();
        }
    }
}
