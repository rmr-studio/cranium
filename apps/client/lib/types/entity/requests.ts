// Re-export entity-related request types from generated code
import type { CreateEntityTypeRequest } from '../models/CreateEntityTypeRequest';
import type { SaveEntityRequest } from '../models/SaveEntityRequest';
import type { SaveTypeDefinitionRequest } from '../models/SaveTypeDefinitionRequest';
import type { SaveTypeDefinitionRequestDefinition } from '../models/SaveTypeDefinitionRequestDefinition';
import type { SaveAttributeDefinitionRequest } from '../models/SaveAttributeDefinitionRequest';
import type { SaveRelationshipDefinitionRequest } from '../models/SaveRelationshipDefinitionRequest';
import type { DeleteTypeDefinitionRequest } from '../models/DeleteTypeDefinitionRequest';
import type { DeleteTypeDefinitionRequestDefinition } from '../models/DeleteTypeDefinitionRequestDefinition';
import type { DeleteAttributeDefinitionRequest } from '../models/DeleteAttributeDefinitionRequest';
import type { DeleteRelationshipDefinitionRequest } from '../models/DeleteRelationshipDefinitionRequest';
import type { EntityAttributeRequest } from '../models/EntityAttributeRequest';
import type { EntityAttributeRequestPayload } from '../models/EntityAttributeRequestPayload';
import type { EntityReferenceRequest } from '../models/EntityReferenceRequest';
import type { SaveTargetRuleRequest } from '../models/SaveTargetRuleRequest';
import type { SaveSemanticMetadataRequest } from '../models/SaveSemanticMetadataRequest';
import type { BulkSaveSemanticMetadataRequest } from '../models/BulkSaveSemanticMetadataRequest';
import type { UpdateEntityTypeConfigurationRequest } from '../models/UpdateEntityTypeConfigurationRequest';
import { EntityTypeRequestDefinition } from '../models/EntityTypeRequestDefinition';

export type {
  CreateEntityTypeRequest,
  SaveEntityRequest,
  SaveTypeDefinitionRequest,
  SaveTypeDefinitionRequestDefinition,
  SaveAttributeDefinitionRequest,
  SaveRelationshipDefinitionRequest,
  DeleteTypeDefinitionRequest,
  DeleteTypeDefinitionRequestDefinition,
  DeleteAttributeDefinitionRequest,
  DeleteRelationshipDefinitionRequest,
  EntityAttributeRequest,
  EntityAttributeRequestPayload,
  EntityReferenceRequest,
  SaveTargetRuleRequest,
  SaveSemanticMetadataRequest,
  BulkSaveSemanticMetadataRequest,
  UpdateEntityTypeConfigurationRequest,
};

export { EntityTypeRequestDefinition };
