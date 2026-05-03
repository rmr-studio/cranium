
# DataConnectorConnectionModel


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`name` | string
`host` | string
`port` | number
`database` | string
`user` | string
`sslMode` | string
`connectionStatus` | [ConnectionStatus](ConnectionStatus.md)
`lastVerifiedAt` | Date
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { DataConnectorConnectionModel } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "name": null,
  "host": null,
  "port": null,
  "database": null,
  "user": null,
  "sslMode": null,
  "connectionStatus": null,
  "lastVerifiedAt": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies DataConnectorConnectionModel

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as DataConnectorConnectionModel
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


