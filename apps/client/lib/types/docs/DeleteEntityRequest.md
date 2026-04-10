
# DeleteEntityRequest


## Properties

Name | Type
------------ | -------------
`type` | [EntitySelectType](EntitySelectType.md)
`entityTypeId` | string
`entityIds` | Array&lt;string&gt;
`filter` | [QueryFilter](QueryFilter.md)
`excludeIds` | Array&lt;string&gt;

## Example

```typescript
import type { DeleteEntityRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "type": null,
  "entityTypeId": null,
  "entityIds": null,
  "filter": null,
  "excludeIds": null,
} satisfies DeleteEntityRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as DeleteEntityRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


