## 2024-05-15 - Migrate SponsorDialog to PreferenceDialog
**Learning:** Legacy UI builder paradigms (like using a raw Compose `Dialog` wrapper around `Card` + `Column`) often duplicate layout boilerplate that the `PreferenceDialog` standard component encapsulates perfectly.
**Action:** When discovering custom standalone dialog implementations without standard scaffolding, prefer wrapping their content in `PreferenceDialog` (using the `buttons` and `content` slots) to enforce consistent margin, shape, and background settings uniformly across the settings screens.
