package com.yo1no.gramarye;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.U027CatalogBarrierBridge;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import com.yo1no.gramarye.magic.definition.scoped.U021FutureCatalogUpdate;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Excluded qualification variant. Prepared owner IDs are never serialized to evidence/console. */
public final class U027FuturePolicyBridge {
    private U027FuturePolicyBridge() { }

    public static Object bind(Object suppliedCatalog, Object suppliedMailbox, Object suppliedCold,
            Object suppliedStartup, Operation<Object> original) {
        if (!"u0-ordinary-ab".equals(System.getProperty("gramarye.p11.online.case", ""))) {
            return original.call(suppliedCatalog, suppliedMailbox, suppliedCold);
        }
        if (!(suppliedCatalog instanceof V0RuntimeCatalog catalog) || !(suppliedMailbox instanceof V0ColdMailbox mailbox)
                || !(suppliedCold instanceof V0NormalColdLane cold) || !(suppliedStartup instanceof V0SlotStartup startup)
                || startup.profile() != V0StartupProfile.Kind.NORMAL || !startup.authority().isOwnerThread()
                || !startup.server().isSameThread()) { throw new IllegalStateException("U027_EXACT_NORMAL_COMPOSITION"); }
        var prepared = preparedInputs();
        var roots = prepared.roots();
        if (!U027CatalogBarrierBridge.seal(catalog, startup.authority())) {
            throw new IllegalStateException("U027_BOOTSTRAP_BARRIER_REFUSED");
        }
        var key = new P11CooldownKey.LogicalPolicy(ResourceLocation.fromNamespaceAndPath("gramarye", "u027_future_fixture"));
        var policy = new NormalSpellPolicy(key, 0, 20, false, roots);
        // Existing factory, original root startup/start/abort/publication logic, no mutable-policy setter.
        var binding = V0SlotBinding.createNormal(catalog, mailbox, cold, policy);
        var target = prepared.first();
        var output = Path.of(P11C4aEvidence.property("output")).toAbsolutePath().normalize();
        if (!Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(output)) {
            throw new IllegalStateException("U021_OWNED_OUTPUT_REQUIRED");
        }
        U021FutureCatalogUpdate.register(catalog, startup.authority(), target.owner(), roots.get(target.owner()),
                target.stable(), target.revision(), target.generation(), target.documentHash(),
                output.resolve("server").resolve("u021-future-catalog-update.json"), P11C4aEvidence.property("runId"));
        return binding;
    }

    private record Target(SkillOwnerId owner, long stable, long revision, long generation, String documentHash) { }
    private record Prepared(Map<SkillOwnerId, V0RuntimeCatalog.Selection> roots, Target first) { }

    private static Prepared preparedInputs() {
        // Only a bounded, root-owned test input is read here. M1/C publication happened before the JVM.
        // This input is not P7/P11/auth authority: the unchanged real ingress must obtain all current witnesses.
        try {
            var file = Path.of(P11C4aEvidence.property("u027Input"));
            var expected = P11C4aEvidence.property("u027InputSha256");
            if (!file.isAbsolute() || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(file) || Files.size(file) > 8192 || !expected.matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("U027_PREPARED_INPUT_PATH");
            }
            var raw=Files.readAllBytes(file);
            if (!P11C4aEvidence.hash(raw).equals(expected)) { throw new IllegalStateException("U027_PREPARED_INPUT_HASH"); }
            var lines=new String(raw,StandardCharsets.US_ASCII).split("\n",-1);
            if (lines.length != 5 || !lines[0].equals("U027_AUTH_CATALOG_ROOTS_V1")
                    || !lines[1].matches("[a-zA-Z0-9_-]{8,64}") || !lines[4].isEmpty()) {
                throw new IllegalStateException("U027_PREPARED_INPUT_SHAPE");
            }
            var roots=new LinkedHashMap<SkillOwnerId,V0RuntimeCatalog.Selection>();
            Target first = null;
            for(int i=2;i<4;i++) {
                var row=lines[i].split("\t",-1);
                if(row.length!=7) { throw new IllegalStateException("U027_PREPARED_OWNER_ROW"); }
                var owner=new SkillOwnerId(UUID.fromString(row[0]));
                long stable=Long.parseLong(row[1]); long revision=Long.parseLong(row[2]);
                var source=UUID.fromString(row[3]); long generation=Long.parseLong(row[4]);
                if(!owner.value().toString().equals(row[0]) || !source.toString().equals(row[3]) || stable<=0
                        || revision<=0 || generation<=0 || !row[5].matches("[0-9a-f]{64}")
                        || !row[6].equals(P11C4aEvidence.hash((lines[1]+"\n"+owner.value()).getBytes(StandardCharsets.UTF_8)))
                        || owner.equals(V0BootstrapRetirementCoordinator.SEED_OWNER)
                        || roots.putIfAbsent(owner,V0RuntimeCatalog.Selection.standalone(stable))!=null) {
                    throw new IllegalStateException("U027_PREPARED_OWNER_IDENTITY");
                }
                if (first == null) { first = new Target(owner, stable, revision, generation, row[5]); }
            }
            if(roots.size()!=2) { throw new IllegalStateException("U027_TWO_ACTUAL_OWNER_ROOTS"); }
            return new Prepared(Map.copyOf(roots), first);
        } catch(java.io.IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("U027_PREPARED_POLICY_UNAVAILABLE",failure);
        }
    }
}
