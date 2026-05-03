# DataConnectorApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**_delete**](DataConnectorApi.md#_delete) | **DELETE** /api/v1/connector/connections/{id} | Soft-delete a connection |
| [**create**](DataConnectorApi.md#create) | **POST** /api/v1/connector/connections | Create a custom-source Postgres connection |
| [**getById**](DataConnectorApi.md#getbyid) | **GET** /api/v1/connector/connections/{id} | Get a connection by id |
| [**list**](DataConnectorApi.md#list) | **GET** /api/v1/connector/connections | List connections for a workspace |
| [**test**](DataConnectorApi.md#test) | **POST** /api/v1/connector/connections/test | Dry-run gate validation for a prospective connection |
| [**update**](DataConnectorApi.md#update) | **PATCH** /api/v1/connector/connections/{id} | Partial update of a connection (re-runs gates on credential-touching fields) |



## _delete

> _delete(workspaceId, id)

Soft-delete a connection

### Example

```ts
import {
  Configuration,
  DataConnectorApi,
} from '';
import type { DeleteRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new DataConnectorApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies DeleteRequest;

  try {
    const data = await api._delete(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

`void` (Empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **204** | No Content |  -  |
| **403** | Forbidden |  -  |
| **404** | Not Found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## create

> DataConnectorConnectionModel create(createDataConnectorConnectionRequest)

Create a custom-source Postgres connection

### Example

```ts
import {
  Configuration,
  DataConnectorApi,
} from '';
import type { CreateRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new DataConnectorApi(config);

  const body = {
    // CreateDataConnectorConnectionRequest
    createDataConnectorConnectionRequest: ...,
  } satisfies CreateRequest;

  try {
    const data = await api.create(body);
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
| **createDataConnectorConnectionRequest** | [CreateDataConnectorConnectionRequest](CreateDataConnectorConnectionRequest.md) |  | |

### Return type

[**DataConnectorConnectionModel**](DataConnectorConnectionModel.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Connection created |  -  |
| **400** | Invalid request, SSRF rejected, or role not read-only |  -  |
| **403** | Workspace access denied |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getById

> DataConnectorConnectionModel getById(workspaceId, id)

Get a connection by id

### Example

```ts
import {
  Configuration,
  DataConnectorApi,
} from '';
import type { GetByIdRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new DataConnectorApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetByIdRequest;

  try {
    const data = await api.getById(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**DataConnectorConnectionModel**](DataConnectorConnectionModel.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |
| **403** | Workspace access denied |  -  |
| **404** | Connection not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## list

> Array&lt;DataConnectorConnectionModel&gt; list(workspaceId)

List connections for a workspace

### Example

```ts
import {
  Configuration,
  DataConnectorApi,
} from '';
import type { ListRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new DataConnectorApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies ListRequest;

  try {
    const data = await api.list(body);
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

[**Array&lt;DataConnectorConnectionModel&gt;**](DataConnectorConnectionModel.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |
| **403** | Workspace access denied |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## test

> ConnectorTestResult test(dataConnectorConnectionTestRequest)

Dry-run gate validation for a prospective connection

### Example

```ts
import {
  Configuration,
  DataConnectorApi,
} from '';
import type { TestRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new DataConnectorApi(config);

  const body = {
    // DataConnectorConnectionTestRequest
    dataConnectorConnectionTestRequest: ...,
  } satisfies TestRequest;

  try {
    const data = await api.test(body);
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
| **dataConnectorConnectionTestRequest** | [DataConnectorConnectionTestRequest](DataConnectorConnectionTestRequest.md) |  | |

### Return type

[**ConnectorTestResult**](ConnectorTestResult.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Gate validation result (pass/fail) |  -  |
| **400** | Invalid request or gate failure |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## update

> DataConnectorConnectionModel update(workspaceId, id, updateDataConnectorConnectionRequest)

Partial update of a connection (re-runs gates on credential-touching fields)

### Example

```ts
import {
  Configuration,
  DataConnectorApi,
} from '';
import type { UpdateRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new DataConnectorApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // UpdateDataConnectorConnectionRequest
    updateDataConnectorConnectionRequest: ...,
  } satisfies UpdateRequest;

  try {
    const data = await api.update(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **updateDataConnectorConnectionRequest** | [UpdateDataConnectorConnectionRequest](UpdateDataConnectorConnectionRequest.md) |  | |

### Return type

[**DataConnectorConnectionModel**](DataConnectorConnectionModel.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |
| **400** | Invalid request, SSRF rejected, or role not read-only |  -  |
| **403** | Forbidden |  -  |
| **404** | Not Found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

