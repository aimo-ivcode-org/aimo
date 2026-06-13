# aimo-model-ollama

Ollama model integration for Aimo. Binds one or more named Ollama models from application properties and exposes them as `AimoChatModelConfig` Spring beans.

---

## Configuration

Models are configured under the `aimo.model.ollama` prefix. Each key beneath it becomes a named model entry.

```
aimo.model.ollama.{name}.<property>
```

### Model properties

| Property | Type | Default | Description |
|---|---|---|---|
| `base-url` | `String` | `http://localhost:11434` | Base URL of the Ollama server to use for this model. |
| `primary` | `Boolean` | `false` | Marks this model as the primary model used by Aimo. Only one model may be primary. If no model is marked primary, the first configured model is used. |
| `context.size` | `Int` | `8192` | Approximate context window size in tokens. Used to budget input tokens per request. |
| `context.excludeThinking` | `Boolean` | `false` | Excludes assistant thinking content from context-window budgeting and prompt history payload when enabled. |
| `options.*` | `Map<String, Any>` | — | Ollama chat options applied to every request for this model. See [Ollama options](#ollama-options) below. |

---

### Ollama options

Options are set under `aimo.model.ollama.{name}.options`. Keys are matched case-insensitively and ignore `-` / `_` separators, so `top-p`, `top_p`, and `topP` are all equivalent.

| Option key | Type | Description |
|---|---|---|
| `model` | `String` | Ollama model name (e.g. `llama3.1:8b`). Defaults to the map entry name if omitted. |
| `temperature` | `Double` | Sampling temperature. Higher = more creative. (Default: `0.8`) |
| `top-p` | `Double` | Top-p / nucleus sampling. (Default: `0.9`) |
| `top-k` | `Int` | Top-k sampling. Higher = more diverse. (Default: `40`) |
| `min-p` | `Double` | Minimum probability threshold relative to top token. (Default: `0.0`) |
| `num-predict` / `max-tokens` | `Int` | Maximum tokens to generate. `-1` = infinite, `-2` = fill context. (Default: `128`) |
| `num-ctx` | `Int` | Context window size sent to Ollama. (Default: `2048`) |
| `seed` | `Int` | Random seed for reproducible output. (Default: `-1`) |
| `repeat-penalty` | `Double` | Penalty for repeated tokens. (Default: `1.1`) |
| `repeat-last-n` | `Int` | How far back to check for repetition. `0` = disabled, `-1` = num-ctx. (Default: `64`) |
| `presence-penalty` | `Double` | Presence penalty. (Default: `0.0`) |
| `frequency-penalty` | `Double` | Frequency penalty. (Default: `0.0`) |
| `mirostat` | `Int` | Mirostat sampling mode. `0` = off, `1` = Mirostat, `2` = Mirostat 2.0. (Default: `0`) |
| `mirostat-tau` | `Float` | Mirostat target entropy. (Default: `5.0`) |
| `mirostat-eta` | `Float` | Mirostat learning rate. (Default: `0.1`) |
| `tfs-z` | `Float` | Tail-free sampling. `1.0` = disabled. (Default: `1.0`) |
| `typical-p` | `Float` | Typical-p sampling. (Default: `1.0`) |
| `num-keep` | `Int` | Number of tokens to keep from initial prompt. (Default: `4`) |
| `num-batch` | `Int` | Prompt processing batch size. (Default: `512`) |
| `num-gpu` | `Int` | Layers to offload to GPU. `-1` = auto. |
| `num-thread` | `Int` | CPU threads to use for generation. Defaults to auto-detect. |
| `main-gpu` | `Int` | Primary GPU index when using multiple GPUs. (Default: `0`) |
| `penalize-newline` | `Boolean` | Penalize newline tokens. (Default: `true`) |
| `stop` | `String` (comma-separated) or `List<String>` | Stop sequences. Generation halts when any sequence is encountered. |
| `low-vram` | `Boolean` | Low VRAM mode. (Default: `false`) |
| `use-mmap` | `Boolean` | Memory-map the model. (Default: `true`) |
| `use-mlock` | `Boolean` | Lock model in RAM. (Default: `false`) |
| `f16-kv` | `Boolean` | Use 16-bit floats for KV cache. (Default: `true`) |
| `logits-all` | `Boolean` | Return logits for all tokens. (Default: `false`) |
| `vocab-only` | `Boolean` | Load only vocabulary, not weights. (Default: `false`) |
| `numa` | `Boolean` | Enable NUMA support. (Default: `false`) |
| `format` | `String` | Output format (e.g. `json`). |
| `keep-alive` | `String` | How long to keep the model loaded (Go duration string, e.g. `5m`). |
| `truncate` | `Boolean` | Truncate input to fit context. (Default: `true`) |

---

## Example

### Single model

```yaml
aimo.model.ollama:
  chatbot:
    base-url: http://localhost:11434
    primary: true
    context:
      size: 16384
      excludeThinking: false
    options:
      model: llama3.1:8b
      temperature: 0.7
      num-predict: 1024
      top-p: 0.9
```

### Multiple models

```yaml
aimo.model.ollama:
  fast:
    base-url: http://localhost:11434
    primary: true
    context:
      size: 8192
      excludeThinking: false
    options:
      model: llama3.1:8b
      temperature: 0.3
      num-predict: 512

  creative:
    base-url: http://other-ollama-host:11434
    context:
      size: 32768
      excludeThinking: false
    options:
      model: gpt-oss:20b
      temperature: 0.9
      top-p: 0.95
      num-predict: 2048
```


---

## Notes

- If `options.model` is omitted, the map entry name (e.g. `chatbot`) is used as the Ollama model name.
- If no model is marked `primary: true` and only one model is configured, it is automatically used as the primary.
- Exactly one model must be resolvable as primary at startup, or the application will fail to start.

