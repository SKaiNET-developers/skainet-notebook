package sk.ainet.app.notebook.display

/**
 * Foundational display options for image rendering in Kotlin Notebooks.
 *
 * Note: Core rendering and HTML emission will be implemented in follow-up tasks
 * (see tasks.md sections 2–3). This class exists now to establish the package
 * and coding conventions for upcoming work.
 */
data class DisplayOptions(
    /** Desired CSS pixel width. When null, width attribute is omitted. */
    var width: Int? = null,
    /** Desired CSS pixel height. When null, height attribute is omitted. */
    var height: Int? = null,
    /** Alternative text for accessibility. */
    var alt: String? = null,
    /**
     * Toggle simple border styling when true.
     *
     * Implementation detail and precedence:
     * - When true, an inline style attribute is emitted on the <img> element: border:1px solid #ccc;
     * - Inline styles take precedence over CSS classes provided via [cssClass].
     * - If you want to fully control border via classes, leave [border] = false and style via [cssClass].
     */
    var border: Boolean = false,
    /**
     * Optional CSS class name to apply to the rendered element.
     *
     * Notes:
     * - The class attribute is omitted when null or blank.
     * - Width/height are emitted as HTML attributes when provided; they are omitted when null.
     * - If both [cssClass] rules and the inline [border] style target the same property, the inline
     *   style wins per CSS specificity rules.
     */
    var cssClass: String? = null,
)
