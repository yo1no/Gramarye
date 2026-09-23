package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Bounded operator-only observation of system content; no player-state dependencies. */
final class P10TemplateValidateCommand {
    private final P10TemplateService templates;
    private final P10TemplateValidation validation;

    P10TemplateValidateCommand(P10TemplateService templates, P10TemplateValidation validation) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.validation = Objects.requireNonNull(validation, "validation");
    }

    void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skill")
                .then(Commands.literal("validate").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("template")
                                .then(Commands.argument("resource_location", ResourceLocationArgument.id())
                                        .executes(context -> validate(context.getSource(),
                                                ResourceLocationArgument.getId(context, "resource_location")))))));
    }

    private int validate(CommandSourceStack source, net.minecraft.resources.ResourceLocation id) {
        var server = source.getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Template command requires the server thread");
        var response = inspectTemplate(id, () -> templates.capture(server),
                body -> validation.validate(server, body), captured -> templates.isCurrent(server, captured));
        for (var component : response.components()) {
            if (response.returnCode() == 1) source.sendSuccess(() -> component, false);
            else source.sendFailure(component);
        }
        return response.returnCode();
    }

    static Response inspectTemplate(net.minecraft.resources.ResourceLocation id,
            Supplier<P10TemplateService.Capture> capture,
            Function<P10TemplateBody, P10TemplateValidation.Result> validate,
            Predicate<P10TemplateService.Capture> isCurrent) {
        var captured = capture.get();
        var report = ValidationResult.valid();
        final Primary primary;
        if (!id.equals(P10TemplateCodec.TEMPLATE_ID)) {
            primary = Primary.UNSUPPORTED_TEMPLATE_ID;
        } else if (captured.body().isEmpty()) {
            primary = Primary.UNAVAILABLE;
        } else if (!captured.contextCurrent()) {
            primary = Primary.STALE_CONTEXT;
        } else {
            var result = validate.apply(captured.body().orElseThrow());
            report = result.report();
            primary = !isCurrent.test(captured) ? Primary.STALE_CONTEXT
                    : result.classification() == P10TemplateValidation.Classification.CONTEXT_UNAVAILABLE
                            ? Primary.INTERNAL_UNAVAILABLE
                            : !result.ready() ? Primary.STALE_CONTEXT
                                    : captured.origin() == P10TemplateService.Origin.CURRENT
                                            ? Primary.READY_CURRENT : Primary.READY_LKG;
        }
        return response(primary, report, captured.lastAttempt());
    }

    static Response response(Primary primary, ValidationResult current,
            P10TemplateService.AttemptStatus latest) {
        var issues = new ArrayList<Component>(16);
        for (var issue : current.issues()) {
            if (issues.size() == 16) break;
            issues.add(Component.literal(summary(issue.code().toString(), issue.path().render())));
        }
        for (var summary : latest.summaries()) {
            if (issues.size() == 16) break;
            issues.add(Component.literal(bounded(summary)));
        }
        int omitted = current.issues().size() + latest.retainedCount() - issues.size();
        boolean truncated = current.truncated() || latest.upstreamTruncated();
        var components = new ArrayList<Component>(17);
        components.add(Component.literal(bounded(primary.name() + " lastAttempt="
                + latest.classification().name() + " displayOmitted=" + omitted
                + " upstreamTruncated=" + truncated)));
        components.addAll(issues);
        int length = 0;
        for (var component : components) {
            int size = component.getString().length();
            if (size > 1_024) throw new IllegalStateException("Template diagnostic component exceeds bound");
            length += size;
        }
        if (components.size() > 17 || length > 17_408 || omitted < 0 || omitted > 2_032) {
            throw new IllegalStateException("Template diagnostic output exceeds bound");
        }
        return new Response(primary == Primary.READY_CURRENT || primary == Primary.READY_LKG ? 1 : 0,
                List.copyOf(components), omitted, truncated);
    }

    static String summary(String code, String path) {
        return bounded(code + " " + path);
    }

    private static String bounded(String value) {
        var safe = new StringBuilder(Math.min(value.length(), 1_024));
        for (int index = 0; index < value.length() && safe.length() < 1_013; index++) {
            char character = value.charAt(index);
            safe.append(character >= 32 && character <= 126 ? character : '?');
        }
        if (value.length() > 1_013) safe.append("[truncated]");
        return safe.toString();
    }

    enum Primary {
        READY_CURRENT, READY_LKG, UNSUPPORTED_TEMPLATE_ID, UNAVAILABLE, STALE_CONTEXT, INTERNAL_UNAVAILABLE
    }

    record Response(int returnCode, List<Component> components, int displayOmitted, boolean upstreamTruncated) {}
}
