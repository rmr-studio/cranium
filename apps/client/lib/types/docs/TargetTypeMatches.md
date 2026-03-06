
# TargetTypeMatches

Type-aware filtering for polymorphic relationships.

## Properties

Name | Type
------------ | -------------
`branches` | [Array&lt;TypeBranch&gt;](TypeBranch.md)

## Example

```typescript
import type { TargetTypeMatches } from ''

// TODO: Update the object below with actual values
const example = {
  "branches": null,
} satisfies TargetTypeMatches

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TargetTypeMatches
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


