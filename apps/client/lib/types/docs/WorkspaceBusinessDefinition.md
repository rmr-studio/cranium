
# WorkspaceBusinessDefinition


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`term` | string
`normalizedTerm` | string
`definition` | string
`category` | [DefinitionCategory](DefinitionCategory.md)
`compiledParams` | { [key: string]: object; }
`status` | [DefinitionStatus](DefinitionStatus.md)
`source` | [DefinitionSource](DefinitionSource.md)
`entityTypeRefs` | Array&lt;string&gt;
`attributeRefs` | Array&lt;string&gt;
`isCustomized` | boolean
`version` | number
`createdBy` | string
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { WorkspaceBusinessDefinition } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "term": null,
  "normalizedTerm": null,
  "definition": null,
  "category": null,
  "compiledParams": null,
  "status": null,
  "source": null,
  "entityTypeRefs": null,
  "attributeRefs": null,
  "isCustomized": null,
  "version": null,
  "createdBy": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies WorkspaceBusinessDefinition

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkspaceBusinessDefinition
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


