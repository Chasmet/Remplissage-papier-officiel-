#!/usr/bin/env sh
set -eu
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT
javac -d "$TEST_DIR" app/src/main/java/com/chasmet/remplissagepapierofficiel/AtomicPdfCopy.java tests/AtomicPdfCopyTest.java
java -cp "$TEST_DIR" com.chasmet.remplissagepapierofficiel.AtomicPdfCopyTest
