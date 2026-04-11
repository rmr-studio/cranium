---
tags:
  - priority/high
  - architecture/feature
  - status/implemented
Created:
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
# Quick Design: Workflow State Management and Data Traversal

## What & Why

Currently the workflow context data stores incomplete and lack enough structure
There are currently two workflow state systems
- WorkflowState
	- Stores node execution records
		- Active, Complete, Failed
	- Stores Workflow status
	- Should store output data registry
		

- WorkflowExecutionContext
	- Also stores data registry??
	- Stores workflow level orchestration metadata
Currently each node is creating its own blank WorkflowExecutionContext with a blank data registry lmao wtf
This needs to be condensed into a singular global state store that is both thread safe and mutable. This would grant a node access to
- Initial trigger data and metadata
- Up to date data registry mapping the output of each step/node
- Custom user defined variables
- Current lop context (Would need to handle multiple concurrent loops for parallel execution of branches)

---

## Data Changes

**New/Modified Entities:**
*Removed* - WorkflowState
*Removed* - WorkflowExecutionContext

*Added* - WorkflowDataStore
```
                                                 
  │  ┌───────────────────────────────────────────────────────────────────────┐  │                                                        
  │  │                      WorkflowDataStore                                 │  │                                                       
  │  ├───────────────────────────────────────────────────────────────────────┤  │                                                        
  │  │                                                                        │  │                                                       
  │  │  trigger:     TriggerContext           ← Initial event data            │  │                                                       
  │  │               ├─ eventType: String                                     │  │                                                       
  │  │               ├─ entity: Entity?        (for entity triggers)          │  │                                                       
  │  │               ├─ payload: JsonObject?   (for webhooks)                 │  │                                                       
  │  │               └─ scheduledAt: Instant?  (for schedules)                │  │                                                       
  │  │                                                                        │  │                                                       
  │  │  steps:       Map<String, StepOutput>  ← Node outputs by name          │  │                                                       
  │  │               ├─ nodeId: UUID                                          │  │                                                       
  │  │               ├─ status: Status                                        │  │                                                       
  │  │               ├─ output: JsonObject                                    │  │                                                       
  │  │               ├─ executedAt: Instant                                   │  │                                                       
  │  │               └─ durationMs: Long                                      │  │                                                       
  │  │                                                                        │  │                                                       
  │  │  variables:   Map<String, Any?>        ← User-defined variables        │  │                                                       
  │  │                                         (for loops, accumulators)      │  │                                                       
  │  │                                                                        │  │                                                       
  │  │  loop:        LoopContext?             ← Active loop state             │  │                                                       
  │  │               ├─ loopName: String                                      │  │                                                       
  │  │               ├─ items: List<Any?>                                     │  │                                                       
  │  │               ├─ index: Int                                            │  │                                                       
  │  │               └─ item: Any?             (current iteration)            │  │                                                       
  │  │                                                                        │  │                                                       
  │  └───────────────────────────────────────────────────────────────────────┘  │                                                        
  │                                                                              │                                                       
  │  Template Resolution Paths:                                                  │                                                       
  │  ─────────────────────────                                                   │                                                       
  │  {{ trigger.entity.Status }}      → trigger context                          │                                                       
  │  {{ steps.query_leads.output }}   → step outputs                             │                                                       
  │  {{ loop.process_items.item }}    → current loop iteration                   │                                                       
  │  {{ variables.totalCount }}       → workflow variables                       │                                                       
  │                                                                              │                                                       
  └─────────────────────────────────────────────────────────────────────────────┘                                                                                                         
```


**New/Modified Fields:**

### `TriggerContext`

- The trigger context should store the context about the initial workflow trigger, and all associated data. 
	- This is so nodes can execution actions based on originating data
	- This supports function invocations, where the trigger would contain all input data passed into the function
```
  sealed interface TriggerContext {                                                               
      data class EntityEvent(                                                                     
          val eventType: EntityEventType,                                                         
          val entity: Entity,                                                                     
          val previousEntity: Entity?  // For UPDATE events                                       
      ) : TriggerContext                                                                          
                                                                                                  
      data class Webhook(                                                                         
          val headers: Map<String, String>,                                                       
          val body: JsonObject,                                                                   
          val queryParams: Map<String, String>                                                    
      ) : TriggerContext                                                                          
                                                                                                  
      data class Schedule(                                                                        
          val scheduledAt: Instant,                                                               
          val cronExpression: String                                                              
      ) : TriggerContext                                                                          
                                                                                                  
      data class Function(                                                                        
          val arguments: JsonObject                                                               
      ) : TriggerContext                                                                          
  }                                                                                               
      
```

