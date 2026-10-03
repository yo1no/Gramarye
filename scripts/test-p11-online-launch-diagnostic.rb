#!/usr/bin/env ruby
# Synthetic, JDK-only processes. No vendor launch, account store, network, or private-console input.
require 'fileutils'
require 'json'
require 'minitest/autorun'
require 'open3'
require 'tmpdir'

class P11OnlineLaunchDiagnosticTest < Minitest::Test
  REPO = File.expand_path('..', __dir__).freeze
  JAVA_HOME = '/Users/yashen/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.8+9/Contents/Home'.freeze
  SOURCE = File.join(REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineLaunchDiagnostic.java').freeze
  SENTINEL = 'SYNTHETIC_SECRET_DO_NOT_SERIALIZE'.freeze
  CLEAN_JAVA_ENV = { 'JAVA_TOOL_OPTIONS' => nil, 'JDK_JAVA_OPTIONS' => nil, '_JAVA_OPTIONS' => nil }.freeze

  def self.prepare_fixture
    return @fixture if @fixture
    root = Dir.mktmpdir('gramarye-p11-diagnostic-test-', '/private/tmp')
    classes = File.join(root, 'classes')
    Dir.mkdir(classes)
    sources = {
      'fixture/Primary.java' => <<~JAVA,
        package fixture;
        public final class Primary {
            public static final java.io.IOException VALUE = new java.io.IOException("#{SENTINEL}");
        }
      JAVA
      'fixture/OriginalAgent.java' => <<~JAVA,
        package fixture;
        public final class OriginalAgent {
            public static void premain(String arg, java.lang.instrument.Instrumentation unused) {
                Thread.currentThread().setUncaughtExceptionHandler((thread, primary) -> {
                    System.err.println("ORIGINAL_HANDLER_CALLED");
                    if (primary == Primary.VALUE) System.err.println("ORIGINAL_PRIMARY_IDENTITY");
                });
            }
        }
      JAVA
      'net/covers1624/devlogin/DevLogin.java' => <<~JAVA,
        package net.covers1624.devlogin;
        // Name-only fixture for the exact stack allowlist. This is NOT the vendor implementation.
        public final class DevLogin {
            public static void main(String[] args) throws Throwable {
                switch (args[0]) {
                    case "explicit" -> System.exit(7);
                    case "oauth-exit" -> MicrosoftOAuth.getMinecraftProfile();
                    case "throw" -> MicrosoftOAuth.authenticateWithXSTS();
                    case "reflection" -> throw new java.lang.reflect.InvocationTargetException(fixture.Primary.VALUE);
                    case "unknown-throw" -> throw new UnlistedFailure("#{SENTINEL}");
                    case "normal" -> { }
                    case "async" -> {
                        Thread other = new Thread(() -> System.exit(11));
                        other.start();
                        other.join();
                    }
                    case "halt" -> Runtime.getRuntime().halt(13);
                    case "replacement" -> {
                        Thread.currentThread().setUncaughtExceptionHandler((thread, primary) ->
                                System.err.println("LATER_NATIVE_HANDLER"));
                        throw fixture.Primary.VALUE;
                    }
                    default -> throw new AssertionError("SYNTHETIC_BAD_MODE");
                }
            }
            private static final class UnlistedFailure extends RuntimeException {
                private static final long serialVersionUID = 1L;
                private UnlistedFailure(String text) { super(text); }
            }
        }
      JAVA
      'net/covers1624/devlogin/MicrosoftOAuth.java' => <<~JAVA,
        package net.covers1624.devlogin;
        public final class MicrosoftOAuth {
            public static void getMinecraftProfile() { System.exit(9); }
            public static void authenticateWithXSTS() throws java.io.IOException { throw fixture.Primary.VALUE; }
        }
      JAVA
      'fixture/ClassifierProbe.java' => <<~JAVA
        package fixture;
        import java.lang.reflect.Method;
        public final class ClassifierProbe {
            public static void main(String[] args) throws Exception {
                Class<?> type = Class.forName("com.yo1no.gramarye.P11OnlineLaunchDiagnostic");
                Method stage = type.getDeclaredMethod("stage", StackTraceElement[].class);
                stage.setAccessible(true);
                Method exit = type.getDeclaredMethod("explicitExit", StackTraceElement[].class);
                exit.setAccessible(true);
                StackTraceElement oauth = frame("net.covers1624.devlogin.MicrosoftOAuth", "getMinecraftProfile");
                StackTraceElement transport = frame("net.covers1624.devlogin.http.java11.JavaHttpEngine", "makeRequest");
                check("MINECRAFT_PROFILE".equals(stage.invoke(null, (Object) new StackTraceElement[] { transport, oauth })));
                check("UNKNOWN".equals(stage.invoke(null, (Object) new StackTraceElement[] {
                        frame("net.covers1624.devlogin.MicrosoftOAuthExtra", "getMinecraftProfile"),
                        frame("net.covers1624.devlogin.MicrosoftOAuth", "getMinecraftProfileExtra") })));
                StackTraceElement shutdown = frame("java.lang.Shutdown", "exit");
                StackTraceElement runtime = frame("java.lang.Runtime", "exit");
                StackTraceElement system = frame("java.lang.System", "exit");
                check(Boolean.TRUE.equals(exit.invoke(null, (Object) new StackTraceElement[] { shutdown, runtime, system, oauth })));
                check(Boolean.FALSE.equals(exit.invoke(null, (Object) new StackTraceElement[] { system, runtime, shutdown, oauth })));
                check(Boolean.FALSE.equals(exit.invoke(null, (Object) new StackTraceElement[] { runtime, system, oauth })));
                check(Boolean.FALSE.equals(exit.invoke(null, (Object) new StackTraceElement[] { oauth })));
                System.out.println("EXACT_CLASSIFIER_CONTROLS_PASS");
            }
            private static StackTraceElement frame(String owner, String method) {
                return new StackTraceElement(owner, method, "#{SENTINEL}", 123);
            }
            private static void check(boolean condition) {
                if (!condition) throw new AssertionError("SYNTHETIC_CLASSIFIER_FAILURE");
            }
        }
      JAVA
    }
    sources.each do |name, text|
      file = File.join(root, name)
      FileUtils.mkdir_p(File.dirname(file))
      File.write(file, text)
    end
    run_tool(File.join(JAVA_HOME, 'bin/javac'), '--release', '21', '-Xlint:all', '-Werror', '-g',
             '-d', classes, SOURCE, *sources.keys.map { |name| File.join(root, name) })
    agent = File.join(root, 'diagnostic.jar')
    original = File.join(root, 'original.jar')
    [['diagnostic', 'com.yo1no.gramarye.P11OnlineLaunchDiagnostic', agent, 'com/yo1no/gramarye'],
     ['original', 'fixture.OriginalAgent', original, 'fixture']].each do |name, entry, jar, family|
      manifest = File.join(root, name + '.mf')
      File.write(manifest, "Manifest-Version: 1.0\nPremain-Class: #{entry}\n\n")
      run_tool(File.join(JAVA_HOME, 'bin/jar'), '--create', '--file', jar, '--manifest', manifest, '-C', classes, family)
    end
    @fixture = { root: root, classes: classes, agent: agent, original: original }
    Minitest.after_run { FileUtils.remove_entry(root) }
    @fixture
  end

  def self.run_tool(*argv)
    stdout, stderr, status = Open3.capture3(CLEAN_JAVA_ENV, *argv)
    raise "SYNTHETIC_TOOL_FAILURE: #{stdout}#{stderr}" unless status.success?
  end

  def setup
    @fixture = self.class.prepare_fixture
    @output = Dir.mktmpdir('output-', @fixture[:root])
    File.chmod(0o700, @output)
  end

  def run_probe(mode, observe: true, output: @output)
    argv = [File.join(JAVA_HOME, 'bin/java'), '-javaagent:' + @fixture[:original]]
    argv << '-javaagent:' + @fixture[:agent] + '=' + output if observe
    argv += ['-cp', @fixture[:classes], 'net.covers1624.devlogin.DevLogin', mode]
    stdout, stderr, status = Open3.capture3(CLEAN_JAVA_ENV, *argv)
    [stdout, stderr, status.exitstatus]
  end

  def record(name)
    file = File.join(@output, name + '.json')
    assert_equal 0o600, File.stat(file).mode & 0o777
    raw = File.binread(file)
    refute_includes raw, SENTINEL
    result = JSON.parse(raw)
    assert_equal %w[authentication category event schema stage], result.keys.sort
    assert_equal 1, result['schema']
    assert_equal 'NOT_PROVEN', result['authentication']
    result
  end

  def test_explicit_main_exit_is_observed_without_changing_exit_code
    assert_equal 7, run_probe('explicit', observe: false).last
    assert_equal ['', '', 7], run_probe('explicit')
    assert_equal 'PREMAIN', record('started')['stage']
    actual = record('shutdown')
    assert_equal 'MAIN_EXPLICIT_EXIT_NOT_AUTH_PROOF', actual['event']
    assert_equal 'DEVLOGIN_MAIN', actual['stage']
    assert_equal 'NOT_OBSERVED', actual['category']
    refute File.exist?(File.join(@output, 'uncaught.json'))
  end

  def test_specific_oauth_exit_and_original_native_exit_are_preserved
    assert_equal 9, run_probe('oauth-exit', observe: false).last
    assert_equal ['', '', 9], run_probe('oauth-exit')
    assert_equal 'MINECRAFT_PROFILE', record('shutdown')['stage']
  end

  def test_uncaught_same_primary_delegates_exactly_once_and_never_serializes_message
    baseline = run_probe('throw', observe: false)
    actual = run_probe('throw')
    assert_equal baseline, actual
    assert_equal ['', "ORIGINAL_HANDLER_CALLED\nORIGINAL_PRIMARY_IDENTITY\n", 1], actual
    uncaught = record('uncaught')
    assert_equal 'XSTS_AUTH', uncaught['stage']
    assert_equal 'IO', uncaught['category']
    assert_equal 'UNKNOWN', record('shutdown')['stage']
  end

  def test_unknown_throwable_and_reflective_cause_are_not_unwrapped_or_serialized
    assert_equal 1, run_probe('unknown-throw').last
    assert_equal 'UNKNOWN', record('uncaught')['category']
    other = Dir.mktmpdir('reflection-', @fixture[:root])
    assert_equal 1, run_probe('reflection', output: other).last
    raw = File.binread(File.join(other, 'uncaught.json'))
    refute_includes raw, SENTINEL
    assert_equal 'REFLECTION', JSON.parse(raw)['category']
  end

  def test_normal_return_does_not_claim_failure_or_authentication
    assert_equal ['', '', 0], run_probe('normal')
    actual = record('shutdown')
    assert_equal 'SHUTDOWN_UNKNOWN_NOT_AUTH_PROOF', actual['event']
    assert_equal 'UNKNOWN', actual['stage']
    refute File.exist?(File.join(@output, 'uncaught.json'))
  end

  def test_other_thread_exit_is_not_inferred_from_main_vendor_frames
    assert_equal ['', '', 11], run_probe('async')
    actual = record('shutdown')
    assert_equal 'SHUTDOWN_UNKNOWN_NOT_AUTH_PROOF', actual['event']
    assert_equal 'UNKNOWN', actual['stage']
  end

  def test_halt_has_no_shutdown_evidence_and_native_code_is_unchanged
    assert_equal ['', '', 13], run_probe('halt')
    assert_equal 'PREMAIN', record('started')['stage']
    refute File.exist?(File.join(@output, 'shutdown.json'))
  end

  def test_later_native_uncaught_handler_replacement_is_not_overridden
    assert_equal ['', "LATER_NATIVE_HANDLER\n", 1], run_probe('replacement')
    refute File.exist?(File.join(@output, 'uncaught.json'))
    assert_equal 'UNKNOWN', record('shutdown')['stage']
  end

  def test_create_new_collision_never_replaces_prior_diagnostic_or_primary
    File.write(File.join(@output, 'uncaught.json'), 'PRIOR_FIXED_DIAGNOSTIC')
    stdout, stderr, code = run_probe('throw')
    assert_equal '', stdout
    assert_equal 1, code
    assert_equal "P11_LAUNCH_DIAGNOSTIC_UNAVAILABLE\nORIGINAL_HANDLER_CALLED\nORIGINAL_PRIMARY_IDENTITY\n", stderr
    assert_equal 'PRIOR_FIXED_DIAGNOSTIC', File.binread(File.join(@output, 'uncaught.json'))
  end

  def test_invalid_output_never_creates_directory_or_changes_vendor_exit
    absent = File.join(@fixture[:root], 'absent-output')
    assert_equal ['', "P11_LAUNCH_DIAGNOSTIC_UNAVAILABLE\n", 7], run_probe('explicit', output: absent)
    refute File.exist?(absent)
    File.chmod(0o755, @output)
    assert_equal ['', "P11_LAUNCH_DIAGNOSTIC_UNAVAILABLE\n", 7], run_probe('explicit')
    assert_empty Dir.children(@output)
  end

  def test_symlink_output_is_rejected_without_writing_to_target
    link = File.join(@fixture[:root], 'linked-output')
    File.symlink(@output, link)
    assert_equal ['', "P11_LAUNCH_DIAGNOSTIC_UNAVAILABLE\n", 7], run_probe('explicit', output: link)
    assert_empty Dir.children(@output)
  end

  def test_exact_code_names_exit_chain_and_specific_stage_priority
    stdout, stderr, status = Open3.capture3(CLEAN_JAVA_ENV, File.join(JAVA_HOME, 'bin/java'),
                                          '-cp', @fixture[:classes], 'fixture.ClassifierProbe')
    assert status.success?
    assert_equal "EXACT_CLASSIFIER_CONTROLS_PASS\n", stdout
    assert_empty stderr
  end
end
