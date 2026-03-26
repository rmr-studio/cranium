
# SaveUserRequest


## Properties

Name | Type
------------ | -------------
`name` | string
`email` | string
`phone` | string
`defaultWorkspaceId` | string
`onboardingCompletedAt` | Date
`removeAvatar` | boolean

## Example

```typescript
import type { SaveUserRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "name": null,
  "email": null,
  "phone": null,
  "defaultWorkspaceId": null,
  "onboardingCompletedAt": null,
  "removeAvatar": null,
} satisfies SaveUserRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SaveUserRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


