## 2024-10-24 - Dynamic drawable lookups
**Learning:** Drawables can be referenced dynamically by name through `Settings.customIconIds` and `context.resources.getIdentifier` as seen in `custom_icon_names`.
**Action:** Never delete drawables (especially `ic_*` icons) without explicitly checking if they are intended for dynamic or user-customizable icon settings.
