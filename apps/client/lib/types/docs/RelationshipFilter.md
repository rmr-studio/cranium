
# RelationshipFilter

Condition for evaluating relationships in filters.

## Properties

Name | Type
------------ | -------------
`type` | string
`entityIds` | Array&lt;string&gt;
`filter` | [QueryFilter](QueryFilter.md)
`branches` | [Array&lt;TypeBranch&gt;](TypeBranch.md)
`operator` | [FilterOperator](FilterOperator.md)
`count` | number

## Example

```typescript
import type { RelationshipFilter } from ''

// TODO: Update the object below with actual values
const example = {
  "type": null,
  "entityIds": null,
  "filter": null,
  "branches": null,
  "operator": null,
  "count": null,
} satisfies RelationshipFilter

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RelationshipFilter
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


