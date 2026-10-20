package com.nekolaska.calabiyau.core.network

/** Capability callbacks only affect the current default network, never a replaced one. */
internal class DefaultNetworkState<N> {
    private var current: N? = null
    private var validated = false
    @Synchronized fun available(network: N): Boolean {
        if (current != network) { current = network; validated = false }
        return validated
    }
    @Synchronized fun capabilities(network: N, internet: Boolean): Boolean? {
        if (network != current) return null
        validated = internet
        return validated
    }
    @Synchronized fun lost(network: N): Boolean? {
        if (network != current) return null
        current = null; validated = false
        return false
    }
}
