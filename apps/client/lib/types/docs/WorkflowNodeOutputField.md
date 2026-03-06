
# WorkflowNodeOutputField


## Properties

Name | Type
------------ | -------------
`key` | string
`label` | string
`type` | [OutputFieldType](OutputFieldType.md)
`description` | string
`nullable` | boolean
`exampleValue` | object
`entityTypeId` | string

## Example

```typescript
import type { WorkflowNodeOutputField } from ''

// TODO: Update the object below with actual values
const example = {
  "key": null,
  "label": null,
  "type": null,
  "description": null,
  "nullable": null,
  "exampleValue": null,
  "entityTypeId": null,
} satisfies WorkflowNodeOutputField

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowNodeOutputField
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


