
# NoteEntityContext


## Properties

Name | Type
------------ | -------------
`entityId` | string
`entityDisplayName` | string
`entityTypeKey` | string
`entityTypeIcon` | string
`entityTypeColour` | string

## Example

```typescript
import type { NoteEntityContext } from ''

// TODO: Update the object below with actual values
const example = {
  "entityId": null,
  "entityDisplayName": null,
  "entityTypeKey": null,
  "entityTypeIcon": null,
  "entityTypeColour": null,
} satisfies NoteEntityContext

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as NoteEntityContext
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


