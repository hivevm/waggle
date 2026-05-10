# ADR-0002: Dev Container runtime

- **Status:** 🟢 Accepted
- **Date:** 2026-07-05
- **Deciders:** HiveVM CC maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

The project must give humans and coding agents an identical, reproducible environment with a JDK and
the agent tooling preinstalled, without each contributor hand-configuring their machine. Coding
agents run arbitrary commands, so the blast radius of a mistake or a compromised dependency has to be
bounded: an agent should be able to touch the workspace, but not the host's other containers, images,
or the Docker daemon.

The build needs a JDK 21+ toolchain (see [ADR-0007](0007-java-21-baseline.md)); CI uses Temurin 21.

## Decision

The environment is defined entirely by
[`.devcontainer/devcontainer.json`](../../.devcontainer/devcontainer.json) — no Dockerfile or Compose
file:

1. **Base image:** the prebuilt `mcr.microsoft.com/devcontainers/java` image; no custom image build.
2. **No host Docker access.** The host Docker socket is deliberately **not** mounted, and no
   Docker-in-Docker feature is added. Agents inside the container cannot manage the host's containers
   or images. To manage host containers, a contributor runs the *Container Tools* extension pinned to
   the **host** side via `remote.extensionKind` in [`.vscode/settings.json`](../../.vscode/settings.json).
3. **Minimal bind mounts.** Only the workspace (`/workspace`) and the agent config directory
   (`~/.claude`) are bind-mounted; an `initializeCommand` pre-creates the config directory on the host
   so credential writes from inside the container succeed.
4. **Preinstalled tooling.** The container installs the coding-agent and Java VS Code extensions so
   the environment is ready on *Reopen in Container*.

## Consequences

- Every contributor and agent gets the same JDK and tooling with no manual setup.
- A misbehaving or compromised agent is confined to the workspace and its own container; it cannot
  reach the host Docker daemon.
- Managing host containers requires a host-side extension — a deliberate, one-time inconvenience that
  buys isolation.
- The base image dictates the available JDK; bumping the language baseline means bumping the image
  tag (and the CI JDK) together.
- Pulling the prebuilt image requires network access on first build.

## Alternatives considered

- **Mount the host Docker socket / Docker-in-Docker.** Rejected: hands an agent full control of the
  host container runtime — an unacceptable blast radius for autonomous tooling.
- **Custom Dockerfile.** Rejected for now: a prebuilt image plus Features covers the need with less
  to maintain; a Dockerfile can be introduced later if a concrete need appears (YAGNI).
- **No container, document local setup instead.** Rejected: reproducibility and isolation would
  depend on every contributor's machine.
</content>
