# Build and CI/CD Pipeline

## Overview

Your application uses **Tekton Pipelines** for continuous integration and continuous delivery. When you push code to your Git repository, webhooks automatically trigger pipelines that build, test, scan, and deploy your application.

## Pipeline Architecture

### GitOps-Driven Pipeline

The pipeline follows a **GitOps pattern** where:
1. Source code changes trigger builds
2. Container images are built and scanned for security
3. The GitOps repository is updated with new image references
4. Argo CD detects the GitOps changes and deploys automatically

This separation of concerns ensures that:
- CI/CD pipelines focus on building and testing
- GitOps/Argo CD handles deployment
- A clear audit trail exists for all changes

## Webhook Configuration

### How Webhooks Work

When your application was created, **webhooks were automatically configured** in your GitLab repository. These webhooks notify the Tekton event listener whenever specific events occur:

1. **Push Webhook**: Triggers on commits to tracked branches (e.g., `main`, `stage`, `prod`)
2. **Tag Webhook**: Triggers when you create a Git tag
3. **Release Webhook**: Triggers when you create a GitLab release

### Webhook Setup

The webhook configuration happens automatically through a Kubernetes Job that:
- Uses the GitLab API to register webhooks
- Configures the webhook URL to point to the Tekton event listener
- Sets up authentication with a webhook secret

You can view the webhook configuration in your GitOps repository:
- `components/http/overlays/ci/cm-create-webhook.yaml`

### Viewing Webhooks

To see your configured webhooks:
1. Go to your GitLab repository
2. Navigate to **Settings** → **Webhooks**
3. You should see three webhooks configured:
   - Push events webhook
   - Tag push events webhook
   - Release events webhook

## Pipeline Triggers

### Tekton Pipelines as Code

Your application uses **Pipelines as Code**, meaning the pipeline definitions live alongside your source code in the `.tekton/` directory:

- `.tekton/on-push.yaml` - Runs on every push to main/stage/prod branches
- `.tekton/on-tag.yaml` - Runs when you create a Git tag
- `.tekton/on-release.yaml` - Runs when you create a GitLab release

### What Triggers a Build?

| Event | Pipeline | Purpose |
|-------|----------|---------|
| Push to `main` branch | `on-push` | Build, test, and deploy to development |
| Push to `stage` branch | `on-push` | Build, test, and deploy to staging |
| Push to `prod` branch | `on-push` | Build, test, and deploy to production |
| Create Git tag | `on-tag` | Build and tag container image |
| Create GitLab release | `on-release` | Promote to production with validation |

## Build Pipeline Stages

### On-Push Pipeline (`maven-build-ci`)

When you push code, the pipeline executes these stages:

#### 1. **Initialize**
- Determines if a rebuild is needed
- Sets up pipeline parameters and workspace

#### 2. **Clone Repository**
- Clones your source code repository
- Checks out the specific commit that triggered the pipeline

#### 3. **Verify Commit** (Optional - if enabled)
{%- if values.verifyCommits == 'enabled' %}
- **Verifies the Git commit signature** using Red Hat Trusted Artifact Signer (RHTAS)
- Ensures commits are cryptographically signed and trusted
- Validates the commit against the Rekor transparency log
- See [Development Environment](./development-environment.md) for details on commit signing

**Why This Matters**: Commit signature verification prevents unsigned or tampered commits from being built and deployed, ensuring supply chain security.
{%- else %}
- This stage is currently **disabled** for your application
- Can be enabled to verify cryptographic signatures on commits
- Provides additional supply chain security
{%- endif %}

#### 4. **Package Application**
- Runs Maven build: `mvn clean package`
- Compiles Java code
- Runs unit tests
- Generates SBOM (Software Bill of Materials)

#### 5. **Build Container Image**
- Uses Buildah to build the container image
- Dockerfile: `src/main/docker/Dockerfile.jvm`
- Tags image with Git commit SHA
- Pushes to container registry (Quay)

#### 6. **Image Scanning**
- **ACS Image Scan**: Scans for CVEs (Common Vulnerabilities and Exposures)
- **ACS Image Check**: Validates against security policies
- Fails the build if critical vulnerabilities are found

#### 7. **SBOM Management**
- Downloads SBOM from image attestations
- Uploads SBOM to Trustification for tracking
- Displays SBOM summary in Red Hat Developer Hub

#### 8. **Deploy Check**
- ACS Deploy Check: Validates deployment against security policies
- Ensures runtime security requirements are met

#### 9. **Update GitOps Repository**
- Clones the GitOps repository
- Updates the image tag for the appropriate environment
- Commits and pushes changes
- **This triggers Argo CD to deploy the new version**

#### 10. **Summary**
- Displays pipeline summary
- Links to container image
- Shows SBOM information
- Provides status in Red Hat Developer Hub

## Viewing Pipeline Runs

### In Red Hat Developer Hub

1. Navigate to your component in the Catalog
2. Click on the **CI** tab
3. View:
   - Recent pipeline runs
   - Success/failure status
   - Duration of each stage
   - Logs for each task

### Pipeline Run Status

You'll see pipeline runs with these statuses:

- **Running**: Pipeline is currently executing
- **Succeeded**: All tasks completed successfully
- **Failed**: One or more tasks failed
- **Cancelled**: Pipeline was manually stopped

### Viewing Logs

To debug a failed pipeline:
1. Click on the failed pipeline run
2. Select the failed task
3. View the task logs to identify the issue
4. Common issues:
   - Test failures
   - Security scan violations
   - Build errors
   - GitOps update failures

