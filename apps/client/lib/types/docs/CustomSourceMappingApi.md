# CustomSourceMappingApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**getSchema**](CustomSourceMappingApi.md#getschema) | **GET** /api/v1/custom-sources/connections/{connectionId}/schema | Introspect live tables + stored mappings + drift + cursor-index warnings |
| [**saveMapping**](CustomSourceMappingApi.md#savemapping) | **POST** /api/v1/custom-sources/connections/{connectionId}/schema/tables/{tableName}/mapping | Persist column mappings; creates or updates the readonly EntityType |



## getSchema

> DataConnectorSchemaResponse getSchema(workspaceId, connectionId)

Introspect live tables + stored mappings + drift + cursor-index warnings

### Example

```ts
import {
  Configuration,
  CustomSourceMappingApi,
} from '';
import type { GetSchemaRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CustomSourceMappingApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    connectionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetSchemaRequest;

  try {
    const data = await api.getSchema(body);
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
| **connectionId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**DataConnectorSchemaResponse**](DataConnectorSchemaResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Schema + drift payload |  -  |
| **403** | Workspace access denied |  -  |
| **404** | Connection not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## saveMapping

> DataConnectorMappingSaveResponse saveMapping(workspaceId, connectionId, tableName, saveDataConnectorMappingRequest)

Persist column mappings; creates or updates the readonly EntityType

### Example

```ts
import {
  Configuration,
  CustomSourceMappingApi,
} from '';
import type { SaveMappingRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CustomSourceMappingApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    connectionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    tableName: tableName_example,
    // SaveDataConnectorMappingRequest
    saveDataConnectorMappingRequest: ...,
  } satisfies SaveMappingRequest;

  try {
    const data = await api.saveMapping(body);
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
| **connectionId** | `string` |  | [Defaults to `undefined`] |
| **tableName** | `string` |  | [Defaults to `undefined`] |
| **saveDataConnectorMappingRequest** | [SaveDataConnectorMappingRequest](SaveDataConnectorMappingRequest.md) |  | |

### Return type

[**DataConnectorMappingSaveResponse**](DataConnectorMappingSaveResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Mapping saved; EntityType created or updated |  -  |
| **400** | Invalid mapping request |  -  |
| **403** | Workspace access denied |  -  |
| **404** | Connection not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

