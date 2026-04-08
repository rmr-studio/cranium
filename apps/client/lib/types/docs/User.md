
# User


## Properties

Name | Type
------------ | -------------
`id` | string
`email` | string
`name` | string
`phone` | string
`avatarUrl` | string
`memberships` | [Array&lt;WorkspaceMember&gt;](WorkspaceMember.md)
`defaultWorkspace` | [Workspace](Workspace.md)
`onboardingCompletedAt` | Date
`acquisitionChannels` | [Array&lt;AcquisitionChannel&gt;](AcquisitionChannel.md)

## Example

```typescript
import type { User } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "email": null,
  "name": null,
  "phone": null,
  "avatarUrl": null,
  "memberships": null,
  "defaultWorkspace": null,
  "onboardingCompletedAt": null,
  "acquisitionChannels": null,
} satisfies User

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as User
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


