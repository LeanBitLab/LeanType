### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.1-beta1

---

### 🐛 Fixed
- **Samsung & Multi-Profile IME Crash Resolution**: Prevented fatal `RuntimeException` on Samsung devices (such as Galaxy S25 Ultra) and isolated environments (Knox, Secure Folder, Work Profiles, Direct Boot) where system `inputMethodList` excludes third-party IMEs, by replacing rigid equality checks with direct package matching and resilient `PackageManager` fallbacks.
- **InputConnection IPC Hardening & Memory Leak Prevention**: Hardened `RichInputConnection` against dead-process and remote app IPC crashes, safely closed unclosed dictionary and asset input streams, and eliminated memory leak vectors.
- **Synchronous Blacklist Enforcement**: Synchronously loads and reloads the blocked words blacklist to eliminate race conditions where blocked words could temporarily appear in suggestions, and resolved deletion persistence issues.
- **N-Gram Context Hash Collision Fix**: Corrected hash collisions in `NgramContext` to ensure accurate next-word context lookups across multi-word prediction pipelines.

### ✨ Added
- **Clear Learned Words & User History**: Added a dedicated option in Text Correction / Suggestions settings to reset learned words, personalized dictionary history, and session word boost caches.

### ⚡ Performance
- **Draw-Loop IPC Elimination & Zero Key Allocations**: Removed Binder IPC queries from active keyboard canvas draw routines, eliminated temporary object allocations per key draw, and optimized gesture trail invalidation for smooth 60fps rendering.

### 🧹 Maintenance
- **Plugin Update Deduplication & Packaging**: Consolidated plugin update checks into a centralized `PluginUpdateHelper` and hardened build scripts for F-Droid reproducibility.
- **Resource & Build Hygiene**: Pruned orphaned translation strings, removed redundant ProGuard rules, and removed unused legacy split keyboard configuration references (#751).

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.1-standard-debug.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.1-offline-debug.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
