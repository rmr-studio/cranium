
# BatchDeleteResponse


## Properties

Name | Type
------------ | -------------
`results` | [Array&lt;BatchItemResult&gt;](BatchItemResult.md)
`succeeded` | number
`failed` | number

## Example

```typescript
import type { BatchDeleteResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "results": null,
  "succeeded": null,
  "failed": null,
} satisfies BatchDeleteResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as BatchDeleteResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


