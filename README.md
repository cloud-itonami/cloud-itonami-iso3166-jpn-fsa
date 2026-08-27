# cloud-itonami-iso3166-jpn-fsa

Open ISO 3166 Agency Blueprint for **JPN-FSA**: Financial Services Agency
(金融庁, FSA) — a Japan-agency-level LEAF under
the `cloud-itonami-iso3166-jpn` country-level coordinator.

This repository designs a forkable OSS business for an independent
compliance consultant: an already-incorporated operator (typically one
already using `cloud-itonami-iso3166-jpn` for general Japan market entry)
gets a Compliance Advisor + independent **Financial Regulatory Compliance Governor** to
navigate registration and classification under the Payment Services Act (資金決済に関する法律/資金決済法) for an operator whose public-sector contract involves handling payments or funds transfer, and general FSA financial-regulatory compliance for contracts touching banking, securities, or insurance-adjacent services.

## No robotics premise — digital/data service exemption

Agency-specific compliance navigation is a pure data/software service with
no physical-domain work — the same exemption class as `cloud-itonami-6310`
and `cloud-itonami-gtin-*`. `blueprint.edn` sets
`:itonami.blueprint/robotics false` and `:required-technologies` lists only
real capabilities (`:identity`, `:forms`, `:dmn`, `:bpmn`, `:audit-ledger`),
no `:robotics`.

## Core Contract

```text
operator intake + prior filing/compliance history
        |
        v
Compliance Advisor -> Financial Regulatory Compliance Governor -> compliance draft, or human sign-off
        |
        v
gated filing / registration / compliance-program submission + audit ledger
```

No automated proposal can submit a filing or registration the governor
refuses, suppress a compliance record, or claim a legal conclusion the
governor has not cleared. `:filing/submit` is never in any phase's `:auto`
set — it always requires human sign-off (mirrors `cloud-itonami-M6910`'s
`filing-submit-never-auto-at-any-phase` invariant).

## What this is NOT

- **Not Financial Services Agency (金融庁) itself, and not the
  government of Japan.** See [`docs/business-model.md`](docs/business-model.md)
  for the boundary with `com-etzhayyim-ooyake`, `matsurigoto`,
  `com-etzhayyim-toritsugi`, `legal-entity.etzhayyim.com`,
  `cloud-itonami-M6910`, and the country-level `cloud-itonami-iso3166-jpn`.
- **Not legal or tax advice.** Every regulatory claim must cite the
  official FSA source and route final filings to
  Japan-licensed counsel or a registered agent where the law requires
  licensed representation.

## Regulatory source register

Every regulatory claim in this repository must cite an official source, and
[`facts.edn`](facts.edn) is the set it must cite from. A regulation that is
not in that table has no spec-basis here — extend the table, never invent a
law id, a law number or a URL.

```bash
nbb scripts/verify-facts.cljs   # re-fetch every entry from the live authority
nbb scripts/break-tests.cljs    # prove the verifier discriminates
```

`verify-facts.cljs` has three exit codes and the third is the point: `0` every
entry agreed, `1` the register is wrong about the world, `2` **REFUSED** — this
run could not answer. A run that could not read a page must not be able to
report the same thing as a run that read it and found it unchanged.

Two measured properties of these authorities shape every check, and both are
written up in `facts.edn`'s header:

- **www.fsa.go.jp's 404 page is longer than its front page** and carries the
  whole subject vocabulary in its navigation. 銀行, 保険 and 金融商品取引 occur
  on it exactly as often as on the 法令・指針等 page — every occurrence there is
  chrome. So needles are chosen by subtracting the live 404 body, not by topic
  relevance, and a needle found on the 404 **refuses** rather than passing.
- **laws.e-gov.go.jp answers a fabricated law id two different ways.**
  `law_data/<id>` returns 404; the much cheaper `laws?law_id=<id>` returns
  **200** with `total_count: 0`. This register uses the cheap endpoint and
  therefore never checks its status — identity is `total_count` plus an exact
  `law_id` match, and being in force is `repeal_status` **and**
  `current_revision_status`, because neither field answers that alone.

The entries whose ids end in `-control` are cited for no proposition. They are
live instances of states the checks claim to detect — a repealed Act, two Acts
that were never repealed but whose served revision is not the current one, and
a fabricated law id. Delete them and the verifier keeps passing while having
stopped discriminating, so it refuses to run without them.

## Capability layer

Resolves via [`kotoba-lang/iso3166`](https://github.com/kotoba-lang/iso3166)
(code `JPN-FSA`, `:parent "JPN"`, cross-referenced to ooyake's
`gov.jpn.finreg`). Required capabilities:

- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
