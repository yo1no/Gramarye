package com.yo1no.gramarye;

import java.lang.instrument.Instrumentation;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/** Excluded launcher diagnostic; no transformer, authentication intervention, or private-output reader. */
public final class P11OnlineLaunchDiagnostic {
    private P11OnlineLaunchDiagnostic() { }

    public static void premain(String outputDirectory, Instrumentation unused) {
        try {
            Path output = Path.of(outputDirectory);
            if (!output.isAbsolute() || !output.equals(output.normalize())
                    || !Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS)
                    || !output.equals(output.toRealPath())
                    || !Files.getPosixFilePermissions(output).equals(PosixFilePermissions.fromString("rwx------"))) {
                unavailable();
                return;
            }
            Thread launchThread = Thread.currentThread();
            Thread.UncaughtExceptionHandler original = launchThread.getUncaughtExceptionHandler();
            if (original == null) {
                unavailable();
                return;
            }
            write(output, "started.json", "PREMAIN_STARTED_NOT_AUTH_PROOF", "PREMAIN", "NONE");
            launchThread.setUncaughtExceptionHandler((thread, primary) -> {
                try {
                    write(output, "uncaught.json", "UNCAUGHT_NOT_AUTH_PROOF",
                            stage(primary.getStackTrace()), category(primary));
                } catch (Throwable diagnosticFailure) {
                    unavailable();
                } finally {
                    original.uncaughtException(thread, primary);
                }
            });
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    StackTraceElement[] frames = launchThread.getStackTrace();
                    boolean explicitExit = explicitExit(frames);
                    write(output, "shutdown.json", explicitExit ? "MAIN_EXPLICIT_EXIT_NOT_AUTH_PROOF"
                                    : "SHUTDOWN_UNKNOWN_NOT_AUTH_PROOF",
                            explicitExit ? stage(frames) : "UNKNOWN", "NOT_OBSERVED", exitSite(frames));
                } catch (Throwable diagnosticFailure) {
                    unavailable();
                }
            }, "p11-fixed-launch-diagnostic"));
        } catch (Throwable diagnosticFailure) {
            unavailable();
        }
    }

    private static boolean explicitExit(StackTraceElement[] frames) {
        int shutdown = -1;
        int runtime = -1;
        int system = -1;
        for (int i = 0; i < frames.length; i++) {
            StackTraceElement frame = frames[i];
            if (!frame.getMethodName().equals("exit")) continue;
            switch (frame.getClassName()) {
                case "java.lang.Shutdown" -> shutdown = i;
                case "java.lang.Runtime" -> runtime = i;
                case "java.lang.System" -> system = i;
                default -> { }
            }
        }
        return shutdown >= 0 && runtime > shutdown && system > runtime;
    }

    private enum ExitSite {
        NOT_OBSERVED, DEVLOGIN_MISSING_TARGET, DEVLOGIN_DUMP_REQUESTED,
        DEVLOGIN_ARGUMENT_BOUND, NATIVE_CLIENT_DESTROY, OTHER_EXPLICIT_EXIT
    }

    /** Fixed public bytecode sites only. A deeper DevLogin.main frame is NOT the exit caller. */
    private static ExitSite exitSite(StackTraceElement[] frames) {
        if (!explicitExit(frames)) { return ExitSite.NOT_OBSERVED; }
        int system = -1;
        for (int i = 0; i < frames.length; i++) {
            if (frames[i].getClassName().equals("java.lang.System") && frames[i].getMethodName().equals("exit")) {
                if (system >= 0) { return ExitSite.OTHER_EXPLICIT_EXIT; }
                system = i;
            }
        }
        if (system < 0 || system + 1 >= frames.length) { return ExitSite.OTHER_EXPLICIT_EXIT; }
        StackTraceElement caller = frames[system + 1];
        if (caller.getClassName().equals("net.covers1624.devlogin.DevLogin")) {
            // DevLogin0.1.0.5 SHA256 dfb3379a190c57c9601f34adc5bcb01c7d3298ffa02c97f1b26428c9301bbf86.
            if (caller.getMethodName().equals("main")) {
                if (caller.getLineNumber() == 45) { return ExitSite.DEVLOGIN_MISSING_TARGET; }
                if (caller.getLineNumber() == 99) { return ExitSite.DEVLOGIN_DUMP_REQUESTED; }
            } else if (caller.getMethodName().equals("consumeArgs") && caller.getLineNumber() == 120) {
                return ExitSite.DEVLOGIN_ARGUMENT_BOUND;
            }
        } else if (caller.getClassName().equals("net.minecraft.client.Minecraft")
                && caller.getMethodName().equals("destroy") && caller.getLineNumber() == 1098) {
            // Public pinned NeoForge21.1.241, original finally exit0; not an authentication result.
            return ExitSite.NATIVE_CLIENT_DESTROY;
        }
        return ExitSite.OTHER_EXPLICIT_EXIT;
    }

    private static String stage(StackTraceElement[] frames) {
        String fallback = "UNKNOWN";
        int fallbackPriority = 0;
        for (StackTraceElement frame : frames) {
            String owner = frame.getClassName();
            String method = frame.getMethodName();
            if (owner.equals("net.covers1624.devlogin.MicrosoftOAuth")) {
                String specific = switch (method) {
                    case "deviceAuth", "startDeviceAuth", "checkDeviceAuth" -> "DEVICE_AUTH";
                    case "refreshMicrosoftAuth", "refreshMicrosoftToken" -> "MS_REFRESH";
                    case "authenticateWithXBL" -> "XBL_AUTH";
                    case "authenticateWithXSTS" -> "XSTS_AUTH";
                    case "authenticateWithMinecraft" -> "MINECRAFT_AUTH";
                    case "getMinecraftProfile" -> "MINECRAFT_PROFILE";
                    case "loginToAccount" -> "ACCOUNT_LOGIN";
                    case "validateAccount" -> "ACCOUNT_VALIDATE";
                    case "refreshMinecraftToken" -> "MC_REFRESH";
                    default -> "UNKNOWN";
                };
                if (!specific.equals("UNKNOWN")) return specific;
            }
            String candidate = "UNKNOWN";
            int priority = 0;
            if (owner.equals("net.covers1624.devlogin.DevLogin")) {
                candidate = switch (method) {
                    case "consumeArgs" -> "LAUNCH_ARGUMENTS";
                    case "loadAccounts" -> "PROFILE_READ";
                    case "saveAccounts" -> "PROFILE_WRITE";
                    case "main" -> "DEVLOGIN_MAIN";
                    default -> "UNKNOWN";
                };
                priority = method.equals("main") ? 1 : 3;
            } else if (owner.equals("net.covers1624.devlogin.http.HttpEngine")
                    && (method.equals("selectEngine") || method.equals("tryLoad"))) {
                candidate = "HTTP_ENGINE";
                priority = 2;
            } else if ((owner.equals("net.covers1624.devlogin.http.java11.JavaHttpEngine")
                    || owner.equals("net.covers1624.devlogin.http.apache.ApacheHttpEngine"))
                    && method.equals("makeRequest")) {
                candidate = "HTTP_TRANSPORT";
                priority = 2;
            } else if (owner.equals("net.neoforged.devlaunch.Main") && method.equals("main")) {
                candidate = "DEVLAUNCH";
                priority = 1;
            }
            if (!candidate.equals("UNKNOWN") && priority > fallbackPriority) {
                fallback = candidate;
                fallbackPriority = priority;
            }
        }
        return fallback;
    }

    private static String category(Throwable primary) {
        return switch (primary.getClass().getName()) {
            case "java.io.IOException", "java.io.UncheckedIOException", "java.io.FileNotFoundException",
                    "java.nio.file.NoSuchFileException", "java.nio.file.AccessDeniedException" -> "IO";
            case "java.net.SocketTimeoutException", "java.net.http.HttpTimeoutException",
                    "java.net.http.HttpConnectTimeoutException" -> "NETWORK_TIMEOUT";
            case "java.net.ConnectException", "java.net.UnknownHostException" -> "NETWORK_CONNECT";
            case "javax.net.ssl.SSLException", "javax.net.ssl.SSLHandshakeException" -> "TLS";
            case "java.lang.IllegalArgumentException" -> "ARGUMENT";
            case "java.lang.IllegalStateException" -> "STATE";
            case "java.lang.RuntimeException" -> "RUNTIME";
            case "java.lang.reflect.InvocationTargetException", "java.lang.IllegalAccessException",
                    "java.lang.NoSuchMethodException" -> "REFLECTION";
            case "java.lang.ClassNotFoundException", "java.lang.NoClassDefFoundError",
                    "java.lang.UnsatisfiedLinkError", "java.lang.ExceptionInInitializerError" -> "LINKAGE";
            case "com.google.gson.JsonSyntaxException" -> "JSON_SYNTAX";
            case "com.google.gson.JsonIOException" -> "JSON_IO";
            case "net.covers1624.devlogin.MicrosoftOAuth$GrantExpiredException" -> "DEVICE_GRANT_EXPIRED";
            case "java.lang.OutOfMemoryError", "java.lang.StackOverflowError" -> "VM_RESOURCE";
            default -> "UNKNOWN";
        };
    }

    private static void write(Path output, String name, String event, String stage, String category) throws Exception {
        write(output, name, event, stage, category, ExitSite.NOT_OBSERVED);
    }

    private static void write(Path output, String name, String event, String stage, String category, ExitSite exitSite) throws Exception {
        // Every serialized value is an internal fixed literal. No input, frame, message, or exception is serialized.
        String json = "{\"schema\":2,\"event\":\"" + event + "\",\"stage\":\"" + stage
                + "\",\"category\":\"" + category + "\",\"exitSite\":\"" + exitSite.name()
                + "\",\"authentication\":\"NOT_PROVEN\"}\n";
        try (SeekableByteChannel channel = Files.newByteChannel(output.resolve(name),
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(json);
            while (bytes.hasRemaining()) channel.write(bytes);
        }
    }

    private static void unavailable() {
        try {
            System.err.println("P11_LAUNCH_DIAGNOSTIC_UNAVAILABLE");
        } catch (Throwable ignored) {
            // A diagnostic failure must never replace vendor authentication, an uncaught primary, or native shutdown.
        }
    }
}
