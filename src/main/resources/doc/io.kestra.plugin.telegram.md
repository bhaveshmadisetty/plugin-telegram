# How to use the Telegram plugin

Send messages, files and execution summaries to Telegram chats via the Telegram Bot API.

## Authentication

Set `token` to your Telegram bot token (obtained from [@BotFather](https://t.me/botfather)) and `channel` to the target chat ID or channel username. Store `token` in a [secret](https://kestra.io/docs/concepts/secret).

## Tasks

`TelegramSend` sends a message as a step within a flow. Set `payload` to the message text for Telegram's `sendMessage` API. Set `parseMode` to `HTML` or `MARKDOWNV2` to enable text formatting.

To send a file, set `from` to its URI, for example `{{ outputs.report.uri }}` or `{{ outputs.export.outputFiles['report.csv'] }}`. The task streams the file to Telegram's `sendDocument` API with the filename from the URI. It does not pass the URI to Telegram. A file upload does not require `payload`; an optional `caption` supplies accompanying text and supports `parseMode`. When `from` is set, `payload` is unused.

```yaml
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
```

`from` and `caption` support expressions. File inputs are resolved through Kestra and may use internal storage (`kestra://`), namespace files (`nsfile://`) or local files (`file://`, subject to the `kestra.local-files.allowed-paths` configuration). `caption` applies only to file uploads and is ignored for text messages. Telegram's hosted [Bot API](https://core.telegram.org/bots/api#senddocument) accepts document uploads up to 50 MB and captions up to 1024 characters after entities parsing. Existing HTTP `options`, `endpointOverride`, and the legacy `io.kestra.plugin.notifications.telegram.TelegramSend` alias are supported in both modes.

`TelegramExecution` sends a structured execution summary including status, duration, and an execution link, and is designed for use with a [Flow trigger](https://kestra.io/docs/workflow-components/triggers) in a dedicated monitoring namespace that watches other namespaces for failures.
