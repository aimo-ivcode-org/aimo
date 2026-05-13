# aimo-model-bedrock

AWS Bedrock integration for AIMO chat models. Provides [AimoChatModelProviderFactory](../aimo-core/src/main/kotlin/org/ivcode/aimo/core/model/AimoChatModelProviderFactory.kt)
implementation using AWS Bedrock Runtime API.

## Features

- Support for any AWS Bedrock model
- Automatic client pooling per AWS region
- Full chat engine implementation with tool/function calling support
- Streaming support ready
- Configuration via Spring properties

## Configuration

Add the following to your `application.yml` or `application.properties`:

### YAML Configuration Example

```yaml
aimo.model.bedrock:
  claude-3-sonnet:
    region: us-east-1
    primary: true
    contextSize: 200000
    options:
      model: anthropic.claude-3-5-sonnet-20241022-v2:0
      temperature: 0.7
      maxTokens: 1024
```

### Properties File Configuration Example

```properties
aimo.model.bedrock.claude-3-sonnet.region=us-east-1
aimo.model.bedrock.claude-3-sonnet.primary=true
aimo.model.bedrock.claude-3-sonnet.contextSize=200000
aimo.model.bedrock.claude-3-sonnet.options.model=anthropic.claude-3-5-sonnet-20241022-v2:0
aimo.model.bedrock.claude-3-sonnet.options.temperature=0.7
aimo.model.bedrock.claude-3-sonnet.options.maxTokens=1024
```

## Configuration Properties

- `region` (default: `us-east-1`) - AWS region for the model
- `primary` (default: `false`) - Mark this model as primary when multiple models are configured
- `contextSize` (default: `8192`) - Model context window size
- `options` - Additional model-specific options:
  - `model` - AWS Bedrock model identifier (overrides the configuration key)
  - `temperature` - Sampling temperature (0.0 to 1.0)
  - `maxTokens` - Maximum tokens in response
  - `topP` - Top-P nucleus sampling parameter
  - `topK` - Top-K sampling parameter (forwarded via `additionalModelRequestFields.top_k`)
  - `stop` - Stop sequences

## AWS Credentials

Make sure your application has access to AWS credentials via one of the standard AWS SDK methods:

- Environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
- AWS credentials file (`~/.aws/credentials`)
- IAM role (when running on AWS)
- AWS SSO

## Usage

Once configured, the Bedrock model will be automatically available through AIMO's chat interfaces:

```kotlin
val aimoImpl = aimo.createChatSession()
val response = aimoImpl.chat(prompt)
```

Or inject the factory directly:

```kotlin
@Autowired
private lateinit var bedrockFactory: AimoChatModelProviderFactory

val model = bedrockFactory.createAimoChatModel("claude-3-sonnet")
```

## Tested Models

The following models are currently configured in `examples/simple-bedrock` and verified for thinking/tool support:

| model | thinking | tools |
| --- | --- | --- |
| `deepseek.v3.2` | ✅ | ✅ |
| `us.amazon.nova-pro-v1:0` | ✅ | ✅ |
| `us.amazon.nova-micro-v1:0` | ⚠️ | ✅ |
| `openai.gpt-oss-120b-1:0` | ✅ | ✅ |
| `openai.gpt-oss-20b-1:0` | ✅ | ✅ |
| `google.gemma-3-27b-it` | ❌ | ❌ |
| `google.gemma-3-4b-it` | ❌ | ❌ |

Check the AWS Bedrock console for the complete list of available models and their IDs.

