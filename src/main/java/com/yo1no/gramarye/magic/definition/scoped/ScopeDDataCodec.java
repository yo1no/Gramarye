package com.yo1no.gramarye.magic.definition.scoped;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadResult;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReader;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReadResult;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReader;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Exact new-family frames. Legacy family entry points retain their original readers and shape. */
final class ScopeDDataCodec {
    private static final int MAGIC = 0x474d3144;
    private static final int PHYSICAL_VERSION = 1;
    private static final int FAMILY_VERSION = 1;
    private static final int FORMAL_SCHEMA = 1;
    private static final int DRAFT_SCHEMA = 1;
    private static final int LOGICAL_VERSION = 1;
    private static final int RAW_LOCATION_VERSION = 1;
    private static final int SPELL_IDENTITY = 1;
    private static final int MANIFEST_IDENTITY = 2;
    private static final int SPELL = 1;
    private static final int MANIFEST = 2;
    private static final int SPELL_DRAFT = 3;
    private static final int MANIFEST_DRAFT = 4;

    private ScopeDDataCodec() {}

    static DataResult<SkillDocumentReadResult> readLegacyDocument(Dynamic<?> input) {
        return SkillDocumentReader.read(input);
    }

    static DataResult<SkillDraftReadResult> readLegacyDraft(Dynamic<?> input) {
        return SkillDraftReader.read(input);
    }

    static byte[] encode(ScopedDocument document) throws IOException {
        var bounded = new ScopeDBoundedOutput(MAX_DOCUMENT_BYTES);
        try (var output = new DataOutputStream(bounded)) {
            output.writeInt(MAGIC);
            output.writeByte(PHYSICAL_VERSION);
            output.writeByte(family(document));
            output.writeByte(FAMILY_VERSION);
            output.writeByte(document instanceof Draft ? DRAFT_SCHEMA : FORMAL_SCHEMA);
            output.writeByte(LOGICAL_VERSION);
            if (document instanceof FormalDocument formal) {
                requireFormalShape(formal);
                writeReference(output, formal.reference());
                writeProvenance(output, formal.provenance());
            } else {
                writeIdentity(output, document.identity());
                switch (document) {
                    case SpellDraft spell -> writeDraftBase(output, spell.baseRevision().map(SpellRevision::value),
                            spell.baseGeneration());
                    case ManifestDraft manifest -> writeDraftBase(output, manifest.baseRevision().map(ManifestRevision::value),
                            manifest.baseGeneration());
                    case FormalDocument ignored -> throw new IllegalStateException("invalid family framing");
                }
            }
            switch (document) {
                case SpellDefinition spell -> writeSpell(output, spell.body());
                case SkillManifest manifest -> writeManifest(output, manifest.body());
                case SpellDraft spell -> writeSpell(output, spell.body());
                case ManifestDraft manifest -> writeManifest(output, manifest.body());
            }
            return bounded.copyBytes();
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IOException("invalid Scope D document shape", exception);
        }
    }

