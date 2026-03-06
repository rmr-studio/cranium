
# AddRelationshipRequest


## Properties

Name | Type
------------ | -------------
`targetEntityId` | string
`definitionId` | string
`semanticContext` | string
`linkSource` | [SourceType](SourceType.md)

## Example

```typescript
import type { AddRelationshipRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "targetEntityId": null,
  "definitionId": null,
  "semanticContext": null,
  "linkSource": null,
} satisfies AddRelationshipRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AddRelationshipRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


