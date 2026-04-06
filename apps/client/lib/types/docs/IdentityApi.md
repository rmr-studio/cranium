# IdentityApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**addEntityToCluster**](IdentityApi.md#addentitytocluster) | **POST** /api/v1/identity/{workspaceId}/clusters/{clusterId}/members | Manually add an entity to a cluster |
| [**confirmSuggestion**](IdentityApi.md#confirmsuggestion) | **POST** /api/v1/identity/{workspaceId}/suggestions/{suggestionId}/confirm | Confirm an identity match suggestion |
| [**getClusterDetail**](IdentityApi.md#getclusterdetail) | **GET** /api/v1/identity/{workspaceId}/clusters/{clusterId} | Get cluster detail with member entities |
| [**getPendingMatchCount**](IdentityApi.md#getpendingmatchcount) | **GET** /api/v1/identity/{workspaceId}/entities/{entityId}/matches | Get pending match count for an entity |
| [**getSuggestion**](IdentityApi.md#getsuggestion) | **GET** /api/v1/identity/{workspaceId}/suggestions/{suggestionId} | Get suggestion detail with signal breakdown |
| [**listClusters**](IdentityApi.md#listclusters) | **GET** /api/v1/identity/{workspaceId}/clusters | List identity clusters |
| [**listSuggestions**](IdentityApi.md#listsuggestions) | **GET** /api/v1/identity/{workspaceId}/suggestions | List identity match suggestions |
| [**rejectSuggestion**](IdentityApi.md#rejectsuggestion) | **POST** /api/v1/identity/{workspaceId}/suggestions/{suggestionId}/reject | Reject an identity match suggestion |
| [**renameCluster**](IdentityApi.md#renameclusteroperation) | **PATCH** /api/v1/identity/{workspaceId}/clusters/{clusterId} | Rename an identity cluster |



## addEntityToCluster

> ClusterDetailResponse addEntityToCluster(workspaceId, clusterId, addClusterMemberRequest)

Manually add an entity to a cluster

### Example

```ts
import {
  Configuration,
  IdentityApi,
} from '';
import type { AddEntityToClusterRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new IdentityApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    clusterId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // AddClusterMemberRequest
    addClusterMemberRequest: ...,
  } satisfies AddEntityToClusterRequest;

  try {
    const data = await api.addEntityToCluster(body);
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
| **clusterId** | `string` |  | [Defaults to `undefined`] |
| **addClusterMemberRequest** | [AddClusterMemberRequest](AddClusterMemberRequest.md) |  | |

### Return type

[**ClusterDetailResponse**](ClusterDetailResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Entity added to cluster successfully |  -  |
| **401** | Unauthorized |  -  |
| **404** | Cluster or entity not found |  -  |
| **409** | Entity already in a cluster |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## confirmSuggestion

> SuggestionResponse confirmSuggestion(workspaceId, suggestionId)

Confirm an identity match suggestion

### Example

```ts
import {
  Configuration,
  IdentityApi,
} from '';
import type { ConfirmSuggestionRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new IdentityApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    suggestionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies ConfirmSuggestionRequest;

  try {
    const data = await api.confirmSuggestion(body);
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
| **suggestionId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**SuggestionResponse**](SuggestionResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Suggestion confirmed successfully |  -  |
| **401** | Unauthorized |  -  |
| **404** | Suggestion not found |  -  |
| **409** | Suggestion already resolved |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getClusterDetail

> ClusterDetailResponse getClusterDetail(workspaceId, clusterId)

Get cluster detail with member entities

### Example

```ts
import {
  Configuration,
  IdentityApi,
} from '';
import type { GetClusterDetailRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new IdentityApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    clusterId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetClusterDetailRequest;

  try {
    const data = await api.getClusterDetail(body);
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
| **clusterId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**ClusterDetailResponse**](ClusterDetailResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Cluster retrieved successfully |  -  |
| **401** | Unauthorized |  -  |
| **404** | Cluster not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getPendingMatchCount

> PendingMatchCountResponse getPendingMatchCount(workspaceId, entityId)

Get pending match count for an entity

### Example

```ts
import {
  Configuration,
  IdentityApi,
} from '';
import type { GetPendingMatchCountRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new IdentityApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    entityId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetPendingMatchCountRequest;

  try {
    const data = await api.getPendingMatchCount(body);
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
| **entityId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**PendingMatchCountResponse**](PendingMatchCountResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Pending match count retrieved successfully |  -  |
| **401** | Unauthorized |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getSuggestion

> SuggestionResponse getSuggestion(workspaceId, suggestionId)

Get suggestion detail with signal breakdown

### Example

```ts
import {
  Configuration,
  IdentityApi,
} from '';
import type { GetSuggestionRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new IdentityApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    suggestionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetSuggestionRequest;

  try {
    const data = await api.getSuggestion(body);
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
| **suggestionId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**SuggestionResponse**](SuggestionResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Suggestion retrieved successfully |  -  |
| **401** | Unauthorized |  -  |
| **404** | Suggestion not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listClusters

> Array&lt;ClusterSummaryResponse&gt; listClusters(workspaceId)

List identity clusters

### Example

```ts
import {
  Configuration,
  IdentityApi,
} from '';
import type { ListClustersRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new IdentityApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies ListClustersRequest;

  try {
    const data = await api.listClusters(body);
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

[**Array&lt;ClusterSummaryResponse&gt;**](ClusterSummaryResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Clusters retrieved successfully |  -  |
| **401** | Unauthorized |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listSuggestions

> Array&lt;SuggestionResponse&gt; listSuggestions(workspaceId)

List identity match suggestions

### Example

```ts
import {
  Configuration,
  IdentityApi,
} from '';
import type { ListSuggestionsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new IdentityApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies ListSuggestionsRequest;

  try {
    const data = await api.listSuggestions(body);
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

[**Array&lt;SuggestionResponse&gt;**](SuggestionResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Suggestions retrieved successfully |  -  |
| **401** | Unauthorized |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## rejectSuggestion

> SuggestionResponse rejectSuggestion(workspaceId, suggestionId)

Reject an identity match suggestion

### Example

```ts
import {
  Configuration,
  IdentityApi,
} from '';
import type { RejectSuggestionRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new IdentityApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    suggestionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies RejectSuggestionRequest;

  try {
    const data = await api.rejectSuggestion(body);
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
| **suggestionId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**SuggestionResponse**](SuggestionResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Suggestion rejected successfully |  -  |
| **401** | Unauthorized |  -  |
| **404** | Suggestion not found |  -  |
| **409** | Suggestion already resolved |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## renameCluster

> IdentityCluster renameCluster(workspaceId, clusterId, renameClusterRequest)

Rename an identity cluster

### Example

```ts
import {
  Configuration,
  IdentityApi,
} from '';
import type { RenameClusterOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new IdentityApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    clusterId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // RenameClusterRequest
    renameClusterRequest: ...,
  } satisfies RenameClusterOperationRequest;

  try {
    const data = await api.renameCluster(body);
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
| **clusterId** | `string` |  | [Defaults to `undefined`] |
| **renameClusterRequest** | [RenameClusterRequest](RenameClusterRequest.md) |  | |

### Return type

[**IdentityCluster**](IdentityCluster.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Cluster renamed successfully |  -  |
| **401** | Unauthorized |  -  |
| **404** | Cluster not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

