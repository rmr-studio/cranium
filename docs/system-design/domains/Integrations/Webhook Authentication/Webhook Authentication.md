---
tags:
  - architecture/subdomain
  - domain/integration
  - tools/nango
Created: 2026-03-18
Domains:
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
---
# Subdomain: Webhook Authentication

## Overview

Handles inbound Nango webhook events for the Integrations domain. After a user completes OAuth in the Nango Connect UI, Nango sends a signed webhook to the backend. This subdomain validates the HMAC signature, routes the event to the appropriate handler, and orchestrates connection creation, installation tracking, and template materialization — replacing the previous frontend-driven `POST /enable` flow with a fully webhook-driven authentication pipeline.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookService]] | Routes and processes inbound Nango webhook events — auth events create connections and trigger materialization, sync events are stubbed for Phase 3 | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookHmacFilter]] | Servlet filter validating HMAC-SHA256 signatures on inbound Nango webhook requests | Filter |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookController]] | Thin REST controller exposing `POST /api/v1/webhooks/nango`, always returns 200 | Controller |

## Flows

| Flow | Type | Description |
|------|------|-------------|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/Flow - Auth Webhook]] | Background | Nango sends auth webhook after OAuth -> HMAC validation -> connection creation -> installation tracking -> materialization |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-18 | Initial implementation — HMAC filter, webhook controller, webhook service with auth event handling and sync event stub | Integration Sync Phase 2 |
