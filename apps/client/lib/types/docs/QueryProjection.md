
# QueryProjection

Field selection for query results.

## Properties

Name | Type
------------ | -------------
`includeAttributes` | Array&lt;string | null&gt;
`includeRelationships` | Array&lt;string | null&gt;
`expandRelationships` | boolean

## Example

```typescript
import type { QueryProjection } from ''

// TODO: Update the object below with actual values
const example = {
  "includeAttributes": null,
  "includeRelationships": null,
  "expandRelationships": null,
} satisfies QueryProjection

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as QueryProjection
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


