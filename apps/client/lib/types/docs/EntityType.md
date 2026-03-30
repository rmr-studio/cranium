
# EntityType


## Properties

Name | Type
------------ | -------------
`id` | string
`key` | string
`version` | number
`icon` | [Icon](Icon.md)
`name` | [DisplayName](DisplayName.md)
`_protected` | boolean
`identifierKey` | string
`semanticGroup` | [SemanticGroup](SemanticGroup.md)
`lifecycleDomain` | [LifecycleDomain](LifecycleDomain.md)
`sourceType` | [SourceType](SourceType.md)
`sourceIntegrationId` | string
`readonly` | boolean
`workspaceId` | string
`schema` | [SchemaUUID](SchemaUUID.md)
`columnConfiguration` | [ColumnConfiguration](ColumnConfiguration.md)
`columns` | [Array&lt;EntityTypeAttributeColumn&gt;](EntityTypeAttributeColumn.md)
`entitiesCount` | number
`relationships` | [Array&lt;RelationshipDefinition&gt;](RelationshipDefinition.md)
`semantics` | [SemanticMetadataBundle](SemanticMetadataBundle.md)
`createdAt` | Date
`updatedAt` | Date
`createdBy` | string
`updatedBy` | string

## Example

```typescript
import type { EntityType } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "key": null,
  "version": null,
  "icon": null,
  "name": null,
  "_protected": null,
  "identifierKey": null,
  "semanticGroup": null,
  "lifecycleDomain": null,
  "sourceType": null,
  "sourceIntegrationId": null,
  "readonly": null,
  "workspaceId": null,
  "schema": null,
  "columnConfiguration": null,
  "columns": null,
  "entitiesCount": null,
  "relationships": null,
  "semantics": null,
  "createdAt": null,
  "updatedAt": null,
  "createdBy": null,
  "updatedBy": null,
} satisfies EntityType

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EntityType
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


