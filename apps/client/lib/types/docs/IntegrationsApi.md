# IntegrationsApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**disableIntegration**](IntegrationsApi.md#disableintegrationoperation) | **POST** /api/v1/integrations/{workspaceId}/disable | Disable an integration for a workspace |
| [**getWorkspaceIntegrationStatus**](IntegrationsApi.md#getworkspaceintegrationstatus) | **GET** /api/v1/integrations/{workspaceId}/status | Get integration status for workspace |
| [**listAvailableIntegrations**](IntegrationsApi.md#listavailableintegrations) | **GET** /api/v1/integrations | List all available integrations |



## disableIntegration

> IntegrationDisableResponse disableIntegration(workspaceId, disableIntegrationRequest)

Disable an integration for a workspace

### Example

```ts
import {
  Configuration,
  IntegrationsApi,
} from '';
import type { DisableIntegrationOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new IntegrationsApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // DisableIntegrationRequest
    disableIntegrationRequest: ...,
  } satisfies DisableIntegrationOperationRequest;

  try {
    const data = await api.disableIntegration(body);
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
| **workspaceId** | `string` |  | [Defaults to `undefined`] |
| **disableIntegrationRequest** | [DisableIntegrationRequest](DisableIntegrationRequest.md) |  | |

### Return type

[**IntegrationDisableResponse**](IntegrationDisableResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Integration disabled successfully |  -  |
| **400** | Invalid request data |  -  |
| **401** | Unauthorized access |  -  |
| **403** | Forbidden — admin role required |  -  |
| **404** | Integration not enabled in this workspace |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getWorkspaceIntegrationStatus

> Array&lt;IntegrationConnectionModel&gt; getWorkspaceIntegrationStatus(workspaceId)

Get integration status for workspace

### Example

```ts
import {
  Configuration,
  IntegrationsApi,
} from '';
import type { GetWorkspaceIntegrationStatusRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new IntegrationsApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetWorkspaceIntegrationStatusRequest;

  try {
    const data = await api.getWorkspaceIntegrationStatus(body);
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
| **workspaceId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**Array&lt;IntegrationConnectionModel&gt;**](IntegrationConnectionModel.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | List of workspace integration connections |  -  |
| **401** | Unauthorized access |  -  |
| **403** | Forbidden — workspace access denied |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listAvailableIntegrations

> Array&lt;IntegrationDefinitionModel&gt; listAvailableIntegrations()

List all available integrations

### Example

```ts
import {
  Configuration,
  IntegrationsApi,
} from '';
import type { ListAvailableIntegrationsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new IntegrationsApi(config);

  try {
    const data = await api.listAvailableIntegrations();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**Array&lt;IntegrationDefinitionModel&gt;**](IntegrationDefinitionModel.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | List of available integration definitions |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

