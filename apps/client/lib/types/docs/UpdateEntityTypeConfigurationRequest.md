
# UpdateEntityTypeConfigurationRequest


## Properties

Name | Type
------------ | -------------
`id` | string
`name` | [DisplayName](DisplayName.md)
`icon` | [Icon](Icon.md)
`semanticGroup` | [SemanticGroup](SemanticGroup.md)
`columns` | [Array&lt;EntityTypeAttributeColumn&gt;](EntityTypeAttributeColumn.md)
`semantics` | [SaveSemanticMetadataRequest](SaveSemanticMetadataRequest.md)

## Example

```typescript
import type { UpdateEntityTypeConfigurationRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "name": null,
  "icon": null,
  "semanticGroup": null,
  "columns": null,
  "semantics": null,
} satisfies UpdateEntityTypeConfigurationRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as UpdateEntityTypeConfigurationRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


