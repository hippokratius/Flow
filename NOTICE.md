# NOTICE

TubeHub is a fork of **Flow** — <https://github.com/A-EDev/Flow> — Copyright © 2025-2026
A-EDev and the Flow contributors, licensed under the GNU General Public License v3.0.

The full license text is in [`License`](License). TubeHub is distributed under the same
license, as GPLv3 requires of derivative works.

## Modification notice (GPLv3 §5(a))

This software is a modified version of Flow. Modified by the TubeHub contributors, 2026.

Changes made relative to upstream Flow:

- Added a content-source abstraction (`io.github.aedev.flow.data.source`) so the app can serve
  content from backends other than YouTube.
- Added PeerTube as a content source, including multi-instance aggregation.
- Added Fediverse (Misskey) interaction — account connection via MiAuth, and like, boost,
  comment and follow against federated video objects.
- Rebranded: application id `de.tubehub.app`, application name TubeHub.
- Removed the upstream Discord application id.

Upstream Flow remains the origin of the great majority of this codebase, including the player,
the YouTube extraction stack, the library and download subsystems, the Android TV interface and
the device-to-device sync protocol.

## Third-party components

TubeHub inherits Flow's third-party dependencies and their notices, including:

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — GPLv3
- [NewPipe](https://github.com/TeamNewPipe/NewPipe) — GPLv3

Portions of the PeerTube and Fediverse layers were ported from the standalone TubeHub PeerTube
client (<https://github.com/hippokratius/tube-hub>), by the same author, contributed to this
project under GPLv3.
