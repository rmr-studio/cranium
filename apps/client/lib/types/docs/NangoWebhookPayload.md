
# NangoWebhookPayload


## Properties

Name | Type
------------ | -------------
`type` | string
`operation` | string
`connectionId` | string
`providerConfigKey` | string
`provider` | string
`environment` | string
`success` | boolean
`tags` | [NangoWebhookTags](NangoWebhookTags.md)
`syncName` | string
`model` | string
`modifiedAfter` | string
`responseResults` | [NangoSyncResults](NangoSyncResults.md)

## Example

```typescript
import type { NangoWebhookPayload } from ''

// TODO: Update the object below with actual values
const example = {
  "type": null,
  "operation": null,
  "connectionId": null,
  "providerConfigKey": null,
  "provider": null,
  "environment": null,
  "success": null,
  "tags": null,
  "syncName": null,
  "model": null,
  "modifiedAfter": null,
  "responseResults": null,
} satisfies NangoWebhookPayload

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as NangoWebhookPayload
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


