package eu.kanade.tachiyomi.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

const val PREF_DOH_CLOUDFLARE = 1
const val PREF_DOH_GOOGLE = 2
const val PREF_DOH_ADGUARD = 3
const val PREF_DOH_QUAD9 = 4
const val PREF_DOH_ALIDNS = 5
const val PREF_DOH_DNSPOD = 6
const val PREF_DOH_360 = 7
const val PREF_DOH_QUAD101 = 8
const val PREF_DOH_MULLVAD = 9
const val PREF_DOH_CONTROLD = 10
const val PREF_DOH_NJALLA = 11
const val PREF_DOH_SHECAN = 12

data class DohProvider(
    val id: Int,
    val url: String,
    val bootstrapIps: List<String>,
)

val dohProviders = listOf(
    DohProvider(
        id = PREF_DOH_CLOUDFLARE,
        url = "https://cloudflare-dns.com/dns-query",
        bootstrapIps = listOf(
            "162.159.36.1",
            "162.159.46.1",
            "1.1.1.1",
            "1.0.0.1",
            "162.159.132.53",
            "2606:4700:4700::1111",
            "2606:4700:4700::1001",
            "2606:4700:4700::0064",
            "2606:4700:4700::6400",
        ),
    ),
    DohProvider(
        id = PREF_DOH_GOOGLE,
        url = "https://dns.google/dns-query",
        bootstrapIps = listOf(
            "8.8.4.4",
            "8.8.8.8",
            "2001:4860:4860::8888",
            "2001:4860:4860::8844",
        ),
    ),
    DohProvider(
        id = PREF_DOH_ADGUARD,
        url = "https://dns-unfiltered.adguard.com/dns-query",
        bootstrapIps = listOf(
            "94.140.14.140",
            "94.140.14.141",
            "2a10:50c0::1:ff",
            "2a10:50c0::2:ff",
        ),
    ),
    DohProvider(
        id = PREF_DOH_QUAD9,
        url = "https://dns.quad9.net/dns-query",
        bootstrapIps = listOf(
            "9.9.9.9",
            "149.112.112.112",
            "2620:fe::fe",
            "2620:fe::9",
        ),
    ),
    DohProvider(
        id = PREF_DOH_ALIDNS,
        url = "https://dns.alidns.com/dns-query",
        bootstrapIps = listOf(
            "223.5.5.5",
            "223.6.6.6",
            "2400:3200::1",
            "2400:3200:baba::1",
        ),
    ),
    DohProvider(
        id = PREF_DOH_DNSPOD,
        url = "https://doh.pub/dns-query",
        bootstrapIps = listOf(
            "1.12.12.12",
            "120.53.53.53",
        ),
    ),
    DohProvider(
        id = PREF_DOH_360,
        url = "https://doh.360.cn/dns-query",
        bootstrapIps = listOf(
            "101.226.4.6",
            "218.30.118.6",
            "123.125.81.6",
            "140.207.198.6",
            "180.163.249.75",
            "101.199.113.208",
            "36.99.170.86",
        ),
    ),
    DohProvider(
        id = PREF_DOH_QUAD101,
        url = "https://dns.twnic.tw/dns-query",
        bootstrapIps = listOf(
            "101.101.101.101",
            "2001:de4::101",
            "2001:de4::102",
        ),
    ),
    DohProvider(
        id = PREF_DOH_MULLVAD,
        url = "https://dns.mullvad.net/dns-query",
        bootstrapIps = listOf(
            "194.242.2.2",
            "2a07:e340::2",
        ),
    ),
    DohProvider(
        id = PREF_DOH_CONTROLD,
        url = "https://freedns.controld.com/p0",
        bootstrapIps = listOf(
            "76.76.2.0",
            "76.76.10.0",
            "2606:1a40::",
            "2606:1a40:1::",
        ),
    ),
    DohProvider(
        id = PREF_DOH_NJALLA,
        url = "https://dns.njal.la/dns-query",
        bootstrapIps = listOf(
            "95.215.19.53",
            "2001:67c:2354:2::53",
        ),
    ),
    DohProvider(
        id = PREF_DOH_SHECAN,
        url = "https://free.shecan.ir/dns-query",
        bootstrapIps = listOf(
            "178.22.122.100",
            "185.51.200.2",
        ),
    ),
)

fun OkHttpClient.Builder.doh(provider: DohProvider) = dns(
    DnsOverHttps.Builder().client(build())
        .url(provider.url.trim().toHttpUrl())
        .bootstrapDnsHosts(provider.bootstrapIps.map { InetAddress.getByName(it) })
        .build(),
)
