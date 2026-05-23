#!/usr/bin/env bash
#
# Build a minimal Graphviz against wasi-sdk and emit dist/graphviz.wasm.
#
# Runs INSIDE the Docker image produced by `make image`. The image supplies
# wasi-sdk, autoconf, automake, libtool, bison, flex, and clang configured
# for `wasm32-wasi`.
#
# Status: SKELETON. Configure invocation, link-time export list, and the
# `wrapper.c` bridge are sketched out; getting Graphviz to actually configure
# clean under wasi-sdk needs iteration we haven't done yet — see TODO blocks
# below and wasm-build/README.md for the open items. When this script
# produces a working dist/graphviz.wasm, copy it to
# kotlin-notebook/src/main/resources/sk/ainet/app/notebook/wasm/graphviz.wasm
# and the GraphvizWasm scaffold flips from "throws NotBundled" to "renders".

set -euo pipefail

GRAPHVIZ_VERSION="${GRAPHVIZ_VERSION:-12.2.1}"
OUTPUT_DIR="${OUTPUT_DIR:-/work/dist}"
BUILD_DIR="${BUILD_DIR:-/work/build}"
SRC_DIR="${BUILD_DIR}/graphviz-${GRAPHVIZ_VERSION}"
SRC_TARBALL="${BUILD_DIR}/graphviz-${GRAPHVIZ_VERSION}.tar.gz"
GRAPHVIZ_URL="https://gitlab.com/api/v4/projects/4207231/packages/generic/graphviz-releases/${GRAPHVIZ_VERSION}/graphviz-${GRAPHVIZ_VERSION}.tar.gz"

echo "==> Building Graphviz ${GRAPHVIZ_VERSION} against wasi-sdk"
echo "    wasi-sdk:  ${WASI_SDK_PATH}"
echo "    CC:        ${CC}"
echo "    output:    ${OUTPUT_DIR}/graphviz.wasm"

mkdir -p "${BUILD_DIR}" "${OUTPUT_DIR}"

# Fetch source only once. Bumping GRAPHVIZ_VERSION drops a new tarball next to
# the old one; nothing is cleaned up automatically — use `make clean` for that.
if [[ ! -f "${SRC_TARBALL}" ]]; then
    echo "==> Fetching ${GRAPHVIZ_URL}"
    curl -fL --retry 3 -o "${SRC_TARBALL}" "${GRAPHVIZ_URL}"
fi
if [[ ! -d "${SRC_DIR}" ]]; then
    echo "==> Extracting ${SRC_TARBALL}"
    tar -xzf "${SRC_TARBALL}" -C "${BUILD_DIR}"
fi

cd "${SRC_DIR}"

# Minimal feature set:
#   - keep:   libcgraph, libgvc, libpathplan, the `dot` layout plugin, the
#             `core` (SVG) render plugin
#   - drop:   pango/cairo/freetype (no native font rasterization in wasm),
#             libgd (raster output), expat (XML input — we only need DOT in),
#             ltdl (no dynamic loading on wasm), X11, Tcl/Perl/Python/PHP
#             language bindings, ghostscript, rsvg, poppler, ZMQ, sodium
#
# The exact flags here are the best-guess starting point; some will need
# adjustment once we observe what configure complains about. Each ./configure
# rerun in here is cheap (a couple of minutes) so iterate.
echo "==> Configuring"
./configure \
    --host=wasm32-wasi \
    --prefix=/usr \
    --disable-shared \
    --enable-static \
    --disable-ltdl \
    --without-x \
    --without-expat \
    --without-libgd \
    --without-pangocairo \
    --without-poppler \
    --without-rsvg \
    --without-ghostscript \
    --without-freetype2 \
    --without-fontconfig \
    --without-gtk \
    --without-gts \
    --without-quartz \
    --without-zmq \
    --without-sodium \
    --without-tcl \
    --without-perl \
    --without-php \
    --without-python3 \
    --without-ruby \
    --without-lua \
    --without-r \
    --without-guile \
    --without-ortho \
    --enable-static-graphviz \
    || { echo "configure failed — see TODO in $(basename "$0")"; exit 1; }

# TODO(follow-up): expect failures here. Known things to chase:
#   - configure tries to compile and run a tiny test binary (AC_TRY_RUN). wasi
#     binaries can't run on the host; pass `cross_compiling=yes ac_cv_*=...`
#     overrides for any AC_TRY_RUN gate Graphviz hits.
#   - `setjmp`/`longjmp` support in wasi-sdk requires `-mllvm
#     -wasm-enable-sjlj` plus matching link-time flag; not all Graphviz call
#     sites need it, but pathplan does.
#   - libxdot uses `<sys/wait.h>` which doesn't exist under wasi; we'll need
#     a small shim or `--disable-xdot` if that flag exists.

echo "==> Building"
make -j"${MAKEFLAGS:-1}"

# Link our wrapper.c against the Graphviz static libs into a single wasm
# module exporting gv_render, gv_free, malloc, free, plus the linear memory.
# `--no-entry` because the module is a library, not a wasi command.
echo "==> Linking wrapper into ${OUTPUT_DIR}/graphviz.wasm"
"${CC}" \
    -O2 \
    -Wl,--no-entry \
    -Wl,--export=gv_render \
    -Wl,--export=gv_free \
    -Wl,--export=malloc \
    -Wl,--export=free \
    -I"${SRC_DIR}/lib/gvc" \
    -I"${SRC_DIR}/lib/cgraph" \
    -I"${SRC_DIR}/lib/cdt" \
    -I"${SRC_DIR}/lib/pathplan" \
    /work/wrapper.c \
    "${SRC_DIR}/lib/gvc/.libs/libgvc.a" \
    "${SRC_DIR}/lib/cgraph/.libs/libcgraph.a" \
    "${SRC_DIR}/lib/cdt/.libs/libcdt.a" \
    "${SRC_DIR}/lib/pathplan/.libs/libpathplan.a" \
    "${SRC_DIR}/lib/xdot/.libs/libxdot.a" \
    "${SRC_DIR}/plugin/core/.libs/libgvplugin_core.a" \
    "${SRC_DIR}/plugin/dot_layout/.libs/libgvplugin_dot_layout.a" \
    "${SRC_DIR}/plugin/neato_layout/.libs/libgvplugin_neato_layout.a" \
    -o "${OUTPUT_DIR}/graphviz.wasm"

echo "==> Done. dist/graphviz.wasm ready."
ls -lh "${OUTPUT_DIR}/graphviz.wasm"
