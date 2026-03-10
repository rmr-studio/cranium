
# GenerateSignedUrlRequest


## Properties

Name | Type
------------ | -------------
`fileId` | string
`expiresInSeconds` | number

## Example

```typescript
import type { GenerateSignedUrlRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "fileId": null,
  "expiresInSeconds": null,
} satisfies GenerateSignedUrlRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as GenerateSignedUrlRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


