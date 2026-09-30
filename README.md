**This is the Zublastic diagnostic fork of [Sable Dynamic Lights](https://github.com/fr0nick/Sable-Dynamic-Lights), itself a fork of [Create: Dynamic Lights](https://github.com/leon-o/Create-Dynamic-Lights) by Leon.**

Sable Dynamic Lights adds dynamic lighting to glowing blocks placed on Create contraptions and Sable sub-levels using LambDynamicLights.

## Dependencies:
- [Create](https://github.com/Creators-of-Create/Create)
- [LambDynamicLights](https://github.com/LambdAurora/LambDynamicLights)
- [Sable](https://github.com/ryanhcode/sable) (optional)

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
