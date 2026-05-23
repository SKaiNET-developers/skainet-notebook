# syntax=docker/dockerfile:1.7
#
# Reproducible C → wasm build environment.
#
# Pinned wasi-sdk + the autoconf/automake/libtool toolchain you need to
# configure most C projects against a WASI target. Designed to be generic
# enough that any C library in this repo's tooling can target wasm without
# touching the host OS — and used in particular to build the Graphviz wasm
# consumed by `kotlin-notebook/src/main/kotlin/.../display/GraphvizWasm.kt`.
#
# Build:    make image
# Shell:    make shell
# Graphviz: make graphviz   (artifact at dist/graphviz.wasm)
#
# Multi-arch: the same Dockerfile produces an amd64 or arm64 image depending
# on the host's BuildKit TARGETARCH; both architectures of wasi-sdk are
# available from upstream.

ARG DEBIAN_TAG=bookworm-slim
FROM debian:${DEBIAN_TAG}

# Pin to a specific wasi-sdk release. Bumping is intentional — record the
# version that produced the bundled graphviz.wasm in wasm-build/README.md so
# the artifact is reproducible.
ARG WASI_SDK_MAJOR=24
ARG WASI_SDK_VERSION=24.0
ARG TARGETARCH

RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        autoconf \
        automake \
        libtool \
        pkg-config \
        bison \
        flex \
        curl \
        ca-certificates \
        xz-utils \
        git \
        make \
        cmake \
        python3 \
        ninja-build \
    && rm -rf /var/lib/apt/lists/*

# wasi-sdk's release tarballs are named with the upstream arch label, which
# doesn't match Debian's amd64/arm64 names. Translate once and download.
RUN set -eux; \
    case "${TARGETARCH:-amd64}" in \
        amd64) WASI_ARCH=x86_64 ;; \
        arm64) WASI_ARCH=arm64 ;; \
        *) echo "Unsupported TARGETARCH=${TARGETARCH}" >&2; exit 1 ;; \
    esac; \
    curl -fL --retry 3 -o /tmp/wasi-sdk.tar.gz \
        "https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-${WASI_SDK_MAJOR}/wasi-sdk-${WASI_SDK_VERSION}-${WASI_ARCH}-linux.tar.gz"; \
    mkdir -p /opt; \
    tar -xzf /tmp/wasi-sdk.tar.gz -C /opt; \
    mv "/opt/wasi-sdk-${WASI_SDK_VERSION}-${WASI_ARCH}-linux" /opt/wasi-sdk; \
    rm /tmp/wasi-sdk.tar.gz

ENV WASI_SDK_PATH=/opt/wasi-sdk
ENV PATH=/opt/wasi-sdk/bin:${PATH}
# Surfaced to autoconf's configure: CC/CXX/AR/RANLIB/LD pick up the wasi-sdk
# clang toolchain instead of host gcc. The `--target=wasm32-wasi` flag is
# already wired into wasi-sdk's clang, so projects don't have to set it.
ENV CC=/opt/wasi-sdk/bin/clang
ENV CXX=/opt/wasi-sdk/bin/clang++
ENV AR=/opt/wasi-sdk/bin/llvm-ar
ENV RANLIB=/opt/wasi-sdk/bin/llvm-ranlib
ENV LD=/opt/wasi-sdk/bin/wasm-ld
ENV CFLAGS="-O2 --sysroot=/opt/wasi-sdk/share/wasi-sysroot"
ENV CXXFLAGS="${CFLAGS}"
# Default `make` parallelism inside the container.
ENV MAKEFLAGS="-j4"

# The Makefile mounts the host's wasm-build/ directory here.
WORKDIR /work

# Default entrypoint is an interactive shell so `make shell` drops you into
# the toolchain. The Makefile overrides this with a script for `make graphviz`.
CMD ["/bin/bash"]
