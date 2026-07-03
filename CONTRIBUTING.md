# Contributing

`cloud-itonami-iso3166-jpn-fsa` accepts contributions to the OSS actor, governor tests,
documentation, examples and open business blueprint.

## Development

```bash
clojure -M:dev:test
clojure -M:lint
```

Keep changes small and include tests for governor, audit or disclosure
behavior.

## Rules

- Do not commit real client or compliance data.
- Keep production actions behind the Financial Regulatory Compliance Governor.
- Never let the advisor state a legal/tax conclusion as fact — every
  regulatory requirement must cite the official FSA source.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which governor invariant is affected
- how it was tested
- whether operator or certification docs need updates
