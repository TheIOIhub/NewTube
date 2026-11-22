package com.example.newtube.account

/** The Fire TV Cobalt user agent proven to produce TV OAuth codes (2026-11-09). */
internal object YouTubeTvUserAgent {
    const val VALUE: String =
        "Mozilla/5.0 (Linux armeabi-v7a; Android 7.1.2; Fire OS 6.0) " +
            "Cobalt/22.lts.3.306369-gold (unlike Gecko) v8/8.8.278.8-jit gles Starboard/13, " +
            "Amazon_ATV_mediatek8695_2019/NS6294 (Amazon, AFTMM, Wireless) " +
            "com.amazon.firetv.youtube/22.3.r2.v66.0"
}
