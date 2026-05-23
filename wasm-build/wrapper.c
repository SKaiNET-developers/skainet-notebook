/*
 * wrapper.c — the C bridge between the Kotlin / chasm side and Graphviz.
 *
 * Linked at the tail of build-graphviz.sh into graphviz.wasm. Exposes a
 * stable, small ABI (`gv_render`, `gv_free`) so Kotlin doesn't have to chase
 * Graphviz's larger internal API across versions.
 *
 * Status: SKELETON. The function bodies sketch the right Graphviz calls
 * (agread, gvLayout, gvRenderData, agclose, gvFreeLayout) but the actual
 * include paths, engine-name strings, and renderer registration need to be
 * verified once the wasi-sdk Graphviz build actually links. See
 * wasm-build/README.md for the open items.
 *
 * NOTE FOR IDE / LSP USERS: this file is compiled INSIDE the wasi-sdk
 * Docker image with Graphviz's source tree on the include path
 * (`-I /work/build/graphviz-X.X.X/lib/gvc/` etc.). A host clang LSP will
 * flag `gvc.h` / `Agraph_t` / `agmemread` as undeclared — that's expected,
 * those symbols only resolve inside the container. Don't add fake
 * declarations; just trust the Docker build.
 */

#include <stdlib.h>
#include <string.h>

/* Graphviz public headers — paths come from the static libs the linker pulls
 * in. If/when build-graphviz.sh changes the static-lib layout, update these
 * includes accordingly. */
#include "gvc.h"      /* gvContext, gvLayout, gvRenderData, gvFreeLayout, ... */
#include "cgraph.h"   /* agmemread, agclose, Agraph_t */

/* Layout engine ids — must stay in sync with the Kotlin DotEngine enum
 * (DotRender.kt). Adding an engine here without updating the Kotlin side
 * (or vice versa) silently mis-renders. */
static const char *engine_name(int engine_id) {
    switch (engine_id) {
        case 0: return "dot";
        case 1: return "neato";
        case 2: return "twopi";
        case 3: return "circo";
        case 4: return "fdp";
        case 5: return "osage";
        case 6: return "patchwork";
        default: return "dot";
    }
}

/*
 * Render `dot` (UTF-8, length `dot_len` — caller knows the length, the buffer
 * doesn't have to be NUL-terminated) using the given engine. Returns a
 * NUL-terminated UTF-8 SVG string allocated via malloc on the wasm heap;
 * caller frees with gv_free. Returns NULL on failure (out of memory,
 * malformed DOT, layout failure).
 *
 * Marked `__attribute__((visibility("default")))` so wasm-ld picks it up via
 * the `--export` list in build-graphviz.sh.
 */
__attribute__((visibility("default")))
char *gv_render(const char *dot, size_t dot_len, int engine_id) {
    /* agmemread expects a NUL-terminated string. The Kotlin side passes the
     * exact byte length so we can verify it's well-formed before parsing. */
    char *src = (char *)malloc(dot_len + 1);
    if (!src) return NULL;
    memcpy(src, dot, dot_len);
    src[dot_len] = '\0';

    Agraph_t *g = agmemread(src);
    free(src);
    if (!g) return NULL;

    GVC_t *gvc = gvContext();
    if (!gvc) { agclose(g); return NULL; }

    char *out = NULL;
    unsigned int out_len = 0;
    int rc = -1;

    if (gvLayout(gvc, g, engine_name(engine_id)) == 0) {
        rc = gvRenderData(gvc, g, "svg", &out, &out_len);
        gvFreeLayout(gvc, g);
    }

    agclose(g);
    gvFreeContext(gvc);

    if (rc != 0 || !out) {
        if (out) free(out);
        return NULL;
    }

    /* gvRenderData hands back a buffer that the caller owns. The Kotlin side
     * reads bytes up to the first NUL — gvRenderData NUL-terminates SVG
     * output but the contract here makes that promise explicit. */
    return out;
}

__attribute__((visibility("default")))
void gv_free(char *ptr) {
    free(ptr);
}
