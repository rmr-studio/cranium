
# Entity


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`typeId` | string
`payload` | [{ [key: string]: EntityAttribute; }](EntityAttribute.md)
`icon` | [Icon](Icon.md)
`validationErrors` | Array&lt;string&gt;
`identifierKey` | string
`createdAt` | Date
`updatedAt` | Date
`createdBy` | string
`updatedBy` | string
`sourceType` | [SourceType](SourceType.md)
`sourceIntegrationId` | string
`sourceExternalId` | string
`sourceUrl` | string
`firstSyncedAt` | Date
`lastSyncedAt` | Date
`syncVersion` | number
`identifier` | string

## Example

```typescript
import type { Entity } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "typeId": null,
  "payload": null,
  "icon": null,
  "validationErrors": null,
  "identifierKey": null,
  "createdAt": null,
  "updatedAt": null,
  "createdBy": null,
  "updatedBy": null,
  "sourceType": null,
  "sourceIntegrationId": null,
  "sourceExternalId": null,
  "sourceUrl": null,
  "firstSyncedAt": null,
  "lastSyncedAt": null,
  "syncVersion": null,
  "identifier": null,
} satisfies Entity

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Entity
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


