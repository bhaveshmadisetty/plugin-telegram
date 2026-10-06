package io.kestra.plugin.telegram;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.annotation.Nullable;

@Controller()
public class FakeTelegramController {

    public static String token;
    public static TelegramBotApiService.TelegramMessage message;
    public static String documentChannel;
    public static String documentCaption;
    public static String documentParseMode;
    public static String documentFilename;
    public static byte[] documentBytes;
    public static String customHeader;
    public static boolean responseOk = true;
    public static HttpStatus responseStatus = HttpStatus.OK;

    @Post("/bot{token}/sendMessage")
    public HttpResponse<TelegramBotApiService.TelegramBotApiResponse> post(String token, @Body TelegramBotApiService.TelegramMessage message) {
        FakeTelegramController.token = token;
        FakeTelegramController.message = message;
        return HttpResponse.<TelegramBotApiService.TelegramBotApiResponse> status(responseStatus)
            .body(new TelegramBotApiService.TelegramBotApiResponse(responseOk, message));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/bot{token}/sendDocument", consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<Map<String, Object>> postDocument(String token, @Part("chat_id") String channel,
        @Part CompletedFileUpload document, @Nullable @Part String caption, @Nullable @Part("parse_mode") String parseMode,
        HttpRequest<?> request) throws IOException {
        FakeTelegramController.token = token;
        documentChannel = channel;
        documentCaption = caption;
        documentParseMode = parseMode;
        documentFilename = document.getFilename();
        customHeader = request.getHeaders().get("X-Telegram-Test");
        try (InputStream input = document.getInputStream()) {
            documentBytes = input.readAllBytes();
        }
        return HttpResponse.<Map<String, Object>> status(responseStatus)
            .body(Map.of("ok", responseOk, "result", Map.of("message_id", 1, "document", Map.of("file_name", documentFilename))));
    }
}
