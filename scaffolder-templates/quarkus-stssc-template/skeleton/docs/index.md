# ${{values.name}} Documentation

${{values.description}}

## Documentation Guide

This application is a Quarkus-based AI customer support chatbot built with Langchain4j, deployed using modern cloud-native practices on OpenShift. Below you'll find comprehensive guides covering all aspects of development, deployment, and operations.

## Key Features

This application demonstrates:

**AI-Powered Conversations** - Using LLMs for intelligent customer support
**Secure Secret Management** - Vault integration with External Secrets
**GitOps Deployment** - Argo CD for automated, declarative deployments
**CI/CD Pipeline** - Tekton pipelines with security scanning and SBOM generation
{%- if values.verifyCommits == 'enabled' %}
**Commit Signing** - Automatic commit verification with Red Hat Trusted Artifact Signer
{%- endif %}
**Cloud-Native Development** - OpenShift Dev Spaces for browser-based development
**Supply Chain Security** - ACS scanning, Enterprise Contract validation, and artifact signing

### Getting Started

- **[Application Overview](./application-overview.md)** - Understand what this application does, its architecture, and how it uses LLMs
- **[Development Environment](./development-environment.md)** - Set up your development environment using Red Hat OpenShift Dev Spaces

### Deployment and Operations

- **[Deployment with Argo CD](./deployment-with-argocd.md)** - Learn how GitOps and Argo CD manage your application deployments
- **[Build and CI/CD Pipeline](./build-and-cicd-pipeline.md)** - Understand the Tekton pipelines that build, test, and deploy your code
- **[Configuration Management](./configuration-management.md)** - Learn how secrets and configuration are managed with Vault and External Secrets

## Quick Links

### For Developers

- **Start developing**: Read [Development Environment](./development-environment.md) to begin coding
- **Make changes**: Commit, push, and watch the [CI/CD Pipeline](./build-and-cicd-pipeline.md) build and deploy
- **Monitor deployment**: Check [Argo CD status](./deployment-with-argocd.md) in Red Hat Developer Hub
