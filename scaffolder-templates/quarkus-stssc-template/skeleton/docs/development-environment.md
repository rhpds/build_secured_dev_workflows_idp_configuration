# Development Environment

## Overview

You can develop this application using **Red Hat OpenShift Dev Spaces**, a cloud-based IDE powered by Eclipse Che. Dev Spaces provides a consistent, pre-configured development environment accessible from your browser with zero local setup required.

## What is OpenShift Dev Spaces?

OpenShift Dev Spaces is a Kubernetes-native IDE that provides:

- **Browser-Based Development**: Code from anywhere without installing tools
- **Pre-Configured Environment**: All dependencies, tools, and SDKs ready to use
- **Consistent Across Team**: Everyone uses the same environment configuration
- **Integrated with Platform**: Direct access to cluster resources and services
{%- if values.verifyCommits == 'enabled' %}
- **Automatic Commit Signing**: Git commits are automatically signed for supply chain security
{%- endif %}

## Accessing Dev Spaces

### Opening Your Workspace

1. Navigate to **Red Hat Developer Hub**
2. Find your component in the Catalog
3. Click the **"Open in Dev Spaces"** button (or similar link)
4. Dev Spaces will provision a workspace based on the `.devfile.yaml` configuration

Alternatively, you can access Dev Spaces directly:
1. Go to your OpenShift Dev Spaces URL (provided by your platform team)
2. Click **"Create Workspace"**
3. Enter your Git repository URL
4. Dev Spaces will detect the `.devfile.yaml` and configure the workspace

### First-Time Setup

The workspace initialization may take a few minutes as it:
1. Pulls the development container image
2. Clones your Git repository
3. Downloads Maven dependencies
{%- if values.verifyCommits == 'enabled' %}
4. Downloads and configures RHTAS (Red Hat Trusted Artifact Signer) CLI tools
{%- endif %}

## Understanding the Devfile

### What is a Devfile?

A **devfile** (`.devfile.yaml`) is a portable configuration file that defines your development environment:

- Container image to use
- Environment variables
- Memory and CPU resources
- Port mappings
- Commands (build, run, test)
{%- if values.verifyCommits == 'enabled' %}
- Pre-start hooks for tool setup
{%- endif %}

### Your Application's Devfile

Your application's `.devfile.yaml` is configured with:

#### 1. Development Container

```yaml
components:
  - name: development-tooling
    container:
      image: quay.io/devfile/universal-developer-image:ubi8-277c10c
      memoryLimit: 5Gi
      cpuRequest: 250m
      cpuLimit: 500m
```

This container includes:
- Java 21 (required for Quarkus)
- Maven for building
- Git for version control
- Common development tools

#### 2. Environment Variables

```yaml
env:
  - name: QUARKUS_HTTP_HOST
    value: 0.0.0.0  # Allows external access
  - name: MAVEN_OPTS
    value: "-Dmaven.repo.local=/home/user/.m2/repository"
```

#### 3. Exposed Endpoints

```yaml
endpoints:
  - name: quarkus-dev
    targetPort: 8080
    exposure: public
    protocol: https
    path: hello
  - name: debug
    targetPort: 5005
    exposure: none
  - name: tests
    targetPort: 8081
    exposure: none
```

The Quarkus dev mode endpoint is automatically accessible from your browser.

#### 4. Persistent Storage

```yaml
- name: m2
  volume:
    size: 1G  # Maven dependencies cache
```

Maven dependencies are cached between workspace restarts for faster builds.

{%- if values.verifyCommits == 'enabled' %}

#### 5. Commit Signing Tools (RHTAS)

Your devfile includes a special component that downloads and configures commit signing tools:

```yaml
- name: rhtas-clis-unzip
  container:
    command:
      - /bin/bash
      - -c
      - |
        mkdir -p /projects/home/bin
        cd /tmp
        wget -q http://cli-server.trusted-artifact-signer.svc:8080/clients/linux/cosign-amd64.gz
        wget -q http://cli-server.trusted-artifact-signer.svc:8080/clients/linux/gitsign-amd64.gz
        gunzip cosign-amd64.gz && chmod a+x cosign-amd64 && mv cosign-amd64 /projects/home/bin/cosign
        gunzip gitsign-amd64.gz && chmod a+x gitsign-amd64 && mv gitsign-amd64 /projects/home/bin/gitsign
```

This runs automatically when the workspace starts (via the `preStart` event).
{%- endif %}

## Development Workflow

### Starting Development Mode

Once your workspace is ready:

1. **Open the Terminal** in Dev Spaces

2. **Run Quarkus in Dev Mode**:
   ```bash
   ./mvnw compile quarkus:dev
   ```

