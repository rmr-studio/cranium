package cranium.core.configuration.auth

import com.fasterxml.jackson.databind.type.TypeFactory
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema

/**
 * Resolves raw `kotlin.Any` / `java.lang.Object` to an untyped object schema.
 *
 * Without this, springdoc emits nullable bare schemas that the typescript-fetch generator
 * materializes as a `Null` model (e.g. `value?: Null`). Rewriting as `type: object` yields
 * `value?: any` in generated clients.
 */
class AnyTypeModelConverter : ModelConverter {

    override fun resolve(
        type: AnnotatedType,
        context: ModelConverterContext,
        chain: MutableIterator<ModelConverter>,
    ): Schema<*>? {
        val javaType = TypeFactory.defaultInstance().constructType(type.type)
        return if (javaType.rawClass == Any::class.java) {
            ObjectSchema()
        } else if (chain.hasNext()) {
            chain.next().resolve(type, context, chain)
        } else {
            null
        }
    }
}
