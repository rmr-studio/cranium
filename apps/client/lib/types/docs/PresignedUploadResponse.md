
# PresignedUploadResponse


## Properties

Name | Type
------------ | -------------
`storageKey` | string
`uploadUrl` | string
`method` | string
`supported` | boolean

## Example

```typescript
import type { PresignedUploadResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "storageKey": null,
  "uploadUrl": null,
  "method": null,
  "supported": null,
} satisfies PresignedUploadResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PresignedUploadResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


