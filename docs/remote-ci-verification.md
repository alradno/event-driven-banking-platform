# Remote CI Verification

The local CI parity target is the source of truth for the remote pipelines:

```sh
make ci-local
```

GitHub Actions runs the same flow with Docker Compose:

```sh
COMPOSE="docker compose" make ci-local
```

Jenkins runs the same target with the default Podman Compose command.

## Standard Git Flow

Use this when the Git credentials on the machine have write access to the repository:

```sh
git status --short --branch
git push --dry-run origin master
git push origin master
```

After the push, verify the `CI` workflow for `master` in GitHub Actions. The goal is complete only after the pushed commit has a passing remote CI run.

## Optional GitHub CLI Flow

The GitHub CLI is optional. It does not replace Git for source transport; it adds GitHub API commands for pull requests, workflow runs, issues, and repository metadata.

When `gh` is installed and authenticated, these commands can inspect the remote CI run:

```sh
gh run list --branch master --limit 5
gh run watch
gh run view --log-failed
```

Use normal `git push` to publish commits. Use `gh` when you want terminal access to GitHub-specific objects such as Actions runs or pull requests.
