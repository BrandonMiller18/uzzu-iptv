---
type: Gotcha
title: gh secret set from PowerShell 5.1 stores empty secrets
description: Piping a value into `gh secret set` from Windows PowerShell 5.1 silently saved empty GitHub secrets.
tags: [release, ci, windows]
generated: {by: claude/opus-5.5, at: 2026-10-20T23:00:00Z}
---
**Symptom**: the `RELEASE_*` secrets exist in repo settings and `gh secret set` reported
success, but the release job cannot sign because the values are empty.

**Cause**: piping a value into `gh secret set` from Windows PowerShell 5.1 stored **empty** secrets.

**Fix**: set secrets from Git Bash, reading the value from a file or stdin there (e.g.
`gh secret set RELEASE_STORE_PASSWORD < file`). Never put the value on the command line.
Re-run the workflow to confirm the secrets are non-empty. See [Releasing](/guides/releasing.md).
