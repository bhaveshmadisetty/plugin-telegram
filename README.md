<p align="center">
  <a href="https://www.kestra.io">
    <img src="https://kestra.io/banner.png"  alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    Event-Driven Declarative Orchestrator
</h1>

<div align="center">
 <a href="https://github.com/kestra-io/kestra/releases"><img src="https://img.shields.io/github/tag-pre/kestra-io/kestra.svg?color=blueviolet" alt="Last Version" /></a>
  <a href="https://github.com/kestra-io/kestra/blob/develop/LICENSE"><img src="https://img.shields.io/github/license/kestra-io/kestra?color=blueviolet" alt="License" /></a>
  <a href="https://github.com/kestra-io/kestra/stargazers"><img src="https://img.shields.io/github/stars/kestra-io/kestra?color=blueviolet&logo=github" alt="Github star" /></a> <br>
<a href="https://kestra.io"><img src="https://img.shields.io/badge/Website-kestra.io-192A4E?color=blueviolet" alt="Kestra infinitely scalable orchestration and scheduling platform"></a>
<a href="https://kestra.io/slack"><img src="https://img.shields.io/badge/Slack-Join%20Community-blueviolet?logo=slack" alt="Slack"></a>
</div>

<br />

<p align="center">
  <a href="https://twitter.com/kestra_io" style="margin: 0 10px;">
        <img src="https://kestra.io/twitter.svg" alt="twitter" width="35" height="25" /></a>
  <a href="https://www.linkedin.com/company/kestra/" style="margin: 0 10px;">
        <img src="https://kestra.io/linkedin.svg" alt="linkedin" width="35" height="25" /></a>
  <a href="https://www.youtube.com/@kestra-io" style="margin: 0 10px;">
        <img src="https://kestra.io/youtube.svg" alt="youtube" width="35" height="25" /></a>
</p>

<br />
<p align="center">
    <a href="https://go.kestra.io/video/product-overview" target="_blank">
        <img src="https://kestra.io/startvideo.png" alt="Get started in 3 minutes with Kestra" width="640px" />
    </a>
</p>
<p align="center" style="color:grey;"><i>Get started with Kestra in 3 minutes.</i></p>

# Kestra Telegram Plugin

## Why

- What user problem does this solve? Teams need to send notifications via Telegram bots from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Telegram steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Telegram.

## What

- Provides plugin components under `io.kestra.plugin.telegram`.
- Includes classes such as `TelegramTemplate`, `TelegramBotApiService`, `TelegramExecution`, `TelegramSend`.

## Send a file from an earlier task

Set `from` on `TelegramSend` to a file URI, such as an earlier task's `uri` or
`outputFiles` value. The task uploads the file using Telegram's `sendDocument`
API and preserves the filename from the URI. It reads the file through Kestra;
Telegram does not need access to the URI itself.

`from` accepts internal storage (`kestra://`), namespace files (`nsfile://`) and
local files (`file://`, subject to the `kestra.local-files.allowed-paths`
configuration).

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

`caption` is optional and supports expressions. Use `parseMode: HTML` or
`parseMode: MARKDOWNV2` to format it; omit `parseMode` for plain text. Telegram
limits captions to 1024 characters after entities parsing and document uploads
to 50 MB on its hosted [Bot API](https://core.telegram.org/bots/api#senddocument).

For text messages, omit `from` and set `payload` to the message text. When `from`
is present, `payload` is unused; use `caption` for text accompanying the file.
`caption` applies only to file uploads and is ignored for text messages.
The legacy `io.kestra.plugin.notifications.telegram.TelegramSend` alias also
supports these properties. HTTP `options` and `endpointOverride` apply to both
text messages and document uploads.

## Documentation
* Full documentation can be found under: [kestra.io/docs](https://kestra.io/docs)
* Documentation for developing a plugin is included in the [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/)


## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)


## Stay up to date

We release new versions every month. Give the [main repository](https://github.com/kestra-io/kestra) a star to stay up to date with the latest releases and get notified about future updates.

![Star the repo](https://kestra.io/star.gif)
