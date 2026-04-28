
# TargetEquals

Entity is related to one of the specified entities.

## Properties

Name | Type
------------ | -------------
`entityIds` | Array&lt;string&gt;

## Example

```typescript
import type { TargetEquals } from ''

// TODO: Update the object below with actual values
const example = {
  "entityIds": ["550e8400-e29b-41d4-a716-446655440000","{{ steps.lookup.output.entityId }}"],
} satisfies TargetEquals

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TargetEquals
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


