# PicoFinder launcher icon

The user supplied a 512 × 512 chat preview: a pale rounded square with three concentric cyan/blue discs.
No original downloadable bitmap was available. The user explicitly chose a vector adaptation of that design.
The shipped asset preserves the composition, gradient palette and approximate 194:128:48 radius ratios;
it does not claim pixel-for-pixel extraction from the unavailable original PNG.

Android resources:

- `drawable/ic_launcher_foreground.xml`: 108 dp vector, centered at (54,54), disc radii 32 / 21.11 / 7.92 dp.
- `drawable/ic_launcher_background.xml`: pale opaque gradient, leaving the launcher to apply its mask.
- `drawable/ic_launcher_monochrome.xml`: same geometry at three alpha levels for API33+ themed icons.
- `mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml`: adaptive icons using these layers.

The outer disc fits inside the 66 dp adaptive safe zone. Vectors scale across densities; minSdk 31 already
supports adaptive icons, so obsolete pre-26 fallbacks are unnecessary. Both manifest icon references are set.
`docs/assets/picofinder.svg` is a small README logo using the same original composition and colours.

`LauncherIconTest` renders the actual Android drawable layers with circle, rounded-square and square masks
on light/dark surroundings and checks the center/alpha. Its saved native previews are software rendering
evidence, not photographs of a physical launcher. Final checks should include the real phone's launcher,
app drawer, recents and API33+ themed icon setting; manufacturers may use different masks and effects.
