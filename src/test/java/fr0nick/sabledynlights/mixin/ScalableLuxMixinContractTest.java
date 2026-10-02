package fr0nick.sabledynlights.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exact optional-mod bytecode contracts; these tests never bootstrap Minecraft or install ScalableLux. */
class ScalableLuxMixinContractTest {
    private static final String ENGINE = "ca/spottedleaf/starlight/common/light/BlockStarLightEngine";
    private static final String PROPAGATOR = "ca/spottedleaf/starlight/common/light/StarLightEngine";
    private static final String READER = "ca/spottedleaf/starlight/common/light/StarLightInterface";
    private static final String CLIENT = "ca/spottedleaf/starlight/common/light/vanillainterface/BaseLevelLightEngineVanillaInterface";
    private static final String MIXINS = "fr0nick/sabledynlights/mixin/";
    private static final String EMISSION = "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I";

    private static ClassNode read(String name) throws IOException {
        try (var input = ScalableLuxMixinContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    @Test void decreaseRechecksBothCachedAndContextualRemainingEmittersWithExactCoordinates() throws IOException {
        var target = read(PROPAGATOR);
        var decrease = method(target, "performLightDecrease");
        var emissions = calls(decrease).stream().filter(c -> c.name.equals("getLightEmission")
                && c.owner.equals("net/minecraft/world/level/block/state/BlockState")).toList();
        assertEquals(4, emissions.size());
        assertEquals(2, emissions.stream().filter(c -> c.desc.equals("()I")).count());
        assertEquals(2, emissions.stream().filter(c -> c.desc.equals(EMISSION)).count());
        // The two loop branches use different slots, so named local capture must resolve each active range.
        for (var call : emissions) for (String name : List.of("offX", "offY", "offZ")) {
            int index = decrease.instructions.indexOf(call);
            var active = decrease.localVariables.stream().filter(v -> v.name.equals(name) && v.desc.equals("I")
                    && decrease.instructions.indexOf(v.start) <= index
                    && decrease.instructions.indexOf(v.end) > index).toList();
            assertEquals(1, active.size(), name + " at " + index);
        }
        var mixin = read(MIXINS + "ScalableLuxLightDecreaseMixin");
        for (String name : List.of("world", "skylightPropagator")) {
            var shadow = mixin.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow();
            assertTrue(target.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(shadow.desc)
                    && (f.access & Opcodes.ACC_FINAL) != 0));
        }
        var hook = method(mixin, "sabledynlights$remainingEmitter");
        var injection = hook.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/ModifyExpressionValue;"))
                .findFirst().orElseThrow();
        assertEquals(4, injection.values.get(injection.values.indexOf("require") + 1));
        assertEquals(4, injection.values.get(injection.values.indexOf("allow") + 1));
        assertTrue(calls(hook).stream().anyMatch(c -> c.name.equals("isCurrentLevel")));
        assertTrue(calls(hook).stream().anyMatch(c -> c.name.equals("withVirtualEmission")));
        assertTrue(Arrays.stream(hook.instructions.toArray()).anyMatch(i -> i instanceof FieldInsnNode f
                && f.name.equals("skylightPropagator")));
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode)
                .map(i -> (MethodInsnNode) i).toList();
    }

