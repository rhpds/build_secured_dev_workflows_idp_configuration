# Configuration Management

## Overview

Your application's configuration is managed using modern cloud-native practices that separate code from configuration and keep sensitive information secure. This document explains how configuration and secrets are managed throughout the application lifecycle.

## Configuration Sources

### 1. Application Properties

Non-sensitive configuration is stored in `src/main/resources/application.properties`:

```properties
# Quarkus configuration
quarkus.langchain4j.openai.chat-model.model-name=granite-3-2-8b-instruct
quarkus.langchain4j.openai.embedding-model.model-name=nomic-embed-text-v1-5
quarkus.langchain4j.openai.chat-model.temperature=0
quarkus.langchain4j.openai.timeout=180s

# Business logic configuration  
booking.daystostart=14
booking.daystoend=50
```

These properties are:
- Committed to Git
- Built into the container image
- Safe to share publicly

### 2. Environment Variables

Runtime configuration is provided via environment variables. The deployment manifest (`components/http/base/deployment.yaml`) shows two ways environment variables are injected:

#### Direct Environment Variables

```yaml
env:
  - name: GIT_REPO
    value: https://your-gitlab.com/your-org/your-app
```

#### Environment Variables from Secrets

```yaml
envFrom:
  - secretRef:
      name: your-app-litellm-secret
```

This approach loads all keys from a Secret as environment variables.

## Secrets Management with Vault

### Why Vault?

Your application uses **HashiCorp Vault** to store sensitive configuration:

- **Centralized Secret Storage**: All secrets in one place
- **Access Control**: Fine-grained permissions on who can access what
- **Audit Trail**: Every secret access is logged
- **Rotation**: Secrets can be rotated without redeploying applications
- **Platform-Managed**: The platform team handles Vault operations

### What's Stored in Vault?

For your application, Vault stores:

1. **LLM API Configuration**:
   - `QUARKUS_LANGCHAIN4J_OPENAI_API_KEY`: API key for the LLM service
   - `QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL`: URL endpoint for the LLM service (e.g., LiteLLM proxy)

2. **Other Platform Secrets**:
   - GitLab tokens
   - Container registry credentials
   - Argo CD credentials

### Vault Path Structure

Secrets are organized in Vault using a key-value store:

```
kv/
├── litellm/
│   ├── virtual_key      # API key for LLM
│   └── base_url         # LLM service endpoint
├── rhads-dh/
│   ├── backend_secret
│   ├── gitlab_token
│   └── ... other platform secrets
```

Your application accesses secrets from the `kv/litellm` path.

## External Secrets Operator

### What is External Secrets?

The **External Secrets Operator** is a Kubernetes controller that:
1. Connects to external secret management systems (like Vault)
2. Fetches secrets from those systems
3. Creates Kubernetes Secrets automatically
4. Keeps Secrets synchronized with the external source

### How It Works

```
┌─────────┐
│  Vault  │ (Stores secrets)
└────┬────┘
     │
     │ (2) Fetches secrets
     ▼
┌─────────────────────┐
│ External Secrets    │ (Kubernetes Operator)
│ Operator            │
└──────────┬──────────┘
           │
           │ (3) Creates/Updates
           ▼
┌─────────────────────┐
│ Kubernetes Secret   │ (standard Secret)
└──────────┬──────────┘
           │
           │ (4) Mounts to Pod
           ▼
┌─────────────────────┐
│ Your Application    │
│ Pod                 │
└─────────────────────┘
```

### ExternalSecret Resource

Your application uses an `ExternalSecret` resource defined in `components/http/base/externalsecret-litellm.yaml`:

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: your-app-litellm-secret
spec:
  refreshInterval: 1h  # Sync every hour
  secretStoreRef:
    name: vault-secret-store  # References cluster-wide Vault config
    kind: ClusterSecretStore
  target:
    name: your-app-litellm-secret  # Name of Secret to create
  data:
    # Map Vault keys to Secret keys
    - secretKey: QUARKUS_LANGCHAIN4J_OPENAI_API_KEY
      remoteRef:
        key: kv/litellm
        property: virtual_key
    - secretKey: QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL
      remoteRef:
        key: kv/litellm
        property: base_url