### `StepOutput`
- Currently there is no way to really determine what a node outputs with the `Map<String, Any>` approach

```
 data class StepOutput(                                                                          
      val nodeId: UUID,                                                                           
      val nodeName: String,                                                                       
      val status: WorkflowStatus,                                                                 
      val output: NodeOutput,                                                                     
      val executedAt: Instant,                                                                    
      val durationMs: Long                                                                        
  )      
```

- Inside the output definition. `NodeOutput` refers to a sealed interface that allows each node to further declare output structure based on the type of node being executed

```
 sealed interface NodeOutput {                                                                   
      val success: Boolean                                                                        
  }     
```

```
  data class CreateEntityOutput(                                                                  
      val entityId: UUID,                                                                         
      val entityTypeId: UUID,                                                                     
      val payload: JsonObject                                                                     
  ) : NodeOutput {                                                                                
      override val success = true                                                                 
  }                                                                                               
                                                                                                  
  // For QUERY_ENTITY                                                                             
  data class QueryEntityOutput(                                                                   
      val entities: List<Entity>,                                                                 
      val totalCount: Int,                                                                        
      val hasMore: Boolean                                                                        
  ) : NodeOutput {                                                                                
      override val success = true                                                                 
  }                                                                                               
                                                                                                  
  // For HTTP_REQUEST                                                                             
  data class HttpResponseOutput(                                                                  
      val statusCode: Int,                                                                        
      val headers: Map<String, String>,                                                           
      val body: JsonObject?                                                                       
  ) : NodeOutput {                                                                                
      override val success get() = statusCode in 200..299                                         
  }                                                                                               
                                                                                                  
  // For CONDITION                                                                                
  data class ConditionOutput(                                                                     
      val result: Boolean,                                                                        
      val evaluatedExpression: String                                                             
  ) : NodeOutput {                                                                                
      override val success = true                                                                 
  }                                                                                               
```


- There would need to be additional template resolution to ensure that a property that is being accessed by a specific node output exists
	- The output type might be further configuration by a type/subtype approach. Not really sure at the moment
```
 fun validateTemplate(                                                                           
      template: String,                                                                           
      nodeOutputTypes: Map<String, KClass<out NodeOutput>>                                        
  ): ValidationResult {                                                                           
      val path = parseTemplate(template)                                                          
      val nodeName = path[1]                                                                      
      val outputType = nodeOutputTypes[nodeName]                                                  
      val propertyName = path[2]                                                                  
                                                                                                  
      // Use reflection to check if outputType has property                                       
      return if (outputType?.memberProperties?.any { it.name == propertyName } == true) {         
          ValidationResult.Valid                                                                  
      } else {                                                                                    
          ValidationResult.Invalid("Property $propertyName not found on                           
  ${outputType?.simpleName}")                                                                     
      }                                                                                           
  }                                                                                               
```

### `LoopContext`
---

## Components Affected

- [[riven/docs/system-design/domains/Workflows/State Management/WorkflowNodeInputResolverService]] - The input resolver should now need to support json path matching to each of the sub attributes within the data store
	- Trigger (TriggerContext)
	- Steps 
	- Loops
	- Variables
- [[riven/docs/system-design/domains/Workflows/Execution Engine/WorkflowOrchestrationService]] - During the instantiation of a workflow. A singular datastore entity should be created that is then passed around throughout the entire workflow
- [[riven/docs/system-design/domains/Workflows/Execution Engine/WorkflowGraphCoordinationService]] - The data store should be passed through into this service, and the `nodeExecutor` callback should accept `dataStore` as a second required parametre, this allows the data store reference to be passed around by every single node that is to be executed during the lifecycle of a workflow

- [[riven/apps/client/lib/types/docs/WorkflowNode]] - After the execution of a node. The node would need to manually update the referenced data store to store the result of its execution 

---

## API Changes

_New endpoints or modifications (if any)_

---

## Failure Handling

_What happens if this fails? Any new failure modes?_

---

## Gotchas & Edge Cases

_Things to watch out for_

---

## Tasks

- [ ]
- [ ]
- [ ]

---

## Notes

_Anything else relevant_