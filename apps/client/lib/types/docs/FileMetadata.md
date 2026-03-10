
# FileMetadata


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`domain` | [StorageDomain](StorageDomain.md)
`storageKey` | string
`originalFilename` | string
`contentType` | string
`fileSize` | number
`uploadedBy` | string
`metadata` | { [key: string]: string; }
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { FileMetadata } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "domain": null,
  "storageKey": null,
  "originalFilename": null,
  "contentType": null,
  "fileSize": null,
  "uploadedBy": null,
  "metadata": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies FileMetadata

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FileMetadata
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


