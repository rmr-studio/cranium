
# WorkflowHttpRequestActionConfig

Configuration for HTTP_REQUEST action nodes.

## Properties

Name | Type
------------ | -------------
`version` | number
`url` | string
`method` | string
`headers` | { [key: string]: string; }
`body` | { [key: string]: string; }
`timeoutSeconds` | number
`configSchema` | [Array&lt;WorkflowNodeConfigField&gt;](WorkflowNodeConfigField.md)
`subType` | [WorkflowActionType](WorkflowActionType.md)
`config` | { [key: string]: object; }
`type` | [WorkflowNodeType](WorkflowNodeType.md)

## Example

```typescript
import type { WorkflowHttpRequestActionConfig } from ''

// TODO: Update the object below with actual values
const example = {
  "version": null,
  "url": null,
  "method": null,
  "headers": null,
  "body": null,
  "timeoutSeconds": null,
  "configSchema": null,
  "subType": null,
  "config": null,
  "type": null,
} satisfies WorkflowHttpRequestActionConfig

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowHttpRequestActionConfig
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


