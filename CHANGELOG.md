# Changelog

All notable changes to this project will be documented in this file.

## [1.3.0] - 2026-01-30
### Added
- **Digital Human Abstraction**: Introduced `IAvatarDriver` interface to decouple business logic from specific SDKs.
- **Xmov Driver**: Implementation of `XmovAvatarDriver`.
- **MindWatch 2.0**: Persistent emotion storage using ObjectBox and refined status logic.
- **Barge-in Support**: Full-duplex interruption based on ASR VAD signals.
- **Standard Docs**: Added `GETTING_STARTED.md`, `CONTRIBUTING.md`, `LICENSE`, etc.

### Fixed
- **ASR Reliability**: Implemented silent recovery for `240007` initialization errors.
- **Resource Management**: Lifecycle-aware cleanup for digital human assets.

## [1.2.0] - 2026-01-15
### Added
- Initial implementation of MindWatch.
- Basic RAG (Retrieval-Augmented Generation) for memory retrieval.
- Material 3 Glassmorphism UI.

## [1.0.0] - 2025-12-01
- Project initialized.
- Integration with Doubao LLM and Aliyun ASR.
