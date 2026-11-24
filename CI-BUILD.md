# CI build notes

The published GitHub Actions JAR compiles against **signature stubs** in `src/ci/stubs` and Paper API. Those stubs are excluded from `Nodes-Military.jar`. It must be run with the real `milzzynodes` core.

The repository never publishes the user's private core jar or private saved `state.json`. For compatibility validation use `build.sh` with the real 21 Nov 2026 `milzzynodes.jar` and Paper API 1.21.x.

A successful CI build and offline tests do not prove live Paper functionality. Verify `/nmilitary gui`, `/nodes military`, army creation, enlistment with two different players, commander permissions, saved state and read-only Nodes-Extra supply reports on staging.
