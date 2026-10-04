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
    P11OnlineRuntime::REWARD_FIXTURE_HASHES.each_key do |leaf|
      relative = File.join('src/p9S5ClientHarness/resources/data/gramarye_p11_engineering', leaf)
      destination = File.join(@options[:repo], relative)
      FileUtils.mkdir_p(File.dirname(destination))
      FileUtils.cp(File.expand_path('../' + relative, __dir__), destination)
    end
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

  def test_c6_fixture_is_pinned_c4a_only_and_manifest_binds_actual_source
    source = File.binread(File.expand_path('fixtures/p11-c4a-c6-startup.toml', __dir__))
    assert_equal P11OnlineRuntime::C4A_C6_FIXTURE_SHA, Digest::SHA256.hexdigest(source)
    File.binwrite(@fixture, source)
    %w[single qctx capacity].each do |name|
      failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, name) }
    end
    %w[c4a-dedicated c4a-host-lan].each do |name|
      actual, hash = P11OnlineRuntime.startup_fixture!(@fixture, @private, name, with_source_hash: true)
      assert_equal P11OnlineRuntime::C4A_C6_FIXTURE_SHA, hash
      assert_equal source.sub('p11.retention.maxUuids = 4', 'p11.retention.maxUuids = 2')
                         .sub('p11.save.dirtyUuidAdmissionWatermark = 4', 'p11.save.dirtyUuidAdmissionWatermark = 2'), actual
      %w[maxWaitingConnections admissionWaitMillis tryBurst tryRefillPerSecond statusBurst statusRefillPerSecond mainQuantaPerTick].each do |key|
        assert_equal source[/^p11\.control\.#{key} = \d+$/], actual[/^p11\.control\.#{key} = \d+$/]
      end
    end
    manifest = prepare(case: 'c4a-dedicated')
    assert_equal P11OnlineRuntime::C4A_C6_FIXTURE_SHA, manifest.fetch('sourceFixtureSha256')
    actual = File.binread(File.join(manifest.fetch('runtime'), 'server/defaultconfigs/gramarye-server.toml'))
    assert_equal Digest::SHA256.hexdigest(actual), manifest.fetch('fixtureSha256')
    File.binwrite(@fixture, source.sub('maxWaitingConnections = 1', 'maxWaitingConnections = 2'))
    failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, 'c4a-dedicated') }
  end

  def test_c6_rate_fixture_is_a_distinct_exact_startup_input
    source = File.binread(File.expand_path('fixtures/p11-c4a-c6-rate-startup.toml', __dir__))
    assert_equal P11OnlineRuntime::C4A_C6_RATE_FIXTURE_SHA, Digest::SHA256.hexdigest(source)
    refute_equal P11OnlineRuntime::C4A_C6_FIXTURE_SHA, P11OnlineRuntime::C4A_C6_RATE_FIXTURE_SHA
    File.binwrite(@fixture, source)
    %w[single qctx capacity].each do |name|
      failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, name) }
    end
    manifest = prepare(case: 'c4a-dedicated')
    assert_equal P11OnlineRuntime::C4A_C6_RATE_FIXTURE_SHA, manifest.fetch('sourceFixtureSha256')
    actual = File.binread(File.join(manifest.fetch('runtime'), 'server/defaultconfigs/gramarye-server.toml'))
    assert_equal Digest::SHA256.hexdigest(actual), manifest.fetch('fixtureSha256')
    assert_includes actual, 'p11.control.tryBurst = 1'
    assert_includes actual, 'p11.control.tryRefillPerSecond = 1'
    assert_includes actual, 'p11.control.statusBurst = 4'
    assert_includes actual, 'p11.control.statusRefillPerSecond = 4'
    File.binwrite(@fixture, source + "\n")
    failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, 'c4a-dedicated') }
  end

  def test_handoff_fixture_keeps_native_timeouts_and_uses_original_four_quanta
    source = File.binread(File.expand_path('fixtures/p11-c4a-handoff-startup.toml', __dir__))
    c6 = File.binread(File.expand_path('fixtures/p11-c4a-c6-startup.toml', __dir__))
    assert_equal c6.sub('p11.control.mainQuantaPerTick = 1', 'p11.control.mainQuantaPerTick = 4'), source
    assert_equal P11OnlineRuntime::C4A_HANDOFF_FIXTURE_SHA, Digest::SHA256.hexdigest(source)
    File.binwrite(@fixture, source)
    %w[single qctx capacity].each do |name|
      failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, name) }
    end
    %w[c4a-dedicated c4a-host-lan].each do |name|
      actual, hash = P11OnlineRuntime.startup_fixture!(@fixture, @private, name, with_source_hash: true)
      assert_equal P11OnlineRuntime::C4A_HANDOFF_FIXTURE_SHA, hash
      assert_equal source.sub('p11.retention.maxUuids = 4', 'p11.retention.maxUuids = 2')
                         .sub('p11.save.dirtyUuidAdmissionWatermark = 4', 'p11.save.dirtyUuidAdmissionWatermark = 2'), actual
      assert_includes actual, 'p11.control.admissionWaitMillis = 45000'
      assert_includes actual, 'p11.control.mainQuantaPerTick = 4'
    end
    manifest = prepare(case: 'c4a-dedicated')
    assert_equal P11OnlineRuntime::C4A_HANDOFF_FIXTURE_SHA, manifest.fetch('sourceFixtureSha256')
    actual = File.binread(File.join(manifest.fetch('runtime'), 'server/defaultconfigs/gramarye-server.toml'))
    assert_equal Digest::SHA256.hexdigest(actual), manifest.fetch('fixtureSha256')
    %w[2 8].each do |quantum|
      File.binwrite(@fixture, source.sub('mainQuantaPerTick = 4', "mainQuantaPerTick = #{quantum}"))
      failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, 'c4a-dedicated') }
    end
  end

  def test_c4a_pin_is_a_separate_exact_slot_not_an_environment_override
    assert_equal %w[c4a-dedicated c4a-host-lan c4a-reward], P11OnlineRuntime::C4A_CASES
    %w[single qctx capacity].each do |name|
      assert_equal P11OnlineRuntime::JAR_SHA, P11OnlineRuntime.product_pin(name)
      assert_equal 'gramarye-p11-online-harness.mixins.json', P11OnlineRuntime.mixin_config(name)
    end
    P11OnlineRuntime::C4A_CASES.each do |name|
      assert_equal 'gramarye-p11-c4a-harness.mixins.json', P11OnlineRuntime.mixin_config(name)
      pin = P11OnlineRuntime.product_pin(name)
      assert_match(/\A[0-9a-f]{64}\z/, pin)
      refute_equal P11OnlineRuntime::JAR_SHA, pin
      failure('FROZEN_JAR_MISMATCH') { P11OnlineRuntime.verify_jar!(@jar, name) }
      failure('FROZEN_JAR_MISMATCH') { P11OnlineRuntime.prepare(@options.merge(case: name)) }
    end
    assert_empty Dir.children(@options[:runtime_root])
    assert_empty Dir.children(@options[:evidence_root])
    %w[c4a c4a-dedicated.extra c4a-host-lan/../single c4a-other].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name) }
    end
    P11OnlineRuntime.stub(:product_pin, 'a' * 64) do
      refute_equal P11OnlineRuntime.frozen_jar_path(@options[:runtime_root], 'single'),
                   P11OnlineRuntime.frozen_jar_path(@options[:runtime_root], 'c4a-dedicated')
    end
  end

  def test_reward_case_has_only_seven_pinned_public_fixture_files
    manifest = prepare(case: 'c4a-reward')
    assert_equal %w[server a b], manifest.fetch('roles')
    assert_equal P11OnlineRuntime::REWARD_FIXTURE_HASHES, manifest.fetch('rewardFixtureHashes')
    assert_equal P11OnlineRuntime::C4A_JAR_SHA, manifest.fetch('jarSha256')
    receipt = JSON.parse(File.read(File.join(manifest.fetch('evidence'), 'reward-fixture.json')))
    assert_equal 'EXACT_PUBLIC_NATIVE_REWARD_INPUTS_NOT_RUNTIME_PROOF', receipt.fetch('status')
    assert_equal P11OnlineRuntime::REWARD_FIXTURE_HASHES, receipt.fetch('sourceHashes')
    pack = File.join(manifest.fetch('runtime'), 'server/p11-online-world/datapacks/p11-online-engineering/data/gramarye_p11_engineering')
    assert_equal (P11OnlineRuntime::REWARD_FIXTURE_HASHES.keys + ['advancement/delivery_root.json']).sort,
                 Dir.glob(pack + '/**/*').select { |path| File.file?(path) }.map { |path| path.delete_prefix(pack + '/') }.sort
    assert_equal JSON.pretty_generate(P11OnlineRuntime.delivery_root) + "\n", File.read(File.join(pack, 'advancement/delivery_root.json'))
    P11OnlineRuntime.verify_reward_fixture!(manifest)
    altered = Marshal.load(Marshal.dump(manifest)); altered['rewardFixtureHashes']['function/reward.mcfunction'] = '0' * 64
    failure('REWARD_FIXTURE_MANIFEST_MISMATCH') { P11OnlineRuntime.verify_reward_fixture!(altered) }
    File.write(File.join(pack, 'function/reward.mcfunction'), 'CHANGED_PUBLIC_FIXTURE_TEST_ONLY')
    failure('REWARD_FIXTURE_CONTENT_CHANGED') { P11OnlineRuntime.verify_reward_fixture!(manifest) }
    %w[c4a-reward.extra c4a-reward/../single c4a_reward].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name) }
    end
  end

  def test_reward_fixture_rejects_source_change_and_other_cases_keep_original_data
    source = File.join(@options[:repo], 'src/p9S5ClientHarness/resources/data/gramarye_p11_engineering/function/reward.mcfunction')
    File.write(source, 'CHANGED_PUBLIC_FIXTURE_TEST_ONLY')
    failure('REWARD_FIXTURE_SOURCE_MISMATCH') { prepare(case: 'c4a-reward') }
    assert_empty Dir.children(@options[:evidence_root])
    %w[single qctx capacity c4a-dedicated c4a-host-lan c4a-qctx c4a-capacity].each_with_index do |name,index|
      assert_empty P11OnlineRuntime.reward_fixture_sources!(@options[:repo], @private, name)
      manifest = prepare(case: name, run_id: "unchanged-#{index}-fixture")
      refute manifest.key?('rewardFixtureHashes')
      refute File.exist?(File.join(manifest.fetch('evidence'), 'reward-fixture.json'))
      pack = File.join(manifest.fetch('runtime'), 'server/p11-online-world/datapacks/p11-online-engineering/data/gramarye_p11_engineering')
      assert_equal [File.join(pack, 'advancement/delivery_root.json')], Dir.glob(pack + '/**/*').select { |path| File.file?(path) }
      assert_equal JSON.pretty_generate(P11OnlineRuntime.delivery_root) + "\n", File.read(File.join(pack, 'advancement/delivery_root.json'))
    end
    File.binwrite(@fixture, File.binread(File.join(__dir__, 'fixtures/p11-c4a-c6-startup.toml')))
    failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, 'c4a-reward') }
  end

  def test_reward_status_admits_only_five_fixed_new_reports_without_content_reads
    manifest = prepare(case: 'c4a-reward')
    server = File.join(manifest.fetch('evidence'), 'server'); Dir.mkdir(server)
    client = File.join(manifest.fetch('evidence'), 'client-a'); Dir.mkdir(client)
    %w[reward-end-armed.json reward-end-complete.json reward-detached-logical.json
       reward-continuity-server.json reward-continuity-server.json.extra latest.log].each do |name|
      File.write(File.join(server, name), 'SYNTHETIC_CONTENT_NOT_READ')
    end
    %w[reward-delivery-client.json reward-delivery-client.json.extra account.json].each do |name|
      File.write(File.join(client, name), 'SYNTHETIC_CONTENT_NOT_READ')
    end
    File.stub(:binread, ->(*) { flunk 'status must not read content' }) do
      values = P11OnlineRuntime.status(manifest).fetch('present')
      assert_equal %w[prepare.json launch-cues.txt reward-fixture.json server/reward-end-armed.json
                      server/reward-end-complete.json server/reward-detached-logical.json
                      server/reward-continuity-server.json client-a/reward-delivery-client.json], values
      refute P11OnlineRuntime.status(manifest.merge('case' => 'c4a-dedicated')).fetch('present')
                       .include?('client-a/reward-delivery-client.json')
    end
  end

  def test_current_context_aliases_select_current_pin_but_not_c4a_scenarios
    assert_equal %w[c4a-qctx c4a-capacity], P11OnlineRuntime::CURRENT_CONTEXT_CASES
    assert_equal %w[c4a-dedicated c4a-host-lan c4a-reward], P11OnlineRuntime::C4A_CASES
    %w[single qctx capacity].each do |name|
      assert_equal P11OnlineRuntime::JAR_SHA, P11OnlineRuntime.product_pin(name)
      refute P11OnlineRuntime.current_product_case?(name)
    end
    P11OnlineRuntime::CURRENT_CONTEXT_CASES.each do |name|
      assert_equal P11OnlineRuntime::C4A_JAR_SHA, P11OnlineRuntime.product_pin(name)
      refute_equal P11OnlineRuntime::JAR_SHA, P11OnlineRuntime.product_pin(name)
      assert_equal %w[server a b], P11OnlineRuntime.roles_for(name)
      assert_equal ['gramarye-p11-online-harness.mixins.json'], P11OnlineRuntime.mixin_configs(name)
      assert_equal File.join(@options[:runtime_root], 'frozen-c4a', P11OnlineRuntime::C4A_JAR_SHA,
                             'gramarye-1.0.0.jar'), P11OnlineRuntime.frozen_jar_path(@options[:runtime_root], name)
      failure('FROZEN_JAR_MISMATCH') { P11OnlineRuntime.verify_jar!(@jar, name) }
    end
    %w[c4a-qctx.extra c4a-capacity-extra c4a-capacity/../qctx current-qctx c4a-single].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name) }
      failure('INVALID_CASE') { P11OnlineRuntime.mixin_configs(name) }
      failure('INVALID_CASE') { prepare(case: name) }
    end
  end

  def test_current_context_aliases_preserve_the_alias_and_only_derive_original_t_limit
    { 'c4a-qctx' => 2, 'c4a-capacity' => 1 }.each do |name, limit|
      manifest = prepare(case: name, run_id: 'context-' + name)
      assert_equal name, manifest.fetch('case')
      assert_equal P11OnlineRuntime::C4A_JAR_SHA, manifest.fetch('jarSha256')
      expected = FIXTURE.sub('p11.retention.maxUuids = 4', "p11.retention.maxUuids = #{limit}")
                        .sub('p11.save.dirtyUuidAdmissionWatermark = 4', "p11.save.dirtyUuidAdmissionWatermark = #{limit}")
      assert_equal expected, File.read(File.join(manifest['runtime'], 'server/defaultconfigs/gramarye-server.toml'))
      assert_equal P11OnlineRuntime::FIXTURE_SHA, manifest.fetch('sourceFixtureSha256')
      assert_equal Digest::SHA256.hexdigest(expected), manifest.fetch('fixtureSha256')
      assert_equal manifest, P11OnlineRuntime.load_manifest(File.join(manifest['runtime'], 'manifest.json'),
        runtime_root: @options[:runtime_root], private_root: @private)
      write_ready(manifest, ready_record(manifest).merge('case' => name.delete_prefix('c4a-')))
      failure('SERVER_READY_MISMATCH') { P11OnlineRuntime.server_ready_for_client!(manifest) }
      write_ready(manifest)
      assert P11OnlineRuntime.server_ready_for_client!(manifest)
    end
    %w[p11-c4a-c6-startup.toml p11-c4a-c6-rate-startup.toml p11-c4a-handoff-startup.toml].each do |leaf|
      File.binwrite(@fixture, File.binread(File.join(__dir__, 'fixtures', leaf)))
      P11OnlineRuntime::CURRENT_CONTEXT_CASES.each do |name|
        failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, name) }
      end
    end
  end

  def test_current_context_alias_export_keeps_real_program_case_and_original_mixin_only
    P11OnlineRuntime::CURRENT_CONTEXT_CASES.each do |name|
      manifest = prepare(case: name, run_id: 'export-' + name)
      base = generated_fixture(manifest)
      file = File.join(base, 'p11OnlineClientARunProgramArgs.txt')
      original = File.binread(file)
      File.binwrite(file, original + "\n--mixin.config\ngramarye-p11-c4a-harness.mixins.json")
      failure('GENERATED_PROGRAM_ARGUMENTS_MISMATCH') { export(manifest) }
      refute File.exist?(File.join(manifest['runtime'], 'launch-bundle'))
      File.binwrite(file, original)
      frozen = export(manifest)
      assert_equal name, frozen.fetch('case')
      assert_equal P11OnlineRuntime::C4A_JAR_SHA, frozen.fetch('jarSha256')
      vm = File.read(File.join(frozen['bundle'], 'originals/p11OnlineClientARunVmArgs.txt'))
      assert_includes vm, '-Dgramarye.p11.online.case=' + name
      assert_includes original, 'gramarye-p11-online-harness.mixins.json'
      refute_includes original, 'gramarye-p11-c4a-harness.mixins.json'
      refute_includes original, 'gramarye-p11-c6-observers.mixins.json'
      assert P11OnlineRuntime.stub(:verify_jar!, true) { P11OnlineRuntime.verify_frozen!(frozen) }
    end
  end

  def test_current_context_build_admission_is_exact_without_widening_scenario_selection
    build = File.binread(File.join(P11OnlineRuntime::REPO, 'build.gradle'))
    names = "['single', 'qctx', 'capacity', 'c4a-dedicated', 'c4a-host-lan', 'c4a-qctx', 'c4a-capacity', 'c4a-reward']"
    valid = lambda do |source|
      source.scan(/!p11OnlineJar\.isFile\(\) \|\| !\(p11OnlineCase in (\[[^\n]+\])\)/).flatten == [names]
    end
    assert valid.call(build)
    [names.sub(", 'c4a-capacity'", ''), names.sub('c4a-qctx', 'c4a-qctx.extra'),
     names.sub(']', ", 'foreign']")].each do |bad|
      refute valid.call(build.sub(names, bad))
    end
    refute valid.call(build.sub("p11OnlineCase in #{names}", "p11OnlineCase.startsWith('c4a')"))
    assert_equal 2, build.scan("p11OnlineCase in ['c4a-dedicated', 'c4a-host-lan', 'c4a-reward']").size
  end


  def test_c6_extra_mixin_is_exactly_c4a_only_and_build_selector_is_closed
    legacy = ['gramarye-p11-online-harness.mixins.json']
    c4a = ['gramarye-p11-c4a-harness.mixins.json', 'gramarye-p11-c6-observers.mixins.json']
    %w[single qctx capacity].each { |name| assert_equal legacy, P11OnlineRuntime.mixin_configs(name) }
    %w[c4a-dedicated c4a-host-lan c4a-reward].each { |name| assert_equal c4a, P11OnlineRuntime.mixin_configs(name) }
    %w[c4a c4a-dedicated.extra c4a-host-lan/../single c4a-foreign].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.mixin_configs(name) }
    end
    build = File.binread(File.expand_path('../build.gradle', __dir__))
    exact = <<~GROOVY.strip
      if (p11OnlineCase in ['c4a-dedicated', 'c4a-host-lan', 'c4a-reward']) {
          programArguments.addAll '--mixin.config', 'gramarye-p11-c6-observers.mixins.json'
      }
    GROOVY
    valid = lambda do |source|
      fragment = source[/if \(p11OnlineCase[^\n]*\) \{\s*programArguments\.addAll '--mixin\.config', 'gramarye-p11-c6-observers\.mixins\.json'\s*\}/m]
      fragment && fragment.gsub(/\s+/, ' ').strip == exact.gsub(/\s+/, ' ').strip
    end
    assert valid.call(build)
    ["p11OnlineCase.startsWith('c4a')",
     "p11OnlineCase in ['c4a-dedicated', 'c4a-host-lan', 'single']",
     "p11OnlineCase in ['c4a-dedicated']"].each do |bad|
      refute valid.call(build.sub("p11OnlineCase in ['c4a-dedicated', 'c4a-host-lan', 'c4a-reward']) {", bad + ') {'))
    end
    refute valid.call(build.sub('gramarye-p11-c6-observers.mixins.json', 'gramarye-p11-c6-observers.mixins.json.extra'))
  end

  def test_c6_export_requires_exact_order_and_no_foreign_or_duplicate_mixin_arguments
    %w[c4a-dedicated single].each do |name|
      manifest = prepare(case: name, run_id: 'mixin-' + name)
      base = generated_fixture(manifest)
      file = File.join(base, 'p11OnlineClientARunProgramArgs.txt')
      original = File.read(file)
      suffix = "\n--mixin.config\ngramarye-p11-c6-observers.mixins.json"
      variants = if name == 'single'
                   [original + suffix]
                 else
                   [original.delete_suffix(suffix),
                    original + suffix,
                    original.sub('gramarye-p11-c6-observers.mixins.json', 'foreign.mixins.json'),
                    original.sub('gramarye-p11-c4a-harness.mixins.json', 'TEMP')
                            .sub('gramarye-p11-c6-observers.mixins.json', 'gramarye-p11-c4a-harness.mixins.json')
                            .sub('TEMP', 'gramarye-p11-c6-observers.mixins.json')]
                 end
      variants.each do |raw|
        refute_equal original, raw
        File.write(file, raw)
        failure('GENERATED_PROGRAM_ARGUMENTS_MISMATCH') { export(manifest) }
        refute File.exist?(File.join(manifest['runtime'], 'launch-bundle'))
      end
      File.write(file, original)
      frozen = export(manifest)
      assert P11OnlineRuntime.stub(:verify_jar!, true) { P11OnlineRuntime.verify_frozen!(frozen) }
    end
  end

  def test_c6_resource_inventory_is_exact_four_and_rejects_near_names_or_missing_resource
    assert_equal ['gramarye-p11-online-private-console.xml', 'gramarye-p11-online-harness.mixins.json',
                  'gramarye-p11-c4a-harness.mixins.json', 'gramarye-p11-c6-observers.mixins.json'].sort,
                 P11OnlineRuntime::COMPANION_RESOURCES
    manifest = prepare(case: 'c4a-dedicated')
    generated_fixture(manifest)
    resources = File.join(manifest['repo'], 'build/resources/p11OnlineHarness')
    selected = File.join(resources, 'gramarye-p11-c6-observers.mixins.json')
    bytes = File.binread(selected)
    File.unlink(selected)
    failure('UNEXPECTED_COMPANION_RESOURCE') { export(manifest) }
    ['gramarye-p11-c6-observers.mixins.json.extra', 'foreign.mixins.json',
     'nested/gramarye-p11-c6-observers.mixins.json'].each do |bad|
      replacement = File.join(resources, bad)
      FileUtils.mkdir_p(File.dirname(replacement))
      File.write(replacement, bytes)
      failure('UNEXPECTED_COMPANION_RESOURCE') { export(manifest) }
      File.unlink(replacement)
    end
    File.write(selected, bytes)
    extra = File.join(resources, 'foreign.xml')
    File.write(extra, '<SYNTHETIC/>')
    failure('UNEXPECTED_COMPANION_RESOURCE') { export(manifest) }
    File.unlink(extra)
    frozen = export(manifest)
    assert_equal 4, frozen['bundleFiles'].keys.count { |path| path.start_with?('resources/') }
  end

  def test_integrated_preparation_retains_original_host_world_creation_and_partner_barrier
    P11OnlineRuntime.stub(:product_pin, 'a' * 64) do
      manifest = prepare(case: 'c4a-host-lan')
      assert_equal %w[host b], manifest['roles']
      assert_equal 'host', P11OnlineRuntime.launch_role(manifest, 'p11OnlineClientA')
      assert_equal 'b', P11OnlineRuntime.launch_role(manifest, 'p11OnlineClientB')
      assert File.file?(File.join(manifest['runtime'], 'host/defaultconfigs/gramarye-server.toml'))
      refute File.exist?(File.join(manifest['runtime'], 'host/saves'))
      refute File.exist?(File.join(manifest['evidence'], 'server'))
      assert_equal File.join(@options[:runtime_root], 'frozen-c4a', 'a' * 64, 'gramarye-1.0.0.jar'), manifest['jar']
      failure('INTEGRATED_HOST_REQUIRES_CLIENT_A') { P11OnlineRuntime.launch_server(manifest) }
      failure('INTEGRATED_HOST_STOPS_WITH_CLIENT') { P11OnlineRuntime.stop(manifest) }
      failure('SERVER_NOT_READY') { P11OnlineRuntime.server_ready_for_client!(manifest) }
      # The historical dedicated receipt cannot authorize the integrated partner.
      write_ready(manifest)
      failure('SERVER_READY_MISMATCH') { P11OnlineRuntime.server_ready_for_client!(manifest) }
      write_ready(manifest, ready_record(manifest).merge(
        'status' => 'ONLINE_INTEGRATED_PUBLISHED_NO_PARTNER_AUTH_CLAIM', 'integrated' => true))
      assert P11OnlineRuntime.server_ready_for_client!(manifest)
    end
  end

  def test_c4a_launcher_export_checks_its_own_pin_and_rejects_historical_case_for_same_bytes
    synthetic_pin = Digest::SHA256.file(@jar).hexdigest
    pins = lambda do |case_name|
      P11OnlineRuntime::C4A_CASES.include?(case_name) ? synthetic_pin : P11OnlineRuntime::JAR_SHA
    end
    P11OnlineRuntime.stub(:product_pin, pins) do
      manifest = prepare(case: 'c4a-dedicated')
      generated_fixture(manifest)
      failure('FROZEN_JAR_MISMATCH') { P11OnlineRuntime.freeze_launchers(manifest.merge('case' => 'qctx')) }
      refute File.exist?(File.join(manifest['runtime'], 'launch-bundle'))
      report = P11OnlineRuntime.stub(:candidate_head, 'e' * 40) { P11OnlineRuntime.freeze_launchers(manifest) }
      assert_equal synthetic_pin, report['productSha256']
      assert_equal 'FROZEN_NOT_LAUNCHED_NOT_ACCEPTANCE', report['status']
      frozen = P11OnlineRuntime.load_manifest(File.join(manifest['runtime'], 'frozen-manifest.json'),
        runtime_root: @options[:runtime_root], private_root: @private)
      assert P11OnlineRuntime.verify_frozen!(frozen)
      original = File.read(File.join(frozen['bundle'], 'originals/p11OnlineClientARunProgramArgs.txt'))
      assert_includes original, 'gramarye-p11-c4a-harness.mixins.json'
      refute_includes original, 'gramarye-p11-online-harness.mixins.json'
    end
  end

  def test_two_corrected_c4a_pins_coexist_without_replacing_prior_immutable_artifact
    original = File.binread(@jar)
    first_pin = Digest::SHA256.hexdigest(original)
    current_pin = first_pin
    P11OnlineRuntime.stub(:product_pin, ->(_case_name) { current_pin }) do
      first = P11OnlineRuntime.freeze_jar!(@jar, @options[:runtime_root], @private, 'c4a-dedicated')
      first_time = File.stat(first).mtime
      File.write(@jar, 'SECOND_SYNTHETIC_PRODUCT_REVISION')
      current_pin = Digest::SHA256.file(@jar).hexdigest
      second_pin = current_pin
      second = P11OnlineRuntime.freeze_jar!(@jar, @options[:runtime_root], @private, 'c4a-host-lan')
      refute_equal first, second
      assert_equal [first_pin, second_pin].sort, Dir.children(File.join(@options[:runtime_root], 'frozen-c4a')).sort
      assert_equal first_pin, Digest::SHA256.file(first).hexdigest
      assert_equal second_pin, Digest::SHA256.file(second).hexdigest
      assert_equal 0o400, File.stat(first).mode & 0o777
      assert_equal 0o400, File.stat(second).mode & 0o777
      current_pin = first_pin
      failure('FROZEN_JAR_MISMATCH') do
        P11OnlineRuntime.freeze_jar!(@jar, @options[:runtime_root], @private, 'c4a-dedicated')
      end
      File.write(@jar, original)
      assert_equal first, P11OnlineRuntime.freeze_jar!(@jar, @options[:runtime_root], @private, 'c4a-dedicated')
      assert_equal first_time, File.stat(first).mtime
      assert_equal second_pin, Digest::SHA256.file(second).hexdigest
    end
    assert_equal File.join(@options[:runtime_root], 'frozen/gramarye-1.0.0.jar'),
                 P11OnlineRuntime.frozen_jar_path(@options[:runtime_root], 'single')
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

  def test_capacity_respawn_status_lists_only_three_exact_structured_receipts
    manifest = prepare(case: 'c4a-capacity')
    client = File.join(manifest['evidence'], 'client-b')
    server = File.join(manifest['evidence'], 'server')
    Dir.mkdir(client)
    Dir.mkdir(server)
    %w[capacity-respawn.json capacity-respawn.json.extra capacity-death.ready latest.log].each do |leaf|
      File.write(File.join(client, leaf), 'SYNTHETIC_CONTENT_NOT_READ')
    end
    %w[b-capacity-death.json b-capacity-respawn.json b-capacity-respawn.json.extra b-capacity-death.ready].each do |leaf|
      File.write(File.join(server, leaf), 'SYNTHETIC_CONTENT_NOT_READ')
    end
    File.stub(:binread, ->(*) { flunk 'status must not read receipt content or native logs' }) do
      values = P11OnlineRuntime.status(manifest).fetch('present')
      assert_equal %w[prepare.json launch-cues.txt server/b-capacity-death.json
                      server/b-capacity-respawn.json client-b/capacity-respawn.json], values
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
      File.write(File.join(dir, 'gramarye-p11-c4a-harness.mixins.json'), '{"required":true}')
      File.write(File.join(dir, 'gramarye-p11-c6-observers.mixins.json'), '{"required":true}')
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
      program += P11OnlineRuntime.mixin_configs(manifest.fetch('case')).flat_map { |config| ['--mixin.config', config] }
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

  def test_expanded_exact_companion_inventory_and_bounded_launch_guard
    manifest = prepare(case: 'c4a-dedicated')
    generated_fixture(manifest)
    130.times do |index|
      name = "com/yo1no/gramarye/InventoryFixture#{index}"
      File.write(File.join(manifest['repo'], 'src/p11OnlineHarness/java', name + '.java'), 'SYNTHETIC_SOURCE_ONLY')
      File.write(File.join(manifest['repo'], 'build/classes/java/p11OnlineHarness', name + '.class'), 'SYNTHETIC_CLASS_ONLY')
    end
    frozen = export(manifest)
    assert_operator frozen.fetch('bundleFiles').size, :>, 256
    P11OnlineRuntime.stub(:verify_jar!, true) { assert P11OnlineRuntime.verify_frozen!(frozen) }
    oversized = frozen.merge('bundleFiles' => (0..P11OnlineRuntime::MAX_FROZEN_BUNDLE_FILES).to_h { |i| ["file#{i}", '0' * 64] })
    failure('INVALID_BUNDLE_INVENTORY') { P11OnlineRuntime.verify_frozen!(oversized) }
    [nil, {}, (0...24).to_h { |i| ["file#{i}", '0' * 64] }].each do |invalid|
      failure('INVALID_BUNDLE_INVENTORY') { P11OnlineRuntime.verify_frozen!(frozen.merge('bundleFiles' => invalid)) }
    end
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
              assert_equal 'OWNED_CLIENT_PROCESS_EXIT_NOT_ACCEPTANCE', report.fetch('status')
              assert_equal 0, report.fetch('exitCode')
              assert_nil report.fetch('signal')
              assert_match(/\A\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z\z/, report.fetch('startedAtUtc'))
              assert_match(/\A\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z\z/, report.fetch('endedAtUtc'))
              assert_operator Time.iso8601(report.fetch('endedAtUtc')), :>=, Time.iso8601(report.fetch('startedAtUtc'))
              assert_kind_of Numeric, report.fetch('elapsedSeconds')
              assert_operator report.fetch('elapsedSeconds'), :>=, 0
            end
          end
        end
      end
    end
    receipt = JSON.parse(File.read(File.join(manifest['evidence'], 'client-a-process-exit.json')))
    assert_equal %w[elapsedSeconds endedAtUtc exitCode signal startedAtUtc status], receipt.keys.sort
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
