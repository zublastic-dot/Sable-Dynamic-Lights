package fr0nick.sabledynlights.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** Checks target linkage against the Minecraft classes actually used by this build. */
class LightEngineMixinContractTest {
    private static final String ENGINE = "net/minecraft/world/level/lighting/LightEngine";
    private static final String CHUNKS = "Lnet/minecraft/world/level/chunk/LightChunkGetter;";

    private static ClassNode read(String name) throws IOException {
        try (var input = LightEngineMixinContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            return node;
        }
    }

    @Test void emissionHookUsesTheRealSuperclassForItsInheritedChunkSource() throws IOException {
        var target = read("net/minecraft/world/level/lighting/BlockLightEngine");
        var mixin = read("fr0nick/sabledynlights/mixin/BlockLightEngineEmissionMixin");
        assertEquals(ENGINE, target.superName);
        assertEquals(target.superName, mixin.superName);
        assertTrue(target.methods.stream().anyMatch(m -> m.name.equals("getEmission")
                && m.desc.equals("(JLnet/minecraft/world/level/block/state/BlockState;)I")));
        // Mixin only resolves @Shadow fields declared in the target itself. This
        // inherited field must be accessed through the matching superclass.
        assertTrue(mixin.fields.stream().noneMatch(f -> f.name.equals("chunkSource")));
        assertTrue(read(ENGINE).fields.stream().anyMatch(f -> f.name.equals("chunkSource")
                && f.desc.equals(CHUNKS) && (f.access & Opcodes.ACC_PROTECTED) != 0));
    }

    @Test void readHookShadowAndInjectionExistOnTheTargetClass() throws IOException {
        var target = read(ENGINE);
        var mixin = read("fr0nick/sabledynlights/mixin/LightEngineMixin");
        var shadow = mixin.fields.stream().filter(f -> f.name.equals("chunkSource")).findFirst().orElseThrow();
        assertEquals(CHUNKS, shadow.desc);
        assertTrue(target.fields.stream().anyMatch(f -> f.name.equals(shadow.name) && f.desc.equals(shadow.desc)));
        assertTrue(target.methods.stream().anyMatch(m -> m.name.equals("getLightValue")
                && m.desc.equals("(Lnet/minecraft/core/BlockPos;)I")));
    }
}
