package com.utc.color.blockads.core.common

import org.json.JSONArray
import org.json.JSONObject

/**
 * GoodbyeDPI-style circumvention, applied by the native engine (`nativecore/src/dpi.rs`).
 * Each field names the GoodbyeDPI flag it mirrors.
 */
data class DpiSettings(
    val enabled: Boolean = false,
    /** `-f`; 0 is off. Also the keep-alive split (`-k`). */
    val httpSplit: Int = 2,
    /** `-e`; 0 is off. */
    val httpsSplit: Int = 2,
    /** `--frag-by-sni`, cutting inside the hostname. */
    val splitAtHost: Boolean = true,
    /** `-r` */
    val hostReplace: Boolean = true,
    /** `-s` */
    val hostRemoveSpace: Boolean = true,
    /** `-m` */
    val hostMixCase: Boolean = false,
    /** `-a` */
    val methodSpace: Boolean = false,
    /** `-k` */
    val keepAlive: Boolean = true,
    /** `--reverse-frag` */
    val disorder: Boolean = false,
    /** `--set-ttl`: decoy request with [fakeTtl]. */
    val fake: Boolean = false,
    val fakeTtl: Int = 5,
    /** `-q` */
    val blockQuic: Boolean = true,
    /** `--blacklist`; empty applies to every site. */
    val blacklist: List<String> = emptyList(),
    /** `--allow-no-sni` */
    val allowNoSni: Boolean = false,
) {
    /** The preset these settings match, or [DpiMode.Custom]. */
    val mode: DpiMode
        get() = DpiMode.entries.firstOrNull { it != DpiMode.Custom && it.apply(this) == this } ?: DpiMode.Custom

    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("httpSplit", httpSplit)
        .put("httpsSplit", httpsSplit)
        .put("splitAtHost", splitAtHost)
        .put("hostReplace", hostReplace)
        .put("hostRemoveSpace", hostRemoveSpace)
        .put("hostMixCase", hostMixCase)
        .put("methodSpace", methodSpace)
        .put("keepAlive", keepAlive)
        .put("disorder", disorder)
        .put("fake", fake)
        .put("fakeTtl", fakeTtl)
        .put("blockQuic", blockQuic)
        .put("blacklist", JSONArray(blacklist))
        .put("allowNoSni", allowNoSni)

    fun validated(): DpiSettings {
        require(httpSplit in SPLITS.first()..SPLITS.last() && httpsSplit in SPLITS.first()..SPLITS.last())
        require(fakeTtl in TTLS.first()..TTLS.last())
        require(blacklist.size <= MAX_SITES && blacklist.all(DOMAIN::matches)) {
            localizedError(R.string.dpi_sites_invalid)
        }
        return this
    }

    companion object {
        const val MAX_SITES = 10_000
        val SPLITS = listOf(0, 1, 2, 3, 4, 40)
        val TTLS = (1..12).toList()
        private val DOMAIN = Regex("[a-z0-9_-]+(\\.[a-z0-9_-]+)*")

        fun fromJson(text: String?): DpiSettings {
            val json = runCatching { JSONObject(text ?: "{}") }.getOrDefault(JSONObject())
            val d = DpiSettings()
            val sites = json.optJSONArray("blacklist")
            return DpiSettings(
                enabled = json.optBoolean("enabled", d.enabled),
                httpSplit = json.optInt("httpSplit", d.httpSplit).coerceIn(0, SPLITS.last()),
                httpsSplit = json.optInt("httpsSplit", d.httpsSplit).coerceIn(0, SPLITS.last()),
                splitAtHost = json.optBoolean("splitAtHost", d.splitAtHost),
                hostReplace = json.optBoolean("hostReplace", d.hostReplace),
                hostRemoveSpace = json.optBoolean("hostRemoveSpace", d.hostRemoveSpace),
                hostMixCase = json.optBoolean("hostMixCase", d.hostMixCase),
                methodSpace = json.optBoolean("methodSpace", d.methodSpace),
                keepAlive = json.optBoolean("keepAlive", d.keepAlive),
                disorder = json.optBoolean("disorder", d.disorder),
                fake = json.optBoolean("fake", d.fake),
                fakeTtl = json.optInt("fakeTtl", d.fakeTtl).coerceIn(TTLS.first(), TTLS.last()),
                blockQuic = json.optBoolean("blockQuic", d.blockQuic),
                blacklist = sites?.let { array -> (0 until array.length()).map { array.optString(it) } }
                    ?.filter(DOMAIN::matches)?.take(MAX_SITES).orEmpty(),
                allowNoSni = json.optBoolean("allowNoSni", d.allowNoSni),
            )
        }

        /** One domain per line or separated by spaces/commas; `*.` and leading dots are dropped. */
        fun parseSites(text: String): List<String> {
            val sites = text.split(Regex("[\\s,;]+"))
                .map { it.trim().lowercase().removePrefix("*.").trim('.') }
                .filter { it.isNotEmpty() }
                .distinct()
            require(sites.size <= MAX_SITES && sites.all(DOMAIN::matches)) {
                localizedError(R.string.dpi_sites_invalid)
            }
            return sites
        }
    }
}

/**
 * Two of GoodbyeDPI's presets, kept few on purpose; everything else is [Custom]. Both also block
 * QUIC (`-q`), since Android apps would otherwise skip TCP, where the tricks apply.
 */
enum class DpiMode {
    /** GoodbyeDPI `-1`: -r -s -f 2 -k 2 -e 2, the most compatible. */
    Basic,
    /** GoodbyeDPI `-9`: -f 2 -e 2, fake requests, --reverse-frag, -q. Its wrong checksum and
     * sequence fakes need raw sockets, so this uses the low-TTL decoy instead. */
    Strong,
    Custom;

    fun apply(s: DpiSettings): DpiSettings = when (this) {
        Basic -> s.preset(r = true, f = 2, k = true, e = 2)
        Strong -> s.preset(r = false, f = 2, k = false, e = 2, fake = true)
        Custom -> s
    }
}

private fun DpiSettings.preset(r: Boolean, f: Int, k: Boolean, e: Int, fake: Boolean = false) = copy(
    hostReplace = r, hostRemoveSpace = r, hostMixCase = false, methodSpace = false,
    httpSplit = f, keepAlive = k, httpsSplit = e, disorder = fake, fake = fake, blockQuic = true,
)

val DpiMode.label: Int
    get() = when (this) {
        DpiMode.Basic -> R.string.dpi_mode_basic
        DpiMode.Strong -> R.string.dpi_mode_strong
        DpiMode.Custom -> R.string.dpi_mode_custom
    }
