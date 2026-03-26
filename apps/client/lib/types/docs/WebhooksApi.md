# WebhooksApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**handleNangoWebhook**](WebhooksApi.md#handlenangowebhook) | **POST** /api/v1/webhooks/nango | Handle Nango webhook events (auth and sync) |



## handleNangoWebhook

> handleNangoWebhook(nangoWebhookPayload)

Handle Nango webhook events (auth and sync)

### Example

```ts
import {
  Configuration,
  WebhooksApi,
} from '';
import type { HandleNangoWebhookRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WebhooksApi(config);

  const body = {
    // NangoWebhookPayload
    nangoWebhookPayload: ...,
  } satisfies HandleNangoWebhookRequest;

  try {
    const data = await api.handleNangoWebhook(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **nangoWebhookPayload** | [NangoWebhookPayload](NangoWebhookPayload.md) |  | |

### Return type

`void` (Empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Webhook processed |  -  |
| **401** | Invalid or missing HMAC signature |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

