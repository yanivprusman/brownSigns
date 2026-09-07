package com.automatelinux.brownSigns.geo

/** "5997" → "5,997". Compose's common source set has no locale number formatter. */
fun groupDigits(value: Int): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    val out = StringBuilder(digits.length + digits.length / 3)
    val lead = digits.length % 3
    if (lead > 0) out.append(digits, 0, lead)
    var i = lead
    while (i < digits.length) {
        if (out.isNotEmpty()) out.append(',')
        out.append(digits, i, i + 3)
        i += 3
    }
    return out.toString()
}
