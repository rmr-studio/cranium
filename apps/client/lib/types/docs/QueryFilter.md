
# QueryFilter

Filter expression for querying entities.

## Properties

Name | Type
------------ | -------------
`type` | string
`relationshipId` | string
`condition` | [RelationshipFilter](RelationshipFilter.md)
`conditions` | [Array&lt;QueryFilter&gt;](QueryFilter.md)
`attributeId` | string
`operator` | [FilterOperator](FilterOperator.md)
`value` | [FilterValue](FilterValue.md)

## Example

```typescript
import type { QueryFilter } from ''

// TODO: Update the object below with actual values
const example = {
  "type": null,
  "relationshipId": null,
  "condition": null,
  "conditions": null,
  "attributeId": null,
  "operator": null,
  "value": null,
} satisfies QueryFilter

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as QueryFilter
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


