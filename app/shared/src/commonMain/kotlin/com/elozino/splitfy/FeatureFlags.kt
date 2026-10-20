package com.elozino.splitfy

/**
 * Compile-time switches for features that exist in the code but are not ready to
 * ship. Each must match the server's `features.*` config: a screen shown for a
 * disabled feature would only ever see 404s.
 */
object FeatureFlags {

    /**
     * The in-app wallet: balance, top-up, withdrawal, payment methods, peer
     * transfers, scan-to-pay and paid plans. Off until a payment provider backs it.
     * With it off, members still settle up by recording payments made outside the
     * app.
     */
    const val WALLET: Boolean = false
}
