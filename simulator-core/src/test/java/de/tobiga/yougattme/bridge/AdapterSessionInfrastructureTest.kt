package de.tobiga.yougattme.bridge

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterSessionInfrastructureTest {
    private val service = bluetoothUuid(0xFFF0)
    private val measurement = bluetoothUuid(0xFFF1)
    private val secondMeasurement = bluetoothUuid(0xFFF2)
    private val control = bluetoothUuid(0xFFF3)
    private val capability = bluetoothUuid(0xFFF4)

    @Test
    fun `registry ranks exact fingerprints ahead of possible fingerprints`() {
        val possible = FakeAdapter("possible", InputMatchConfidence.POSSIBLE)
        val exact = FakeAdapter("exact", InputMatchConfidence.EXACT)
        val registry = InputAdapterRegistry(listOf(provider(possible, exact)))

        assertEquals(
            listOf("exact", "possible"),
            registry.candidates(discovery()).map { it.first.adapterId }
        )
    }

    @Test
    fun `selection requires a manual choice when multiple exact candidates validate`() {
        val first = FakeAdapter("first", InputMatchConfidence.EXACT)
        val second = FakeAdapter("second", InputMatchConfidence.EXACT)

        val result = selectInputAdapters(
            setOf(service),
            listOf(
                first to first.match(discovery()),
                second to second.match(discovery())
            )
        )

        assertTrue(result is InputAdapterSelection.Ambiguous)
        assertEquals(
            setOf("first", "second"),
            (result as InputAdapterSelection.Ambiguous).adapters.mapTo(linkedSetOf()) { it.adapterId }
        )
    }

    @Test
    fun `compatible override wins and incompatible override is actionable`() {
        val possible = FakeAdapter("possible", InputMatchConfidence.POSSIBLE)
        val exact = FakeAdapter("exact", InputMatchConfidence.EXACT)
        val candidates = listOf(
            possible to possible.match(discovery()),
            exact to exact.match(discovery())
        )

        val selected = selectInputAdapters(setOf(service), candidates, "possible")
        assertSame(possible, (selected as InputAdapterSelection.Selected).adapters.single())

        assertEquals(
            InputAdapterSelection.IncompatibleOverride("missing"),
            selectInputAdapters(setOf(service), candidates, "missing")
        )
    }

    @Test
    fun `legacy adapter gets a per-device session with service-qualified transactions`() {
        val adapter = FakeAdapter("legacy", InputMatchConfidence.EXACT)
        val session = adapter.openSession(discovery())!!
        val measurementRef = GattCharacteristicRef(service, measurement)

        assertEquals(setOf(measurementRef, GattCharacteristicRef(service, control)),
            session.subscriptionCharacteristics)
        assertEquals(setOf(GattCharacteristicRef(service, capability)), session.readableCharacteristics)
        assertEquals(service, session.initializationTransactions().single().serviceUuid)
        assertEquals(measurementRef, session.endpointDefinitions(emptyMap()).single().primaryMeasurement)
        assertEquals(
            7.0,
            session.decode("endpoint", measurementRef, byteArrayOf(7), 100L)
                ?.metrics?.get(Metric.POWER_WATTS)
        )
        assertNull(session.decode("endpoint", measurementRef, ByteArray(0), 100L))
    }

    @Test
    fun `logical endpoint can consume multiple physical notifications`() {
        val primary = GattCharacteristicRef(service, measurement)
        val secondary = GattCharacteristicRef(service, secondMeasurement)
        val definition = InputEndpointDefinition(
            key = "multiplexed",
            primaryMeasurement = primary,
            measurementCharacteristics = setOf(primary, secondary),
            supportedMetrics = setOf(Metric.SPEED_MPS, Metric.POWER_WATTS),
            protocolId = "test.multi",
            serviceName = "Multiplexed test"
        )

        assertEquals(setOf(primary, secondary), definition.measurementCharacteristics)
    }

    @Test
    fun `active probes carry an explicit safe purpose`() {
        val transaction = AdapterTransaction(control, byteArrayOf(0))
        val plan = InputProbePlan(
            safeTransactions = listOf(SafeProbeTransaction(transaction, SafeProbePurpose.NO_OP))
        )

        assertTrue(plan.transactions.isEmpty())
        assertEquals(SafeProbePurpose.NO_OP, plan.safeTransactions.single().purpose)
    }

    private fun discovery() = InputDeviceDiscovery(
        InputAdvertisement("00:11:22:33:44:55", "Test device", setOf(service)),
        mapOf(
            service to InputGattService(
                service,
                listOf(measurement, secondMeasurement, control, capability).associateWith { uuid ->
                    InputGattCharacteristic(
                        uuid,
                        readable = uuid == capability,
                        writable = uuid == control,
                        writableWithoutResponse = false,
                        notifiable = uuid == measurement || uuid == secondMeasurement,
                        indicatable = false
                    )
                }
            )
        )
    )

    private fun provider(vararg adapters: InputAdapter) = object : InputAdapterProvider {
        override val providerId = "test"
        override fun createAdapters() = adapters.toList()
    }

    private inner class FakeAdapter(
        override val adapterId: String,
        private val confidence: InputMatchConfidence
    ) : InputAdapter {
        override val providerId = adapterId
        override val displayName = adapterId
        override val supportedServices = setOf(service)
        override val measurementCharacteristics = setOf(measurement)
        override val controlPointCharacteristics = setOf(control)
        override val readableCapabilityCharacteristics = setOf(capability)

        override fun match(discovery: InputDeviceDiscovery) =
            InputAdapterMatch(confidence, "test fingerprint")

        override fun serviceName(serviceUuid: UUID) = "Test service"
        override fun supportedMetrics(characteristicUuid: UUID) = setOf(Metric.POWER_WATTS)
        override fun initializationTransactions(serviceUuid: UUID) =
            listOf(AdapterTransaction(control, byteArrayOf(1)))

        override fun decode(
            characteristicUuid: UUID,
            value: ByteArray,
            timestampMillis: Long
        ): DecodedMeasurement? = value.firstOrNull()?.let {
            DecodedMeasurement(
                service = null,
                characteristicUuid = characteristicUuid,
                flags = 0,
                metrics = mapOf(Metric.POWER_WATTS to (it.toInt() and 0xff).toDouble()),
                protocolFields = emptyMap(),
                timestampMillis = timestampMillis,
                protocolId = adapterId
            )
        }
    }
}
