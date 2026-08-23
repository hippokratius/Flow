/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.utils

import java.util.Locale

/**
 * The countries YouTube serves a regional feed for, by their ISO code, sorted by display name.
 *
 * Lived as a private value inside the settings screen, which meant the one list that decides what a
 * valid region is could not be consulted by the code that resolves one.
 */
val YOUTUBE_REGIONS: Map<String, String> = mapOf(
    "DZ" to "Algeria", "AS" to "American Samoa", "AI" to "Anguilla", "AR" to "Argentina",
    "AW" to "Aruba", "AU" to "Australia", "AT" to "Austria", "AZ" to "Azerbaijan",
    "BH" to "Bahrain", "BD" to "Bangladesh", "BY" to "Belarus", "BE" to "Belgium",
    "BM" to "Bermuda", "BO" to "Bolivia", "BA" to "Bosnia and Herzegovina", "BR" to "Brazil",
    "IO" to "British Indian Ocean Territory", "VG" to "British Virgin Islands", "BG" to "Bulgaria", "KH" to "Cambodia",
    "CA" to "Canada", "KY" to "Cayman Islands", "CL" to "Chile", "CO" to "Colombia",
    "CR" to "Costa Rica", "HR" to "Croatia", "CY" to "Cyprus", "CZ" to "Czech Republic",
    "DK" to "Denmark", "DO" to "Dominican Republic", "EC" to "Ecuador", "EG" to "Egypt",
    "SV" to "El Salvador", "EE" to "Estonia", "FK" to "Falkland Islands", "FO" to "Faroe Islands",
    "FI" to "Finland", "FR" to "France", "GF" to "French Guiana", "PF" to "French Polynesia",
    "GE" to "Georgia", "DE" to "Germany", "GH" to "Ghana", "GI" to "Gibraltar",
    "GR" to "Greece", "GL" to "Greenland", "GP" to "Guadeloupe", "GU" to "Guam",
    "GT" to "Guatemala", "HN" to "Honduras", "HK" to "Hong Kong", "HU" to "Hungary",
    "IS" to "Iceland", "IN" to "India", "ID" to "Indonesia", "IQ" to "Iraq",
    "IE" to "Ireland", "IL" to "Israel", "IT" to "Italy", "JM" to "Jamaica",
    "JP" to "Japan", "JO" to "Jordan", "KZ" to "Kazakhstan", "KE" to "Kenya",
    "KW" to "Kuwait", "LA" to "Laos", "LV" to "Latvia", "LB" to "Lebanon",
    "LY" to "Libya", "LI" to "Liechtenstein", "LT" to "Lithuania", "LU" to "Luxembourg",
    "MY" to "Malaysia", "MT" to "Malta", "MQ" to "Martinique", "YT" to "Mayotte",
    "MX" to "Mexico", "MD" to "Moldova", "ME" to "Montenegro", "MS" to "Montserrat",
    "MA" to "Morocco", "NP" to "Nepal", "NL" to "Netherlands", "NC" to "New Caledonia",
    "NZ" to "New Zealand", "NI" to "Nicaragua", "NG" to "Nigeria", "NF" to "Norfolk Island",
    "MP" to "Northern Mariana Islands", "NO" to "Norway", "OM" to "Oman", "PK" to "Pakistan",
    "PA" to "Panama", "PG" to "Papua New Guinea", "PY" to "Paraguay", "PE" to "Peru",
    "PH" to "Philippines", "PL" to "Poland", "PT" to "Portugal", "PR" to "Puerto Rico",
    "QA" to "Qatar", "RE" to "Reunion", "RO" to "Romania", "RU" to "Russia",
    "SH" to "Saint Helena", "PM" to "Saint Pierre and Miquelon", "SA" to "Saudi Arabia", "SN" to "Senegal",
    "RS" to "Serbia", "SG" to "Singapore", "SK" to "Slovakia", "SI" to "Slovenia",
    "ZA" to "South Africa", "KR" to "South Korea", "ES" to "Spain", "LK" to "Sri Lanka",
    "SJ" to "Svalbard and Jan Mayen", "SE" to "Sweden", "CH" to "Switzerland", "TW" to "Taiwan",
    "TZ" to "Tanzania", "TH" to "Thailand", "TN" to "Tunisia", "TR" to "Turkey",
    "TC" to "Turks and Caicos Islands", "UG" to "Uganda", "UA" to "Ukraine", "AE" to "United Arab Emirates",
    "GB" to "United Kingdom", "US" to "United States", "VI" to "U.S. Virgin Islands", "UY" to "Uruguay",
    "VE" to "Venezuela", "VN" to "Vietnam"
).toList().sortedBy { it.second }.toMap()

/**
 * The region every YouTube request should carry.
 *
 * [stored] is the user's choice and wins whenever it names a region YouTube knows. Absence of a
 * stored value means the user never opened the picker — not that they chose the United States — so
 * the device's own country answers for them, which is what a German phone expects without anyone
 * configuring anything.
 *
 * [deviceCountry] must not come from `Locale.getDefault()`: [AppLanguageManager.wrapContext] sets
 * the process default from a language tag alone, so from the first launch after someone picks an app
 * language, that locale carries no country at all. Read it from `Resources.getSystem()`, which keeps
 * the device's own configuration — see [ContentLocale.deviceRegion].
 */
fun resolveContentRegion(
    stored: String?,
    deviceCountry: String,
    known: Set<String> = YOUTUBE_REGIONS.keys,
): String {
    stored?.asRegionCode()?.takeIf { it in known }?.let { return it }
    deviceCountry.asRegionCode()?.takeIf { it in known }?.let { return it }
    return FALLBACK_REGION
}

/** The region of last resort, for a device whose country YouTube has no feed for. */
const val FALLBACK_REGION = "US"

private fun String.asRegionCode(): String? =
    trim().uppercase(Locale.US).takeIf { it.matches(REGION_CODE) }

private val REGION_CODE = Regex("[A-Z]{2}")
