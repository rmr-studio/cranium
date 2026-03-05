
# RelationshipResponse


## Properties

Name | Type
------------ | -------------
`id` | string
`sourceEntityId` | string
`targetEntityId` | string
`definitionId` | string
`definitionName` | string
`semanticContext` | string
`linkSource` | [SourceType](SourceType.md)
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { RelationshipResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "sourceEntityId": null,
  "targetEntityId": null,
  "definitionId": null,
  "definitionName": null,
  "semanticContext": null,
  "linkSource": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies RelationshipResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RelationshipResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


