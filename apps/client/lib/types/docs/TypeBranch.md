
# TypeBranch

Type branch for polymorphic relationship filtering.

## Properties

Name | Type
------------ | -------------
`entityTypeId` | string
`filter` | [QueryFilter](QueryFilter.md)

## Example

```typescript
import type { TypeBranch } from ''

// TODO: Update the object below with actual values
const example = {
  "entityTypeId": null,
  "filter": null,
} satisfies TypeBranch

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TypeBranch
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


