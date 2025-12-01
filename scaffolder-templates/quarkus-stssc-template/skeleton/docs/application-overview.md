# Application Overview

## Introduction

This is an AI-powered customer support application built with Quarkus and Langchain4j. It demonstrates how to build intelligent, conversational applications using Large Language Models (LLMs) while maintaining security and compliance through the Red Hat Trusted Software Supply Chain (TSSC) platform.

## What Does This Application Do?

The application is a **customer support chatbot for "Miles of Smiles"**, a fictional car rental company. It showcases several advanced AI capabilities:

### Core Features

1. **Intelligent Conversation**: Uses an LLM to understand natural language queries and provide helpful responses about car rental bookings
   
2. **RAG (Retrieval-Augmented Generation)**: The chatbot uses Easy RAG to incorporate the company's Terms of Service directly into its knowledge base, ensuring accurate policy information

3. **Function Calling (Tools)**: The AI assistant can execute real business operations:
   - Look up booking details
   - Cancel bookings (with proper validation)
   
4. **Security Guardrails**: 
   - **Prompt Injection Detection**: Protects against malicious attempts to manipulate the AI
   - **Business Logic Validation**: Enforces cancellation policies (e.g., no cancellations within 7 days of booking start date)

5. **Real-time WebSocket Communication**: Provides instant, bidirectional communication for a smooth chat experience

## Technical Architecture

### Technology Stack

- **Quarkus 3.26.4**: Modern, cloud-native Java framework optimized for containers
- **Langchain4j 1.1.3**: Java library for building LLM-powered applications
- **WebSockets**: Real-time bidirectional communication
- **Jakarta REST**: RESTful API endpoints
- **Maven**: Build and dependency management

### Key Components

#### 1. Chat Interface (`ChatSocket.java`)
The WebSocket endpoint that handles real-time chat messages. It catches exceptions and provides user-friendly error messages when security guardrails are triggered.

#### 2. AI Assistant (`AssistantForCustomerSupport.java`)
The core AI service configured with:
- System prompts that define the assistant's role and behavior
- Integration with booking tools
- Input guardrails for security
- Session-scoped memory to maintain conversation context

#### 3. Business Logic (`BookingService.java`, `BookingTools.java`)
Tools that the AI can invoke to:
- Retrieve booking information
- Cancel bookings with proper validation
- Enforce business rules from the Terms of Service

#### 4. Security (`PromptInjectionGuard.java`)
Uses a secondary LLM to detect and block potential prompt injection attacks before they reach the main assistant.

#### 5. RAG Knowledge Base
The `catalog/miles-of-smiles-terms-of-use.txt` file is automatically embedded and indexed, allowing the AI to reference company policies accurately.

## LLM Configuration

### Platform-Managed LLM Service

The application connects to an **OpenAI-compatible LLM API** that is managed and secured by the platform team. This approach provides several benefits:

- **No API Key Management**: Developers don't need to manage their own LLM API keys
- **Cost Control**: The platform team controls and monitors LLM usage across all applications
- **Flexibility**: The platform team can switch LLM providers without requiring application changes
- **Security**: API keys and endpoints are injected securely at runtime

### Models Used

The application is configured to use specific models via the platform's LiteLLM proxy:

- **Chat Model**: `granite-3-2-8b-instruct` - IBM's Granite model optimized for instruction-following
- **Embedding Model**: `nomic-embed-text-v1-5` - For converting text into vector embeddings for RAG

### Configuration

In `application.properties`:

```properties
# Target specific models from the LiteLLM server
quarkus.langchain4j.openai.chat-model.model-name=granite-3-2-8b-instruct
quarkus.langchain4j.openai.embedding-model.model-name=nomic-embed-text-v1-5

# Minimize hallucination with low temperature
quarkus.langchain4j.openai.chat-model.temperature=0

# Extended timeout for slower models
quarkus.langchain4j.openai.timeout=180s
```

The actual API endpoint (`QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL`) and API key (`QUARKUS_LANGCHAIN4J_OPENAI_API_KEY`) are injected at runtime from Vault via External Secrets. See [Configuration Management](./configuration-management.md) for details.

## Development Experience

### Local Development

During local development (`quarkus:dev`), you can:
- Use a local LLM endpoint or test API
- Hot reload code changes instantly
- Access the Dev UI at `http://localhost:8080/q/dev/`
- View request/response logs for debugging

### Customizing the Application

To adapt this template for your own use case:

1. **Modify the System Prompt**: Edit `AssistantForCustomerSupport.java` to change the AI's role and behavior
2. **Add Your Own Tools**: Create new `@Tool` methods to give the AI access to your business logic
3. **Update the Knowledge Base**: Replace the content in `src/main/resources/catalog/` with your own documents
4. **Adjust Security**: Configure guardrails in the AI service registration

## Related Documentation

- [Deployment with Argo CD](./deployment-with-argocd.md) - How your application is deployed and managed
- [Build and CI/CD Pipeline](./build-and-cicd-pipeline.md) - How code changes trigger builds and deployments
- [Configuration Management](./configuration-management.md) - How secrets and configuration are managed
- [Development Environment](./development-environment.md) - Using Red Hat OpenShift Dev Spaces for development

