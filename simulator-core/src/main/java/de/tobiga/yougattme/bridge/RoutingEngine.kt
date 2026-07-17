package de.tobiga.yougattme.bridge

class RoutingEngine {
    fun resolve(
        route: ServiceRoute,
        requiredMetrics: Set<Metric>,
        samples: Map<String, Map<Metric, MetricSample>>,
        nowMillis: Long
    ): ResolvedRoute {
        val values = linkedMapOf<Metric, MetricSample>()
        val manual = linkedSetOf<Metric>()
        val missing = linkedSetOf<Metric>()
        val stale = linkedSetOf<Metric>()

        requiredMetrics.forEach { metric ->
            val configured = route.fieldOverrides[metric] ?: RouteSource.Inherit
            val source = if (configured == RouteSource.Inherit) route.defaultSource else configured
            when (source) {
                RouteSource.Manual, RouteSource.Inherit -> manual += metric
                is RouteSource.Endpoint -> {
                    val sample = samples[source.endpointId]?.get(metric)
                    when {
                        sample == null -> missing += metric
                        !sample.valid -> { stale += metric; values[metric] = sample }
                        !sample.isFresh(nowMillis) -> { stale += metric; values[metric] = sample }
                        else -> values[metric] = sample
                    }
                }
            }
        }

        val freshness = when {
            missing.isNotEmpty() -> RouteFreshness.INCOMPLETE
            stale.isNotEmpty() -> RouteFreshness.STALE
            else -> RouteFreshness.FRESH
        }
        return ResolvedRoute(route.outputKey, freshness, values, manual, missing, stale)
    }
}
