
# WorkflowHttpRequestActionConfig

Configuration for HTTP_REQUEST action nodes.

## Properties

Name | Type
------------ | -------------
`version` | number
`url` | string
`method` | string
`headers` | { [key: string]: string | null; }
`body` | { [key: string]: string | null; }
`timeoutSeconds` | number
`configSchema` | [Array&lt;WorkflowNodeConfigField&gt;](WorkflowNodeConfigField.md)
`config` | { [key: string]: object; }
`subType` | [WorkflowActionType](WorkflowActionType.md)
`type` | [WorkflowNodeType](WorkflowNodeType.md)

## Example

```typescript
import type { WorkflowHttpRequestActionConfig } from ''

// TODO: Update the object below with actual values
const example = {
  "version": null,
  "url": https://api.example.com/users,
  "method": POST,
  "headers": {"Content-Type":"application/json","Authorization":"Bearer {{ steps.auth.output.token }}"},
  "body": {"name":"{{ steps.user.output.name }}","email":"user@example.com"},
  "timeoutSeconds": 30,
  "configSchema": null,
  "config": null,
  "subType": null,
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


