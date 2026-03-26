package riven.core.configuration.util

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase
import com.fasterxml.jackson.databind.type.TypeFactory

/**
 * Jackson [com.fasterxml.jackson.databind.jsontype.TypeIdResolver] that performs
 * case-insensitive type-id lookups.
 *
 * Reads the [JsonSubTypes] annotation from the base type during [init] to build:
 * - An uppercase-keyed map for case-insensitive deserialization
 * - A class-to-canonical-name map for deterministic serialization
 *
 * This allows the frontend to send any casing (e.g. `"Or"`, `"or"`, `"OR"`)
 * while the backend always serializes the canonical name defined in [JsonSubTypes].
 */
class CaseInsensitiveTypeIdResolver : TypeIdResolverBase() {

    private var idToType: Map<String, JavaType> = emptyMap()
    private var typeToId: Map<Class<*>, String> = emptyMap()

    override fun init(baseType: JavaType) {
        super.init(baseType)
        buildMaps(baseType.rawClass, baseType)
    }

    private fun buildMaps(baseClass: Class<*>, baseType: JavaType) {
        val subtypes = baseClass.getAnnotation(JsonSubTypes::class.java) ?: return

        val typeFactory = TypeFactory.defaultInstance()

        idToType = subtypes.value.associate { sub ->
            sub.name.uppercase() to typeFactory.constructSpecializedType(baseType, sub.value.java)
        }

        typeToId = subtypes.value.associate { sub ->
            sub.value.java to sub.name
        }
    }

    override fun idFromValue(value: Any): String =
        typeToId[value::class.java]
            ?: throw IllegalArgumentException("No type id mapping for ${value::class.java.name}")

    override fun idFromValueAndType(value: Any?, suggestedType: Class<*>): String =
        typeToId[suggestedType]
            ?: throw IllegalArgumentException("No type id mapping for ${suggestedType.name}")

    override fun typeFromId(context: DatabindContext, id: String): JavaType? =
        idToType[id.uppercase()]

    override fun getMechanism(): JsonTypeInfo.Id = JsonTypeInfo.Id.CUSTOM
}
