package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;

/** The logical owner catalog image inside the separately framed physical commit record. */
record ScopeDCatalogImage(
        SkillOwnerId owner,
        CatalogGeneration generation,
        Map<Identity, ExactReference> routes,
        Map<ExactReference, FormalDocument> revisions,
        Map<Identity, LocalHighWater> localHighWater,
        Set<ExactReference> externalRoots) {

    private static final int VERSION = 1;
    private static final int REF_BYTES = 17;
    private static final Comparator<ExactReference> REF_ORDER = Comparator
            .comparingInt((ExactReference reference) -> family(reference))
            .thenComparingLong(ScopeDCatalogImage::id)
            .thenComparingLong(ScopeDCatalogImage::revision);

    ScopeDCatalogImage {
        if (routes.size() > MAX_ROUTES || revisions.size() > MAX_REVISIONS
                || localHighWater.size() != routes.size() || externalRoots.size() > MAX_EXTERNAL_ROOTS) {
            throw new IllegalArgumentException("Catalog capacity or local allocation coverage");
        }
        routes = Map.copyOf(routes);
        revisions = Map.copyOf(revisions);
        localHighWater = Map.copyOf(localHighWater);
        externalRoots = Set.copyOf(externalRoots);
        for (var entry : revisions.entrySet()) {
            if (!entry.getKey().identity().owner().equals(owner) || !entry.getKey().equals(entry.getValue().reference())) {
                throw new IllegalArgumentException("Catalog revision identity mismatch");
            }
        }
        for (var entry : routes.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().identity()) || !revisions.containsKey(entry.getValue())
                    || !localHighWater.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("Catalog route missing matching material");
            }
        }
        if (!revisions.keySet().containsAll(externalRoots)) {
            throw new IllegalArgumentException("External exact root material missing");
        }
    }

    static ScopeDCatalogImage empty(SkillOwnerId owner) {
        return new ScopeDCatalogImage(owner, new CatalogGeneration(0), Map.of(), Map.of(), Map.of(), Set.of());
    }

    Map<Identity, FormalDocument> currentBodies() {
        Map<Identity, FormalDocument> current = new HashMap<>();
        routes.forEach((identity, reference) -> current.put(identity, revisions.get(reference)));
        return Map.copyOf(current);
    }

    byte[] encode() throws IOException {
        var bytes = new ScopeDBoundedOutput(MAX_CATALOG_PAYLOAD);
        try (var out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeLong(generation.value());
            out.writeShort(routes.size());
            out.writeShort(revisions.size());
            out.writeShort(externalRoots.size());
            for (ExactReference reference : sorted(routes.values())) {
                writeRef(out, reference);
                LocalHighWater limits = localHighWater.get(reference.identity());
                out.writeLong(limits.handlers());
                out.writeLong(limits.blocks());
                out.writeLong(limits.entries());
            }
            for (ExactReference reference : sorted(externalRoots)) {
                writeRef(out, reference);
            }
            for (ExactReference reference : sorted(revisions.keySet())) {
                writeDocument(out, revisions.get(reference));
            }
        }
        return bytes.copyBytes();
    }

    static ScopeDCatalogImage decode(SkillOwnerId owner, long generation, byte[] bytes) throws IOException {
        if (bytes.length > MAX_CATALOG_PAYLOAD) {
            throw new IOException("Catalog frame exceeds capacity");
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readUnsignedByte() != VERSION || in.readLong() != generation) {
                throw new IOException("Catalog schema or generation mismatch");
            }
            int routeCount = count(in, MAX_ROUTES);
            int revisionCount = count(in, MAX_REVISIONS);
            int rootCount = count(in, MAX_EXTERNAL_ROOTS);
            if (in.available() < routeCount * (REF_BYTES + 3 * Long.BYTES) + rootCount * REF_BYTES
                    + revisionCount * (Integer.BYTES + 1)) {
                throw new IOException("Catalog entries truncated");
            }
            Map<Identity, ExactReference> routes = new HashMap<>();
            Map<Identity, LocalHighWater> watermarks = new HashMap<>();
            for (int index = 0; index < routeCount; index++) {
                ExactReference reference = readRef(in, owner);
                if (routes.putIfAbsent(reference.identity(), reference) != null) {
                    throw new IOException("Duplicate catalog route");
                }
                watermarks.put(reference.identity(), new LocalHighWater(in.readLong(), in.readLong(), in.readLong()));
            }
            Set<ExactReference> roots = new HashSet<>();
            for (int index = 0; index < rootCount; index++) {
                if (!roots.add(readRef(in, owner))) {
                    throw new IOException("Duplicate catalog root");
                }
            }
            Map<ExactReference, FormalDocument> revisions = new HashMap<>();
            for (int index = 0; index < revisionCount; index++) {
                if (!(readDocument(in) instanceof FormalDocument document)
                        || revisions.putIfAbsent(document.reference(), document) != null) {
                    throw new IOException("Invalid or duplicate catalog revision");
                }
            }
            eof(in);
            return new ScopeDCatalogImage(owner, new CatalogGeneration(generation), routes, revisions, watermarks, roots);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Malformed catalog contents", malformed);
        }
    }

    static void writeDocument(DataOutputStream out, ScopedDocument document) throws IOException {
        byte[] encoded = ScopeDDataCodec.encode(document);
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    static ScopedDocument readDocument(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_DOCUMENT_BYTES || length > in.available()) {
            throw new IOException("Invalid document length");
        }
        return ScopeDDataCodec.decode(in.readNBytes(length));
    }

    static int count(DataInputStream in, int maximum) throws IOException {
        int count = in.readUnsignedShort();
        if (count > maximum) {
            throw new IOException("Collection capacity");
        }
        return count;
    }

    static void eof(DataInputStream in) throws IOException {
        if (in.read() != -1) {
            throw new IOException("Trailing image bytes");
        }
    }

    private static List<ExactReference> sorted(java.util.Collection<ExactReference> references) {
        var ordered = new ArrayList<>(references);
        ordered.sort(REF_ORDER);
        return ordered;
    }

    private static int family(ExactReference reference) {
        return reference instanceof SpellReference ? 1 : 2;
    }

    static long id(ExactReference reference) {
        return switch (reference) {
            case SpellReference spell -> spell.identity().id().value();
            case ManifestReference manifest -> manifest.identity().id().value();
        };
    }

    static long revision(ExactReference reference) {
        return switch (reference) {
            case SpellReference spell -> spell.revision().value();
            case ManifestReference manifest -> manifest.revision().value();
        };
    }

    private static void writeRef(DataOutputStream out, ExactReference reference) throws IOException {
        out.writeByte(family(reference));
        out.writeLong(id(reference));
        out.writeLong(revision(reference));
    }

    private static ExactReference readRef(DataInputStream in, SkillOwnerId owner) throws IOException {
        int family = in.readUnsignedByte();
        long id = in.readLong();
        long revision = in.readLong();
        return switch (family) {
            case 1 -> new SpellReference(new SpellIdentity(owner, new SpellDefinitionId(id)), new SpellRevision(revision));
            case 2 -> new ManifestReference(new ManifestIdentity(owner, new SkillManifestId(id)), new ManifestRevision(revision));
            default -> throw new IOException("Unsupported catalog reference family");
        };
    }

    record LocalHighWater(long handlers, long blocks, long entries) {
        static final LocalHighWater EMPTY = new LocalHighWater(0, 0, 0);

        LocalHighWater {
            if (handlers < 0 || blocks < 0 || entries < 0) {
                throw new IllegalArgumentException("Negative local allocation watermark");
            }
        }
    }
}
