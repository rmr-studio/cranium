
# EntityQueryRequest

Request body for querying entities with filtering, pagination, and sorting

## Properties

Name | Type
------------ | -------------
`filter` | [QueryFilter](QueryFilter.md)
`pagination` | [QueryPagination](QueryPagination.md)
`projection` | [QueryProjection](QueryProjection.md)
`includeCount` | boolean
`maxDepth` | number

## Example

```typescript
import type { EntityQueryRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "filter": null,
  "pagination": null,
  "projection": null,
  "includeCount": null,
  "maxDepth": 3,
} satisfies EntityQueryRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EntityQueryRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


