
# EntityQueryResponse

Paginated response from an entity query

## Properties

Name | Type
------------ | -------------
`entities` | [Array&lt;Entity&gt;](Entity.md)
`totalCount` | number
`hasNextPage` | boolean
`limit` | number
`offset` | number

## Example

```typescript
import type { EntityQueryResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "entities": null,
  "totalCount": null,
  "hasNextPage": null,
  "limit": null,
  "offset": null,
} satisfies EntityQueryResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EntityQueryResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


