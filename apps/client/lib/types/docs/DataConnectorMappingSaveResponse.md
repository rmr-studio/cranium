
# DataConnectorMappingSaveResponse


## Properties

Name | Type
------------ | -------------
`entityTypeId` | string
`relationshipsCreated` | Array&lt;string&gt;
`pendingRelationships` | [Array&lt;PendingRelationship&gt;](PendingRelationship.md)
`compositeFkSkipped` | Array&lt;string&gt;
`cursorIndexWarning` | [CursorIndexWarning](CursorIndexWarning.md)

## Example

```typescript
import type { DataConnectorMappingSaveResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "entityTypeId": null,
  "relationshipsCreated": null,
  "pendingRelationships": null,
  "compositeFkSkipped": null,
  "cursorIndexWarning": null,
} satisfies DataConnectorMappingSaveResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as DataConnectorMappingSaveResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