## Pipeline Configuration

### Key Parameters

The pipeline accepts these parameters (configured in `.tekton/on-push.yaml`):

```yaml
- component-name: Your application name
- dockerfile: Path to Dockerfile (./src/main/docker/Dockerfile.jvm)
- git-url: Your source repository URL
- output-image: Container registry and image name
- path-context: Build context path
{%- if values.verifyCommits == 'enabled' %}
- certificate-identity: Email address for commit verification
- certificate-oidc-issuer: OIDC issuer for signature verification
- verify-commit: 'true' to enable commit verification
{%- endif %}
```

### Workspaces

Pipelines use these workspaces for shared data:

- **workspace**: Stores source code and build artifacts
- **maven-settings**: Maven configuration
- **git-auth**: Credentials for cloning repositories
- **gitops-auth**: Credentials for updating GitOps repository

## Container Registry

### Image Storage

Your container images are stored in **Quay.io** (or your organization's Quay instance):

- **Repository**: `tssc/{your-app-name}`
- **Tagging Strategy**: 
  - Git commit SHA (e.g., `abc123def`)
  - Branch name (e.g., `main`, `stage`, `prod`)
  - Git tags (e.g., `v1.0.0`)

### Image Retention

Images are tagged with an expiration policy:
- **Development images**: Expire after 5 days
- **Tagged releases**: Permanent (no expiration)

## Security Scanning

### Advanced Cluster Security (ACS)

The pipeline integrates with Red Hat Advanced Cluster Security to:

1. **Scan Container Images**: Detect known vulnerabilities (CVEs)
2. **Policy Enforcement**: Fail builds that violate security policies
3. **Deployment Validation**: Ensure secure runtime configurations

### Software Bill of Materials (SBOM)

Every build generates an SBOM that:
- Lists all dependencies and their versions
- Tracks open source components
- Enables vulnerability management
- Stored in Trustification for long-term tracking

You can view the SBOM for your application:
1. In Red Hat Developer Hub → Component → **Supply Chain** tab
2. Shows all dependencies and their vulnerability status

## Promotion Workflows

### Development → Staging

To promote to staging:

```bash
# Option 1: Merge main to stage
git checkout stage
git merge main
git push origin stage

# Option 2: Fast-forward stage to main
git push origin main:stage
```

### Staging → Production (Release Process)

For production deployments, create a release:

```bash
# Create and push a tag
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0

# Create a GitLab release from the tag
# This triggers the release pipeline
```

The release pipeline performs additional validation:
1. **Gather Images**: Identifies the image to promote
2. **Verify Enterprise Contract**: Ensures compliance policies are met
3. **Copy Image**: Promotes image to production registry
4. **Update GitOps**: Updates production environment configuration

## Customizing the Pipeline

### Adding New Tasks

To extend the pipeline:

1. Add or reference new Tekton tasks in `.tekton/on-push.yaml`:

```yaml
pipelinesascode.tekton.dev/task-12: "https://your-gitlab/org/repo/-/raw/main/tasks/your-custom-task.yaml"
```

2. Add the task to the pipeline spec:

```yaml
tasks:
  - name: your-custom-task
    runAfter:
      - some-previous-task
    taskRef:
      name: your-custom-task
    params:
      - name: param1
        value: value1
```

### Modifying Build Behavior

Edit `.tekton/on-push.yaml` to:
- Change Maven goals
- Add additional scan tasks
- Modify deployment logic
- Add notifications

## Troubleshooting

### Build Failures

**Maven Build Failed**:
- Check test results in pipeline logs
- Verify dependencies are available
- Ensure `pom.xml` is valid

**Container Build Failed**:
- Verify Dockerfile syntax
- Check base image availability
- Ensure build context is correct

**Security Scan Failed**:
- Review vulnerability report in ACS
- Update dependencies with known CVEs
- Request policy exception if needed

{%- if values.verifyCommits == 'enabled' %}

**Commit Verification Failed**:
- Ensure commits are signed (see [Development Environment](./development-environment.md))
- Verify your email matches the certificate identity
- Check Rekor transparency log is accessible
{%- endif %}

### Pipeline Not Triggering

**Check Webhooks**:
1. Verify webhooks are configured in GitLab
2. Check webhook delivery logs in GitLab
3. Ensure webhook secret is correct

**Check Event Listener**:
1. Verify Tekton event listener pod is running
2. Check event listener logs for errors
3. Validate `.tekton/` YAML syntax

## Best Practices

1. **Keep Pipelines Fast**: Optimize build times by caching dependencies
2. **Fail Fast**: Put quick checks (linting, unit tests) early in the pipeline
3. **Secure by Default**: Don't disable security scans without review
4. **Version Everything**: Use Git tags for releases
5. **Monitor Pipeline Metrics**: Track build times and failure rates in Developer Hub
{%- if values.verifyCommits == 'enabled' %}
6. **Always Sign Commits**: Use the Dev Spaces editor to ensure commits are signed
{%- endif %}

## Related Documentation

- [Deployment with Argo CD](./deployment-with-argocd.md) - How pipeline updates trigger deployments
- [Configuration Management](./configuration-management.md) - Managing secrets in pipelines
{%- if values.verifyCommits == 'enabled' %}
- [Development Environment](./development-environment.md) - Setting up commit signing
{%- endif %}
- [Application Overview](./application-overview.md) - Understanding your application

