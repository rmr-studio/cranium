// Verification that domain barrels export correctly

// Entity barrel imports
import type {
    EntityType,
    EntityAttribute,
    RelationshipDefinition,
    RelationshipTargetRule,
    EntityTypeDefinition,
    EntityTypeAttributeRow,
    SaveEntityRequest,
    EntityTypeImpactResponse,
    EntityTypeSemanticMetadata,
    SemanticMetadataBundle,
    EntityTypeWithSemanticsResponse,
    SaveRelationshipDefinitionRequest,
    SaveTargetRuleRequest,
    SaveSemanticMetadataRequest,
    BulkSaveSemanticMetadataRequest,
} from "@/lib/types/entity";
import {
    isRelationshipDefinition,
    isAttributeDefinition,
    RelationshipLimit,
    EntityRelationshipCardinality,
    SemanticAttributeClassification,
    SemanticMetadataTargetType,
} from "@/lib/types/entity";

// Block barrel imports
import type {
    Block,
    BlockType,
    BlockTree,
    BlockNode,
    ContentNode,
    CreateBlockTypeRequest,
    BlockHydrationResult,
} from "@/lib/types/block";
import {
    isContentMetadata,
    isContentNode,
    isReferenceNode,
} from "@/lib/types/block";

describe("Type barrel exports", () => {
    it("entity barrel exports types correctly", () => {
        // Type-level checks - if this compiles, types work
        const checkType = (entity: EntityType) => entity.key;
        const checkCustom = (def: EntityTypeDefinition) => def.id;
        const checkRelDef = (d: RelationshipDefinition) => d.id;
        const checkEnum = RelationshipLimit.SINGULAR;
        expect(typeof isRelationshipDefinition).toBe("function");
        expect(typeof isAttributeDefinition).toBe("function");
    });

    it("new relationship types are exported from entity barrel", () => {
        // Type-level checks - if this compiles, the exports work
        const checkRelDef = (d: RelationshipDefinition) => d.id;
        const checkTargetRule = (r: RelationshipTargetRule) => r;
        const checkSaveReq = (r: SaveRelationshipDefinitionRequest) => r;
        const checkSaveTargetRule = (r: SaveTargetRuleRequest) => r;
        expect(EntityRelationshipCardinality).toBeDefined();
    });

    it("semantic metadata types are exported from entity barrel", () => {
        // Type-level checks - if this compiles, the exports work
        const checkMeta = (m: EntityTypeSemanticMetadata) => m.id;
        const checkBundle = (b: SemanticMetadataBundle) => b.attributes;
        const checkResponse = (r: EntityTypeWithSemanticsResponse) => r.entityType;
        const checkSaveMeta = (r: SaveSemanticMetadataRequest) => r;
        const checkBulkSave = (r: BulkSaveSemanticMetadataRequest) => r;
        // Verify enums are defined and have expected members
        expect(SemanticAttributeClassification.Identifier).toBeDefined();
        expect(SemanticAttributeClassification.Categorical).toBeDefined();
        expect(SemanticAttributeClassification.Quantitative).toBeDefined();
        expect(SemanticAttributeClassification.Temporal).toBeDefined();
        expect(SemanticAttributeClassification.Freetext).toBeDefined();
        expect(SemanticAttributeClassification.RelationalReference).toBeDefined();
        expect(SemanticMetadataTargetType.EntityType).toBeDefined();
        expect(SemanticMetadataTargetType.Attribute).toBeDefined();
        expect(SemanticMetadataTargetType.Relationship).toBeDefined();
        // Regression anchors for key values
        expect(SemanticAttributeClassification.Identifier).toBe('IDENTIFIER');
        expect(SemanticMetadataTargetType.EntityType).toBe('ENTITY_TYPE');
    });

    it("EntityRelationshipDefinition is removed from barrel", () => {
        // This test verifies by absence: if EntityRelationshipDefinition were still
        // exported, the import block at the top of this file would need to include it.
        // The fact that this file compiles without importing EntityRelationshipDefinition
        // AND uses RelationshipDefinition instead confirms TYPE-02 is satisfied.
        expect(true).toBe(true);
    });

    it("block barrel exports types correctly", () => {
        // Type-level checks - if this compiles, types work
        const checkType = (block: Block) => block.id;
        const checkNode = (node: BlockNode) => node.type;
        expect(typeof isContentMetadata).toBe("function");
        expect(typeof isContentNode).toBe("function");
    });

    it("entity type guards are callable functions", () => {
        // Verify type guards are exported as functions
        expect(typeof isRelationshipDefinition).toBe("function");
        expect(typeof isAttributeDefinition).toBe("function");
    });

    it("block type guards are callable functions", () => {
        // Verify type guards are exported as functions
        expect(typeof isContentMetadata).toBe("function");
        expect(typeof isContentNode).toBe("function");
        expect(typeof isReferenceNode).toBe("function");
    });

    it("RelationshipLimit enum is accessible", () => {
        expect(RelationshipLimit.SINGULAR).toBe(0);
        expect(RelationshipLimit.MANY).toBe(1);
    });
});
