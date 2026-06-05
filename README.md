# VP

VP is a small Kotlin/JVM CLI utility for batch-exporting Awesome Graphs for Bitbucket user commit activity as CSV files.

It can run as a CLI from YAML or as a Kotlin Compose desktop UI. Both entry points use the same core export code.

PDF generation and native Bitbucket repository traversal are out of scope.

## Modules

```text
core  shared export, config, Bitbucket search, and Awesome Graphs clients
cli   YAML-driven command line app
ui    Kotlin Compose desktop app
```

## Build

```bash
./gradlew build
```

The fat jar is produced at:

```text
cli/build/libs/vp-cli-0.1.0-all.jar
ui/build/libs/vp-ui-0.1.0-all.jar
```

The jar targets Java 11 bytecode and should run on Java 11 or newer.

## CLI

```bash
java -jar cli/build/libs/vp-cli-0.1.0-all.jar --conf examples/vendors.yml
```

`--conf <path>` is required. There are no other runtime CLI options in the first version.

## UI

```bash
java -jar ui/build/libs/vp-ui-0.1.0-all.jar
```

The UI searches Bitbucket users through:

```text
{bitbucketUrl}/rest/api/1.0/users?filter={query}&limit=10
```

Exports still use:

```text
{awesomeGraphsApiUrl}/users/{slug}/commits/export/csv
```

## Config

All `options` fields are required.

```yaml
options:
  baseUrl: "https://stash.example/rest/awesome-graphs-api/latest"
  auth:
    method: "basic"
    username: "bitbucket-login"
    password: "bitbucket-password"
    token: null
  sinceDate: "2026-03-04"
  untilDate: "2026-06-04"
  merges: "exclude"
  order: "newest"
  outputDir: "output"
  archive: true
  debug: false
  insecure: false
  timeoutSeconds: 60
  retries: 0
  ssh: null

exports:
  - path: ["Аутсорсинг", "ООО Ромашка"]
    slugs:
      - petrov.iv
      - ivanov.ia
```

To route Bitbucket and Awesome Graphs HTTP requests through an SSH host, replace `ssh: null` with:

```yaml
  ssh:
    host: "jump.example"
    port: 22
    user: "ssh-user"
    password: "optional-password"
    privateKeyPath: "/home/user/.ssh/id_ed25519"
    passphrase: "optional-key-passphrase"
    knownHostsPath: "/home/user/.ssh/known_hosts"
    strictHostKeyChecking: false
```

At least `password` or `privateKeyPath` must be set. VP stays local and writes output locally; only HTTP traffic to Bitbucket/Awesome Graphs is routed through the SSH connection.

`baseUrl` must be the full Awesome Graphs REST API base. VP appends only:

```text
/users/{slug}/commits/export/csv
```

By default VP uses HTTP Basic auth:

```http
Authorization: Basic <base64(username:password)>
```

Token auth is still available when needed:

```yaml
  auth:
    method: "token"
    username: null
    password: null
    token: "paste-token-here"
```

Token auth is sent as:

```http
Authorization: Bearer <token>
```

Passwords and tokens are not printed to stdout, stderr, debug logs, summaries, or generated artifacts.

`insecure: true` disables TLS certificate and hostname verification for both Bitbucket user search and Awesome Graphs CSV downloads. Use it only for corporate/self-signed test environments.

The desktop UI can build the same grouped export config, search Bitbucket users with autocomplete, save/load YAML configs, and remember the last config path locally.

For the current real-environment verification work, external tests are routed only through `vdi-wsl` and cumulative downloaded CSV bodies are capped at 100 MB.

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
| `42` | SSH tunnel failure |
| `50` | Archive already exists |
| `51` | Archive creation failure |
| `99` | Unexpected internal error |
