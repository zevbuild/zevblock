# ZevBlock Feature and Reliability Plan

This plan prioritizes correctness of existing protection before expanding coverage. Each item should have acceptance criteria and tests before release.

## P0 — SafeSearch correctness
- [x] Match Google search hostnames by complete DNS labels, preserving common country domains and rejecting misleading prefixes.
- [ ] Apply equivalent exact-host or label-boundary rules to YouTube, Bing, DuckDuckGo, Yahoo, Brave, and Qwant.
- [ ] Cover A, AAAA, CNAME, unsupported query types, and malformed names with unit tests.

## P1 — DNS reliability
- [ ] Validate IPv4 total length, fragmentation, IPv6 payload length, extension headers, UDP lengths, DNS label bounds, and compression pointers.
- [ ] Verify upstream responses against transaction ID, question name, type, class, source address, and source port.
- [ ] Return a bounded failure response and record a useful status when all upstream resolvers time out.
- [ ] Add deterministic parser, packet-builder, and forwarding tests for IPv4 and IPv6.

## P1 — Rule semantics
- [ ] Separate adult-domain rules, general blocklist rules, and custom rules so temporary bypass and allowlist behavior is explicit.
- [ ] Synchronize all mutable rule collections consistently.
- [ ] Show users which rule category caused a block and how a rule can be changed.

## P2 — Protection state and onboarding
- [ ] Report actual VPN, Accessibility, and Device Owner state independently.
- [ ] Explain which features require Device Owner and what remains available without it.
- [ ] Add recovery guidance for revoked permissions, VPN interruption, and unsupported Android behavior.

## P2 — Privacy and limits
- [ ] Explain locally inspected screen text and DNS queries sent to upstream resolvers.
- [ ] Keep DoH, direct-IP, cached-DNS, and manufacturer-specific limitations visible in product documentation.
- [ ] Publish a device and Android-version validation matrix; avoid claims stronger than demonstrated coverage.

## Release acceptance
- All P0 items have regression coverage.
- DNS packet tests pass for supported IPv4 and IPv6 cases.
- Manual checks confirm protection status reflects real OS state.
- README feature claims match measured behavior and documented limitations.
