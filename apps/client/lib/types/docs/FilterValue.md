
# FilterValue

Value for filter comparison - literal or template expression.

## Properties

Name | Type
------------ | -------------
`kind` | string
`expression` | string
`value` | object

## Example

```typescript
import type { FilterValue } from ''

// TODO: Update the object below with actual values
const example = {
  "kind": null,
  "expression": {{ steps.lookup.output.status }},
  "value": null,
} satisfies FilterValue

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FilterValue
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


