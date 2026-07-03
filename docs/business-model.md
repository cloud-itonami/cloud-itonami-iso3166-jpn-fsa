# Business Model: Independent FSA Payment-Services & Financial-Regulatory Compliance Service — Japan (FSA)

## Classification

- Repository: `cloud-itonami-iso3166-jpn-fsa`
- ISO 3166 (agency-level): `JPN-FSA`, parent `JPN`
- Ooyake cross-reference: `gov.jpn.finreg` (Financial Services Agency / 金融庁)
- Activity: registration and classification under the Payment Services Act (資金決済に関する法律/資金決済法) for an operator whose public-sector contract involves handling payments or funds transfer, and general FSA financial-regulatory compliance for contracts touching banking, securities, or insurance-adjacent services
- Social impact: [:payment-services-clarity :financial-regulatory-compliance :public-spend-transparency]

## Customer

- an operator whose government contract involves a payment or funds-transfer service requiring Payment Services Act registration
- a fintech operator bidding on a public-sector payments or financial-infrastructure contract
- a foreign financial-services vendor confirming FSA registration tier before bidding

## Offer

- Payment Services Act (資金決済法) registration-tier classification walkthrough
- FSA registration application checklist
- ongoing regulatory-change monitoring for FSA guideline updates
- compliance-audit export package for the operator's own records

## Revenue

- per-engagement compliance-review fee
- recurring regulatory-change monitoring subscription
- compliance-audit export package

## Trust Controls

- any actual filing, registration, or compliance-program submission
  requires Financial Regulatory Compliance Governor clearance and always escalates to human
  sign-off (`:filing/submit` is never automated at any phase)
- a false or fabricated regulatory-requirement claim is a HARD hold that
  cannot be overridden by human approval alone — it must be corrected
  against a cited FSA source first
- this service does **not** provide legal or tax advice; characterization
  and filing on the client's behalf beyond checklist/draft assistance
  routes to Japan-licensed counsel or a registered agent
- every requirement cites the official FSA source or
  regulation, never invented

## Boundary with adjacent actors (read before forking)

- **`cloud-itonami-iso3166-jpn`**: the COUNTRY-level coordinator (general
  Japan public-sector market entry). This repo is a narrower, deeper
  AGENCY-level leaf — most operators need the country-level blueprint plus
  only the agency-level blueprints that actually apply to their contract.
- **`com-etzhayyim-ooyake`** (etzhayyim/root): read-only civic-wayfinding
  mirror of government structure, non-commercial, barred from acting as or
  for the government (G3 impersonation ban). This blueprint is commercial
  and never claims to be Financial Services Agency or an official channel.
- **`matsurigoto`** (etzhayyim/root): sovereign e-government statecraft —
  literally the government. This blueprint is an independent operator that
  engages with FSA under its public rules — never the
  agency itself.
- **`com-etzhayyim-toritsugi`** (etzhayyim/root): guides a consenting
  INDIVIDUAL citizen through their OWN procedure, non-profit,
  donation-only. This blueprint's client is a business operator, not an
  individual citizen, and it is commercial.
- **`cloud-itonami-M6910`**: helps a client BECOME a legal entity
  (incorporation, ISIC 6910) — a prior, different regulatory phase (company
  law). This blueprint assumes incorporation is already done and handles
  FSA-specific compliance (a different regulatory domain).
