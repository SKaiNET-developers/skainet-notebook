# Reproducible C → wasm build harness.
#
# `make image`     — build the wasi-sdk Docker image (idempotent; cached).
# `make graphviz`  — run build-graphviz.sh inside the image, output to dist/.
# `make shell`     — drop into an interactive shell inside the image.
# `make clean`     — drop build/ and dist/ from the host side.
#
# All targets are no-ops at the host level except for orchestrating Docker;
# the actual toolchain (wasi-sdk, autoconf, ...) lives inside the image so
# contributors don't need any of it on their machine.

IMAGE      ?= skainet-wasm-build:latest
HOST_DIR   := $(shell pwd)
CONTAINER  := docker run --rm \
                  -v $(HOST_DIR):/work \
                  -w /work \
                  $(IMAGE)

.PHONY: image graphviz shell clean

image:
	docker build -t $(IMAGE) .

graphviz: image
	@mkdir -p dist
	$(CONTAINER) ./build-graphviz.sh

shell: image
	docker run --rm -it \
	    -v $(HOST_DIR):/work \
	    -w /work \
	    $(IMAGE) /bin/bash

clean:
	rm -rf build dist
