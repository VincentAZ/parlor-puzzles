> **Historical record:** this document predates the [fully free edition](FREE_EDITION.md). Pro, ads, billing and reward limits described below no longer apply.

# Locale formatting and right-to-left layouts

The launch copy remains English. The interface follows Android's layout direction,
while puzzle coordinates keep the same physical meaning in every locale. Column
one stays at the left, positive horizontal movement goes right, the Traffic Jam
exit stays on the right, and the named river banks stay on their named sides.

`BoardCoordinates` applies left-to-right layout only to spatial board content:
Traffic Jam pieces, shared planning grids, Switch Grid cells, Hanoi pegs, jugs,
triangular peg holes and crossing banks. The surrounding navigation, controls and
overview placement still inherit the interface direction. Canvas overviews,
image crops, row/column descriptions, model coordinates and physical drags now
agree. This does not change an engine, stored move, selected ID or saved viewport.

The shared `DisplayFormats` helper obtains the locale from Compose's current
resources, the same source as `stringResource`. It remembers number and date
formatters per locale, including a decimal style for localized date digits.
Changes cover board labels, Traffic Jam destination numbers, fractional progress,
daily dates, reward offers, backup/recovery timestamps and counts, and reminder
times. Reminder display uses Android's preferred clock pattern and the user's
12/24-hour setting. Stored dates remain ISO strings; counts, IDs and coordinates
remain canonical data. Numeric counter totals are formatted alongside the current
count, including stars, onboarding steps and the daily rewarded-ad limit.

Typography uses the text's content direction. English fallback sentences retain
their punctuation order in an RTL interface; the layout direction of the containing
control does not force the paragraph direction. Home's explicit content-direction
styles remain compatible with the shared theme.

Installed locale-change checks also exposed an existing results-navigation error:
resuming from Home left Home underneath Play, so “Back to levels” returned there.
Results now request their destination explicitly: the completed campaign's
collection or the Daily Challenge screen. The library consumes this saved request
once, retains its resulting navigation state through recreation, and allows normal
back navigation afterward. Results ad/upsell handling still wraps the navigation
callback. Leaving an unfinished attempt continues to use its existing back stack.

Compose dialogs can install their own configuration local while inheriting
resource overrides. Reading the locale from the current resources keeps formatted
dates consistent with the dialog's resource strings. AndroidX documents that
`LocalResources` invalidates its readers on configuration changes in its
[composition-local implementation](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidCompositionLocals.android.kt).

## Verification and limits

The evidence packet records native regression runs,
screenshots, build hashes and installed-app checks. The tests exercise actual
pointer drags and puzzle-engine transitions, compact 320 × 640 dp layouts at 200%
font size, all twelve Expert renderer families, overview/pan behavior, resource
locales different from the process default, live resource-locale changes, and
Arabic/German numbers and dates. Clock tests restore the device's original setting.

Automated resource-locale fixtures do not establish translated-copy quality or
human TalkBack usability. Physical phone/tablet/foldable checks, native speakers'
review of mixed-direction text, and human completion tests remain open. There is
no additional translated resource catalog or language selector in this increment.
