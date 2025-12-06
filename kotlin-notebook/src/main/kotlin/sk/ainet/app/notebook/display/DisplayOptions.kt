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
    /** Toggle simple border styling when true. */
    var border: Boolean = false,
    /** Optional CSS class name to apply to the rendered element. */
    var cssClass: String? = null,
)
