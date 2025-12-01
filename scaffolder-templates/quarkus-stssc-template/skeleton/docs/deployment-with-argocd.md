# Deployment with Argo CD

## Overview

Your application is deployed and managed using **GitOps** principles with **Argo CD**. This means that the desired state of your application is defined in Git, and Argo CD continuously ensures that your running application matches that state.

## What is GitOps?

GitOps is a deployment methodology where:
- **Git is the single source of truth** for your application's configuration
- **Changes are made via Git commits** (pull requests, merges)
- **Automated systems** (like Argo CD) detect changes and apply them to the cluster
- **Declarative configuration** ensures predictability and reproducibility

## How Argo CD Works with Your Application

### GitOps Repository Structure

When your application was created from the template, a companion GitOps repository was also generated. This repository contains Kubernetes manifests organized by environment:

```
gitops-repo/
├── application.yaml              # Root Argo CD Application
├── app-of-apps/                  # App-of-Apps pattern
│   ├── application-dev.yaml      # Development environment
│   ├── application-stage.yaml    # Staging environment
│   ├── application-prod.yaml     # Production environment
│   └── ci-tekton.yaml           # CI/CD pipeline resources
└── components/
    └── http/
        ├── base/                 # Base Kubernetes resources
        │   ├── deployment.yaml
        │   ├── service.yaml
        │   ├── route.yaml
        │   └── externalsecret-litellm.yaml
        └── overlays/            # Environment-specific customizations
            ├── development/
            ├── stage/
            ├── prod/
            └── ci/
```

### The App-of-Apps Pattern

Your application uses the **App-of-Apps pattern**, which is an Argo CD best practice:

1. **Root Application**: Defined in `application.yaml`, this is registered with Argo CD and points to the `app-of-apps/` directory
2. **Child Applications**: The root application creates multiple child applications, one for each environment plus the CI/CD pipeline
3. **Automatic Sync**: All applications are configured with automated sync, meaning changes in Git are automatically applied

### Continuous Deployment Workflow

```
1. Code Change → Merged to Branch
                    ↓
2. Tekton Pipeline → Builds Container Image
                    ↓
3. Pipeline Updates → GitOps Repo (new image tag)
                    ↓
4. Argo CD Detects → Git Change
                    ↓
5. Argo CD Syncs → Deploys to Cluster
                    ↓
6. Application Updated → New version running
```

## Viewing Argo CD Status in Red Hat Developer Hub

You don't need to access the Argo CD UI directly. Instead, you can view your application's deployment status directly in **Red Hat Developer Hub**:

### Accessing Your Application's Component View

1. Navigate to Red Hat Developer Hub
2. Go to **Catalog** → Find your component
3. Click on your component name
4. Select the **CD (Continuous Deployment)** tab or **Kubernetes** tab

### What You'll See

The Component view shows:

- **Sync Status**: Whether your application is in sync with Git
  - **Synced**: The deployed state matches Git
  - **Out of Sync**: There are differences between Git and the cluster
  - **Sync Failed**: An error occurred during synchronization

- **Health Status**: The health of your application
  - **Healthy**: All resources are running correctly
  - **Progressing**: Deployment is in progress
  - **Degraded**: Some resources are not healthy
  - **Suspended**: Application is paused

- **Application Resources**: All Kubernetes resources managed by Argo CD:
  - Deployments, Pods, Services, Routes, ConfigMaps, Secrets, etc.
  - Real-time status of each resource

- **Recent Sync Activity**: Recent synchronization events and their results

- **Resource Tree**: Visual representation of your application's resources and their relationships

### Manual Operations

From the Developer Hub interface, you can:

- **Refresh**: Force Argo CD to check Git for changes
- **Sync**: Manually trigger a synchronization
- **View Logs**: Check pod logs for debugging
- **View Events**: See Kubernetes events for your resources

## Deployment to Different Environments

### Development Environment

- **Auto-sync enabled**: Changes are automatically deployed
- **Self-healing enabled**: Argo CD will correct manual changes or drift
- **Namespace**: `{app-name}-dev`
- **Trigger**: Commits to the `main` branch update the dev environment

### Staging Environment

- **Auto-sync enabled**: Changes are automatically deployed
- **Namespace**: `{app-name}-stage`
- **Trigger**: Commits or merges to the `stage` branch

### Production Environment

- **Auto-sync enabled**: Changes are automatically deployed
- **Namespace**: `{app-name}-prod`
- **Trigger**: Git tags or merges to the `prod` branch
- **Additional validation**: Enterprise Contract verification runs before promotion

## Environment Promotion Workflow

To promote your application through environments:

### Development → Staging

```bash
# Create and push a branch for staging
git checkout -b stage
git push origin stage
```

The Tekton pipeline will:
1. Build and scan the container image
2. Update the GitOps repo's `stage` branch
3. Argo CD deploys to staging

### Staging → Production

```bash
# Create and push a branch for production
git checkout -b prod
git push origin prod
```

Or create a Git release, which triggers:
1. Enterprise Contract verification
2. Image promotion (copying to prod registry)
3. GitOps repo update for production
4. Argo CD deployment to production

## Argo CD Sync Policies

Your applications are configured with these sync policies:

```yaml
syncPolicy:
  automated:
    prune: true      # Remove resources deleted from Git
    selfHeal: true   # Revert manual changes
  syncOptions:
    - CreateNamespace=true  # Auto-create namespaces
    - PruneLast=true        # Delete resources after creating new ones
```

### What This Means

- **Automated Sync**: No manual intervention needed
- **Prune**: If you delete a resource from Git, it's deleted from the cluster
- **Self-Heal**: Manual changes (e.g., scaling pods via CLI) are reverted
- **Namespace Management**: Namespaces are created automatically

## Troubleshooting Deployments

### Application Not Syncing

1. Check the sync status in Developer Hub
2. Look for errors in the Argo CD application events
3. Verify your GitOps repository is accessible
4. Ensure manifests are valid Kubernetes YAML

### Application Unhealthy

1. Check pod logs in the Developer Hub
2. View pod events for startup errors
3. Verify secrets and config maps are present (see [Configuration Management](./configuration-management.md))
4. Check resource limits and pod status

### Rollback to Previous Version

Since everything is in Git, rollbacks are simple:

```bash
# Revert the GitOps repository commit
git revert <commit-hash>
git push origin main  # Or appropriate branch
```

Argo CD will automatically deploy the previous version.

## Best Practices

1. **Always Use Git**: Never make manual changes in the cluster - they'll be reverted
2. **Test in Dev First**: Validate changes in development before promoting
3. **Use Pull Requests**: Review changes before merging to staging/prod branches
4. **Monitor in Developer Hub**: Regularly check your application's health
5. **Keep Manifests Simple**: Use Kustomize overlays for environment-specific changes

## Related Documentation

- [Build and CI/CD Pipeline](./build-and-cicd-pipeline.md) - How builds trigger GitOps updates
- [Configuration Management](./configuration-management.md) - Managing secrets in GitOps
- [Application Overview](./application-overview.md) - Understanding your application

