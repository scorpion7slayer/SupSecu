package be.supsecu.app.reputation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmedThreatCatalogTest {
    @Test
    fun recognizesReportedLidlFraudWithoutBrandInHost() {
        val match = ConfirmedThreatCatalog.lookup("xcoemruf.shop")

        assertEquals("xcoemruf.shop", match?.matchedDomain)
        assertEquals("lidl", match?.brandId)
    }

    @Test
    fun recognizesSubdomainsButNotUnrelatedHosts() {
        assertEquals("lidl", ConfirmedThreatCatalog.lookup("checkout.xcoemruf.shop")?.brandId)
        assertNull(ConfirmedThreatCatalog.lookup("xcoemruf.shop.example.com"))
        assertNull(ConfirmedThreatCatalog.lookup("unrelated.example"))
    }
}
