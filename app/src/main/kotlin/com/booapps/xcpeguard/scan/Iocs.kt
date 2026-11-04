package com.booapps.xcpeguard.scan

import com.booapps.xcpeguard.R

object Iocs {
    class Pkg(val name: String, val info: Int)

    val MALWARE = listOf(
        Pkg("x.cpe", R.string.info_pkg_xcpe),
        Pkg("com.oemservice", R.string.info_pkg_xcpe),
        Pkg("com.abc.nexus", R.string.info_pkg_nexus),
        Pkg("com.vs.grass", R.string.info_pkg_grass),
        Pkg("com.android.system.ntp", R.string.info_pkg_ntp),
        Pkg("com.android.sso.auth.handler", R.string.info_pkg_sso),
        Pkg("com.ota.service", R.string.info_pkg_ota_service),
        Pkg("com.newtv.cboxtv", R.string.info_pkg_cboxtv),
        Pkg("com.gaganode", R.string.info_pkg_gaganode),
        Pkg("com.gaganode.sdk", R.string.info_pkg_gaganode),
    )

    val SUSPICIOUS = listOf(
        Pkg("com.oem.qa", R.string.info_pkg_oem_qa),
    )

    val PROCESSES = listOf("oemservice", "x.cpe", "abc.nexus", "vs.grass", "system.ntp", "sso.auth.handler",
        "lw_android_agent", "gaganode")

    val LIBS = listOf("libseccomp_guard.so", "libexploit.so", "libnative_keys.so", "libfleetshare.so",
        "libjaguar.so", "libtraceroute.so", "lw_android_agent")

    val PROPS = listOf("persist.networkstack.uninstall", "persist.networkstack.skip_system_install",
        "persist.networkstack.install_time")

    val IPS = setOf("219.134.163.210", "219.134.163.211", "23.94.49.57", "38.145.197.135")
    val MQTT_PORTS = 3885..3887

    val DOMAINS = listOf("mqtt.szbboys.com", "ott.szbboys.com", "mcstb.anyevonline.com", "intermedia-arts.tv",
        "yokatvonline.com", "linkfog.cn", "hiddeninplainsight.top")

    val BAD_SHA256 = setOf(
        "3768f470934a111f1c3e3d098eb1392a3cb6f3e298ef5fd2a6309e6a98ee1e18",
        "350d6db477e8567cf9347c1144b3a0b25550e7ce63bb800b056b8a7a8ef56af4",
        "1f2ab93c35f0c37d9b88f82268183a2f77491aa6b142a48381524b18e217b960",
        "b3690b3b013ac60752a6d05c3f53ea1f3e844d4603c5b90d258a30c596d0691b",
        "ebcca22b96f7a30ae155efa2f25af29a369c6e9cfc99cb3bb0b72f35bc5528ed",
        "42be8f18d6006ca092de191331d38e70a0d8383db701a759466dec59a0986135",
        "7830ce3c3222dd9b25a77a16116dcf832a24b4b9a0bc998501e12415a828fb42",
    )

    const val REPO_URL = "https://github.com/burakztrk/xcpe-guard"

    const val SOURCE_URL =
        "https://cloud.google.com/blog/topics/threat-intelligence/shutting-the-door-on-the-xcpe-botnet"
}
