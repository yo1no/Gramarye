#!/usr/bin/env ruby
require 'minitest/autorun'
require 'minitest/mock'
require 'tmpdir'
require_relative 'p11-online-runtime'

class P11OnlineRuntimeTest < Minitest::Test
  FIXTURE = <<~TOML.freeze
    # P11 owned engineering fixture; not production defaults.
    runtime.pendingEventsPerSkillInstance = 256
    runtime.pendingEventsPerAttribution = 1024
    runtime.pendingEventsPerServer = 4096
    runtime.activeSkillInstancesPerAttribution = 32
    runtime.activeSkillInstancesPerServer = 128
    runtime.rootAdmissionsPerTick = 64
    runtime.executionsPerSkillInstancePerTick = 64
    runtime.executionsPerAttributionPerTick = 128
    runtime.executionsPerServerPerTick = 512
    runtime.eventsPerSkillInstance = 512
    runtime.maximumDepth = 32
    runtime.directChildrenPerEvent = 32
    runtime.zeroDelayChildrenPerEvent = 16
    runtime.maximumDelayTicks = 12000
    runtime.maximumDeadlineHorizonTicks = 12000
    runtime.cancellationsPerTick = 128
    p11.retention.maxUuids = 4
    p11.control.maxWaitingConnections = 4
    p11.control.admissionWaitMillis = 30000
    p11.control.tryBurst = 4
    p11.control.tryRefillPerSecond = 2
    p11.control.statusBurst = 4
    p11.control.statusRefillPerSecond = 2
    p11.control.mainQuantaPerTick = 4
    p11.save.maxSealedSnapshots = 1
    p11.save.maxSealedBytes = 1
    p11.save.dirtyUuidAdmissionWatermark = 4
    p11.save.oldestDirtyWarnMillis = 30000
  TOML

  def setup
    @root = Dir.mktmpdir('p11-online-script-test-', '/private/tmp')
    %w[repo runtime evidence private].each { |name| Dir.mkdir(File.join(@root, name), 0o700) }
    @fixture = File.join(@root, 'startup.toml')
    File.write(@fixture, FIXTURE)
    @jar = File.join(@root, 'test-distribution.jar')
    File.write(@jar, 'FAKE_DISTRIBUTION_TEST_ONLY')
    @private = File.join(@root, 'private')
    File.write(File.join(@private, 'accounts.json'), 'FAKE_SECRET_SENTINEL_NOT_REAL_CREDENTIALS')
    @java_home = File.join(@root, 'test-jdk')
    Dir.mkdir(@java_home)
    Dir.mkdir(File.join(@java_home, 'bin'))
    File.write(File.join(@java_home, 'bin/java'), "#!/bin/sh\nexit 0\n")
    File.chmod(0o700, File.join(@java_home, 'bin/java'))
    File.write(File.join(@java_home, 'release'), "JAVA_VERSION=\"21.0.8\"\n")
    @options = { repo: File.join(@root, 'repo'), runtime_root: File.join(@root, 'runtime'),
                 evidence_root: File.join(@root, 'evidence'), private_root: @private,
                 fixture: @fixture, jar: @jar, case: 'single', run_id: 'direct-test-001',
                 port: 0, java_home: @java_home, ready_only: false, accept_eula: false }
  end

  def teardown
    if @root && File.directory?(@root)
      Find.find(@root) { |entry| File.chmod(0o700, entry) if File.directory?(entry) && !File.symlink?(entry) }
    end
    FileUtils.remove_entry(@root) if @root && File.directory?(@root)
  end

  # Only the distribution pin is stubbed in preparation tests; pin rejection is tested separately.
  def prepare(changes = {})
    P11OnlineRuntime.stub(:verify_jar!, true) { P11OnlineRuntime.prepare(@options.merge(changes)) }
  end

  def failure(code)
    error = assert_raises(P11OnlineRuntime::Failure) { yield }
    assert_equal code, error.code
  end

  def test_fixed_original_distribution_pin_rejects_test_bytes
    assert_equal '4d79086f628daa137281f71312596d4dda861bafbaa9b62a65cd70e5257d248b', P11OnlineRuntime::JAR_SHA
    failure('FROZEN_JAR_MISMATCH') { P11OnlineRuntime.verify_jar!(@jar) }
  end

  def online_configuration_paths
    %w[
      scripts/p11-online-runtime.rb
      scripts/test-p11-online-runtime.rb
      scripts/test-p11-online-launch-diagnostic.rb
      src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineClientHarness.java
      src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineInputs.java
      src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineLaunchDiagnostic.java
      src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineLoginAccess.java
      src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineNativeContextProbe.java
      src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineServerHarness.java
      src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11OnlineAuthenticatorMixin.java
      src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11OnlineClientObservationMixin.java
      src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11OnlineLoginMixin.java
      src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11OnlineRewardObservationMixin.java
      src/p11OnlineHarness/resources/gramarye-p11-online-harness.mixins.json
      src/p11OnlineHarness/resources/gramarye-p11-online-private-console.xml
    ]
  end

  def test_shared_configuration_scope_accepts_only_exact_online_continuation_paths
    classifier = File.join(P11OnlineRuntime::REPO, 'scripts/verify-p7-s4-source-contracts.sh')
    paths = online_configuration_paths
    assert_equal 15, paths.length
    paths.each do |path|
      assert system('bash', classifier, '--is-s4-path', path), path
      refute system('bash', classifier, '--is-s4-path', path + '.extra'), path
      refute system('bash', classifier, '--is-s4-path', './' + path), path
    end
    %w[
      scripts/p11-online-foreign.rb
      scripts/test-p11-online-runtime.rb/foreign
      src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineForeign.java
      src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11OnlineForeignMixin.java
      src/p11OnlineHarness/resources/foreign.json
      src/p11OnlineHarness/resources/foreign.xml
      src/p11OnlineHarness-extra/resources/gramarye-p11-online-harness.mixins.json
      src/p11OnlineHarness/../main/resources/gramarye-p11-online-harness.mixins.json
      src/main/resources/gramarye-p11-online-harness.mixins.json
      docs/codex-spec/19_P11持久化與多人修正案.md
      gradle.properties
    ].each { |path| refute system('bash', classifier, '--is-s4-path', path), path }
  end

  def test_e1_actual_filetype_consumer_admits_exact_online_ruby_and_resources_only
    source = File.binread(File.join(P11OnlineRuntime::REPO, 'scripts/verify-p4-e1-configuration.sh'))
    body = source[/            case "\$path" in\n.*?            esac/m]
    refute_nil body
    candidate = File.join(@root, 'filetype-candidate')
    File.write(candidate, 'SYNTHETIC_NONEXECUTABLE_FILE')
    File.chmod(0o600, candidate)
    probe = "set -euo pipefail\nfail() { exit 1; }\npath=$1\ncandidate=$2\n" + body
    paths = online_configuration_paths.select { |path| path.end_with?('.rb', '.json', '.xml') }
    assert_equal 5, paths.length
    paths.each do |path|
      assert system('bash', '-c', probe, 'e1-filetype-test', path, candidate), path
      refute system('bash', '-c', probe, 'e1-filetype-test', path + '.extra', candidate), path
    end
    %w[scripts/foreign.rb src/p11OnlineHarness/resources/foreign.json
       src/p11OnlineHarness/resources/foreign.xml
       src/main/resources/gramarye-p11-online-harness.mixins.json].each do |path|
      refute system('bash', '-c', probe, 'e1-filetype-test', path, candidate), path
    end
    File.chmod(0o700, candidate)
    paths.select { |path| path.end_with?('.rb') }.each do |path|
      refute system('bash', '-c', probe, 'e1-filetype-test', path, candidate), path
    end
  end

  def test_direct_gradle_clients_fail_before_input_validation_without_disabling_server_or_generation
    build = File.binread(File.join(P11OnlineRuntime::REPO, 'build.gradle'))
    guard = <<~GROOVY.strip
      if (name == 'runP11OnlineClientA' || name == 'runP11OnlineClientB') {
          throw new GradleException('P11 authenticated clients require frozen helper private-terminal launch')
      }
    GROOVY
    protected_first = lambda do |source|
      block = source.match(/\['runP11OnlineServer', 'runP11OnlineClientA', 'runP11OnlineClientB'\]\.each \{ name ->\s*\
tasks\.named\(name, JavaExec\)\.configure \{\s*standardInput = System\.in\s*doFirst \{\s*(.*?)\s*def paths =/m)
      block && block[1].gsub(/\s+/, ' ').strip == guard.gsub(/\s+/, ' ').strip
    end
    assert protected_first.call(build)
    refute protected_first.call(build.sub("name == 'runP11OnlineClientB'", "name == 'runP11OnlineServer'"))
    refute protected_first.call(build.sub('P11 authenticated clients require frozen helper private-terminal launch', 'different'))
    refute protected_first.call(build.sub("        doFirst {\n            if (name == 'runP11OnlineClientA'",
                                         "        doFirst {\n            p11OnlineJar.isFile()\n            if (name == 'runP11OnlineClientA'"))
  end

  def test_loaded_mod_selection_is_exact_three_online_runs_with_production_fallback
    build = File.binread(File.join(P11OnlineRuntime::REPO, 'build.gradle'))
    expected = "name in ['p11OnlineServer', 'p11OnlineClientA', 'p11OnlineClientB']"
    exact_selector = lambda do |source|
      branches = source.scan(/\? p9S5ClientHarnessMod\s*:\s*(.*?)\s*\? p11OnlineHarnessMod : productionMod\]\)/m)
      branches.length == 1 && branches[0][0] == expected
    end
    assert exact_selector.call(build)
    ["name.startsWith('p11Online')",
     expected.sub('p11OnlineClientB', 'p11OnlineClientBExtra'),
     expected.sub(', \'p11OnlineClientB\'', ''),
     expected.sub(']', ", 'p11OnlineForeign']"),
     expected.sub(']', ", 'p11OnlineClientA']")].each do |mutation|
      refute exact_selector.call(build.sub(expected, mutation)), mutation
    end
    refute exact_selector.call(build.sub('? p11OnlineHarnessMod : productionMod])',
                                         '? productionMod : p11OnlineHarnessMod])'))
  end

  def test_fixture_is_exact_historical_input_and_only_two_keys_change
    assert_equal P11OnlineRuntime::FIXTURE_SHA, Digest::SHA256.hexdigest(FIXTURE)
    %w[single qctx capacity].each do |case_name|
      manifest = prepare(case: case_name, run_id: "direct-test-#{case_name}")
      actual = File.read(File.join(manifest['runtime'], 'server/p11-online-world/serverconfig/gramarye-server.toml'))
      expected = FIXTURE.sub('p11.retention.maxUuids = 4', "p11.retention.maxUuids = #{case_name == 'capacity' ? 1 : 2}")
                        .sub('p11.save.dirtyUuidAdmissionWatermark = 4', "p11.save.dirtyUuidAdmissionWatermark = #{case_name == 'capacity' ? 1 : 2}")
      assert_equal expected, actual
      assert_equal actual, File.read(File.join(manifest['runtime'], 'server/defaultconfigs/gramarye-server.toml'))
    end
  end

  def test_fixture_drift_and_extra_fields_are_not_accepted
    [FIXTURE.sub('maximumDepth = 32', 'maximumDepth = 31'), FIXTURE + "extra = 1\n"].each do |text|
      File.write(@fixture, text)
      failure('STARTUP_FIXTURE_MISMATCH') { prepare }
    end
  end

  def test_fresh_roles_world_properties_datapack_and_no_premade_harness_output
    manifest = prepare(case: 'qctx')
    assert_equal %w[server a b], manifest['roles']
    assert_equal 'NOT_RUN_NOT_PROVEN', manifest['authenticationAcceptance']
    props = File.read(File.join(manifest['runtime'], 'server/server.properties'))
    %w[online-mode=true server-ip=127.0.0.1 enable-rcon=false enable-query=false enable-status=false
       max-players=2 difficulty=normal level-name=p11-online-world].each { |line| assert_includes props.lines.map(&:strip), line }
    world = File.join(manifest['runtime'], 'server/p11-online-world')
    advancement = JSON.parse(File.read(File.join(world, 'datapacks/p11-online-engineering/data/gramarye_p11_engineering/advancement/delivery_root.json')))
    assert_equal({ 'experience' => 0 }, advancement['rewards'])
    assert_equal({ 'manual' => { 'trigger' => 'minecraft:impossible' } }, advancement['criteria'])
    refute advancement['display']['show_toast']
    refute File.exist?(File.join(manifest['evidence'], 'server'))
    refute File.exist?(File.join(manifest['evidence'], 'client-a'))
    assert_equal "eula=false\n", File.read(File.join(manifest['runtime'], 'server/eula.txt'))
    failure('RUN_ALREADY_EXISTS') { prepare(case: 'qctx') }
  end

  def test_private_store_is_not_read_even_when_a_manifest_or_fixture_is_requested_there
    reads = []
    original = File.method(:binread)
    guarded = lambda do |file, *args|
      reads << file
      raise 'FAKE_SECRET_READ' if P11OnlineRuntime.within?(file, @private)
      original.call(file, *args)
    end
    File.stub(:binread, guarded) do
      prepare
      failure('PRIVATE_PATH_FORBIDDEN') { prepare(fixture: File.join(@private, 'accounts.json')) }
      failure('PRIVATE_PATH_FORBIDDEN') { prepare(jar: File.join(@private, 'accounts.json')) }
      failure('PRIVATE_PATH_FORBIDDEN') do
        P11OnlineRuntime.load_manifest(File.join(@private, 'manifest.json'),
                                       runtime_root: @options[:runtime_root], private_root: @private)
      end
    end
    refute reads.any? { |file| P11OnlineRuntime.within?(file, @private) }
  end

  def test_root_overlap_repo_private_and_prefix_near_boundaries
    failure('ROOTS_OVERLAP') { prepare(evidence_root: @options[:runtime_root]) }
    failure('ROOT_IN_REPOSITORY') { prepare(runtime_root: @options[:repo]) }
    failure('PRIVATE_PATH_FORBIDDEN') { prepare(runtime_root: @private) }
    failure('PRIVATE_PATH_FORBIDDEN') { prepare(evidence_root: File.join(@private, 'evidence')) }
    near = File.join(@root, 'private-near')
    Dir.mkdir(near, 0o700)
    assert_equal near, P11OnlineRuntime.nonsecret_path!(near, @private)
    assert_equal @options[:runtime_root], prepare(private_root: @private + '-near')['runtimeRoot']
  end

  def test_symlink_runtime_and_symlink_manifest_are_rejected_before_read
    link = File.join(@root, 'linked-runtime')
    File.symlink(@private, link)
    failure('SYMLINK_FORBIDDEN') { prepare(runtime_root: link) }
    manifest = prepare
    file = File.join(manifest['runtime'], 'manifest.json')
    File.unlink(file)
    File.symlink(File.join(@private, 'accounts.json'), file)
    failure('SYMLINK_FORBIDDEN') do
      P11OnlineRuntime.load_manifest(file, runtime_root: @options[:runtime_root], private_root: @private)
    end
  end

  def test_invalid_case_run_id_port_and_nonprivate_roots
    failure('INVALID_CASE') { prepare(case: 'single-extra') }
    failure('INVALID_RUN_ID') { prepare(run_id: '../escape-run') }
    failure('INVALID_PORT') { prepare(port: 65_536) }
    failure('INVALID_PORT') { prepare(port: 22) }
    File.chmod(0o755, @options[:runtime_root])
    failure('ROOT_NOT_PRIVATE') { prepare }
  end

  def test_port_probe_rejects_an_occupied_loopback_port
    TCPServer.open('127.0.0.1', 0) do |socket|
      failure('PORT_UNAVAILABLE') { prepare(port: socket.addr[1]) }
    end
  end

  def test_client_launchers_are_local_console_only_with_no_secret_arguments
    manifest = prepare(case: 'qctx', accept_eula: true)
    %w[a b].each do |client|
      file = File.join(manifest['runtime'], "launch-client-#{client}.command")
      assert_includes File.read(file), 'FROZEN_LAUNCHERS_REQUIRED'
      # Command rendering is tested separately; normal preparation never authorizes launch.
      text = P11OnlineRuntime.client_launcher(manifest.merge('launchMode' => 'FROZEN_MDG', 'bundle' => File.join(manifest['runtime'], 'launch-bundle')), client)
      assert_equal 0o700, File.stat(file).mode & 0o777
      exec_line = text.lines.find { |line| line.start_with?('exec ') }
      refute_nil exec_line
      refute_match(/(?:>|\||\btee\b|--info|--debug|accessToken|refreshToken|launch_profile)/, exec_line)
      assert_includes Shellwords.split(exec_line), @private
      assert_includes text, 'unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS'
      assert_includes text, 'export JAVA_HOME=' + @java_home
      assert_includes exec_line, 'launch-client'
      refute_includes exec_line, 'gradlew'
    end
    assert_equal "eula=true\n", File.read(File.join(manifest['runtime'], 'server/eula.txt'))
  end

  def test_ready_only_never_launches_auth_and_single_does_not_launch_b
    smoke = prepare(ready_only: true)
    %w[a b].each do |client|
      text = File.read(File.join(smoke['runtime'], "launch-client-#{client}.command"))
      assert_includes text, 'READY_ONLY_DOES_NOT_LAUNCH_AUTHENTICATION'
      refute_match(/^exec /, text)
    end
    single = prepare(run_id: 'single-test-002')
    assert_includes File.read(File.join(single['runtime'], 'launch-client-b.command')), 'CLIENT_B_NOT_APPLICABLE_TO_SINGLE'
  end

  def test_java_major_is_exact_and_unfrozen_launch_is_blocked
    manifest = prepare
    failure('FROZEN_LAUNCHERS_REQUIRED') { P11OnlineRuntime.launch_server(manifest) }
    File.write(File.join(@java_home, 'release'), "JAVA_VERSION=\"17.0.1\"\n")
    failure('JAVA_21_REQUIRED') { prepare(run_id: 'java-test-002') }
    failure('PRIVATE_PATH_FORBIDDEN') { prepare(java_home: @private) }
  end

  def test_status_uses_exact_file_presence_not_logs_cues_suffixes_or_json_values
    manifest = prepare
    dir = File.join(manifest['evidence'], 'client-single')
    Dir.mkdir(dir)
    %w[bootstrap.json onboarding-continued.json result.json result.json.extra latest.log reconnect.ready accounts.json].each do |name|
      File.write(File.join(dir, name), 'FAKE_SECRET_SENTINEL_NOT_REAL_CREDENTIALS')
    end
    File.stub(:binread, ->(*) { raise 'NO_CONTENT_READ_ALLOWED' }) do
      report = P11OnlineRuntime.status(manifest)
      assert_equal %w[prepare.json launch-cues.txt client-single/bootstrap.json client-single/onboarding-continued.json client-single/result.json], report['present']
      assert_equal 'NOT_INFERRED_FROM_FILE_PRESENCE', report['authenticationAcceptance']
    end
  end

  def test_manifest_exact_location_and_roundtrip_without_arbitrary_json_read
    manifest = prepare
    file = File.join(manifest['runtime'], 'manifest.json')
    assert_equal manifest, P11OnlineRuntime.load_manifest(file, runtime_root: @options[:runtime_root], private_root: @private)
    failure('INVALID_MANIFEST_LOCATION') do
      P11OnlineRuntime.load_manifest(@fixture, runtime_root: @options[:runtime_root], private_root: @private)
    end
    failure('INVALID_MANIFEST_LOCATION') do
      P11OnlineRuntime.load_manifest(File.join(@root, 'manifest.json'), runtime_root: @options[:runtime_root], private_root: @private)
    end
  end

  def test_fifo_stop_only_writes_literal_stop_and_does_not_claim_terminal
    manifest = prepare
    fifo = File.join(manifest['runtime'], 'server.stdin')
    failure('NO_OWNED_FIFO_READER') { P11OnlineRuntime.stop(manifest) }
    File.open(fifo, File::RDWR | File::NONBLOCK) do |reader|
      assert_equal 'STOP_COMMAND_WRITTEN_NOT_TERMINAL_PROOF', P11OnlineRuntime.stop(manifest)['status']
      assert_equal "stop\n", reader.read_nonblock(32)
    end
    File.unlink(fifo)
    File.write(fifo, '')
    failure('INVALID_OWNED_FIFO') { P11OnlineRuntime.stop(manifest) }
  end

  def test_cli_errors_do_not_echo_private_argument_values_or_backtraces
    stdout, stderr = capture_io do
      assert_equal 2, P11OnlineRuntime.main(['prepare', '--unknown-secret-option=FAKE_SECRET_SENTINEL'])
    end
    assert_empty stdout
    refute_includes stderr, 'FAKE_SECRET_SENTINEL'
    assert_equal 'OptionParser::InvalidOption', JSON.parse(stderr)['class']
  end

  def generated_fixture(manifest)
    repo = manifest['repo']
    base = File.join(repo, 'build/moddev')
    classes = File.join(repo, 'build/classes/java/p11OnlineHarness')
    resources = File.join(repo, 'build/resources/p11OnlineHarness')
    source = File.join(repo, 'src/p11OnlineHarness')
    [base, classes, resources, File.join(source, 'java/com/yo1no/gramarye'),
     File.join(source, 'resources'), File.join(base, 'artifacts'), File.join(repo, 'scripts')].each { |dir| FileUtils.mkdir_p(dir) }
    File.write(File.join(repo, 'build.gradle'), '// Synthetic engineering build input only')
    File.write(File.join(repo, 'scripts/p11-online-runtime.rb'), File.binread(File.join(__dir__, 'p11-online-runtime.rb')))
    File.write(File.join(source, 'java/com/yo1no/gramarye/P11OnlineFixture.java'), 'final class P11OnlineFixture {}')
    FileUtils.mkdir_p(File.join(classes, 'com/yo1no/gramarye'))
    File.write(File.join(classes, 'com/yo1no/gramarye/P11OnlineFixture.class'), 'SYNTHETIC_BYTECODE_NOT_MINECRAFT')
    console = File.binread(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/resources', P11OnlineRuntime::CONSOLE_XML))
    [resources, File.join(source, 'resources')].each do |dir|
      File.write(File.join(dir, P11OnlineRuntime::CONSOLE_XML), console)
      File.write(File.join(dir, 'gramarye-p11-online-harness.mixins.json'), '{"required":true}')
    end
    dependency = File.join(@root, '.gradle/caches/modules-2/files-2.1/fixture/dependency/1', '0' * 40, 'dependency.jar')
    FileUtils.mkdir_p(File.dirname(dependency))
    File.write(dependency, 'SYNTHETIC_DISTRIBUTION_ONLY')
    platform = P11OnlineRuntime::PLATFORM_JARS.map do |name|
      file = File.join(base, 'artifacts', name)
      File.write(file, 'SYNTHETIC_PLATFORM_ONLY')
      file
    end
    P11OnlineRuntime::LAUNCH_STEMS.each do |stem|
      role = P11OnlineRuntime.launch_role(manifest, stem)
      argv = P11OnlineRuntime.launch_argv(manifest, stem, base, classes, resources)
      File.write(File.join(base, 'run' + stem.sub(/^p/, 'P') + '.sh'), "(cd '#{manifest['runtime']}/#{role}'; exec #{argv.map { |arg| "'#{arg}'" }.join(' ')})")
      File.write(File.join(base, stem + 'RunClasspath.txt'), "-classpath\n#{([classes, resources, manifest['jar']] + platform + [dependency]).join(':')}")
      File.write(File.join(base, stem + 'LegacyClasspath.txt'), ([dependency] + platform).join("\n"))
      flags = ['-p', dependency] + %w[--add-modules ALL-MODULE-PATH --add-opens java.base/java.util.jar=cpw.mods.securejarhandler
        --add-opens java.base/java.lang.invoke=cpw.mods.securejarhandler --add-exports java.base/sun.security.util=cpw.mods.securejarhandler
        --add-exports jdk.naming.dns/com.sun.jndi.dns=java.naming]
      flags << '-XstartOnFirstThread' unless role == 'server'
      flags += %w[-Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError]
      props = {
        'log4j2.configurationFile' => File.join(base, stem + 'Log4j2.xml'), 'legacyClassPath.file' => File.join(base, stem + 'LegacyClasspath.txt'),
        'java.net.preferIPv6Addresses' => 'system', 'ignoreList' => 'mixinextras-neoforge-0.5.3.jar,client-extra,neoforge-',
        'forge.logging.markers' => 'REGISTRIES', 'gramarye.p11.online.readyOnly' => manifest['readyOnly'].to_s,
        'gramarye.p11.online.role' => role, 'gramarye.p11.online.output' => manifest['evidence'], 'gramarye.p11.online.case' => manifest['case'],
        'gramarye.p11.online.runId' => manifest['runId'], 'gramarye.p11.online.host' => '127.0.0.1',
        'gramarye.p11.online.jar' => manifest['jar'], 'gramarye.p11.online.port' => manifest['port'].to_s
      }
      unless role == 'server'
        props.merge!('devlogin.launch_target' => 'cpw.mods.bootstraplauncher.BootstrapLauncher', 'devlogin.launch_profile' => 'p11-local',
                     'neoforge.enableGameTest' => 'true', 'devlogin.storage' => File.join(@private, stem.end_with?('A') ? 'a' : 'b'))
      end
      File.write(File.join(base, stem + 'RunVmArgs.txt'), (flags + props.map { |key, value| "-D#{key}=#{value}" }).join("\n"))
      program = if role == 'server'
                  %w[cpw.mods.bootstraplauncher.BootstrapLauncher --launchTarget forgeserverdev]
                else
                  %w[net.covers1624.devlogin.DevLogin --launchTarget forgeclientdev --version 21.1.241 --assetIndex 17 --assetsDir] + [File.join(@root, '.gradle/caches/neoformruntime/assets')]
                end
      program += %w[--gameDir . --fml.fmlVersion 4.0.43 --fml.mcVersion 1.21.1 --fml.neoForgeVersion 21.1.241 --fml.neoFormVersion 20240808.144430]
      program << '--nogui' if role == 'server'
      program += %w[--mixin.config gramarye-p11-online-harness.mixins.json]
      File.write(File.join(base, stem + 'RunProgramArgs.txt'), program.join("\n"))
      File.write(File.join(base, stem + 'Log4j2.xml'), '<Configuration><Root level="DEBUG"/></Configuration>')
    end
    base
  end

  def export(manifest)
    P11OnlineRuntime.stub(:verify_jar!, true) do
      P11OnlineRuntime.stub(:candidate_head, 'e' * 40) { P11OnlineRuntime.freeze_launchers(manifest) }
    end
    P11OnlineRuntime.load_manifest(File.join(manifest['runtime'], 'frozen-manifest.json'),
                                  runtime_root: @options[:runtime_root], private_root: @private)
  end

  def test_frozen_export_preserves_originals_and_has_no_shared_build_references
    manifest = prepare(case: 'qctx')
    base = generated_fixture(manifest)
    before = File.binread(File.join(manifest['runtime'], 'manifest.json'))
    frozen = export(manifest)
    assert_equal before, File.binread(File.join(manifest['runtime'], 'manifest.json'))
    assert_equal 'e' * 40, frozen['candidateHead']
    assert frozen['sourceInputs'].key?('build.gradle')
    assert frozen['sourceInputs'].key?('scripts/p11-online-runtime.rb')
    assert_equal 18, frozen['generatedOriginals'].size
    assert_equal 1, frozen['externalDistributions'].size
    P11OnlineRuntime::LAUNCH_STEMS.each do |stem|
      original_xml = File.read(File.join(frozen['bundle'], 'originals', stem + 'Log4j2.xml'))
      assert_includes original_xml, 'level="DEBUG"'
      P11OnlineRuntime::LAUNCH_SUFFIXES.each do |suffix|
        file = File.join(frozen['bundle'], 'moddev', stem + suffix)
        refute_includes File.read(file), File.join(manifest['repo'], 'build') + '/'
        assert_equal 0o400, File.stat(file).mode & 0o777
      end
      active_xml = File.read(File.join(frozen['bundle'], 'moddev', stem + 'Log4j2.xml'))
      assert_includes active_xml, '<Root level="INFO">'
      refute_includes active_xml, '<RollingRandomAccessFile'
    end
    assert_equal 0o500, File.stat(frozen['bundle']).mode & 0o777
    assert_includes File.read(File.join(manifest['runtime'], 'launch-client-a.command')), 'FROZEN_LAUNCHERS_REQUIRED'
    refute_includes File.read(File.join(manifest['runtime'], 'run-client-a.command')), 'gradlew'
    File.write(File.join(base, 'p11OnlineClientARunVmArgs.txt'), 'LATER_DIFFERENT_COHORT')
    P11OnlineRuntime.stub(:verify_jar!, true) { assert P11OnlineRuntime.verify_frozen!(frozen) }
    failure('BUNDLE_ALREADY_EXISTS') { export(manifest) }
  end

  def test_export_rejects_wrong_cohort_unsafe_flags_and_prefix_near_classpath
    manifest = prepare(case: 'qctx')
    base = generated_fixture(manifest)
    vm = File.join(base, 'p11OnlineClientARunVmArgs.txt')
    original = File.read(vm)
    [original.sub('gramarye.p11.online.runId=direct-test-001', 'gramarye.p11.online.runId=wrong-cohort'),
     original + "\n-Ddevlogin.yes_i_really_just_want_to_dump_to_console=true"].each do |raw|
      File.write(vm, raw)
      failure('GENERATED_VM_PROPERTIES_MISMATCH') { export(manifest) }
    end
    File.write(vm, original)
    cp = File.join(base, 'p11OnlineClientARunClasspath.txt')
    File.write(cp, File.read(cp).sub('build/classes/java/p11OnlineHarness', 'build/classes/java/p11OnlineHarness-extra'))
    failure('UNEXPECTED_CLASSPATH') { export(manifest) }
  end

  def test_trimmed_gradle_hex_directory_still_pins_actual_distribution_bytes
    manifest = prepare(case: 'qctx')
    generated_fixture(manifest)
    short = File.join(@root, '.gradle/caches/modules-2/files-2.1/fixture/dependency/1', '1' * 39, 'short.jar')
    FileUtils.mkdir_p(File.dirname(short))
    File.write(short, 'EXACT_SYNTHETIC_DISTRIBUTION')
    hashes = {}
    P11OnlineRuntime.distribution_path!(short, manifest, [], hashes)
    assert_equal Digest::SHA256.hexdigest('EXACT_SYNTHETIC_DISTRIBUTION'), hashes[short]
    failure('UNEXPECTED_CLASSPATH') { P11OnlineRuntime.distribution_path!(short.sub('1' * 39, '1' * 41), manifest, [], {}) }
    failure('UNEXPECTED_CLASSPATH') { P11OnlineRuntime.distribution_path!(short.sub('1' * 39, '0' * 39), manifest, [], {}) }
  end

  def test_export_rejects_shell_suffix_symlink_and_unexpected_companion_family
    manifest = prepare(case: 'qctx')
    base = generated_fixture(manifest)
    shell = File.join(base, 'runP11OnlineClientA.sh')
    original = File.read(shell)
    File.write(shell, original + '; echo UNSUPPORTED')
    failure('GENERATED_SCRIPT_SHAPE') { export(manifest) }
    File.write(shell, original)
    classes = File.join(manifest['repo'], 'build/classes/java/p11OnlineHarness/com/yo1no/gramarye')
    extra = File.join(classes, 'Unexpected.class')
    File.write(extra, 'UNSUPPORTED')
    failure('UNEXPECTED_COMPANION_CLASS') { export(manifest) }
    File.unlink(extra)
    File.symlink(File.join(@private, 'accounts.json'), extra)
    failure('SYMLINK_FORBIDDEN') { export(manifest) }
  end

  def test_export_rejects_active_console_debug_and_stale_resource
    manifest = prepare(case: 'qctx')
    generated_fixture(manifest)
    source = File.join(manifest['repo'], 'src/p11OnlineHarness/resources', P11OnlineRuntime::CONSOLE_XML)
    resource = File.join(manifest['repo'], 'build/resources/p11OnlineHarness', P11OnlineRuntime::CONSOLE_XML)
    unsafe = File.read(source).sub('<Root level="INFO">', '<Root level="DEBUG">')
    File.write(resource, unsafe)
    failure('CONSOLE_RESOURCE_STALE') { export(manifest) }
    File.write(source, unsafe)
    failure('PRIVATE_CONSOLE_POLICY') { export(manifest) }
  end

  def test_frozen_bundle_tamper_and_distribution_tamper_are_not_accepted
    manifest = prepare(case: 'qctx')
    generated_fixture(manifest)
    frozen = export(manifest)
    file = File.join(frozen['bundle'], 'moddev/p11OnlineClientARunVmArgs.txt')
    original = File.binread(file)
    File.chmod(0o600, file)
    File.write(file, 'MODIFIED')
    failure('BUNDLE_CONTENT_CHANGED') { P11OnlineRuntime.verify_frozen!(frozen) }
    File.write(file, original)
    File.write(frozen['externalDistributions'].keys.first, 'MODIFIED')
    failure('DISTRIBUTION_CONTENT_CHANGED') { P11OnlineRuntime.verify_frozen!(frozen) }
  end

  def test_client_launch_requires_all_three_real_terminal_streams_before_spawn
    manifest = prepare(case: 'qctx')
    Process.stub(:spawn, ->(*) { flunk 'must not spawn authentication' }) do
      [[false, true, true], [true, false, true], [true, true, false]].each do |input, output, error|
        STDIN.stub(:tty?, input) do
          STDOUT.stub(:tty?, output) do
            STDERR.stub(:tty?, error) { failure('PRIVATE_TERMINAL_REQUIRED') { P11OnlineRuntime.launch_client(manifest, 'a') } }
          end
        end
      end
    end
  end

  def test_owned_fake_client_wait_records_only_numeric_exit_with_inherited_console
    manifest = prepare(case: 'qctx')
    generated_fixture(manifest)
    frozen = export(manifest)
    write_ready(manifest)
    original_spawn = Process.method(:spawn)
    checked_spawn = lambda do |*args|
      options = args.last
      refute options.key?(:out)
      refute options.key?(:err)
      refute options.key?(:in)
      assert_nil args.first['JAVA_TOOL_OPTIONS']
      assert_equal @java_home, args.first['JAVA_HOME']
      assert_equal File.join(@java_home, 'bin/java'), args[1]
      refute args.any? { |arg| arg.is_a?(String) && arg.include?('gradlew') }
      original_spawn.call(*args)
    end
    P11OnlineRuntime.stub(:verify_jar!, true) do
      STDIN.stub(:tty?, true) do
        STDOUT.stub(:tty?, true) do
          STDERR.stub(:tty?, true) do
            Process.stub(:spawn, checked_spawn) do
              report = P11OnlineRuntime.launch_client(frozen, 'a')
              assert_equal({ 'status' => 'OWNED_CLIENT_PROCESS_EXIT_NOT_ACCEPTANCE', 'exitCode' => 0, 'signal' => nil }, report)
            end
          end
        end
      end
    end
    receipt = JSON.parse(File.read(File.join(manifest['evidence'], 'client-a-process-exit.json')))
    assert_equal %w[exitCode signal status], receipt.keys.sort
  end

  def ready_record(manifest)
    { 'status' => 'ONLINE_DEDICATED_READY_NO_AUTH_CLAIM', 'case' => manifest.fetch('case'),
      'runId' => manifest.fetch('runId'), 'productionJarSha256' => manifest.fetch('jarSha256'),
      'onlineMode' => true, 'integrated' => false,
      'expectedPlayers' => manifest.fetch('case') == 'single' ? 1 : 2,
      'configurationSha256' => manifest.fetch('fixtureSha256') }
  end

  def write_ready(manifest, values = ready_record(manifest))
    server = File.join(manifest.fetch('evidence'), 'server')
    Dir.mkdir(server, 0o700) unless File.exist?(server)
    File.write(File.join(server, 'ready.json'), JSON.generate(values))
  end

  def assert_client_rejected_before_spawn(manifest, code)
    P11OnlineRuntime.stub(:verify_frozen!, true) do
      Process.stub(:spawn, ->(*) { flunk 'must reject before authentication process starts' }) do
        STDIN.stub(:tty?, true) do
          STDOUT.stub(:tty?, true) do
            STDERR.stub(:tty?, true) { failure(code) { P11OnlineRuntime.launch_client(manifest, 'a') } }
          end
        end
      end
    end
    refute File.exist?(File.join(manifest.fetch('runtime'), 'client-a.launch-reserved'))
  end

  def test_client_readiness_missing_and_each_wrong_field_reject_before_reservation_or_spawn
    manifest = prepare(case: 'qctx')
    assert_client_rejected_before_spawn(manifest, 'SERVER_NOT_READY')
    wrong = { 'status' => 'NOT_READY', 'case' => 'capacity', 'runId' => 'other-run-001',
              'productionJarSha256' => '0' * 64, 'onlineMode' => false, 'integrated' => true,
              'expectedPlayers' => 1, 'configurationSha256' => '0' * 64 }
    wrong.each do |key, value|
      write_ready(manifest, ready_record(manifest).merge(key => value))
      assert_client_rejected_before_spawn(manifest, 'SERVER_READY_MISMATCH')
    end
    write_ready(manifest, ready_record(manifest).merge('expectedPlayers' => 2.0))
    assert_client_rejected_before_spawn(manifest, 'SERVER_READY_MISMATCH')
    write_ready(manifest, ready_record(manifest).merge('extra' => true))
    assert_client_rejected_before_spawn(manifest, 'SERVER_READY_MISMATCH')
    write_ready(manifest)
    assert P11OnlineRuntime.server_ready_for_client!(manifest)
  end

  def test_client_readiness_invalid_bounded_and_symlink_inputs_are_not_read_as_logs
    manifest = prepare(case: 'qctx')
    write_ready(manifest)
    file = File.join(manifest.fetch('evidence'), 'server/ready.json')
    ['{', ' ' * 4097].each do |raw|
      File.write(file, raw)
      assert_client_rejected_before_spawn(manifest, 'SERVER_READY_INVALID')
    end
    File.unlink(file)
    File.symlink(File.join(@private, 'accounts.json'), file)
    File.stub(:binread, ->(*) { flunk 'must not read a symlinked authentication input' }) do
      assert_client_rejected_before_spawn(manifest, 'SYMLINK_FORBIDDEN')
    end
  end

  def test_known_owned_server_terminal_receipts_block_clients_without_reading_receipt_contents
    manifest = prepare(case: 'qctx')
    write_ready(manifest)
    [File.join(manifest.fetch('runtime'), 'server.exit.json'),
     File.join(manifest.fetch('evidence'), 'server/stopped.json')].each do |terminal|
      File.write(terminal, 'FIXED_SYNTHETIC_TERMINAL_CONTENT_NOT_READ')
      File.stub(:binread, ->(*) { flunk 'known terminal must reject without reading files' }) do
        assert_client_rejected_before_spawn(manifest, 'SERVER_ALREADY_TERMINAL')
      end
      File.unlink(terminal)
    end
  end

  def test_passive_launch_diagnostic_is_explicit_client_only_and_separately_frozen
    manifest = prepare(case: 'qctx', diagnose_client: true)
    base = generated_fixture(manifest)
    %w[a b].each { |role| assert File.directory?(File.join(manifest['evidence'], "client-#{role}-launch-diagnostic")) }
    refute File.exist?(File.join(manifest['evidence'], 'server-launch-diagnostic'))
    built = false
    builder = lambda do |_input, bundle, _classes, _names|
      built = true
      directory = File.join(bundle, 'diagnostic')
      Dir.mkdir(directory)
      P11OnlineRuntime.write_new(File.join(directory, 'launch-diagnostic.jar'), 'SYNTHETIC_AGENT_NOT_AUTHENTICATION', 0o400)
    end
    frozen = P11OnlineRuntime.stub(:build_launch_diagnostic!, builder) { export(manifest) }
    assert built
    assert frozen['bundleFiles'].key?('diagnostic/launch-diagnostic.jar')
    P11OnlineRuntime::LAUNCH_STEMS.each do |stem|
      args = P11OnlineRuntime.launch_argv(frozen, stem, base, 'classes', 'resources')
      agents = args.select { |arg| arg.start_with?('-javaagent:') }
      if stem == 'p11OnlineServer'
        assert_empty agents
      else
        role = P11OnlineRuntime.launch_role(frozen, stem)
        assert_equal ["-javaagent:#{frozen['bundle']}/diagnostic/launch-diagnostic.jar=#{frozen['evidence']}/client-#{role}-launch-diagnostic"], agents
      end
      # Original MDG export is unchanged and the real vendor main remains the launch target.
      refute_includes File.read(File.join(frozen['bundle'], 'originals', stem + 'RunVmArgs.txt')), '-javaagent'
      assert_equal 'net.neoforged.devlaunch.Main', args[-2]
    end
    file = File.join(frozen['evidence'], 'client-b-launch-diagnostic/shutdown.json')
    File.write(file, 'SYNTHETIC_FIXED_CATEGORY_ONLY')
    File.stub(:binread, ->(*) { flunk 'status must not read diagnostic contents' }) do
      assert_includes P11OnlineRuntime.status(frozen)['present'], 'client-b-launch-diagnostic/shutdown.json'
    end
    File.chmod(0o600, File.join(frozen['bundle'], 'diagnostic/launch-diagnostic.jar'))
    File.write(File.join(frozen['bundle'], 'diagnostic/launch-diagnostic.jar'), 'TAMPERED')
    failure('BUNDLE_CONTENT_CHANGED') { P11OnlineRuntime.verify_frozen!(frozen) }
  end

  def test_diagnostic_mode_rejects_invalid_option_and_missing_compiled_observer
    failure('INVALID_DIAGNOSTIC_MODE') { prepare(diagnose_client: 'true') }
    manifest = prepare(case: 'qctx', diagnose_client: true)
    generated_fixture(manifest)
    failure('LAUNCH_DIAGNOSTIC_CLASS_MISSING') { export(manifest) }
  end
end