3. **Access Your Application**:
   - Dev Spaces will show a notification with the URL
   - Or click the "Open" icon next to the `quarkus-dev` endpoint
   - Your application is now running with hot reload enabled!

### Development Mode Features

Quarkus Dev Mode provides:

- **Hot Reload**: Code changes are automatically compiled and reloaded
- **Dev UI**: Access at `http://localhost:8080/q/dev/` for:
  - Configuration editor
  - Langchain4j testing interface
  - Continuous testing
  - Metrics and health checks

### Making Changes

1. **Edit Code**: Modify Java files in `src/main/java/`
2. **Save File**: Changes are detected automatically
3. **Refresh Browser**: See changes immediately (no restart needed)

### Testing Your AI Assistant

1. Navigate to your application's home page (`/`)
2. Use the chat interface to interact with the AI assistant
3. Test different scenarios:
   - Look up bookings
   - Try to cancel bookings
   - Test the prompt injection guardrails

### Debugging

Dev Spaces supports debugging:

1. Set breakpoints in your IDE
2. Attach the debugger to port `5005`
3. Dev Spaces automatically forwards debug port

## Working with Git

### Committing Changes

In Dev Spaces terminal:

```bash
# Stage changes
git add .

# Commit changes
git commit -m "Your commit message"

# Push to remote
git push origin main
```

{%- if values.verifyCommits == 'enabled' %}

### Automatic Commit Signing

When you commit code in Dev Spaces, your commits are **automatically signed** using Red Hat Trusted Artifact Signer (RHTAS):

#### How It Works

1. **Gitsign Integration**: The `gitsign` tool is downloaded during workspace startup
2. **Automatic Configuration**: Git is configured to use gitsign for signing commits
3. **Transparent Signing**: When you commit, gitsign:
   - Uses your OpenShift identity (from your login session)
   - Signs the commit cryptographically
   - Records the signature in the Rekor transparency log
   - Attaches the signature to the commit

#### Why This Matters

Signed commits provide **supply chain security**:

- ✅ **Non-Repudiation**: Proves who made each commit
- ✅ **Integrity**: Ensures commits haven't been tampered with
- ✅ **Auditability**: All signatures are recorded in a public transparency log
- ✅ **Pipeline Validation**: The build pipeline verifies commit signatures before building

#### How to Verify It's Working

After committing, verify your commit is signed:

```bash
git log --show-signature -1
```

You should see:
```
commit abc123... (signed)
```

You can also view the signature details:

```bash
git show --show-signature HEAD
```

#### Signature Verification in Pipeline

When you push signed commits:

1. The Git hook triggers the Tekton pipeline
2. The **verify-commit** task runs:
   - Downloads the commit signature from Rekor
   - Validates the signature matches your identity
   - Checks the OIDC issuer and certificate identity
3. If verification passes, the build continues
4. If verification fails, the build is blocked

This ensures only trusted, signed commits are built and deployed.

#### What If I Commit Outside Dev Spaces?

Commits made outside Dev Spaces (e.g., from your local machine) may not be signed, causing the verify-commit pipeline task to fail.

**Options**:

1. **Use Dev Spaces** (recommended): Always commit from Dev Spaces to ensure automatic signing

2. **Set Up Local Signing**: Configure gitsign on your local machine:
   ```bash
   # Install gitsign
   brew install sigstore/tap/gitsign  # macOS
   # or download from https://github.com/sigstore/gitsign

   # Configure Git
   git config --global gpg.x509.program gitsign
   git config --global commit.gpgsign true
   git config --global gpg.format x509
   ```

3. **Disable Verification** (not recommended): Contact your platform team to disable commit verification for your application

{%- endif %}

## Building and Packaging

### Maven Build

To build without running:

```bash
./mvnw clean package
```

This produces:
- `target/quarkus-app/quarkus-run.jar` - Runnable JAR
- `target/quarkus-app/lib/` - Dependencies

### Running Tests

```bash
# Run unit tests
./mvnw test

# Run with continuous testing (in dev mode)
./mvnw quarkus:dev
# Then press 'r' in the terminal to run tests
```

## Configuring LLM Access for Local Development

During local development, you have options for LLM access:

### Option 1: Use Platform LLM Service

Point to the platform's LiteLLM service:

```bash
export QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL="http://litellm.litellm.svc:4000/v1"
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY="<your-dev-key>"
```

Ask your platform team for a development API key.

### Option 2: Use Local LLM

Run a local LLM using Ollama or similar:

```bash
# Start Ollama locally with Granite model
ollama run granite3.1-dense:8b

# Configure to use local Ollama
export QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL="http://localhost:11434/v1"
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY="demo"
```