    static ScopedDocument decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_DOCUMENT_BYTES) {
            throw new IOException("Scope D document byte capacity exceeded");
        }
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes.clone()))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("invalid Scope D document marker");
            }
            requireVersion(input, PHYSICAL_VERSION);
            int family = input.readUnsignedByte();
            if (family < SPELL || family > MANIFEST_DRAFT) {
                throw new IOException("unsupported Scope D document family");
            }
            requireVersion(input, FAMILY_VERSION);
            requireVersion(input, family >= SPELL_DRAFT ? DRAFT_SCHEMA : FORMAL_SCHEMA);
            requireVersion(input, LOGICAL_VERSION);
            var document = switch (family) {
                case SPELL -> readSpellDefinition(input);
                case MANIFEST -> readSkillManifest(input);
                case SPELL_DRAFT -> readSpellDraft(input);
                case MANIFEST_DRAFT -> readManifestDraft(input);
                default -> throw new IOException("unsupported Scope D document family");
            };
            if (input.available() != 0) {
                throw new IOException("trailing Scope D document bytes");
            }
            if (document instanceof FormalDocument formal) {
                requireFormalShape(formal);
            }
            return document;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IOException("invalid Scope D document shape", exception);
        }
    }

    static void writeIdentity(DataOutput output, Identity identity) throws IOException {
        output.writeByte(identity instanceof SpellIdentity ? SPELL_IDENTITY : MANIFEST_IDENTITY);
        var owner = identity.owner().value();
        output.writeLong(owner.getMostSignificantBits());
        output.writeLong(owner.getLeastSignificantBits());
        output.writeLong(switch (identity) {
            case SpellIdentity spell -> spell.id().value();
            case ManifestIdentity manifest -> manifest.id().value();
        });
    }

    static Identity readIdentity(DataInput input) throws IOException {
        int family = input.readUnsignedByte();
        if (family != SPELL_IDENTITY && family != MANIFEST_IDENTITY) {
            throw new IOException("unsupported Scope D identity family");
        }
        var owner = new SkillOwnerId(new UUID(input.readLong(), input.readLong()));
        long id = input.readLong();
        try {
            return family == SPELL_IDENTITY ? new SpellIdentity(owner, new SpellDefinitionId(id))
                    : new ManifestIdentity(owner, new SkillManifestId(id));
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid Scope D identity", exception);
        }
    }

    static void writeReference(DataOutput output, ExactReference reference) throws IOException {
        writeIdentity(output, reference.identity());
        output.writeLong(reference.revisionNumber());
    }

    static ExactReference readReference(DataInput input) throws IOException {
        var identity = readIdentity(input);
        long revision = input.readLong();
        try {
            return switch (identity) {
                case SpellIdentity spell -> new SpellReference(spell, new SpellRevision(revision));
                case ManifestIdentity manifest -> new ManifestReference(manifest, new ManifestRevision(revision));
            };
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid Scope D revision", exception);
        }
    }

    private static SpellDefinition readSpellDefinition(DataInputStream input) throws IOException {
        if (!(readReference(input) instanceof SpellReference reference)) {
            throw new IOException("expected exact Spell revision");
        }
        var provenance = readProvenance(input);
        return new SpellDefinition(reference, readSpell(input, reference.identity()), provenance);
    }

    private static SkillManifest readSkillManifest(DataInputStream input) throws IOException {
        if (!(readReference(input) instanceof ManifestReference reference)) {
            throw new IOException("expected exact Manifest revision");
        }
        var provenance = readProvenance(input);
        return new SkillManifest(reference, readManifest(input, reference.identity()), provenance);
    }

    private static SpellDraft readSpellDraft(DataInputStream input) throws IOException {
        var identity = readSpellIdentity(input);
        var revision = flag(input) ? Optional.of(new SpellRevision(input.readLong())) : Optional.<SpellRevision>empty();
        var generation = new CatalogGeneration(input.readLong());
        return new SpellDraft(identity, revision, generation, readSpell(input, identity));
    }

    private static ManifestDraft readManifestDraft(DataInputStream input) throws IOException {
        if (!(readIdentity(input) instanceof ManifestIdentity identity)) {
            throw new IOException("expected Manifest Draft identity");
        }
        var revision = flag(input) ? Optional.of(new ManifestRevision(input.readLong())) : Optional.<ManifestRevision>empty();
        var generation = new CatalogGeneration(input.readLong());
        return new ManifestDraft(identity, revision, generation, readManifest(input, identity));
    }

    private static SpellIdentity readSpellIdentity(DataInput input) throws IOException {
        if (!(readIdentity(input) instanceof SpellIdentity identity)) {
            throw new IOException("expected Spell identity");
        }
        return identity;
    }

    private static void writeDraftBase(DataOutput output, Optional<Long> revision, CatalogGeneration generation)
            throws IOException {
        output.writeBoolean(revision.isPresent());
        if (revision.isPresent()) {
            output.writeLong(revision.orElseThrow());
        }
        output.writeLong(generation.value());
    }

    private static void writeProvenance(DataOutput output, Optional<ForkProvenance> provenance) throws IOException {
        output.writeBoolean(provenance.isPresent());
        if (provenance.isPresent()) {
            var source = provenance.orElseThrow();
            output.writeLong(source.sourceId().getMostSignificantBits());
            output.writeLong(source.sourceId().getLeastSignificantBits());
            writeReference(output, source.source());
            output.writeLong(source.generation().value());
        }
    }

    private static Optional<ForkProvenance> readProvenance(DataInput input) throws IOException {
        if (flag(input)) {
            var sourceId = new UUID(input.readLong(), input.readLong());
            return Optional.of(new ForkProvenance(sourceId, readReference(input), new CatalogGeneration(input.readLong())));
        }
        return Optional.empty();
    }

    private static int family(ScopedDocument document) {
        return switch (document) {
            case SpellDefinition ignored -> SPELL;
            case SkillManifest ignored -> MANIFEST;
            case SpellDraft ignored -> SPELL_DRAFT;
            case ManifestDraft ignored -> MANIFEST_DRAFT;
        };
    }

    private static void writeSpell(DataOutputStream output, SpellBody spell) throws IOException {
        writeSlot(output, spell.backend(), ScopeDDataCodec::writeBackend);
        writeSlot(output, spell.activation(), ScopeDDataCodec::writeDescriptor);
        writeSlot(output, spell.settings(), (stream, settings) -> writeList(stream, settings, ScopeDDataCodec::writeSetting));
        writeSlot(output, spell.handlers(), (stream, handlers) -> writeList(stream, handlers, ScopeDDataCodec::writeHandler));
        writeSlot(output, spell.appearance(), ScopeDDataCodec::writeDescriptor);
        writeSlot(output, spell.phenomenon(), ScopeDDataCodec::writeDescriptor);
    }

    private static SpellBody readSpell(DataInputStream input, SpellIdentity identity) throws IOException {
        var budget = new ReadBudget();
        return new SpellBody(
                readSlot(input, budget, new RawLocation(identity, RawField.BACKEND), ScopeDDataCodec::readBackend),
                readSlot(input, budget, new RawLocation(identity, RawField.ACTIVATION), ScopeDDataCodec::readDescriptor),
                readSlot(input, budget, new RawLocation(identity, RawField.SETTINGS), stream -> readList(stream, MAX_SETTINGS,
                        setting -> new Setting(readString(setting, MAX_SYMBOL_BYTES), readExpression(setting, 1, budget.expressions)))),
                readSlot(input, budget, new RawLocation(identity, RawField.HANDLERS), stream -> readList(stream, MAX_HANDLERS,
                        handler -> readHandler(handler, identity, budget))),
                readSlot(input, budget, new RawLocation(identity, RawField.APPEARANCE), ScopeDDataCodec::readDescriptor),
                readSlot(input, budget, new RawLocation(identity, RawField.PHENOMENON), ScopeDDataCodec::readDescriptor));
    }

    private static void writeManifest(DataOutputStream output, ManifestBody manifest) throws IOException {
        writeSlot(output, manifest.entries(), (stream, entries) -> writeList(stream, entries, ScopeDDataCodec::writeEntry));
        writeString(output, manifest.label());
        output.writeBoolean(manifest.exportIntent());
    }

    private static ManifestBody readManifest(DataInputStream input, ManifestIdentity identity) throws IOException {
        return new ManifestBody(readSlot(input, new ReadBudget(), new RawLocation(identity, RawField.ENTRIES),
                stream -> readList(stream, MAX_MANIFEST_ENTRIES, ScopeDDataCodec::readEntry)),
                readString(input, MAX_TEXT_BYTES), flag(input));
    }

    private static void writeEntry(DataOutputStream output, ManifestEntry entry) throws IOException {
        output.writeLong(entry.id().value());
        writeIdentity(output, entry.target().target());
        writeString(output, entry.label());
        writeString(output, entry.group());
    }

    private static ManifestEntry readEntry(DataInputStream input) throws IOException {
        return new ManifestEntry(new ManifestEntryId(input.readLong()), new SpellIdentityReference(readSpellIdentity(input)),
                readString(input, MAX_TEXT_BYTES), readString(input, MAX_SYMBOL_BYTES));
    }

    private static void writeBackend(DataOutputStream output, Backend backend) throws IOException {
        writeDescriptor(output, backend.descriptor());
        writeString(output, backend.category());
        writeList(output, backend.capabilities(), ScopeDDataCodec::writeString);
    }

    private static Backend readBackend(DataInputStream input) throws IOException {
        return new Backend(readDescriptor(input), readString(input, MAX_SYMBOL_BYTES),
                readList(input, MAX_CAPABILITIES, item -> readString(item, MAX_SYMBOL_BYTES)));
    }

    private static void writeDescriptor(DataOutputStream output, Descriptor descriptor) throws IOException {
        writeString(output, descriptor.tag());
        output.writeInt(descriptor.version());
        writeList(output, descriptor.arguments(), (stream, argument) -> {
            writeString(stream, argument.name());
            writeValue(stream, argument.value());
        });
    }

    private static Descriptor readDescriptor(DataInputStream input) throws IOException {
        return new Descriptor(readString(input, MAX_SYMBOL_BYTES), input.readInt(), readList(input, MAX_ARGUMENTS,
                item -> new Argument(readString(item, MAX_SYMBOL_BYTES), readValue(item))));
    }

    private static void writeValue(DataOutputStream output, Value value) throws IOException {
        writeType(output, value.type());
        switch (value) {
            case BooleanValue bool -> output.writeBoolean(bool.value());
            case IntegerValue integer -> output.writeLong(integer.value());
            case TextValue text -> writeString(output, text.value());
        }
    }

    private static Value readValue(DataInputStream input) throws IOException {
        return switch (readType(input)) {
            case BOOLEAN -> new BooleanValue(flag(input));
            case INTEGER -> new IntegerValue(input.readLong());
            case TEXT -> new TextValue(readString(input, MAX_TEXT_BYTES));
        };
    }

    private static void writeType(DataOutput output, ValueType type) throws IOException {
        output.writeByte(switch (type) {
            case BOOLEAN -> 1;
            case INTEGER -> 2;
            case TEXT -> 3;
        });
    }

    private static ValueType readType(DataInput input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 1 -> ValueType.BOOLEAN;
            case 2 -> ValueType.INTEGER;
            case 3 -> ValueType.TEXT;
            default -> throw new IOException("unsupported Scope D value type");
        };
    }

    private static void writeSetting(DataOutputStream output, Setting setting) throws IOException {
        writeString(output, setting.name());
        writeExpression(output, setting.expression());
    }

    private static void writeExpression(DataOutputStream output, Expression expression) throws IOException {
        switch (expression) {
            case Literal literal -> {
                output.writeByte(1);
                writeValue(output, literal.value());
            }
            case CreationInput input -> {
                output.writeByte(2);
                writeString(output, input.name());
                writeType(output, input.type());
            }
            case Equal equal -> {
                output.writeByte(3);
                writeExpression(output, equal.left());
                writeExpression(output, equal.right());
            }
            case Select select -> {
                output.writeByte(4);
                writeType(output, select.type());
                writeExpression(output, select.condition());
                writeExpression(output, select.whenTrue());
                writeExpression(output, select.whenFalse());
            }
        }
    }

    private static Expression readExpression(DataInputStream input, int depth, ExpressionBudget budget) throws IOException {
        budget.accept(depth);
        return switch (input.readUnsignedByte()) {
            case 1 -> new Literal(readValue(input));
            case 2 -> new CreationInput(readString(input, MAX_SYMBOL_BYTES), readType(input));
            case 3 -> new Equal(readExpression(input, depth + 1, budget), readExpression(input, depth + 1, budget));
            case 4 -> new Select(readType(input), readExpression(input, depth + 1, budget),
                    readExpression(input, depth + 1, budget), readExpression(input, depth + 1, budget));
            default -> throw new IOException("unsupported Scope D expression shape");
        };
    }

    private static void writeHandler(DataOutputStream output, Handler handler) throws IOException {
        output.writeLong(handler.id().value());
        writeString(output, handler.event());
        output.writeInt(handler.eventVersion());
        writeList(output, handler.blocks(), ScopeDDataCodec::writeBlock);
    }

    private static Handler readHandler(DataInputStream input, SpellIdentity identity, ReadBudget budget) throws IOException {
        var id = new HandlerId(input.readLong());
        var event = readString(input, MAX_SYMBOL_BYTES);
        int version = input.readInt();
        var blocks = readList(input, MAX_BLOCKS - budget.blocks, item -> readBlock(item, identity, id, budget));
        budget.blocks += blocks.size();
        return new Handler(id, event, version, blocks);
    }

    private static void writeBlock(DataOutputStream output, Block block) throws IOException {
        switch (block) {
            case CreateSpell create -> {
                output.writeByte(1);
                output.writeLong(create.id().value());
                writeIdentity(output, create.target().target());
            }
            case EffectBlock effect -> {
                output.writeByte(2);
                output.writeLong(effect.id().value());
                writeDescriptor(output, effect.descriptor());
            }
            case UnknownBlock unknown -> {
                output.writeByte(3);
                output.writeLong(unknown.id().value());
                writeRaw(output, unknown.raw());
            }
        }
    }

    private static Block readBlock(DataInputStream input, SpellIdentity identity, HandlerId handler, ReadBudget budget)
            throws IOException {
        int kind = input.readUnsignedByte();
        var id = new BlockId(input.readLong());
        return switch (kind) {
            case 1 -> new CreateSpell(id, new SpellIdentityReference(readSpellIdentity(input)));
            case 2 -> new EffectBlock(id, readDescriptor(input));
            case 3 -> new UnknownBlock(id, readRaw(input, budget, new RawLocation(identity, new BlockPayload(handler, id))));
            default -> throw new IOException("unsupported Scope D block shape");
        };
    }

    private static <T> void writeSlot(DataOutputStream output, Slot<T> slot, Writer<T> writer) throws IOException {
        switch (slot) {
            case Missing<T> ignored -> output.writeByte(0);
            case Absent<T> ignored -> output.writeByte(1);
            case Known<T> known -> {
                output.writeByte(2);
                writer.write(output, known.value());
            }
            case Unknown<T> unknown -> {
                output.writeByte(3);
                writeRaw(output, unknown.raw());
            }
        }
    }

    private static <T> Slot<T> readSlot(DataInputStream input, ReadBudget budget, RawLocation location, Reader<T> reader)
            throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new Missing<>();
            case 1 -> new Absent<>();
            case 2 -> new Known<>(reader.read(input));
            case 3 -> new Unknown<>(readRaw(input, budget, location));
            default -> throw new IOException("unsupported Scope D slot shape");
        };
    }

    private static void writeRaw(DataOutputStream output, RawEnvelope raw) throws IOException {
        output.writeByte(RAW_LOCATION_VERSION);
        writeIdentity(output, raw.location().identity());
        writePosition(output, raw.location().position());
        writeString(output, raw.tag());
        output.writeInt(raw.payloadVersion());
        output.writeByte(raw.context().family() == SerializedTreeFamily.JSON ? 1 : 2);
        output.writeBoolean(raw.context().registryContext());
        output.writeBoolean(raw.context().compressedMaps());
        output.writeInt(raw.byteCount());
        output.write(raw.copyBytes());
    }

    private static RawEnvelope readRaw(DataInputStream input, ReadBudget budget, RawLocation expected) throws IOException {
        requireVersion(input, RAW_LOCATION_VERSION);
        var location = new RawLocation(readIdentity(input), readPosition(input));
        if (!location.equals(expected)) {
            throw new IOException("raw slot location mismatch");
        }
        var tag = readString(input, MAX_SYMBOL_BYTES);
        var version = input.readInt();
        var family = switch (input.readUnsignedByte()) {
            case 1 -> SerializedTreeFamily.JSON;
            case 2 -> SerializedTreeFamily.NBT;
            default -> throw new IOException("unsupported raw slot tree family");
        };
        var context = new SerializedTreeContext(family, flag(input), flag(input));
        int length = input.readInt();
        if (length <= 0 || length > MAX_RAW_SLOT_BYTES || length > input.available()) {
            throw new IOException("raw slot length exceeded or truncated");
        }
        budget.rawSlots = addBounded(budget.rawSlots, 1, MAX_RAW_SLOTS, "raw slots");
        budget.rawBytes = addBounded(budget.rawBytes, length, MAX_RAW_BYTES, "aggregate raw bytes");
        var bytes = new byte[length];
        input.readFully(bytes);
        return RawEnvelope.fromBytes(location, tag, version, context, bytes);
    }

    private static void writePosition(DataOutput output, RawPosition position) throws IOException {
        switch (position) {
            case RawField field -> output.writeByte(switch (field) {
                case BACKEND -> 1;
                case ACTIVATION -> 2;
                case SETTINGS -> 3;
                case HANDLERS -> 4;
                case APPEARANCE -> 5;
                case PHENOMENON -> 6;
                case ENTRIES -> 7;
            });
            case BlockPayload block -> {
                output.writeByte(8);
                output.writeLong(block.handler().value());
                output.writeLong(block.block().value());
            }
        }
    }

    private static RawPosition readPosition(DataInput input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 1 -> RawField.BACKEND;
            case 2 -> RawField.ACTIVATION;
            case 3 -> RawField.SETTINGS;
            case 4 -> RawField.HANDLERS;
            case 5 -> RawField.APPEARANCE;
            case 6 -> RawField.PHENOMENON;
            case 7 -> RawField.ENTRIES;
            case 8 -> new BlockPayload(new HandlerId(input.readLong()), new BlockId(input.readLong()));
            default -> throw new IOException("unsupported raw slot location");
        };
    }

    private static void writeString(DataOutput output, String value) throws IOException {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, int maximum) throws IOException {
        int length = input.readUnsignedShort();
        if (length > maximum || length > input.available()) {
            throw new IOException("Scope D text length exceeded or truncated");
        }
        var bytes = new byte[length];
        input.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static boolean flag(DataInput input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> false;
            case 1 -> true;
            default -> throw new IOException("invalid Scope D boolean marker");
        };
    }

    private static void requireVersion(DataInput input, int expected) throws IOException {
        if (input.readUnsignedByte() != expected) {
            throw new IOException("unsupported Scope D frame version");
        }
    }

    private static <T> void writeList(DataOutputStream output, List<T> values, Writer<T> writer) throws IOException {
        output.writeShort(values.size());
        for (var value : values) {
            writer.write(output, value);
        }
    }

    private static <T> List<T> readList(DataInputStream input, int maximum, Reader<T> reader) throws IOException {
        int count = input.readUnsignedShort();
        if (count > maximum || count > input.available()) {
            throw new IOException("Scope D list count exceeded or truncated");
        }
        var values = new ArrayList<T>(count);
        for (int index = 0; index < count; index++) {
            values.add(reader.read(input));
        }
        return values;
    }

    @FunctionalInterface
    private interface Writer<T> { void write(DataOutputStream output, T value) throws IOException; }

    @FunctionalInterface
    private interface Reader<T> { T read(DataInputStream input) throws IOException; }

    private static final class ReadBudget {
        private final ExpressionBudget expressions = new ExpressionBudget();
        private int blocks;
        private int rawSlots;
        private int rawBytes;
    }
}
