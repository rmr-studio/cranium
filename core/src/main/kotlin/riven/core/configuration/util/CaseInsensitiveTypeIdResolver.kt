package riven.core.configuration.util

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import tools.jackson.databind.DatabindContext
import tools.jackson.databind.JavaType
import tools.jackson.databind.jsontype.impl.TypeIdResolverBase
import tools.jackson.databind.type.TypeFactory

/**
 * Jackson [tools.jackson.databind.jsontype.TypeIdResolver] performing
 * case-insensitive type-id lookups.
 *
 * Reads [JsonSubTypes] from the base type during [init] to build an
 * uppercase-keyed map for case-insensitive deserialization and a
 * class-to-canonical-name map for deterministic serialization.
 */
class CaseInsensitiveTypeIdResolver : TypeIdResolverBase() {

    private var idToType: Map<String, JavaType> = emptyMap()
    private var typeToId: Map<Class<*>, String> = emptyMap()

    override fun init(baseType: JavaType) {
        super.init(baseType)
        val subtypes = baseType.rawClass.getAnnotation(JsonSubTypes::class.java) ?: return
        val typeFactory = TypeFactory.createDefaultInstance()

        idToType = subtypes.value.associate { sub ->
            sub.name.uppercase() to typeFactory.constructSpecializedType(baseType, sub.value.java)
        }
        typeToId = subtypes.value.associate { sub ->
            sub.value.java to sub.name
        }
    }

    override fun idFromValue(context: DatabindContext, value: Any): String =
        typeToId[value::class.java]
            ?: throw IllegalArgumentException("No type id mapping for ${value::class.java.name}")

    override fun idFromValueAndType(context: DatabindContext, value: Any?, suggestedType: Class<*>): String =
        typeToId[suggestedType]
            ?: throw IllegalArgumentException("No type id mapping for ${suggestedType.name}")

    override fun typeFromId(context: DatabindContext, id: String): JavaType? =
        idToType[id.uppercase()]

    override fun getMechanism(): JsonTypeInfo.Id = JsonTypeInfo.Id.CUSTOM
}
