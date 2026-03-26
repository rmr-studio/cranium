# NotificationApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createNotification**](NotificationApi.md#createnotificationoperation) | **POST** /api/v1/notifications/workspace/{workspaceId} | Create a notification in a workspace |
| [**deleteNotification**](NotificationApi.md#deletenotification) | **DELETE** /api/v1/notifications/workspace/{workspaceId}/{notificationId} | Delete (soft-delete) a notification |
| [**getInbox**](NotificationApi.md#getinbox) | **GET** /api/v1/notifications/workspace/{workspaceId} | Get notification inbox for the current user in a workspace |
| [**getUnreadCount**](NotificationApi.md#getunreadcount) | **GET** /api/v1/notifications/workspace/{workspaceId}/unread-count | Get unread notification count for the current user |
| [**markAllAsRead**](NotificationApi.md#markallasread) | **POST** /api/v1/notifications/workspace/{workspaceId}/read-all | Mark all notifications as read for the current user |
| [**markAsRead**](NotificationApi.md#markasread) | **POST** /api/v1/notifications/workspace/{workspaceId}/{notificationId}/read | Mark a single notification as read |



## createNotification

> Notification createNotification(workspaceId, createNotificationRequest)

Create a notification in a workspace

### Example

```ts
import {
  Configuration,
  NotificationApi,
} from '';
import type { CreateNotificationOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NotificationApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CreateNotificationRequest
    createNotificationRequest: ...,
  } satisfies CreateNotificationOperationRequest;

  try {
    const data = await api.createNotification(body);
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
| **createNotificationRequest** | [CreateNotificationRequest](CreateNotificationRequest.md) |  | |

### Return type

[**Notification**](Notification.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Notification created |  -  |
| **403** | Access denied |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## deleteNotification

> deleteNotification(workspaceId, notificationId)

Delete (soft-delete) a notification

### Example

```ts
import {
  Configuration,
  NotificationApi,
} from '';
import type { DeleteNotificationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NotificationApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    notificationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies DeleteNotificationRequest;

  try {
    const data = await api.deleteNotification(body);
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
| **notificationId** | `string` |  | [Defaults to `undefined`] |

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
| **204** | Notification deleted |  -  |
| **404** | Notification not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getInbox

> NotificationInboxResponse getInbox(workspaceId, cursor, pageSize)

Get notification inbox for the current user in a workspace

### Example

```ts
import {
  Configuration,
  NotificationApi,
} from '';
import type { GetInboxRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NotificationApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string (optional)
    cursor: cursor_example,
    // number (optional)
    pageSize: 56,
  } satisfies GetInboxRequest;

  try {
    const data = await api.getInbox(body);
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
| **cursor** | `string` |  | [Optional] [Defaults to `undefined`] |
| **pageSize** | `number` |  | [Optional] [Defaults to `20`] |

### Return type

[**NotificationInboxResponse**](NotificationInboxResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Inbox retrieved |  -  |
| **403** | Access denied |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getUnreadCount

> number getUnreadCount(workspaceId)

Get unread notification count for the current user

### Example

```ts
import {
  Configuration,
  NotificationApi,
} from '';
import type { GetUnreadCountRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NotificationApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetUnreadCountRequest;

  try {
    const data = await api.getUnreadCount(body);
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

**number**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Count retrieved |  -  |
| **403** | Access denied |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## markAllAsRead

> markAllAsRead(workspaceId)

Mark all notifications as read for the current user

### Example

```ts
import {
  Configuration,
  NotificationApi,
} from '';
import type { MarkAllAsReadRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NotificationApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies MarkAllAsReadRequest;

  try {
    const data = await api.markAllAsRead(body);
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

`void` (Empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **204** | All marked as read |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## markAsRead

> markAsRead(workspaceId, notificationId)

Mark a single notification as read

### Example

```ts
import {
  Configuration,
  NotificationApi,
} from '';
import type { MarkAsReadRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NotificationApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    notificationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies MarkAsReadRequest;

  try {
    const data = await api.markAsRead(body);
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
| **notificationId** | `string` |  | [Defaults to `undefined`] |

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
| **204** | Marked as read |  -  |
| **404** | Notification not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

