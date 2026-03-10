
# ConfirmUploadRequest


## Properties

Name | Type
------------ | -------------
`storageKey` | string
`originalFilename` | string
`metadata` | { [key: string]: string; }

## Example

```typescript
import type { ConfirmUploadRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "storageKey": null,
  "originalFilename": null,
  "metadata": null,
} satisfies ConfirmUploadRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ConfirmUploadRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


