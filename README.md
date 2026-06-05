# VP

VP is a small Kotlin/JVM CLI utility for batch-exporting Awesome Graphs for Bitbucket user commit activity as CSV files.

It reads one YAML config, downloads one CSV per configured Bitbucket user slug, writes files into the configured directory hierarchy, and can optionally create a ZIP archive.

PDF generation and native Bitbucket repository traversal are out of scope.

## Build

```bash
./gradlew build
```

The fat jar is produced at:

```text
build/libs/vp-0.1.0-all.jar
```

The jar targets Java 11 bytecode and should run on Java 11 or newer.

## Run

```bash
java -jar build/libs/vp-0.1.0-all.jar --conf examples/vendors.yml
```

`--conf <path>` is required. There are no other runtime CLI options in the first version.

## Config

All `options` fields are required.

```yaml
options:
  baseUrl: "https://stash.example/rest/awesome-graphs-api/latest"
  token: "paste-token-here"
  sinceDate: "2026-03-04"
  untilDate: "2026-06-04"
  merges: "exclude"
  order: "newest"
  outputDir: "output"
  archive: true
  debug: false
  timeoutSeconds: 60
  retries: 0

exports:
  - path: ["Аутсорсинг", "ООО Ромашка"]
    slugs:
      - petrov.iv
      - ivanov.ia
```

`baseUrl` must be the full Awesome Graphs REST API base. VP appends only:

```text
/users/{slug}/commits/export/csv
```

The token is sent as:

```http
Authorization: Bearer <token>
```

The token is not printed to stdout, stderr, debug logs, summaries, or generated artifacts.

## Output

`outputDir` must not already exist.

For each slug, VP writes:

```text
{outputDir}/{path...}/{slug}-{sinceDate}_{untilDate}-commits.csv
```

When `archive: true`, VP also creates:

```text
{outputDir}.zip
```

When `debug: true`, VP writes:

```text
{outputDir}/vp-debug.log
{outputDir}/export-summary.csv
```

## Exit Codes

| Code | Meaning |
| ---: | --- |
| `0` | Success |
| `10` | Missing or invalid CLI args |
| `11` | Config file not found or not readable |
| `12` | Invalid YAML syntax |
| `13` | Invalid config schema |
| `14` | Invalid date range or date format |
| `15` | Invalid option value |
| `20` | `outputDir` already exists |
| `21` | Filesystem create/write error |
| `22` | Invalid output path segment, duplicate export path, or duplicate slug |
| `30` | Authentication failed |
| `31` | Permission denied |
| `32` | Awesome Graphs endpoint not found or unavailable |
| `33` | User slug not found |
| `34` | HTTP 200 but non-CSV response |
| `40` | Network timeout/connect failure |
| `41` | HTTP 5xx after retries |
| `50` | Archive already exists |
| `51` | Archive creation failure |
| `99` | Unexpected internal error |
