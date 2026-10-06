# Simulation and warfare roadmap

This document describes future design. It is not a list of implemented features.

## Distant settlement event model

Each town needs a persistent ID, generator cell, faction, development stage, population, workforce, resource ledger, relations, recent event history, revision, last simulation time, and random-step counter. Event definitions contain prerequisites, resource inputs/outputs, weights, cooldowns, and outcome rules.

Suggested first events:

| Event | Preconditions | Bounded result |
| --- | --- | --- |
| Harvest | Farm workforce and available farmland | Food limited by labor, time, and capacity. |
| Production | Workers, facilities, resources, and tools | Inputs consumed before outputs are credited. |
| Shortage | Reserve below target | Priority changes and a future trade request. |
| Migration | Food and a free housing bed | A small population change within capacity. |
| Construction | Approved plan, materials, labor | Plan progress; completed structures materialize once. |
| Convoy departure | Route, available goods, and transport | Reserve cargo and create one persistent convoy ID. |

Previous events influence eligibility and weight. A food shortage can encourage farming; a recent raid can increase escort demand. These are rules over town state, with controlled randomness, rather than unrelated dice rolls.

Generation should use a configurable distance band around players' explored areas, deterministic seed/cell identifiers, spacing rules, and a persisted generated-cell index. Initial suggestions are a 256–768 block band and sparse cells, subject to playtesting. Persist the actual placed position after checking terrain and existing claims on the server thread. Do not force-load unexplored terrain to satisfy a spawn roll.

## Async contract

1. Server thread creates a snapshot containing only plain values and a revision.
2. A bounded worker queue computes a proposed event result using a deterministic random stream.
3. Server thread checks that the town still exists, its revision still matches, and costs remain affordable.
4. Apply the result once, update its event/random counters, mark saved data dirty, and append a bounded history record.
5. Discard stale results and stop/drain workers when the server stops.

Use a bounded simulation interval and capped catch-up window. Towns should not receive days of instantaneous population/resource growth after a long outage. Offline owners' towns must remain inviting to return to, with explicit policy for new attacks and ongoing wars.

## Detailed/abstract handoff

A town has one authoritative simulation mode. Entering player range freezes abstract scheduling, commits outstanding accepted events, then materializes population, structures, and cargo without granting them again. Leaving range snapshots the detailed result before abstract scheduling resumes. Loaded NPCs and abstract worker counts cannot both produce resources for the same interval.

The first alpha intentionally pauses unloaded towns. Keep that safe behavior until the accounting and handoff are verified.

## Structure and room progression

Role stations currently declare use within an automatic 7×7×7 range. Complete beds and deterministic overlap ownership are implemented; roofs, connected floor space, detailed room quality, and reachability validation are future work. Keep the immediate placement preview as richer room validation is added. Later a builder consumes materials against an approved blueprint and changes individual blocks; proposed autonomous expansion still obeys player-selected limits.

Hospital capacity is separate from permanent population capacity. Barracks housing later links to military recruitment. Medical care consumes supplies and uses patient reservations. Owners should be able to authorize some routine construction while reserving major changes for their approval.

## Warfare and world progression

Introduce squads after the economy works reliably. Soldiers consume equipment and food from actual production. Convoys have persistent IDs, source/destination, real reserved cargo, escort strength, and progress. Visible convoys instantiate that same cargo; raiding them consumes or transfers it once and affects the recipient's economy.

Independent settlements can grow into countries composed of multiple towns and shared territory. Local factions may have simpler technology alongside different terrain knowledge, motives, and combat strengths. Technology research should unlock actual production capabilities; its final ceiling is not decided yet.

Open design choices: endgame technology; conquest versus looting; relations and diplomacy; offline war protection; maximum simulation population; whether a player can own multiple countries; and rebuilding after defeats.
