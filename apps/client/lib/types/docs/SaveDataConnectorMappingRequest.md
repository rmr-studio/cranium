
# SaveDataConnectorMappingRequest


## Properties

Name | Type
------------ | -------------
`lifecycleDomain` | [LifecycleDomain](LifecycleDomain.md)
`semanticGroup` | [SemanticGroup](SemanticGroup.md)
`columns` | [Array&lt;SaveDataConnectorFieldMappingRequest&gt;](SaveDataConnectorFieldMappingRequest.md)

## Example

```typescript
import type { SaveDataConnectorMappingRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "lifecycleDomain": null,
  "semanticGroup": null,
  "columns": null,
} satisfies SaveDataConnectorMappingRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SaveDataConnectorMappingRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


