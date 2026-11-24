package com.utc.color.blockads.core.enginebridge

import com.utc.color.blockads.core.common.R as L10n
import com.utc.color.blockads.core.common.localizedError
import io.github.tothanggtvt.openadblock.core.AdBlockEngine
import io.github.tothanggtvt.openadblock.core.CertificateSigner
import io.github.tothanggtvt.openadblock.core.EngineEventListener
import io.github.tothanggtvt.openadblock.core.NativeEngine

/** Host-side callback types come from the core's Java API (open-adblock-core/bindings/java). */
typealias SocketProtector = io.github.tothanggtvt.openadblock.core.SocketProtector

typealias DnsEventSink = EngineEventListener

typealias RootCaSigner = CertificateSigner

/**
 * The app's view of the native core: Android defaults and localized errors on top of
 * [AdBlockEngine]. All engine logic lives in open-adblock-core; keep this class thin.
 */
class EngineBridge(private val core: AdBlockEngine = NativeEngine.get()) {
    fun supportsHttps(): Boolean = core.apiVersion() >= 1

    fun generateCa(signer: RootCaSigner, publicPoint: ByteArray): ByteArray =
        core.generateRootCa(signer, publicPoint) ?: error(localizedError(L10n.string.ca_create_failed))

    /** A null [signer] removes the root CA. */
    fun setCa(
        signer: RootCaSigner?,
        publicPoint: ByteArray = byteArrayOf(),
        certificate: ByteArray = byteArrayOf(),
    ): Boolean =
        if (signer == null) {
            core.clearRootCa()
            true
        } else {
            core.setRootCa(signer, publicPoint, certificate)
        }

    fun configureHttps(json: String): Boolean = core.configureHttps(json)

    fun configurePolicy(json: String): Boolean = core.configurePolicy(json)

    /** DNS lists feed only the domain matcher; browser lists only decrypted HTTPS. */
    fun replaceRules(dns: String, browser: String, custom: String, apply: Boolean = true): String =
        core.replaceRules(dns, browser, custom, apply)

    fun configureDns(json: String): Boolean = core.configureDns(json)

    fun setDnsEventSink(sink: DnsEventSink?) = core.setEventListener(sink)

    fun setRequestCapture(uid: Int, session: Long, minutes: Int) =
        core.setRequestCapture(uid, session, minutes)

    /**
     * Installs AdGuard Scriptlets `scriptlets.corelibs.json` / `redirects.json` (either may be
     * empty). Returns `{"scriptlets":n,"redirects":n,"skipped":n}` or `{"error":"..."}`; on error
     * the previous set stays active.
     */
    fun setResources(scriptlets: String, redirects: String): String =
        core.setResources(scriptlets, redirects) ?: error(localizedError(L10n.string.resources_load_failed))

    fun version(): String = core.version()

    /** Consumes a detached TUN fd, even when startup returns false. */
    fun start(fd: Int, protector: SocketProtector, dns: String): Boolean = core.start(fd, protector, dns)

    fun stop() = core.stop()

    fun updateNetwork(dns: String): Boolean = core.updateNetwork(dns)

    fun isRunning(): Boolean = core.isRunning()

    /** See [io.github.tothanggtvt.openadblock.core.TrafficCounters] for the order. */
    fun counters(): LongArray = core.counters().toArray()
}
