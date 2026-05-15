# Simple Bedrock Example

Runnable Spring Boot application integrating Aimo with AWS Bedrock models.

## Prerequisites

- JDK 21
- Node.js + npm (for frontend builds)
- AWS account with Bedrock access
- AWS credentials configured

## AWS Credentials Setup

> [!WARNING]
> Never commit AWS credentials (`awsAccessKeyId`, `awsSecretAccessKey`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`) to source control.
> Prefer short-lived credentials (IAM roles, AWS SSO/session credentials) over long-lived static access keys.

### Option 1: IAM Role / Workload Identity (Recommended for production)

Use AWS-managed credential delivery so the SDK resolves credentials automatically:

- EC2 instance profile
- ECS task role
- EKS IAM role for service account (IRSA)

No static credentials are required in `application.yml` for this flow.

### Option 2: AWS CLI Profile / AWS SSO (Recommended for developer machines)

```powershell
# Use an existing AWS profile configured via `aws configure` or `aws configure sso`
$env:AWS_PROFILE = 'your-profile-name'
$env:AWS_REGION = 'us-east-1'
```

### Option 3: Environment Variables / Secret Injection

```powershell
# Set before running the app
$env:AWS_ACCESS_KEY_ID = 'your-access-key'
$env:AWS_SECRET_ACCESS_KEY = 'your-secret-key'
$env:AWS_REGION = 'us-east-1'
```

Use this with your platform secret manager/injection mechanism in CI or container environments.

### Option 4: application.yml Explicit Credentials (last resort)

Use only when needed (for local experiments or controlled environments).
Do not commit real secrets.

Edit `src/main/resources/application.yml`:

```yaml
aimo.model.bedrock:
  claude-sonnet:
    region: us-east-1
    awsAccessKeyId: ${AWS_ACCESS_KEY_ID}
    awsSecretAccessKey: ${AWS_SECRET_ACCESS_KEY}
    # ... rest of config
```

Or set properties via command line:

```powershell
.\gradlew.bat :examples:simple-bedrock:bootRun `
  -Daimo.model.bedrock.claude-sonnet.aws-access-key-id=your-key `
  -Daimo.model.bedrock.claude-sonnet.aws-secret-access-key=your-secret
```

### Option 5: AWS Credentials File

Create or edit `~/.aws/credentials`:

```ini
[default]
aws_access_key_id = your-access-key
aws_secret_access_key = your-secret-key

[your-profile]
aws_access_key_id = your-access-key
aws_secret_access_key = your-secret-key
```

## Running the Application

With environment variables set:

```powershell
.\gradlew.bat :examples:simple-bedrock:bootRun
```

With an AWS profile:

```powershell
$env:AWS_PROFILE = 'your-profile'
.\gradlew.bat :examples:simple-bedrock:bootRun
```

## Configuration

Model, region, inference parameters, and optional AWS credential overrides are defined in `src/main/resources/application.yml`:

- **Model ID**: `anthropic.claude-3-5-sonnet-20241022-v2:0` (Claude 3.5 Sonnet)
- **Region**: `us-east-1`
- **Temperature**: `0.7`
- **Max Tokens**: `1024`
- **AWS Credentials** (optional override): `awsAccessKeyId` and `awsSecretAccessKey`

To override these, you can:

1. Edit `application.yml` directly
2. Create `application-local.yml` and specify different values
3. Pass JVM properties:
   - `-Daimo.model.bedrock.claude-sonnet.region=us-west-2`
   - `-Daimo.model.bedrock.claude-sonnet.aws-access-key-id=your-key`
   - `-Daimo.model.bedrock.claude-sonnet.aws-secret-access-key=your-secret`

## API

Once running, the application listens on `http://localhost:8080`.

- **Web UI**: `http://localhost:8080`
- **API Base**: `/aimo-api`
- **Conversation endpoints**: `/aimo-api/conversation/*`
- **Chat streaming**: `POST /aimo-api/chat/{chatId}`

## Troubleshooting

**"Could not resolve aws credentials"**
- Verify `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` are set
- Or ensure AWS credentials file exists at `~/.aws/credentials`
- Or set `AWS_PROFILE` to a valid profile name

**"Access Denied for model"**
- Confirm your AWS account has Bedrock access in the configured region
- Verify the model ID is available in that region

**Port already in use**
- Change port: `.\gradlew.bat :examples:simple-bedrock:bootRun --args='--server.port=8081'`
