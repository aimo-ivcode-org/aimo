# aimo-session-cache-ehcache

Ehcache-backed implementation of `AimoSessionCache`.

## What it stores per session (`chatId`)

- Session metadata (`Map<String, Any>`)
- Cached chat messages (`List<AimoChatMessage>`)
- Token calibration (`SessionTokenCalibration`)

## Configuration

```yaml
aimo:
  session-cache:
    ehcache:
      max-entries: 10000
      ttl: 1h
```

## Usage

Add the module dependency to your app. Auto-configuration will provide an `AimoSessionCacheProvider` bean.

```kotlin
implementation(project(":aimo-session-cache-ehcache"))
```

