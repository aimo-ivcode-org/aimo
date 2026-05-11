# Simple Bedrock Example

Runnable Spring Boot application integrating Aimo with AWS Bedrock Claude models.

## Prerequisites

- JDK 21
- Node.js + npm (for frontend builds)
- AWS account with Bedrock access
- AWS credentials configured

## AWS Credentials Setup

### Option 1: application.yml Configuration (Recommended for production)

Edit `src/main/resources/application.yml`:

```yaml
aimo.model.bedrock:
  claude-sonnet:
    region: us-east-1
    awsAccessKeyId: your-access-key
    awsSecretAccessKey: your-secret-key
    # ... rest of config
```

Or set properties via command line:

```powershell
.\gradlew.bat :examples:simple-bedrock:bootRun `
  -Daimo.model.bedrock.claude-sonnet.aws-access-key-id=your-key `
  -Daimo.model.bedrock.claude-sonnet.aws-secret-access-key=your-secret
```

### Option 2: Environment Variables (Recommended for local development)

```powershell
# Set before running the app
$env:AWS_ACCESS_KEY_ID = 'your-access-key'
$env:AWS_SECRET_ACCESS_KEY = 'your-secret-key'
$env:AWS_REGION = 'us-east-1'
```

### Option 3: AWS CLI Profile

```powershell
# Use an existing AWS profile configured in ~/.aws/credentials
$env:AWS_PROFILE = 'your-profile-name'
```

### Option 4: AWS Credentials File

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

Model, region, inference parameters, and AWS credentials are defined in `src/main/resources/application.yml`:

- **Model ID**: `anthropic.claude-3-5-sonnet-20241022-v2:0` (Claude 3.5 Sonnet)
- **Region**: `us-east-1`
- **Temperature**: `0.7`
- **Max Tokens**: `1024`
- **AWS Credentials** (optional): `awsAccessKeyId` and `awsSecretAccessKey`

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
- **Session endpoints**: `/aimo-api/session/*`
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