```

### What This Configuration Does

1. **References Vault**: Uses the cluster-wide `vault-secret-store` configured by the platform team
2. **Fetches from Vault**: Retrieves `kv/litellm` from Vault
3. **Creates Kubernetes Secret**: Creates a Secret named `your-app-litellm-secret` with:
   - `QUARKUS_LANGCHAIN4J_OPENAI_API_KEY` = value from `virtual_key`
   - `QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL` = value from `base_url`
4. **Refreshes Periodically**: Syncs every hour to pick up changes in Vault

### ClusterSecretStore

The **ClusterSecretStore** is a cluster-wide resource (managed by the platform team) that configures how to connect to Vault:

- **Authentication**: Uses Kubernetes service account authentication
- **Vault URL**: Points to the internal Vault service
- **Namespace**: Configured for proper access control

You don't need to create this resource - it's already configured by the platform team.

## How Secrets Reach Your Application

### The Complete Flow

1. **Platform Team**: Stores secrets in Vault at `kv/litellm`
   
2. **External Secrets Operator**: 
   - Reads your `ExternalSecret` resource
   - Authenticates to Vault using the `ClusterSecretStore`
   - Fetches secrets from `kv/litellm`
   - Creates/updates a Kubernetes Secret: `your-app-litellm-secret`

3. **Kubernetes**: 
   - Your Deployment references the Secret via `envFrom`
   - Kubernetes mounts the Secret as environment variables in your Pod

4. **Your Application**:
   - Starts up and reads environment variables
   - Uses `QUARKUS_LANGCHAIN4J_OPENAI_API_KEY` and `QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL`
   - Connects to the LLM service with the injected credentials

### Benefits of This Approach

✅ **No Secrets in Git**: Secrets never appear in source code or GitOps repositories
✅ **Automatic Updates**: When secrets rotate in Vault, Pods are updated automatically
✅ **Secure by Default**: Developers never see or handle API keys
✅ **Platform-Managed**: Platform team controls access to LLM services
✅ **Auditability**: Every secret access is logged in Vault

## Managing Configuration Changes

### Updating Non-Sensitive Configuration

To change application properties:

1. Edit `src/main/resources/application.properties`
2. Commit and push your changes
3. The CI/CD pipeline will rebuild and redeploy
4. New configuration takes effect

### Updating Secrets (Platform Team)

To update secrets in Vault:

1. **Platform Team** updates the secret in Vault CLI or UI:
   ```bash
   vault kv put kv/litellm virtual_key="new-api-key" base_url="https://new-url"
   ```

2. External Secrets Operator detects the change within 1 hour (or immediately if synced manually)

3. Kubernetes Secret is updated

4. For the application to use new secrets:
   - **Option A**: Wait for Pods to restart naturally
   - **Option B**: Manually restart the deployment:
     ```bash
     oc rollout restart deployment/your-app-name -n your-namespace
     ```

### Viewing Current Configuration

#### In Development

During local development, configuration is read from:
1. `application.properties` (base configuration)
2. Environment variables (override properties)

You can set environment variables locally:

```bash
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY="your-local-key"
export QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL="http://localhost:8080/v1"
./mvnw quarkus:dev
```

#### In OpenShift

To view the configuration (without seeing secret values):

```bash
# View deployment environment variables
oc describe deployment your-app-name -n your-namespace

# View secret keys (not values)
oc get secret your-app-litellm-secret -n your-namespace -o yaml
```

**Note**: Secret values are base64-encoded. Do not share or commit decoded secrets.

## Configuration for Different Environments

### Environment-Specific Configuration

Your application supports profile-based configuration in `application.properties`:

```properties
# Development profile
%dev.quarkus.langchain4j.easy-rag.path=src/main/resources/catalog/

# Production profile (default)
quarkus.langchain4j.easy-rag.path=/deployments/catalog/
```

### Deployment Overlays

Kustomize overlays allow environment-specific customization:

- **Development** (`overlays/development/`): 
  - 1 replica
  - Lower resource limits
  - Debug logging enabled

- **Staging** (`overlays/stage/`):
  - 2 replicas
  - Medium resource limits
  - Info logging

- **Production** (`overlays/prod/`):
  - 3+ replicas
  - Higher resource limits
  - Warn logging
  - Resource quotas enforced

Each overlay can include:
- Different replica counts
- Different resource requests/limits
- Environment-specific environment variables (non-sensitive)

## Security Best Practices

### What Should Be in Vault?

Store in Vault:
- ✅ API keys and tokens
- ✅ Database passwords
- ✅ TLS certificates and private keys
- ✅ OAuth client secrets
- ✅ Encryption keys

### What Should Be in Application Properties?

Store in application properties:
- ✅ Feature flags
- ✅ Model names and parameters
- ✅ Timeouts and retry policies
- ✅ Non-sensitive URLs (without credentials)
- ✅ Business logic configuration

### What Should NEVER Be Committed?

Never commit:
- ❌ API keys
- ❌ Passwords
- ❌ Private keys
- ❌ OAuth secrets
- ❌ Any credentials

## Troubleshooting

### Secret Not Available in Pod

**Symptoms**: Application logs show missing environment variables

**Debugging Steps**:

1. Check if ExternalSecret exists:
   ```bash
   oc get externalsecret -n your-namespace
   ```

2. Check ExternalSecret status:
   ```bash
   oc describe externalsecret your-app-litellm-secret -n your-namespace
   ```
   Look for errors or status messages.

3. Check if Secret was created:
   ```bash
   oc get secret your-app-litellm-secret -n your-namespace
   ```

4. Verify Secret has correct keys:
   ```bash
   oc get secret your-app-litellm-secret -n your-namespace -o jsonpath='{.data}'
   ```

5. Check External Secrets Operator logs:
   ```bash
   oc logs -n external-secrets deployment/external-secrets
   ```

### Secret Out of Sync

If Vault is updated but the Secret isn't:

1. Force a refresh:
   ```bash
   oc annotate externalsecret your-app-litellm-secret \
     force-sync=$(date +%s) -n your-namespace
   ```

2. Restart the deployment:
   ```bash
   oc rollout restart deployment/your-app-name -n your-namespace
   ```

### Application Can't Connect to LLM

**Check Configuration**:

1. Verify the base URL is correct:
   ```bash
   oc get secret your-app-litellm-secret -n your-namespace \
     -o jsonpath='{.data.QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL}' | base64 -d
   ```

2. Check if API key is set:
   ```bash
   oc get secret your-app-litellm-secret -n your-namespace \
     -o jsonpath='{.data.QUARKUS_LANGCHAIN4J_OPENAI_API_KEY}' | base64 -d
   ```

3. Test connectivity from inside the Pod:
   ```bash
   oc exec -it deployment/your-app-name -n your-namespace -- \
     curl -v $QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL/health
   ```

## Related Documentation

- [Application Overview](./application-overview.md) - Understanding the LLM configuration
- [Deployment with Argo CD](./deployment-with-argocd.md) - How configuration is deployed
- [Build and CI/CD Pipeline](./build-and-cicd-pipeline.md) - How builds use secrets
- [Development Environment](./development-environment.md) - Local configuration setup

