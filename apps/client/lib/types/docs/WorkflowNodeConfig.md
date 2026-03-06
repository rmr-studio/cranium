
# WorkflowNodeConfig

Polymorphic workflow node configuration. Discriminated by \'type\' and \'subType\' fields.

## Properties

Name | Type
------------ | -------------
`version` | number
`entityTypeId` | string
`payload` | { [key: string]: string; }
`timeoutSeconds` | number
`configSchema` | [Array&lt;WorkflowNodeConfigField&gt;](WorkflowNodeConfigField.md)
`config` | { [key: string]: object; }
`subType` | [WorkflowControlType](WorkflowControlType.md)
`type` | [WorkflowNodeType](WorkflowNodeType.md)
`entityId` | string
`query` | [EntityQuery](EntityQuery.md)
`pagination` | [QueryPagination](QueryPagination.md)
`projection` | [QueryProjection](QueryProjection.md)
`errorHandling` | [BulkUpdateErrorHandling](BulkUpdateErrorHandling.md)
`url` | string
`method` | [RequestMethodType](RequestMethodType.md)
`headers` | { [key: string]: string; }
`body` | { [key: string]: string; }
`key` | string
`operation` | [OperationType](OperationType.md)
`field` | Array&lt;string&gt;
`expressions` | object
`cronExpression` | string
`interval` | [WorkflowScheduleTriggerConfigInterval](WorkflowScheduleTriggerConfigInterval.md)
`timeZone` | [WorkflowScheduleTriggerConfigTimeZone](WorkflowScheduleTriggerConfigTimeZone.md)
`authentication` | [AuthenticationType](AuthenticationType.md)
`signature` | [Signature](Signature.md)
`payloadSchema` | [SchemaString](SchemaString.md)
`schema` | [SchemaString](SchemaString.md)
`expression` | string
`contextEntityId` | string

## Example

```typescript
import type { WorkflowNodeConfig } from ''

// TODO: Update the object below with actual values
const example = {
  "version": null,
  "entityTypeId": null,
  "payload": null,
  "timeoutSeconds": null,
  "configSchema": null,
  "config": null,
  "subType": null,
  "type": null,
  "entityId": null,
  "query": null,
  "pagination": null,
  "projection": null,
  "errorHandling": null,
  "url": null,
  "method": null,
  "headers": null,
  "body": null,
  "key": null,
  "operation": null,
  "field": null,
  "expressions": null,
  "cronExpression": null,
  "interval": null,
  "timeZone": null,
  "authentication": null,
  "signature": null,
  "payloadSchema": null,
  "schema": null,
  "expression": null,
  "contextEntityId": null,
} satisfies WorkflowNodeConfig

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowNodeConfig
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


