## 2024-10-04 - TypefaceUtils: Paint.measureText vs getTextBounds
**Learning:** Measuring text width using `Paint.getTextBounds` coupled with a synchronized global `Rect` instance causes unnecessary thread contention and overhead.
**Action:** Use `Paint.measureText()` for advance width calculations which is faster, does not evaluate bounding boxes, and is thread-safe without requiring locks on global objects.