    @Test void everyPropagationPathReadsTheContextualEmissionExactlyOnce() throws IOException {
        var engine = read(ENGINE);
        for (String name : List.of("checkBlock", "calculateLightValue", "lightChunk")) {
            assertEquals(1, calls(method(engine, name)).stream().filter(call ->
                    call.owner.equals("net/minecraft/world/level/block/state/BlockState")
                            && call.name.equals("getLightEmission") && call.desc.equals(EMISSION)).count(), name);
        }
        var mixin = read(MIXINS + "ScalableLuxBlockLightEngineMixin");
        var redirect = method(mixin, "sabledynlights$virtualEmission").visibleAnnotations.stream()
                .filter(a -> a.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
        int index = redirect.values.indexOf("method");
        assertEquals(List.of("checkBlock", "calculateLightValue", "lightChunk"), redirect.values.get(index + 1));
        assertEquals(3, redirect.values.get(redirect.values.indexOf("require") + 1));
        assertEquals(3, redirect.values.get(redirect.values.indexOf("allow") + 1));
    }

    @Test void relightHookTargetsActualSourceScanAndItsResultFeedsLightChunk() throws IOException {
        var engine = read(ENGINE);
        assertEquals("(Lnet/minecraft/world/level/chunk/LightChunkGetter;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/List;",
                method(engine, "getSources").desc);
        assertTrue(calls(method(engine, "lightChunk")).stream().anyMatch(c -> c.name.equals("getSources")));
        // A plain emission redirect cannot fix source discovery: all-air sections are skipped first.
        assertTrue(calls(method(engine, "getSources")).stream().anyMatch(c -> c.name.equals("hasOnlyAir")));
        var hook = method(read(MIXINS + "ScalableLuxBlockLightEngineMixin"), "sabledynlights$virtualSources");
        assertTrue(calls(hook).stream().anyMatch(c -> c.name.equals("getVirtualSources")));
        assertTrue(calls(hook).stream().anyMatch(c -> c.name.equals("merge")));
    }

    @Test void readerFieldsAndSignatureExistAndRawDataLayersRemainUnmodified() throws IOException {
        var target = read(READER);
        var mixin = read(MIXINS + "ScalableLuxLightReaderMixin");
        for (var field : mixin.fields) {
            assertTrue(target.fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc)
                    && (f.access & Opcodes.ACC_FINAL) != 0), field.name);
        }
        assertEquals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/ChunkAccess;)I",
                method(target, "getBlockLightValue").desc);
        assertTrue(calls(method(read(READER + "$2"), "getLightValue")).stream()
                .anyMatch(c -> c.name.equals("getBlockLightValue")));
        assertTrue(calls(method(read(READER + "$2"), "getDataLayerData")).stream()
                .anyMatch(c -> c.name.equals("toVanillaNibble")));
        assertFalse(mixin.methods.stream().anyMatch(m -> calls(m).stream()
                .anyMatch(c -> c.name.contains("setLight") || c.name.contains("setNibble"))));
    }

    @Test void emptySectionHooksMatchDeclaredEngineMethodsAndNeverLoadChunks() throws IOException {
        var engine = read(ENGINE);
        assertEquals("(Lnet/minecraft/world/level/Level;)V", method(engine, "<init>").desc);
        assertEquals("(IIIZZ)V", method(engine, "initNibble").desc);
        assertEquals("(III)V", method(engine, "setNibbleNull").desc);
        assertEquals("(Lnet/minecraft/world/level/chunk/LightChunkGetter;III)V", method(engine, "checkBlock").desc);
        assertTrue(calls(method(engine, "initNibble")).stream().anyMatch(c -> c.name.equals("getChunkInCache")));
        assertTrue(calls(method(engine, "initNibble")).stream().anyMatch(c -> c.name.equals("setNonNull")));
        var mixin = read(MIXINS + "ScalableLuxBlockLightEngineMixin");
        assertTrue(calls(method(mixin, "sabledynlights$retainVirtualSections")).stream()
                .anyMatch(c -> c.name.equals("needsLightSection")));
        assertTrue(calls(method(mixin, "sabledynlights$prepareVirtualSource")).stream()
                .anyMatch(c -> c.name.equals("withVirtualEmission")));
        assertTrue(mixin.methods.stream().flatMap(m -> calls(m).stream())
                .noneMatch(c -> c.name.equals("getChunk") || c.name.equals("getChunkSource")));
    }

    @Test void actualScalableLuxHiddenNibbleRequiresUnhideForRawLightSamples() throws Exception {
        // Reflection keeps the optional mod entirely off the production compile/runtime dependency surface.
        var type = Class.forName("ca.spottedleaf.starlight.common.light.SWMRNibbleArray");
        var nibble = type.getConstructor().newInstance();
        type.getMethod("set", int.class, int.class).invoke(nibble, 0, 15);
        type.getMethod("setHidden").invoke(nibble);
        type.getMethod("updateVisible").invoke(nibble);
        assertEquals(15, type.getMethod("getVisible", int.class).invoke(nibble, 0));
        assertNull(type.getMethod("toVanillaNibble").invoke(nibble));
        type.getMethod("setNonNull").invoke(nibble);
        type.getMethod("updateVisible").invoke(nibble);
        var vanilla = type.getMethod("toVanillaNibble").invoke(nibble);
        assertNotNull(vanilla);
        assertEquals(15, vanilla.getClass().getMethod("get", int.class, int.class, int.class)
                .invoke(vanilla, 0, 0, 0));
        type.getMethod("setHidden").invoke(nibble);
        type.getMethod("updateVisible").invoke(nibble);
        assertNull(type.getMethod("toVanillaNibble").invoke(nibble));
    }

    @Test void optionalAdaptersHaveNoScalableLuxClassDependenciesAndAreClientListed() throws IOException {
        for (String name : List.of("ScalableLuxBlockLightEngineMixin", "ScalableLuxLightDecreaseMixin", "ScalableLuxLightReaderMixin", "ScalableLuxClientLightUpdatesMixin")) {
            var node = read(MIXINS + name);
            assertEquals("java/lang/Object", node.superName);
            assertTrue(node.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;")));
            for (var m : node.methods) {
                assertFalse(m.desc.contains("ca/spottedleaf"));
                assertTrue(calls(m).stream().noneMatch(c -> c.owner.startsWith("ca/spottedleaf")));
            }
        }
        try (var input = getClass().getClassLoader().getResourceAsStream("sabledynlights.mixins.json")) {
            assertNotNull(input);
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.indexOf("\"client\"") < json.indexOf("\"ScalableLuxBlockLightEngineMixin\""));
            assertTrue(json.contains("\"ScalableLuxLightReaderMixin\""));
            assertTrue(json.contains("\"ScalableLuxClientLightUpdatesMixin\""));
            assertTrue(json.contains("\"ScalableLuxLightDecreaseMixin\""));
        }
    }

    @Test void clientPacketAndChunkHooksRunAfterRawNibbleReplacement() throws IOException {
        var target = read(CLIENT);
        assertEquals("(Lnet/minecraft/world/level/chunk/LightChunkGetter;ZZ)V", method(target, "<init>").desc);
        assertEquals("(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/DataLayer;Z)V",
                method(target, "scalablelux$clientUpdateLight").desc);
        assertEquals("(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
                method(target, "scalablelux$clientChunkLoad").desc);
        assertTrue(calls(method(target, "scalablelux$clientUpdateLight")).stream().anyMatch(c -> c.name.equals("scalablelux$setBlockNibbles")));
        assertTrue(calls(method(target, "scalablelux$clientChunkLoad")).stream().anyMatch(c -> c.name.equals("scalablelux$setBlockNibbles")));
        var mixin = read(MIXINS + "ScalableLuxClientLightUpdatesMixin");
        for (String name : List.of("sabledynlights$afterLightPacket", "sabledynlights$afterChunkLoad")) {
            var inject = method(mixin, name).visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
            var at = (List<?>) inject.values.get(inject.values.indexOf("at") + 1);
            var point = (AnnotationNode) at.getFirst();
            assertEquals("RETURN", point.values.get(point.values.indexOf("value") + 1));
        }
    }

    @Test void allAdaptersDelegateThroughActualClientIdentityGuards() throws IOException {
        var manager = read("fr0nick/sabledynlights/virtual/VirtualLightManager");
        for (String name : List.of("withVirtualEmission", "getVirtualSources", "needsLightSection",
                "recheckSectionSources", "recheckChunkSources")) {
            assertTrue(Arrays.stream(method(manager, name).instructions.toArray())
                    .anyMatch(i -> i instanceof TypeInsnNode t && t.getOpcode() == Opcodes.INSTANCEOF
                            && t.desc.equals("net/minecraft/client/multiplayer/ClientLevel")), name);
        }
        var reader = method(read(MIXINS + "ScalableLuxLightReaderMixin"), "sabledynlights$dynamicRead");
        assertTrue(calls(reader).stream().anyMatch(c -> c.name.equals("isCurrentLevel")));
        assertTrue(calls(reader).stream().anyMatch(c -> c.name.equals("isOutsideBuildHeight")));
    }
}
