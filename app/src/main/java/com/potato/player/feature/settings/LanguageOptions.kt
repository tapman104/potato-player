package com.potato.player.feature.settings

import java.util.Locale

/**
 * ISO 639-2/B codes supported by MPV, sorted alphabetically by display name.
 * "off" is a sentinel value meaning "no preference" — MPV ignores it and
 * falls back to the first available track.
 */
val LANGUAGE_OPTIONS: List<Pair<String, String>> by lazy {
    val iso639Codes = listOf(
        "off",
        "afr", "aka", "amh", "ara", "arg", "asm", "ava", "ave", "aym", "aze",
        "bak", "bam", "bel", "ben", "bih", "bis", "bod", "bos", "bre", "bul",
        "cat", "ces", "cha", "che", "chu", "chv", "cor", "cos", "cre", "cym",
        "dan", "deu", "div", "dzo",
        "ell", "eng", "epo", "est", "eus",
        "fao", "fas", "fij", "fin", "fra", "fry",
        "ful",
        "gla", "gle", "glg", "glv", "grn", "guj",
        "hat", "hau", "heb", "her", "hin", "hmo", "hrv", "hun", "hye",
        "ibo", "ido", "iii", "iku", "ile", "ina", "ind", "ipk", "isl",
        "jav", "jpn",
        "kal", "kan", "kas", "kat", "kau", "kaz", "khm", "kik", "kin", "kir", "kom", "kon", "kor", "kua", "kur",
        "lao", "lat", "lav", "lim", "lin", "lit", "lub", "lug",
        "mah", "mal", "mar", "mkd", "mlg", "mlt", "mon", "mri", "msa", "mya",
        "nau", "nav", "nbl", "nde", "ndo", "nep", "nld", "nno", "nob", "nor", "nya",
        "oci", "oji", "ori", "orm", "oss",
        "pan", "pli", "pol", "por", "pus",
        "que",
        "roh", "ron", "run", "rus",
        "sag", "san", "sin", "slk", "slv", "sme", "smo", "sna", "snd", "som", "sot", "spa", "sqi", "srd", "srp", "ssw", "sun", "swa", "swe",
        "tah", "tam", "tat", "tel", "tgk", "tgl", "tha", "tir", "ton", "tsn", "tso", "tuk", "tur", "twi",
        "uig", "ukr", "urd", "uzb",
        "ven", "vie", "vol",
        "wln", "wol",
        "xho",
        "yid", "yor",
        "zha", "zho", "zul"
    )

    val noneEntry = "off" to "None"
    val rest = iso639Codes
        .filter { it != "off" }
        .map { code ->
            // ISO 639-2 is 3-letter; Locale accepts 2-letter (639-1).
            // We keep the 3-letter code for MPV but derive the display name
            // by constructing a Locale from the code directly — Android handles
            // the mapping internally for most codes.
            val displayName = Locale(code).getDisplayLanguage(Locale.getDefault())
                .replaceFirstChar { it.uppercaseChar() }
                .takeIf { it.isNotBlank() && it != code } ?: code
            code to displayName
        }
        .sortedBy { it.second }

    listOf(noneEntry) + rest
}

/** Resolve a stored code to a display label for the settings list item. */
fun displayLabelForCode(code: String): String =
    LANGUAGE_OPTIONS.firstOrNull { it.first == code }?.second ?: code
