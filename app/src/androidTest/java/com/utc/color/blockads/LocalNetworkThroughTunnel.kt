package com.utc.color.blockads

import com.utc.color.blockads.core.data.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * The host fixture is reached at 10.0.2.2, a private address that the default "bypass local
 * network" setting routes outside the tunnel. Tests that measure tunnelled traffic need it inside.
 * Left off afterwards so later test classes do not trigger a tunnel re-establish mid-test.
 */
class LocalNetworkThroughTunnel : ExternalResource() {
    override fun before() = runBlocking { SettingsRepository(targetContext).setBypassLan(false) }
}
