package riven.core.entity.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import riven.core.models.catalog.ConnotationSignals
import riven.core.service.util.factory.catalog.CatalogFactory

class CatalogEntityTypeEntityTest {

    @Test
    fun `toModel propagates connotation signals when present`() {
        val signals: ConnotationSignals = CatalogFactory.connotationSignals()
        val entity = CatalogFactory.catalogEntityTypeEntityWithSignals(signals = signals)

        val model = entity.toModel(semanticMetadata = emptyList())

        assertEquals(signals, model.connotationSignals)
    }

    @Test
    fun `toModel returns null connotation signals when column is null`() {
        val entity = CatalogFactory.catalogEntityTypeEntityWithSignals(signals = null)

        val model = entity.toModel(semanticMetadata = emptyList())

        assertNull(model.connotationSignals)
    }
}
