# UserApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**deleteUserProfileById**](UserApi.md#deleteuserprofilebyid) | **DELETE** /api/v1/user/{userId} | Delete a user profile by ID |
| [**getCurrentUser**](UserApi.md#getcurrentuser) | **GET** /api/v1/user/ | Get current user\&#39;s profile |
| [**getUserById**](UserApi.md#getuserbyid) | **GET** /api/v1/user/{userId} | Get a user by ID |
| [**updateUserProfile**](UserApi.md#updateuserprofile) | **PUT** /api/v1/user/ | Update current user\&#39;s profile |



## deleteUserProfileById

> deleteUserProfileById(userId)

Delete a user profile by ID

Deletes a user profile with the specified ID, if the user has access.

### Example

```ts
import {
  Configuration,
  UserApi,
} from '';
import type { DeleteUserProfileByIdRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new UserApi(config);

  const body = {
    // string
    userId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies DeleteUserProfileByIdRequest;

  try {
    const data = await api.deleteUserProfileById(body);
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
| **userId** | `string` |  | [Defaults to `undefined`] |

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
| **204** | User profile deleted successfully |  -  |
| **401** | Unauthorized access |  -  |
| **403** | User does not have permission to delete this profile |  -  |
| **404** | User not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCurrentUser

> User getCurrentUser()

Get current user\&#39;s profile

Retrieves the profile of the authenticated user based on the current session.

### Example

```ts
import {
  Configuration,
  UserApi,
} from '';
import type { GetCurrentUserRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new UserApi(config);

  try {
    const data = await api.getCurrentUser();
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

[**User**](User.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | User profile retrieved successfully |  -  |
| **401** | Unauthorized access |  -  |
| **404** | User not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getUserById

> User getUserById(userId)

Get a user by ID

Retrieves a specific user\&#39;s profile by their ID, if the user has access.

### Example

```ts
import {
  Configuration,
  UserApi,
} from '';
import type { GetUserByIdRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new UserApi(config);

  const body = {
    // string
    userId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetUserByIdRequest;

  try {
    const data = await api.getUserById(body);
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
| **userId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**User**](User.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | User profile retrieved successfully |  -  |
| **401** | Unauthorized access |  -  |
| **403** | User does not have permission to access this profile |  -  |
| **404** | User not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateUserProfile

> User updateUserProfile(user, avatar)

Update current user\&#39;s profile

Updates the profile of the authenticated user based on the provided data. Optionally accepts an avatar file upload.

### Example

```ts
import {
  Configuration,
  UserApi,
} from '';
import type { UpdateUserProfileRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new UserApi(config);

  const body = {
    // SaveUserRequest
    user: ...,
    // Blob (optional)
    avatar: BINARY_DATA_HERE,
  } satisfies UpdateUserProfileRequest;

  try {
    const data = await api.updateUserProfile(body);
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
| **user** | [SaveUserRequest](SaveUserRequest.md) |  | [Defaults to `undefined`] |
| **avatar** | `Blob` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**User**](User.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `multipart/form-data`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | User profile updated successfully |  -  |
| **401** | Unauthorized access |  -  |
| **404** | User not found |  -  |
| **400** | Invalid request data |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

