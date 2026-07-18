package de.tobiga.yougattme.bridge

/** Result of one asynchronous upstream Control Point procedure. */
data class RemoteControlResult(val success: Boolean, val response: ByteArray?)

fun interface RemoteControlTransport {
    fun execute(outputKey: String, request: ByteArray, calibration: Boolean,
        completion: (RemoteControlResult) -> Unit)
}

fun interface SemanticRemoteControlTransport {
    fun execute(outputKey: String, command: ControlCommand, completion: (ControlResult) -> Unit)
}

/**
 * Decouples public GATT profiles from the Android central implementation. A future private input
 * adapter can install the same transport without introducing proprietary code into simulator-core.
 */
object RemoteControlDispatcher {
    @Volatile var transport: RemoteControlTransport? = null
    @Volatile var semanticTransport: SemanticRemoteControlTransport? = null

    fun forwardIfConfigured(
        outputKey: String,
        request: ByteArray,
        calibration: Boolean = false,
        completion: (RemoteControlResult) -> Unit
    ): Boolean {
        if (BridgeStateStore.shared.controlRoutes.value.orEmpty()[outputKey] !is ControlRoute.Remote) return false
        val active = transport
        if (active == null) completion(RemoteControlResult(false, null))
        else active.execute(outputKey, request.copyOf(), calibration, completion)
        return true
    }

    fun forwardSemanticIfConfigured(
        outputKey: String,
        command: ControlCommand,
        sameProtocolId: String? = null,
        completion: (ControlResult) -> Unit
    ): Boolean {
        val route = BridgeStateStore.shared.controlRoutes.value.orEmpty()[outputKey] as? ControlRoute.Remote
            ?: return false
        if (sameProtocolId != null && BridgeStateStore.shared.endpoint(route.endpointId)?.protocolId == sameProtocolId) {
            return false
        }
        val active = semanticTransport
        if (active == null) completion(ControlResult(ControlStatus.FAILED))
        else active.execute(outputKey, command, completion)
        return true
    }
}
