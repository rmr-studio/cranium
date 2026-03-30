
# ClusterMemberContext


## Properties

Name | Type
------------ | -------------
`entityId` | string
`typeKey` | string
`sourceType` | [SourceType](SourceType.md)
`identifierKey` | string
`joinedAt` | Date

## Example

```typescript
import type { ClusterMemberContext } from ''

// TODO: Update the object below with actual values
const example = {
  "entityId": null,
  "typeKey": null,
  "sourceType": null,
  "identifierKey": null,
  "joinedAt": null,
} satisfies ClusterMemberContext

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ClusterMemberContext
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


