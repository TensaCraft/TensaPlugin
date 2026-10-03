# Local Velocity tools

These are maintained development tools, not production runtime files.

- `run-velocity.cmd` / `run-velocity.ps1` build and start a local dev proxy.
- `smoke-velocity.cmd` / `smoke-velocity.ps1` run isolated compatibility checks.
- `Velocity4TextPipelineProbe.java` verifies text-event binary compatibility.
- `TensaDevVelocity.run.xml` is an optional shared IntelliJ launch configuration.

Generated `velocity*/` directories and `*.log` files are ignored by Git.
`velocity/` is the persistent personal dev profile: preserve its configs, plugin
data and forwarding secret. `velocity-smoke/` is recreated by the smoke tool.
No runtime directory, credential or crash dump belongs in a commit.

Run commands from the repository root. Requirements and examples are in the
[project README](../README.md#dev-server).