### Option 3: Use OpenAI API (if allowed)

If your organization permits:

```bash
export QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL="https://api.openai.com/v1"
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY="<your-openai-key>"
```

**Note**: In production, these values are injected from Vault. See [Configuration Management](./configuration-management.md).

## Workspace Persistence

### What's Persisted

- ✅ Source code and uncommitted changes
- ✅ Maven dependencies cache (`.m2/`)
- ✅ Git configuration and credentials
{%- if values.verifyCommits == 'enabled' %}
- ✅ RHTAS CLI tools
{%- endif %}

### What's Not Persisted

- ❌ Running processes (you'll need to restart `quarkus:dev`)
- ❌ Temporary files in `/tmp`

### Workspace Lifecycle

- **Stop**: Workspace stops when idle for 30 minutes (configurable)
- **Restart**: Restarting a workspace preserves your data
- **Delete**: Deleting a workspace removes all data (commit and push first!)

## Common Commands

Dev Spaces provides pre-configured commands you can run:

| Command | Description | Shortcut |
|---------|-------------|----------|
| Package | `./mvnw package` | Build the application |
| Start Dev Mode | `./mvnw quarkus:dev` | Run with hot reload |

Access these commands via:
- Command Palette: `F1` → search for command
- Terminal: Right-click → **Run Task**

## Troubleshooting

### Workspace Won't Start

**Check Resource Limits**:
- Ensure your quota allows 5 GiB memory
- Contact platform team if you need increased limits

**Check Image Availability**:
- Verify the base image is accessible
- Check for image pull errors in workspace events

### Maven Dependencies Won't Download

**Check Network Access**:
- Verify proxy settings (if required)
- Ensure Maven Central is accessible

**Clear Cache and Retry**:
```bash
rm -rf /home/user/.m2/repository
./mvnw clean package
```

### Quarkus Dev Mode Not Hot Reloading

**Restart Dev Mode**:
- Press `Ctrl+C` to stop
- Run `./mvnw quarkus:dev` again

**Check File Watchers**:
- Ensure you're editing files in the mounted workspace directory

{%- if values.verifyCommits == 'enabled' %}

### Commit Signing Not Working

**Check RHTAS Tools**:
```bash
ls -la /projects/home/bin/
which gitsign
which cosign
```

If tools are missing:
1. Check workspace startup logs
2. Verify RHTAS CLI server is accessible
3. Manually trigger the preStart event:
   ```bash
   /bin/bash -c "wget -q http://cli-server.trusted-artifact-signer.svc:8080/clients/linux/gitsign-amd64.gz && gunzip gitsign-amd64.gz && chmod +x gitsign-amd64 && mv gitsign-amd64 /projects/home/bin/gitsign"
   ```

**Verify Git Configuration**:
```bash
git config --list | grep sign
```

You should see:
```
commit.gpgsign=true
gpg.format=x509
gpg.x509.program=gitsign
```
{%- endif %}

## Best Practices

1. **Commit Frequently**: Push your work regularly to avoid data loss
2. **Use Dev Mode**: Take advantage of hot reload for faster development
3. **Test Locally First**: Validate changes before pushing
4. **Check Resource Usage**: Monitor memory in Dev Spaces dashboard
{%- if values.verifyCommits == 'enabled' %}
5. **Always Use Dev Spaces for Commits**: Ensures commits are properly signed
{%- endif %}
6. **Clean Up**: Stop workspaces when not in use to free resources

## Alternatives to Dev Spaces

While Dev Spaces is recommended, you can also develop locally:

### Local Development Setup

1. **Install Prerequisites**:
   - Java 21 (OpenJDK or similar)
   - Maven 3.9+
   - Git
   - Your favorite IDE (VS Code, IntelliJ IDEA)

2. **Clone Repository**:
   ```bash
   git clone <your-repo-url>
   cd your-app-name
   ```

3. **Set Environment Variables**:
   ```bash
   export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY="your-key"
   export QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL="http://localhost:11434/v1"
   ```

4. **Run Dev Mode**:
   ```bash
   ./mvnw quarkus:dev
   ```

{%- if values.verifyCommits == 'enabled' %}

**Note**: If developing locally with commit verification enabled, you'll need to configure gitsign manually (see above).
{%- endif %}

## Related Documentation

- [Application Overview](./application-overview.md) - Understanding the application architecture
- [Build and CI/CD Pipeline](./build-and-cicd-pipeline.md) - How commits trigger builds
{%- if values.verifyCommits == 'enabled' %}
- [Configuration Management](./configuration-management.md) - Managing secrets
{%- endif %}
- [Deployment with Argo CD](./deployment-with-argocd.md) - How applications are deployed

