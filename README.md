**This is the Zublastic diagnostic fork of [Sable Dynamic Lights](https://github.com/fr0nick/Sable-Dynamic-Lights), itself a fork of [Create: Dynamic Lights](https://github.com/leon-o/Create-Dynamic-Lights) by Leon.**

Sable Dynamic Lights adds dynamic lighting to glowing blocks placed on Create contraptions and Sable sub-levels using LambDynamicLights.

## Dependencies:
- [Create](https://github.com/Creators-of-Create/Create)
- [LambDynamicLights](https://github.com/LambdAurora/LambDynamicLights)
- [Sable](https://github.com/ryanhcode/sable) (optional)

## 2.0.1-zublastic.2: optional ScalableLux propagation

ScalableLux replaces Minecraft's light engine, bypassing the existing virtual
emission and light-read hooks. This version supplies optional adapters for
ScalableLux **0.3.0-alpha.0.8+1.21.1** on NeoForge. Without ScalableLux, the existing
vanilla light-engine hooks continue to operate; ScalableLux is neither bundled
nor a required runtime dependency.

The Portal Lab ScalableLux baseline requires both the ScalableLux core JAR and
**SableScalableLux 1.21.1-NeoForge-1.0.0** for Sable plot-engine integration.
SableScalableLux does not replace Sable Dynamic Lights or LambDynamicLights:
those still provide the client held-item, entity, Create and Sable virtual sources.
Enabling the compatibility JAR alone while the ScalableLux core is disabled is
not that baseline.

The adapters merge real and virtual emissions using their maximum, including
the engine's cached and contextual rechecks when another light disappears.
Full-chunk relighting also discovers virtual emitters in all-air sections. A
source-section index retains light storage within one adjacent section of each
source and rechecks nearby stationary sources after server light packets or
client chunk loads replace their storage. These operations inspect cached
chunks only and never load new ones. The same actual-client-level ownership
guards used by `.1` exclude integrated-server and unrelated client worlds.

The public light reader preserves the existing LambDynamicLights distance-based
fallback. It does not add occlusion to that fallback. Consumers that require
occluded source light, such as portal transport, must sample propagated raw block
light rather than the public dynamic-light override. Real and virtual light are
not added together, and no server light storage is changed.

Run `./gradlew test build` with Java 21. The 31 tests include exact optional-engine
method/field/local-variable signatures, actual ScalableLux hidden-nibble behavior,
all-air source discovery, bounded source-section retention, movement/removal,
overlap and client-world isolation. The ScalableLux test dependency is not
packaged. These checks do not substitute for live startup, relighting and
held-light occlusion tests with the complete mod stack.

## 2.0.1-zublastic.1: isolate client lighting

Upstream 2.0.1 can report client-held lights as real emissions to the integrated
server. A server light check while holding glowstone can store level 15 in air;
putting the item away clears only the client source, leaving persistent server
light. Portal Lab reproduced this with raw stored-light probes, without placing
a block or changing the room geometry.

This fork prevents virtual emission and dynamic read overrides in server worlds.
Virtual-source snapshots belong to one actual client-level instance, and removal
or a world change invalidates the old owning client engine after publishing the
source removal. Overlapping Create/Sable contributors retain their maximum light.
Remote client-level ticks/unloads no longer replace the active world's cache.

The change prevents new contamination. It does not globally rewrite saved light
data: previously affected areas require a normal light recalculation after the
sources are removed. Client stale data may also need a world reload. This is not
a shader lighting model, cross-portal illumination, or a universal fix for every
renderer/mod's dynamic-light occlusion.

Build with Java 21: `./gradlew test build`. Tests cover cache ownership, removal,
movement, contributor overlap, reload/disconnect, snapshot isolation and actual
Minecraft mixin target signatures. They do not replace an in-game mixin/startup
test and a repeat of the held-light/server-recalculation experiment. Keep the
original 2.0.1 JAR for rollback and replace it only while Minecraft is closed.
