
# ClusterDetailResponse


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`name` | string
`memberCount` | number
`members` | [Array&lt;ClusterMemberContext&gt;](ClusterMemberContext.md)
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { ClusterDetailResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "name": null,
  "memberCount": null,
  "members": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies ClusterDetailResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ClusterDetailResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


