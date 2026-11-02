# Menura

Menura is the content and stream hub of OneLiteFeather. It turns stream recordings into
searchable content, lets team members cut shorts and long videos with Claude on their own
GPU machines, distributes approved content natively to YouTube, Twitch, TikTok and Instagram,
and keeps branding and the modular stream overlay central for everyone.

The project is in its planning phase. Its principles live in
[`openspec/config.yaml`](openspec/config.yaml); every feature starts as an
[OpenSpec](https://github.com/Fission-AI/OpenSpec) proposal under `openspec/changes/`
before any code is written.

## Components

| Component | Runs on | Purpose |
|---|---|---|
| server | OLF Kubernetes cluster | REST API, web backend, sign-in, hub MCP, feeds, media job queue |
| jobs | OLF Kubernetes cluster | Distribution to platforms, analytics collection and rollups |
| web | any browser | Library, brand kit, overlays, approvals, content pipeline |
| obs-plugin | every streaming PC (Windows, macOS, Linux) | Sign-in, recording upload, scene and brand sync |
| desktop | PCs with a GPU | Transcription and local editing with Claude through a local MCP server |
| overlay | OBS browser sources | Modular stream overlay fed by the hub |

## License

[GNU Affero General Public License v3.0](LICENSE)
