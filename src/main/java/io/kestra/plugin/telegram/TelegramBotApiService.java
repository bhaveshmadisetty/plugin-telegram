package io.kestra.plugin.telegram;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.HttpClientResponseException;

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

        ContentType textContentType = ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8);
        // Use UTF-8 multipart headers to preserve non-ASCII filenames.
        MultipartEntityBuilder multipart = MultipartEntityBuilder.create()
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
        HttpEntity entity = multipart.build();

        // Use a streamed multipart entity to preserve the filename without buffering the file.
        // The entity supplies the Content-Type header, including its generated boundary.
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
                throw new ErrorSendingMessageException(exchange.getStatus(), null);
            }
        } catch (HttpClientResponseException e) {
            throw new ErrorSendingMessageException(Objects.requireNonNull(e.getResponse()).getStatus(), e);
        } catch (IllegalVariableEvaluationException | HttpClientException e) {
            throw new RuntimeException(e);
        }
    }

    public record TelegramBotApiResponse(boolean ok, TelegramMessage result) {
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
            super(String.format("Unable to send Telegram message: %s ", httpStatus), e);
            this.httpStatus = httpStatus;
        }
    }
}
