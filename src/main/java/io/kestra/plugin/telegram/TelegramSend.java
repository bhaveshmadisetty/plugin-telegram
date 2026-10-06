package io.kestra.plugin.telegram;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.property.URIFetcher;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Telegram chat message or file",
    description = "Sends text using Telegram `sendMessage`, or uploads a file using `sendDocument` when `from` is set. Files can come from an earlier task's output and have an optional caption. Supports HTML/MarkdownV2 formatting, HTTP client overrides and an optional API endpoint override for local testing."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Telegram message on a failed flow execution.",
            full = true,
            code = """
                id: unreliable_flow
                namespace: company.team

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: alert_on_failure
                    type: io.kestra.plugin.telegram.TelegramSend
                    token: "{{ secret('TELEGRAM_TOKEN') }}" # format: 6090305634:xyz
                    channel: "2072728690"
                    parseMode: HTML
                    payload: "<b>Telegram Alert</b>"
                """
        ),
        @Example(
            title = "Send a file produced by an earlier task with a caption.",
            full = true,
            code = """
                id: send_report_to_telegram
                namespace: company.team

                tasks:
                  - id: report
                    type: io.kestra.plugin.core.storage.Write
                    extension: .csv
                    content: |
                      name,total
                      example,42

                  - id: send_file
                    type: io.kestra.plugin.telegram.TelegramSend
                    token: "{{ secret('TELEGRAM_TOKEN') }}"
                    channel: "{{ secret('TELEGRAM_CHAT_ID') }}"
                    from: "{{ outputs.report.uri }}"
                    caption: "<b>Report</b> for {{ flow.id }}"
                    parseMode: HTML
                """
        )
    },
    aliases = "io.kestra.plugin.notifications.telegram.TelegramSend"
)
public class TelegramSend extends AbstractTelegramConnection {
    private static final String TELEGRAMAPI_BASE_URL = "https://api.telegram.org";
    private static final String INVALID_FROM_MESSAGE = "'from' must specify a file URI pointing to a file, using the kestra, nsfile or file scheme.";

    @Schema(title = "Bot access token", description = "Telegram Bot API token; store as a secret and avoid hardcoding.")
    @NotNull
    @PluginProperty(secret = true, group = "main")
    @ToString.Exclude
    protected Property<String> token;

    @Schema(title = "Chat ID or channel ID", description = "Target chat identifier for the user or channel; supports expressions.")
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> channel;

    @Schema(
        title = "Message payload",
        description = "Message text sent as Telegram `sendMessage` `text` when `from` is absent. Required for text messages. Do not wrap the value in a JSON object. For file uploads, use `caption` instead."
    )
    @PluginProperty(group = "main")
    protected Property<String> payload;

    @Schema(
        title = "File to send",
        description = "URI of the file to upload as a Telegram document, for example `{{ outputs.report.uri }}`. Accepts Kestra internal storage (`kestra://`), namespace files (`nsfile://`) and local files (`file://`). Supports expressions. The file is read through Kestra and uploaded with the filename from the URI; the URI itself is not sent to Telegram. When set, `payload` is unused and `caption` is optional."
    )
    @Nullable
    @PluginProperty(group = "main")
    protected Property<String> from;

    @Schema(
        title = "Document caption",
        description = "Optional caption for the uploaded file when `from` is set. Ignored for text messages. Supports expressions and the formatting specified by `parseMode`. Telegram allows up to 1024 characters after entities parsing."
    )
    @Nullable
    @PluginProperty(group = "main")
    protected Property<String> caption;

    @Schema(
        title = "Telegram Bot parse-Mode",
        description = "Optional formatting mode for message text or a document caption. Use the case-sensitive enum names HTML or MARKDOWNV2 (values sent to Telegram are HTML and MarkdownV2). Default sends plain text.",
        example = "MARKDOWNV2"
    )
    @Nullable
    @PluginProperty(group = "advanced")
    protected Property<ParseMode> parseMode;
    @Schema(
        title = "Custom Telegram API base URL",
        description = "Override the Telegram API endpoint for local testing; defaults to https://api.telegram.org."
    )
    @PluginProperty(group = "connection")
    protected Property<String> endpointOverride;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String rEndpointOverride = runContext.render(this.endpointOverride).as(String.class).orElse(TELEGRAMAPI_BASE_URL);

        HttpRequest.HttpRequestBuilder requestBuilder = createRequestBuilder(runContext);

        try (HttpClient httpClient = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            String rDestination = runContext.render(this.channel).as(String.class).orElseThrow();
            String rApiToken = runContext.render(this.token).as(String.class).orElseThrow();
            String rParseMode = this.parseMode != null
                ? Optional.ofNullable(runContext.render(this.parseMode.toString()))
                    .map(ParseMode::fromString)
                    .map(ParseMode::getValue)
                    .orElse(null)
                : null;
            if (this.from != null) {
                String rFrom = runContext.render(this.from).as(String.class)
                    .filter(value -> !value.isBlank())
                    .orElseThrow(() -> new IllegalArgumentException(INVALID_FROM_MESSAGE));
                URI fileUri;
                try {
                    fileUri = new URI(rFrom);
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException(INVALID_FROM_MESSAGE, e);
                }
                if (!URIFetcher.supports(fileUri) || fileUri.getPath() == null || fileUri.getPath().isEmpty() || fileUri.getPath().endsWith("/")) {
                    throw new IllegalArgumentException(INVALID_FROM_MESSAGE);
                }
                String filename = fileUri.getPath().substring(fileUri.getPath().lastIndexOf('/') + 1);
                String rCaption = runContext.render(this.caption).as(String.class).orElse(null);

                // Resolve the file before uploading so read failures are not reported as send failures.
                InputStream resolved;
                try {
                    resolved = URIFetcher.of(fileUri).fetch(runContext);
                } catch (IOException e) {
                    throw new IOException("Unable to read the file specified by 'from'.", e);
                }
                try (InputStream document = resolved) {
                    TelegramBotApiService.sendDocument(httpClient, rDestination, rApiToken, document, filename, rCaption, rEndpointOverride, requestBuilder, rParseMode);
                }
            } else {
                if (this.caption != null) {
                    runContext.logger().warn("'caption' is ignored for text messages; it only applies when 'from' is set.");
                }
                String rPayload = runContext.render(this.payload).as(String.class)
                    .orElseThrow(() -> new IllegalArgumentException("Either 'payload' or 'from' must be provided."));
                TelegramBotApiService.send(httpClient, rDestination, rApiToken, rPayload, rEndpointOverride, requestBuilder, rParseMode);
            }
        }

        return null;
    }

    public enum ParseMode {
        HTML("HTML"),
        MARKDOWNV2("MarkdownV2");

        private final String value;

        ParseMode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @JsonCreator
        public static ParseMode fromString(String value) {
            if (value == null) {
                return null;
            }
            for (ParseMode parseMode : ParseMode.values()) {
                if (parseMode.name().equals(value)) {
                    return parseMode;
                }
            }
            throw new IllegalArgumentException(
                "Invalid parseMode value '" + value + "'. Valid values (case-sensitive): " +
                    Arrays.stream(ParseMode.values()).map(Enum::name).collect(Collectors.joining(", "))
            );
        }
    }
}
