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
    P11OnlineRuntime::L1_FIXTURE_HASHES.each_key do |leaf|
      relative = File.join('src/p11OnlineHarness/fixtures/l1', leaf)
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

  def test_java_and_launcher_product_pins_match_for_each_exact_family
    java = File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineInputs.java'))
    expected = { 'HISTORICAL_PRODUCT_PIN' => P11OnlineRuntime::JAR_SHA,
                 'C4A_PRODUCT_PIN' => P11OnlineRuntime::C4A_JAR_SHA,
                 'L1_PRODUCT_PIN' => P11OnlineRuntime::L1_JAR_SHA }
    expected.each do |name, hash|
      pattern = /private static final String #{Regexp.escape(name)} = "([0-9a-f]{64})";/
      assert_equal [hash], java.scan(pattern).flatten, name
      changed = java.sub(pattern, 'private static final String ' + name + ' = "' + ('0' * 64) + '";')
      refute_equal [hash], changed.scan(pattern).flatten, name
      refute_equal [hash], java.sub(pattern, '').scan(pattern).flatten, name
      refute_equal [hash], (java + "\n" + java.match(pattern)[0]).scan(pattern).flatten, name
    end
  end

  def test_cooldown_pin_is_matching_and_distinct_or_pending_fail_closed
    java = File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineInputs.java'))
    pattern = /private static final String COOLDOWN_PRODUCT_PIN = (null|"[0-9a-f]{64}");/
    pin = P11OnlineRuntime::COOLDOWN_JAR_SHA
    expected = pin.nil? ? 'null' : '"' + pin + '"'
    assert_equal [expected], java.scan(pattern).flatten
    refute_equal [expected], java.sub(pattern, '').scan(pattern).flatten
    refute_equal [expected], (java + "\n" + java.match(pattern)[0]).scan(pattern).flatten
    replacement = pin.nil? ? ('"' + ('0' * 64) + '"') : 'null'
    refute_equal [expected], java.sub(pattern, 'private static final String COOLDOWN_PRODUCT_PIN = ' + replacement + ';').scan(pattern).flatten
    (P11OnlineRuntime::COOLDOWN_CASES + P11OnlineRuntime::COOLDOWN_L1_CASES).each do |name|
      if pin.nil?
        failure('COOLDOWN_PRODUCT_PIN_PENDING') { P11OnlineRuntime.product_pin(name) }
        failure('COOLDOWN_PRODUCT_PIN_PENDING') { prepare(case: name, run_id: name) }
        refute File.exist?(File.join(@options[:runtime_root], name))
        refute File.exist?(File.join(@options[:evidence_root], name))
      else
        assert_match(/\A[0-9a-f]{64}\z/, pin)
        refute_includes [P11OnlineRuntime::JAR_SHA, P11OnlineRuntime::C4A_JAR_SHA, P11OnlineRuntime::L1_JAR_SHA], pin
        assert_equal pin, P11OnlineRuntime.product_pin(name)
        failure('FROZEN_JAR_MISMATCH') { P11OnlineRuntime.verify_jar!(@jar, name) }
      end
    end
    assert_includes java, 'COOLDOWN_PRODUCT_PIN != null && COOLDOWN_PRODUCT_PIN.matches("[0-9a-f]{64}")'
    assert_equal P11OnlineRuntime::JAR_SHA, P11OnlineRuntime.product_pin('single')
    assert_equal P11OnlineRuntime::C4A_JAR_SHA, P11OnlineRuntime.product_pin('c4a-dedicated')
    assert_equal P11OnlineRuntime::L1_JAR_SHA, P11OnlineRuntime.product_pin('l1-open')
  end

  # Pending-pin tests above use the real guard. Preparation below uses only our fake tree;
  # no constant, historical family, product artifact, or native launch is substituted.
  def with_cooldown_preparation_pin
    original = P11OnlineRuntime.method(:product_pin)
    pin = P11OnlineRuntime::COOLDOWN_JAR_SHA || Digest::SHA256.hexdigest('SYNTHETIC_COOLDOWN_PREPARATION_ONLY')
    selected = lambda do |name, l1_host_stop: false, cooldown_host: false|
      if P11OnlineRuntime::COOLDOWN_JAR_SHA.nil? && ((P11OnlineRuntime::COOLDOWN_CASES + P11OnlineRuntime::COOLDOWN_L1_CASES).include?(name) || cooldown_host)
        P11OnlineRuntime.cooldown_host_selection!(name, cooldown_host, l1_host_stop: l1_host_stop)
        pin
      else
        original.call(name, l1_host_stop: l1_host_stop, cooldown_host: cooldown_host)
      end
    end
    P11OnlineRuntime.stub(:product_pin, selected) { yield pin }
  end

  def test_cooldown_cases_mixins_and_startup_fixture_are_exact
    assert_equal %w[cooldown-d1 cooldown-d120 cooldown-d600 cooldown-restart-write cooldown-restart-read cooldown-prepared-reentry cooldown-add-false cooldown-add-remove cooldown-clone cooldown-before-arm-throw cooldown-after-arm-throw cooldown-unarmed-stop cooldown-save-active cooldown-save-clear cooldown-dual], P11OnlineRuntime::COOLDOWN_CASES
    P11OnlineRuntime::COOLDOWN_CASES.each do |name|
      assert_equal 1, P11OnlineRuntime::CASES.count(name)
      assert_equal %w[server a b], P11OnlineRuntime.roles_for(name)
      assert_equal ['gramarye-p11-cooldown-harness.mixins.json'], P11OnlineRuntime.mixin_configs(name)
      assert_equal({}, P11OnlineRuntime.reward_fixture_sources!(@options[:repo], @private, name))
      assert_equal FIXTURE, P11OnlineRuntime.startup_fixture!(@fixture, @private, name)
      failure('INVALID_L1_HOST_SELECTION') { P11OnlineRuntime.product_pin(name, l1_host_stop: true) }
    end
    %w[cooldown cooldown-d0 cooldown-d2 cooldown-d601 cooldown-d01 cooldown-d120.extra COOLDOWN-D1 cooldown-d1/../single].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name) }
      failure('INVALID_CASE') { P11OnlineRuntime.mixin_config(name) }
    end
    [FIXTURE.sub('maximumDepth = 32', 'maximumDepth = 31'),
     FIXTURE.sub('maxSealedSnapshots = 1', 'maxSealedSnapshots = 4')
            .sub('maxSealedBytes = 1', 'maxSealedBytes = 67108864'),
     FIXTURE + "extra = 1\n"].each do |changed|
      File.write(@fixture, changed)
      P11OnlineRuntime::COOLDOWN_CASES.each do |name|
        failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, name) }
      end
    end
    config = JSON.parse(File.read(File.join(P11OnlineRuntime::REPO,
      'src/p11OnlineHarness/resources/gramarye-p11-cooldown-harness.mixins.json')))
    assert_equal true, config['required']
    assert_equal 'com.yo1no.gramarye.harnessmixin', config['package']
    assert_equal 'JAVA_21', config['compatibilityLevel']
    assert_equal %w[P11OnlineAuthenticatorMixin P11CooldownLoginMixin P11CooldownCompositionMixin
                    P11CooldownOwnerMixin P11CooldownRuntimeMixin P11CooldownInstanceMixin P11CooldownFoundationMixin
                    P11CooldownFaultAddMixin P11CooldownFaultProjectileMixin P11CooldownFaultRuntimeMixin
                    P11CooldownFaultServerMixin P11CooldownCostCellMixin P11CooldownCostMixin P11CooldownFaultArmMixin
                    P11CooldownCloneCopyMixin P11CooldownCloneSerializerMixin P11CooldownCostPacketMixin
                    P11CooldownDurabilityAddMixin P11CooldownDurabilityWriterMixin
                    P11CooldownFaultOperationMixin P11CooldownFaultSourceMixin], config['mixins']
    assert_equal %w[P11CooldownClientInputMixin P11CooldownClientMirrorMixin P11CooldownHudMixin
                    P11CooldownCloneClientMixin P11C4aMouseInputMixin], config['client']
    assert_equal({ 'defaultRequire' => 1 }, config['injectors'])
  end

  def test_cooldown_preparation_is_fresh_non_authenticating_and_never_reads_private_inputs
    reads = []
    original_read = File.method(:binread)
    original_symlink = File.method(:symlink?)
    guarded_read = lambda do |file, *args|
      reads << file
      flunk 'private contents must not be read' if P11OnlineRuntime.within?(file, @private) || P11OnlineRuntime.within?(file, P11OnlineRuntime::PRIVATE_ROOT)
      original_read.call(file, *args)
    end
    guarded_symlink = lambda do |file|
      flunk 'real private root must not be inspected' if P11OnlineRuntime.within?(file, P11OnlineRuntime::PRIVATE_ROOT)
      original_symlink.call(file)
    end
    with_cooldown_preparation_pin do |pin|
      File.stub(:binread, guarded_read) do
        File.stub(:symlink?, guarded_symlink) do
          (P11OnlineRuntime::COOLDOWN_CASES - ['cooldown-restart-read']).each do |name|
            manifest = prepare(case: name, run_id: name)
            assert_equal name, manifest['case']
            assert_equal %w[server a b], manifest['roles']
            assert_equal pin, manifest['jarSha256']
            assert_equal File.join(@options[:runtime_root], 'frozen-cooldown', pin, 'gramarye-1.0.0.jar'), manifest['jar']
            assert_equal 'NOT_RUN_NOT_PROVEN', manifest['authenticationAcceptance']
            refute manifest.key?('rewardFixtureHashes')
            refute File.exist?(File.join(manifest['evidence'], 'server'))
            refute File.exist?(File.join(manifest['evidence'], 'client-a'))
            refute File.exist?(File.join(manifest['evidence'], 'client-b'))
            %w[a b].each do |role|
              assert_includes File.read(File.join(manifest['runtime'], "launch-client-#{role}.command")), 'FROZEN_LAUNCHERS_REQUIRED'
            end
            assert_equal manifest, P11OnlineRuntime.load_manifest(File.join(manifest['runtime'], 'manifest.json'),
              runtime_root: @options[:runtime_root], private_root: @private)
          end
          failure('PRIVATE_PATH_FORBIDDEN') { prepare(case: 'cooldown-d1', fixture: File.join(@private, 'accounts.json')) }
          failure('PRIVATE_PATH_FORBIDDEN') { prepare(case: 'cooldown-d1', jar: File.join(@private, 'accounts.json')) }
          failure('PRIVATE_PATH_FORBIDDEN') do
            P11OnlineRuntime.load_manifest(File.join(@private, 'manifest.json'), runtime_root: @options[:runtime_root], private_root: @private)
          end
        end
      end
    end
    refute reads.any? { |file| P11OnlineRuntime.within?(file, @private) }
    assert_equal ['accounts.json'], Dir.children(@private)
    assert_equal 'FAKE_SECRET_SENTINEL_NOT_REAL_CREDENTIALS', File.binread(File.join(@private, 'accounts.json'))
  end

  def test_cooldown_export_rejects_missing_duplicate_foreign_or_historical_mixin
    with_cooldown_preparation_pin do
      (P11OnlineRuntime::COOLDOWN_CASES - ['cooldown-restart-read']).each do |name|
        manifest = prepare(case: name, run_id: name)
        base = generated_fixture(manifest)
        file = File.join(base, 'p11OnlineClientARunProgramArgs.txt')
        original = File.read(file)
        suffix = "\n--mixin.config\ngramarye-p11-cooldown-harness.mixins.json"
        assert original.end_with?(suffix)
        [original.delete_suffix(suffix), original + suffix,
         original.sub('gramarye-p11-cooldown-harness.mixins.json', 'gramarye-p11-cooldown-harness.mixins.json.extra'),
         original.sub('gramarye-p11-cooldown-harness.mixins.json', 'gramarye-p11-online-harness.mixins.json'),
         original + "\n--mixin.config\ngramarye-p11-c6-observers.mixins.json"].each do |raw|
          refute_equal original, raw
          File.write(file, raw)
          failure('GENERATED_PROGRAM_ARGUMENTS_MISMATCH') { export(manifest) }
          refute File.exist?(File.join(manifest['runtime'], 'launch-bundle'))
        end
        File.write(file, original)
        frozen = export(manifest)
        assert P11OnlineRuntime.stub(:verify_jar!, true) { P11OnlineRuntime.verify_frozen!(frozen) }
        assert_equal 9, frozen['bundleFiles'].keys.count { |path| path.start_with?('resources/') }
      end
    end
  end

  def test_cooldown_status_is_only_named_receipts_without_content_or_auth_inference
    names = %w[cooldown-result.json data-terminal.json formal-submission.json arm-1.json arm-2.json
               saved-active.json active-refusal.json reconnect-active.json]
    with_cooldown_preparation_pin do
      (P11OnlineRuntime::COOLDOWN_CASES - ['cooldown-restart-read']).each do |name|
        manifest = prepare(case: name, run_id: name)
        server = File.join(manifest['evidence'], 'server')
        Dir.mkdir(server)
        (names + names.map { |leaf| leaf + '.extra' } + %w[arm-3.json cooldown-result.ready accounts.json native-console.txt]).each do |leaf|
          File.write(File.join(server, leaf), 'SYNTHETIC_CONTENT_MUST_NOT_BE_READ')
        end
        File.stub(:binread, ->(*) { flunk 'status must not read receipt contents' }) do
          report = P11OnlineRuntime.status(manifest)
          assert_equal ['prepare.json', 'launch-cues.txt'] + names.map { |leaf| 'server/' + leaf }, report['present']
          assert_equal 'STRUCTURED_FILE_PRESENCE_ONLY', report['status']
          assert_equal 'NOT_INFERRED_FROM_FILE_PRESENCE', report['authenticationAcceptance']
        end
      end
    end
  end

  def test_cooldown_restart_new_r_waits_for_actual_ready_draw_without_changing_original_sender
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    server = File.read(File.join(base, 'P11CooldownServerHarness.java'))
    client = File.read(File.join(base, 'P11CooldownClientHarness.java'))
    valid = lambda do |source|
      branch = source[/case RESTART_EXPIRY -> \{(.*?)case RESTART_NEW_ARM/m, 1]
      branch && branch.include?('gameTime() < P11CooldownRestartProbe.expiresAt()') &&
        branch.include?('!receipt("client-a", "hud-ready.json")') &&
        branch.index('return;') < branch.index('cue("a-cast-2.ready")') &&
        source.scan('!receipt("client-a", "hud-ready.json")').size == 1
    end
    assert valid.call(server), 'READ starts ACTIVE; natural expiry alone does not prove actual READY delivery/draw'
    refute valid.call(server.sub('!receipt("client-a", "hud-ready.json")', 'false'))
    refute valid.call(server.gsub('gameTime() < P11CooldownRestartProbe.expiresAt()', 'false'))
    refute valid.call(server + '\n!receipt("client-a", "hud-ready.json")')
    assert_includes client, '&& readyApplied && readyDrawn && (P11CooldownServerHarness.duration() == 1'
    assert_includes client, 'P11C4aEvidence.write(output, "hud-ready.json"'
    refute_match(/setGameTime\(|Thread\.sleep\(|setSyncSequence\(/, server)
  end

  def test_cooldown_each_named_cast_may_request_focus_but_never_bypass_original_input_gates
    source = File.read(File.join(P11OnlineRuntime::REPO,
      'src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownClientHarness.java'))
    valid = lambda do |text|
      branch = text[/if \(starter && expectedReference != null && casts < expectedCasts\(\)(.*?)if \(starter && inputReady/m, 1]
      branch && branch.include?('focusedCast < casts + 1 && cue(castRole() + "-cast-" + (casts + 1) + ".ready")') &&
        branch.include?('focusedCast = casts + 1; focusAt = phaseTicks;') &&
        branch.include?('GLFW.glfwFocusWindow(minecraft.getWindow().getWindow()); return;') &&
        branch.include?('phaseTicks - focusAt >= 100 && stalledCast < focusedCast') &&
        !branch.match?(/inputReady\s*=|inputArmed\s*=|casts\+\+|sends\+\+|sendCastIntent|keyPress\(/)
    end
    assert valid.call(source)
    refute valid.call(source.sub('casts < expectedCasts()', 'true'))
    refute valid.call(source.sub('focusedCast < casts + 1', 'true'))
    refute valid.call(source.sub('phaseTicks - focusAt >= 100', 'true'))
    refute valid.call(source.sub('focusedCast = casts + 1;', 'inputReady = true; focusedCast = casts + 1;'))
    assert_includes source, 'if (starter && inputReady && minecraft.isWindowActive() && expectedReference != null'
    assert_includes source, 'expectedReference.equals(mirroredReference) && cue(castRole() + "-cast-" + (casts + 1) + ".ready")'
    assert_includes source, 'if (minecraft.getOverlay() != null || minecraft.screen != null) return;'
    with_cooldown_preparation_pin do
      manifest = prepare(case: 'cooldown-clone', run_id: 'cooldown-clone-input')
      dir = File.join(manifest['evidence'], 'client-a'); Dir.mkdir(dir)
      names = %w[input-focus-1.json input-focus-2.json input-focus-3.json input-stall-1.json input-stall-2.json input-stall-3.json]
      (names + %w[input-focus-4.json input-stall-4.json input-focus-1.json.extra accounts.json]).each do |leaf|
        File.write(File.join(dir, leaf), 'SYNTHETIC_CONTENT_NOT_READ')
      end
      File.stub(:binread, ->(*) { flunk 'presence reader must not read content' }) do
        assert_equal ['prepare.json', 'launch-cues.txt'] + names.map { |name| "client-a/#{name}" }, P11OnlineRuntime.status(manifest)['present']
      end
    end
  end

  def test_l1_cases_are_closed_and_do_not_reuse_an_old_product_pin
    assert_equal %w[l1-pre-spawn l1-open l1-claimed l1-impact-close-custody l1-two-work-reload l1-two-work-stop l1-revision l1-p8-send-fault l1-ack-fault l1-work-death l1-work-dimension l1-work-config l1-partial-reward-function l1-work-multi-uuid-qctx l1-stats-write-fault-memory l1-work-deadline l1-spawn-callback-remove l1-logout-cleanup-fault l1-tracking-retirement l1-work-capacity l1-natural-unload l1-online-peer l1-work-context-refusal l1-restart-write l1-restart-read], P11OnlineRuntime::L1_CASES
    P11OnlineRuntime::L1_CASES.each do |name|
      assert_equal ['gramarye-p11-l1-harness.mixins.json'], P11OnlineRuntime.mixin_configs(name)
      assert_equal %w[server a b], P11OnlineRuntime.roles_for(name)
      failure('L1_PRODUCT_PIN_PENDING') { P11OnlineRuntime.product_pin(name) } if P11OnlineRuntime::L1_JAR_SHA.nil?
      source = P11OnlineRuntime.reward_fixture_sources!(@options[:repo], @private, name)
      assert_equal P11OnlineRuntime::L1_FIXTURE_HASHES.keys.sort, source.keys.sort
      assert_equal source.transform_values { |bytes| Digest::SHA256.hexdigest(bytes) }, P11OnlineRuntime::L1_FIXTURE_HASHES
    end
    %w[l1-open.extra l1 l1-open/../single L1-OPEN].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name) }
      failure('INVALID_CASE') { P11OnlineRuntime.mixin_config(name) }
    end
  end

  def test_current_cooldown_l1_aliases_keep_original_semantics_with_exact_new_pin
    assert_equal %w[cooldown-l1-pre-spawn cooldown-l1-open cooldown-l1-claimed], P11OnlineRuntime::COOLDOWN_L1_CASES
    with_cooldown_preparation_pin do |pin|
      P11OnlineRuntime::COOLDOWN_L1_CASES.each do |name|
        assert_equal pin, P11OnlineRuntime.product_pin(name)
        assert_equal ['gramarye-p11-l1-harness.mixins.json'], P11OnlineRuntime.mixin_configs(name)
        assert_equal FIXTURE, P11OnlineRuntime.startup_fixture!(@fixture, @private, name)
        assert_equal P11OnlineRuntime::L1_FIXTURE_HASHES,
          P11OnlineRuntime.reward_fixture_sources!(@options[:repo], @private, name).transform_values { |raw| Digest::SHA256.hexdigest(raw) }
        manifest = prepare(case: name, run_id: name)
        assert_equal pin, manifest.fetch('jarSha256')
        assert_equal P11OnlineRuntime::L1_FIXTURE_HASHES, manifest.fetch('rewardFixtureHashes')
        generated_fixture(manifest)
        frozen = export(manifest)
        assert P11OnlineRuntime.stub(:verify_jar!, true) { P11OnlineRuntime.verify_frozen!(frozen) }
        failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name + '.extra') }
      end
      assert_equal P11OnlineRuntime::L1_JAR_SHA, P11OnlineRuntime.product_pin('l1-open')
      refute_equal pin, P11OnlineRuntime.product_pin('l1-open')
    end
  end

  def test_positive_l1_receipts_and_abort_remain_exact_current_case_only
    names = %w[cooldown-l1-formal-submission.json cooldown-l1-episode-1.json
               cooldown-l1-episode-2.json cooldown-l1-episode-3.json]
    with_cooldown_preparation_pin do
      P11OnlineRuntime::COOLDOWN_L1_CASES.each do |name|
        manifest = prepare(case: name, run_id: name)
        server = File.join(manifest['evidence'], 'server')
        Dir.mkdir(server)
        (names + names.map { |leaf| leaf + '.extra' } + %w[cooldown-l1-episode-4.json native-console.txt accounts.json]).each do |leaf|
          File.write(File.join(server, leaf), 'SYNTHETIC_CONTENT_MUST_NOT_BE_READ')
        end
        File.stub(:binread, ->(*) { flunk 'status must not read receipt contents' }) do
          report = P11OnlineRuntime.status(manifest)
          assert_equal ['prepare.json', 'launch-cues.txt', 'reward-fixture.json'] + names.map { |leaf| 'server/' + leaf }, report['present']
          assert_equal 'NOT_INFERRED_FROM_FILE_PRESENCE', report['authenticationAcceptance']
          historical = manifest.merge('case' => 'l1-open')
          refute P11OnlineRuntime.status(historical)['present'].any? { |leaf| leaf.include?('cooldown-l1-') }
        end
      end
    end
    client = File.read(File.join(P11OnlineRuntime::REPO,
      'src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1ClientHarness.java'))
    guarded = lambda do |source|
      source.include?('if (P11CooldownL1Probe.selected() && serverOutput != null && cue("abort.ready")) {') &&
        source.include?('fail(minecraft, "OWNED_SUPERVISOR_ABORT"); return;')
    end
    assert guarded.call(client)
    refute guarded.call(client.sub('P11CooldownL1Probe.selected() && ', ''))
    refute guarded.call(client.sub('serverOutput != null && ', ''))
    refute guarded.call(client.sub('fail(minecraft, "OWNED_SUPERVISOR_ABORT"); return;', 'return;'))
  end

  def test_positive_l1_focus_request_does_not_bypass_original_input_gates
    file = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1ClientHarness.java')
    source = File.read(file)
    branch = source[/private static boolean prepareCooldownInput.*?private static boolean twoWork/m]
    guarded = lambda do |value|
      value.include?('!P11CooldownL1Probe.selected() || !"a".equals(role) || casts >= 3') &&
        value.include?('cooldownInputObservedCast < casts + 1 && cue("a-cast-" + (casts + 1) + ".ready")') &&
        value.include?('cooldownInputObservedCast = casts + 1; cooldownInputObservedAt = ticks;') &&
        value.scan('GLFW.glfwFocusWindow(').size == 1
    end
    assert branch && guarded.call(branch)
    refute guarded.call(branch.sub('!P11CooldownL1Probe.selected() || ', ''))
    refute guarded.call(branch.sub('casts >= 3', 'false'))
    refute guarded.call(branch.sub('cooldownInputObservedCast < casts + 1 && ', ''))
    refute_match(/keyPress|sendCommand|sendPayload|resetMapping|releaseAll|ready =|casts\+\+/, branch)
    assert_includes source, 'starter && casts < castLimit() && ready && initialDelivered() && minecraft.isWindowActive() && cue(role + "-cast-" + (casts + 1) + ".ready")'
    assert_includes branch, 'ACTUAL_INPUT_GATES_STILL_WAITING_NOT_CAUSAL_ATTRIBUTION'
  end

  def cooldown_restart_write_fixture
    manifest = prepare(case: 'cooldown-restart-write', accept_eula: true, run_id: 'cooldown-write-001')
    generated_fixture(manifest)
    frozen = export(manifest)
    server = File.join(frozen.fetch('evidence'), 'server')
    FileUtils.mkdir_p(server)
    terminal = { 'exitCode' => 0, 'signal' => nil, 'elapsedSeconds' => 1.0,
                 'endedAtUtc' => '2026-10-08T00:00:00.000000Z' }
    File.write(File.join(frozen.fetch('runtime'), 'server.exit.json'), JSON.generate(terminal.merge('status' => 'PROCESS_EXIT_NOT_ACCEPTANCE')))
    { 'a' => 1, 'b' => 0 }.each do |role, sends|
      File.write(File.join(frozen.fetch('evidence'), "client-#{role}-process-exit.json"),
        JSON.generate(terminal.merge('status' => 'OWNED_CLIENT_PROCESS_EXIT_NOT_ACCEPTANCE')))
      client = File.join(frozen.fetch('evidence'), "client-#{role}")
      FileUtils.mkdir_p(client)
      File.write(File.join(client, 'result.json'), JSON.generate(
        'status' => 'ORIGINAL_SERVER_STOP_CLIENT_TERMINAL', 'logins' => 1,
        'originalP9Sends' => sends, 'originalKeyCallbackClicks' => sends,
        'closed' => true, 'playerAbsent' => true, 'levelAbsent' => true, 'currentListenerAbsent' => true))
    end
    File.write(File.join(server, 'data-terminal.json'), JSON.generate(
      'status' => 'NORMAL_NATIVE_STOP_AND_ROOTS_ZERO', 'nativeStopNormal' => true, 'sourceFailures' => 0, 'dirtyUuids' => 0,
      'allRootCounts' => %w[WORK NATIVE_CREDIT OPERATION COMMAND_CONTEXT TRANSITION].map { |kind| { 'kind' => kind, 'count' => 0 } }))
    world = File.join(frozen.fetch('runtime'), 'server/p11-online-world')
    public_uuid = '00000000-0000-0000-0000-000000000001'
    File.write(File.join(server, 'cooldown-restart-expected.json'), JSON.generate(
      'schema' => 1, 'status' => 'ORIGINAL_COOLDOWN_STOPPED_WORLD_NOT_RESTART_PROOF',
      'case' => 'cooldown-restart-write', 'writeRunId' => frozen.fetch('runId'),
      'productionJarSha256' => frozen.fetch('jarSha256'), 'world' => world,
      'publicMinecraftUuid' => public_uuid, 'skillId' => '00000000-0000-0000-0000-000000000002',
      'revision' => 1, 'cooldownTicks' => 600,
      'obligation' => { 'acceptedAt' => 1000, 'releaseNotAfter' => 1101, 'releasedAt' => 1001,
        'expiresAt' => 1601, 'attemptId' => '00000000-0000-0000-0000-000000000003' },
      'definitionSha256' => '1' * 64,
      'fileSha256' => { 'data/gramarye_skill_definitions.dat' => '2' * 64, "playerdata/#{public_uuid}.dat" => '3' * 64 },
      'loadedConfiguration' => { 'path' => File.join(world, 'serverconfig/gramarye-server.toml'),
        'sha256' => P11OnlineRuntime::FIXTURE_SHA }, 'stoppedGameTime' => 1002,
      'originalStopNormal' => true, 'openWorkClosedByStop' => true))
    frozen
  end

  def cooldown_restart_source_for(frozen)
    P11OnlineRuntime.stub(:verify_jar!, true) do
      P11OnlineRuntime.cooldown_restart_source!(File.join(frozen.fetch('runtime'), 'frozen-manifest.json'),
        @options[:repo], @options[:runtime_root], @options[:evidence_root], @private, 'cooldown-read-002')
    end
  end

  def test_cooldown_restart_exact_stopped_world_binding_and_frozen_universe
    with_cooldown_preparation_pin do
      old = cooldown_restart_write_fixture
      source, bytes = cooldown_restart_source_for(old)
      before = Dir.glob(File.join(source.fetch('world'), '**/*')).select { |file| File.file?(file) }
        .to_h { |file| [file, Digest::SHA256.file(file).hexdigest] }
      read = prepare(case: 'cooldown-restart-read', accept_eula: true, run_id: 'cooldown-read-002',
        restart_from: File.join(old.fetch('runtime'), 'frozen-manifest.json'))
      assert_equal source, read.fetch('restartSource')
      assert_equal bytes, File.binread(File.join(read.fetch('evidence'), 'cooldown-restart-input.json'))
      refute File.exist?(File.join(read.fetch('runtime'), 'server/p11-online-world'))
      assert_equal before, before.keys.to_h { |file| [file, Digest::SHA256.file(file).hexdigest] }
      assert_includes P11OnlineRuntime.gradle_argv(read, 'createP11OnlineServerLaunchScript'),
        "-PgramaryeP11OnlineRestartUniverse=#{source.fetch('universe')}"
      generated_fixture(read)
      frozen = export(read)
      program = File.read(File.join(frozen.fetch('bundle'), 'originals/p11OnlineServerRunProgramArgs.txt'))
      assert_includes program, "--universe\n#{source.fetch('universe')}\n--world\np11-online-world\n"
      assert P11OnlineRuntime.stub(:verify_jar!, true) { P11OnlineRuntime.verify_frozen!(frozen) }
      input = File.join(read.fetch('evidence'), 'cooldown-restart-input.json')
      File.chmod(0o600, input)
      File.binwrite(input, bytes + ' ')
      P11OnlineRuntime.stub(:verify_jar!, true) { failure('RESTART_FIXED_INPUT_CHANGED') { P11OnlineRuntime.verify_restart_input!(read) } }
    end
  end

  def test_cooldown_restart_requires_its_own_case_process_and_native_terminal_facts
    with_cooldown_preparation_pin do
      failure('RESTART_SOURCE_ONLY_EXACT_READ_CASE') { prepare(case: 'cooldown-restart-read') }
      failure('RESTART_SOURCE_ONLY_EXACT_READ_CASE') { prepare(case: 'cooldown-d600', restart_from: @fixture) }
      old = cooldown_restart_write_fixture
      paths = {
        File.join(old.fetch('runtime'), 'server.exit.json') => ['RESTART_ORIGINAL_PROCESS_NOT_NORMAL_TERMINAL', { 'exitCode' => 1 }, { 'signal' => 15 }],
        File.join(old.fetch('evidence'), 'client-a/result.json') => ['RESTART_ORIGINAL_CLIENT_FLOW_NOT_TERMINAL', { 'originalP9Sends' => 0 }, { 'closed' => false }],
        File.join(old.fetch('evidence'), 'client-b/result.json') => ['RESTART_ORIGINAL_CLIENT_FLOW_NOT_TERMINAL', { 'originalP9Sends' => 1 }, { 'currentListenerAbsent' => false }],
        File.join(old.fetch('evidence'), 'server/data-terminal.json') => ['RESTART_ORIGINAL_DATA_NOT_CLEAN_TERMINAL', { 'sourceFailures' => 1 }, { 'nativeStopNormal' => false }, { 'dirtyUuids' => 1 }, { 'allRootCounts' => [] }]
      }
      paths.each do |file, (code, *changes)|
        original = File.binread(file)
        changes.each do |change|
          File.write(file, JSON.generate(JSON.parse(original).merge(change)))
          failure(code) { cooldown_restart_source_for(old) }
        end
        File.binwrite(file, original)
      end
      historical = restart_write_fixture
      failure('RESTART_SOURCE_COHORT_MISMATCH') { cooldown_restart_source_for(historical) }
      failure('RESTART_SOURCE_COHORT_MISMATCH') { restart_source_for(old) }
      source, = cooldown_restart_source_for(old)
      assert_equal old.fetch('runId'), source.fetch('writeRunId')
    end
  end

  def test_cooldown_restart_closed_identity_and_obligation_reject_unknown_or_fabricated_inputs
    with_cooldown_preparation_pin do
      old = cooldown_restart_write_fixture
      receipt = File.join(old.fetch('evidence'), 'server/cooldown-restart-expected.json')
      original = File.binread(receipt)
      value = JSON.parse(original)
      [{ 'case' => 'l1-restart-write' }, { 'extra' => true }, { 'productionJarSha256' => '0' * 64 },
       { 'world' => File.join(@root, 'foreign-world') }, { 'openWorkClosedByStop' => false }].each do |change|
        File.binwrite(receipt, JSON.generate(value.merge(change)))
        failure('RESTART_EXACT_STOPPED_WORLD_MISMATCH') { cooldown_restart_source_for(old) }
      end
      [{ 'cooldownTicks' => 120 }, { 'revision' => -1 }, { 'stoppedGameTime' => 1601 },
       { 'obligation' => value.fetch('obligation').merge('expiresAt' => 1701) },
       { 'obligation' => value.fetch('obligation').merge('releaseNotAfter' => 1102) },
       { 'publicMinecraftUuid' => '../private' }].each do |change|
        File.binwrite(receipt, JSON.generate(value.merge(change)))
        failure('COOLDOWN_RESTART_OBLIGATION_MISMATCH') { cooldown_restart_source_for(old) }
      end
      File.binwrite(receipt, JSON.generate(value.merge('fileSha256' => { '../foreign.dat' => '0' * 64 })))
      failure('COOLDOWN_RESTART_STORAGE_IDENTITY_MISMATCH') { cooldown_restart_source_for(old) }
      File.binwrite(receipt, original)
      File.write(File.join(old.fetch('evidence'), 'server/cooldown-restart-stop-failure.json'), 'SYNTHETIC_FAILURE_PRESENCE_ONLY')
      failure('RESTART_SOURCE_RECORDED_FAILURE') { cooldown_restart_source_for(old) }
    end
  end

  def test_l1_impact_close_is_one_original_uncancelled_hit_and_native_cleanup
    assert_includes P11OnlineRuntime::L1_CASES, 'l1-impact-close-custody'
    failure('INVALID_CASE') { P11OnlineRuntime.product_pin('l1-impact-close-custody.extra') }
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    helper = File.read(File.join(base, 'P11L1ImpactCustodyProbe.java'))
    hook = File.read(File.join(base, 'harnessmixin/P11L1ImpactCustodyMixin.java'))
    parent = File.read(File.join(base, 'P11L1ServerHarness.java'))
    assert_equal 1, helper.scan('r.connection.disconnect(').length
    refute_includes helper, '.handleDisconnection('
    assert_operator hook.index('original.call(projectile, hit)'), :<, hook.index('impactReturned(projectile, hit, cancelled)')
    assert_includes hook, 'return cancelled;'
    assert_includes parent, 'IMPACT_SELECTED_HOLD_NOT_EARLY_CLAIM'
    assert_includes parent, 'require(actualVictim == run.victim && run.claims == 1'
    assert_includes helper, 'r.claimRuntimeTick == r.impactRuntimeTick'
    assert_includes helper, 'child.deadlineRuntimeTick() == r.deadline'
    assert_includes helper, 'r.candidate == candidate'
    assert_includes helper, 'if (primary != null) { r.logoutFailed = true; return; }'
    %w[P11L1ImpactCustodyProbe.java harnessmixin/P11L1ImpactCustodyMixin.java harnessmixin/P11L1ImpactClaimMixin.java].each do |name|
      exact = "src/p11OnlineHarness/java/com/yo1no/gramarye/#{name}"
      catalogue = File.read(File.join(P11OnlineRuntime::REPO, 'scripts/verify-p7-s4-source-contracts.sh'))
      assert_includes catalogue, exact + ' |'
      refute_includes catalogue, exact + '.extra |'
    end
  end

  def test_l1_native_reward_inputs_and_named_paths_are_exact
    P11OnlineRuntime::L1_FIXTURE_HASHES.each do |leaf, hash|
      file = File.join(@options[:repo], 'src/p11OnlineHarness/fixtures/l1', leaf)
      assert_equal hash, Digest::SHA256.file(file).hexdigest
      original = File.binread(file)
      File.write(file, original + ' ')
      failure('REWARD_FIXTURE_SOURCE_MISMATCH') { P11OnlineRuntime.reward_fixture_sources!(@options[:repo], @private, 'l1-open') }
      File.binwrite(file, original)
    end
    first = JSON.parse(File.read(File.join(@options[:repo], 'src/p11OnlineHarness/fixtures/l1/advancement/l1_first_kill.json')))
    assert_equal 'minecraft:player_killed_entity', first.dig('criteria', 'native_kill', 'trigger')
    assert_equal 7, first.dig('rewards', 'experience')
    assert_equal ['minecraft:bread'], first.dig('rewards', 'recipes')
    assert_equal 'gramarye_p11_engineering:l1_reward', first.dig('rewards', 'function')
    catalog = File.read(File.join(P11OnlineRuntime::REPO, 'scripts/verify-p7-s4-source-contracts.sh'))
    %w[P11L1ServerHarness.java P11L1ClientHarness.java].each do |name|
      exact = "src/p11OnlineHarness/java/com/yo1no/gramarye/#{name}"
      assert_includes catalog, exact + ' |'
      refute_includes catalog, exact + '.extra |'
    end
  end

  def test_l1_context_aliases_use_new_pin_and_original_context_driver_only
    assert_equal %w[l1-qctx l1-capacity], P11OnlineRuntime::L1_CONTEXT_CASES
    { 'l1-qctx' => 2, 'l1-capacity' => 1 }.each do |name, limit|
      assert_equal P11OnlineRuntime::L1_JAR_SHA, P11OnlineRuntime.product_pin(name)
      assert_equal ['gramarye-p11-online-harness.mixins.json'], P11OnlineRuntime.mixin_configs(name)
      assert_equal %w[server a b], P11OnlineRuntime.roles_for(name)
      assert_equal File.join(@options[:runtime_root], 'frozen-l1', P11OnlineRuntime::L1_JAR_SHA,
                             'gramarye-1.0.0.jar'), P11OnlineRuntime.frozen_jar_path(@options[:runtime_root], name)
      manifest = prepare(case: name, run_id: 'context-' + name)
      expected = FIXTURE.sub('p11.retention.maxUuids = 4', "p11.retention.maxUuids = #{limit}")
                        .sub('p11.save.dirtyUuidAdmissionWatermark = 4', "p11.save.dirtyUuidAdmissionWatermark = #{limit}")
      assert_equal expected, File.binread(File.join(manifest['runtime'], 'server/defaultconfigs/gramarye-server.toml'))
      assert_equal P11OnlineRuntime::L1_JAR_SHA, manifest.fetch('jarSha256')
      assert_equal name, manifest.fetch('case')
      assert_equal({}, P11OnlineRuntime.reward_fixture_sources!(@options[:repo], @private, name))
    end
    %w[l1-qctx.extra l1-capacity-extra l1-capacity/../qctx L1-CAPACITY].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name) }
      failure('INVALID_CASE') { P11OnlineRuntime.mixin_configs(name) }
    end
    inputs = File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineInputs.java'))
    assert_includes inputs, 'P11L1ServerHarness.enabled() || l1ContextCase()'
    assert_includes inputs, 'case "c4a-qctx", "l1-qctx" -> "qctx";'
    assert_includes inputs, 'case "c4a-capacity", "l1-capacity" -> "capacity";'
    natural = File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1ServerHarness.java'))
    refute_match(/List\.of\([^;]+"l1-qctx"/m, natural)
  end

  def test_l1_two_work_branches_preserve_natural_three_and_original_boundary_calls
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    server = File.read(File.join(base, 'P11L1ServerHarness.java'))
    client = File.read(File.join(base, 'P11L1ClientHarness.java'))
    probe = File.read(File.join(base, 'P11L1WorkBoundaryProbe.java'))
    assert_includes server, 'if (twoWork()) { progressTwoWork(); return; }'
    assert_operator server.index('if (twoWork())'), :<, server.index('if (run == null)')
    %w[accepted instanceCreated damageEntering experience transferred claimed rootRetired].each do |name|
      assert_includes server, "P11L1WorkBoundaryProbe.#{name}("
    end
    assert_includes server, 'twoWorkStop() && P11L1WorkBoundaryProbe.stopRequested()'
    assert_includes probe, 'r.server.reloadResources(r.server.getPackRepository().getSelectedIds())'
    assert_includes probe, 'r.server.halt(false);'
    assert_includes probe, 'workCount(r) == 2 && otherRoots(r) == 0 && r.releases == 0'
    assert_includes probe, 'r.releaseAfter[slot] == 1 - r.releases'
    forbidden = /\.admitAuthenticatedPlayerCast\(|\.onHitEntity\(|\.tickCount\s*=(?!=)|Thread\.sleep/
    refute_match(forbidden, probe)
    assert_match(forbidden, probe.sub('.tickCount == 0', '.tickCount = 0'))
    assert_includes client, 'ORIGINAL_SERVER_STOP_CLIENT_TERMINAL_NOT_SERVER_PROOF'
    assert_includes client, 'P11C4aEvidence.receiptPresent(serverOutput, "two-work-result.json")'
    assert_includes client, 'twoWork() ? logins == 1 && sends == 2 && casts == 2'
    assert_includes client, ': logins == 4 && sends == 3 && casts == 3'
    json = JSON.parse(File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/resources/gramarye-p11-l1-harness.mixins.json')))
    assert_equal 1, json.fetch('mixins').count('P11L1WorkReleaseMixin')
    catalog = File.read(File.join(P11OnlineRuntime::REPO, 'scripts/verify-p7-s4-source-contracts.sh'))
    %w[P11L1WorkBoundaryProbe.java harnessmixin/P11L1WorkReleaseMixin.java].each do |name|
      exact = 'src/p11OnlineHarness/java/com/yo1no/gramarye/' + name
      assert_includes catalog, exact + ' |'
      refute_includes catalog, exact + '.extra |'
    end
  end

  def test_l1_nonlogout_lifecycle_routes_keep_original_calls_and_actual_client_receipts
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    server = File.read(File.join(base, 'P11L1ServerHarness.java'))
    client = File.read(File.join(base, 'P11L1ClientHarness.java'))
    probe = File.read(File.join(base, 'P11L1LifecycleBoundaryProbe.java'))
    ui = File.read(File.join(base, 'P11L1LifecycleClientProbe.java'))
    branch = server.index('if (lifecycle()) { progressLifecycle(); return; }')
    refute_nil branch
    assert_operator branch, :<, server.index('if (run == null)')
    %w[instanceCreated accepted transferred claimed damageEntering experience].each do |name|
      assert_includes server, "P11L1LifecycleBoundaryProbe.#{name}("
    end
    %w[l1-work-death l1-work-dimension l1-work-config].each do |name|
      assert_includes P11OnlineRuntime::L1_CASES, name
      assert_includes server, '"' + name + '"'
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name + '.extra') }
    end
    assert_includes probe, 'case ONE_WORK_DEATH -> r.actor.kill();'
    assert_includes probe, 'r.actor.changeDimension(new DimensionTransition('
    assert_includes probe, 'case ONE_WORK_CONFIG -> r.actor.connection.switchToConfig();'
    assert_includes probe, 'configuration.returnToWorld();'
    assert_includes probe, 'r.instance.logoutState != SkillRuntimeService.LogoutState.COMPLETE'
    assert_includes probe, 'r.permit.qualification(r.server, r.projectile) == SkillRuntimeService.WorkQualification.INVALID'
    assert_includes probe, 'lifecycle-world-ready.json'
    assert_includes client, 'if (lifecycleArmed && !P11L1LifecycleClientProbe.tick(minecraft)) { return; }'
    assert_includes client, 'ACTUAL_SAME_CONNECTION_CONFIG_LOGIN_NOT_NEW_AUTH'
    assert_includes server, 'ORIGINAL_CONFIG_LOGIN_NOT_NEW_AUTHENTICATION'
    %w["ACTUAL_CONFIG_TERMINAL" "TRUE_CONFIG_RETURN_LOGIN" "TRUE_DEATH_RESPAWN" "TRUE_ORIGINAL_DIMENSION_FRAME"].each do |guard|
      assert_includes ui, guard
      refute_includes ui.sub(guard, '"REMOVED_GUARD"'), guard
    end
    assert_includes ui, 'state.actorGeneration() == 0 && state.requestSeq() == 0 && minecraft.player == null'
    assert_includes ui, 'state.targetActorGeneration() > 0 && minecraft.player != original'
    assert_includes ui, 'GLFW.GLFW_PRESS'
    assert_includes ui, 'GLFW.GLFW_RELEASE'
    refute_match(/\.respawn\(|P11ClientTransitions\.(?:retry|send)\(|\.setScreen\(/, ui)
    refute_match(/\.admitAuthenticatedPlayerCast\(|\.onHitEntity\(|\.tickCount\s*=(?!=)|Thread\.sleep/, probe)
    manifest = JSON.parse(File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/resources/gramarye-p11-l1-harness.mixins.json')))
    assert_equal 1, manifest.fetch('mixins').count('P11L1LifecycleReleaseMixin')
    %w[P11L1LifecycleClientMixin P11C4aMouseInputMixin].each { |name| assert_equal 1, manifest.fetch('client').count(name) }
    catalog = File.read(File.join(P11OnlineRuntime::REPO, 'scripts/verify-p7-s4-source-contracts.sh'))
    %w[P11L1LifecycleBoundaryProbe.java P11L1LifecycleClientProbe.java harnessmixin/P11L1LifecycleReleaseMixin.java harnessmixin/P11L1LifecycleClientMixin.java].each do |name|
      exact = 'src/p11OnlineHarness/java/com/yo1no/gramarye/' + name
      assert_equal 1, catalog.scan(exact + ' |').size
      refute_includes catalog, exact + '.extra |'
    end
  end

  def test_l1_native_lava_statistics_branch_does_not_invent_type_kill
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    server = File.read(File.join(base, 'P11L1ServerHarness.java'))
    reward = File.read(File.join(base, 'harnessmixin/P11L1RewardMixin.java'))
    assert_includes reward, 'beginKillScore((ServerPlayer) (Object) this, victim, source)'
    assert_includes server, 'fatalSource.getEntity() == run.actor && fatalSource.getDirectEntity() == run.projectile'
    assert_includes server, 'fatalSource.getEntity() == null && fatalSource.getDirectEntity() == null'
    %w[LAVA ON_FIRE IN_FIRE].each { |name| assert_includes server, "DamageTypes.#{name}" }
    exact = '(episode == 1 || episode == 2) && chicken == 1 && cow == 0 && mobKills == episode'
    assert_includes server, exact
    refute_includes server.sub('cow == 0', 'cow == 1'), exact
    refute_includes server.sub('mobKills == episode', 'mobKills >= episode'), exact
    assert_includes server, 'statisticsMatch(episode, chicken, cow, mobKills) && run.fatalSourceObservations == 1'
    assert_includes server, 'run.nativeRootsAtDeath > 0 && run.workRootsAtDeath == 0'
    assert_includes server, 'run.readbackChickenKills = chicken; run.readbackCowKills = cow; run.readbackMobKills = mobKills;'
  end

  def test_l1_controlled_tracking_is_closed_and_not_a_physical_unload_claim
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    server = File.read(File.join(base, 'P11L1ServerHarness.java'))
    client = File.read(File.join(base, 'P11L1ClientHarness.java'))
    probe = File.read(File.join(base, 'P11L1TrackingBoundaryProbe.java'))
    branch = server.index('if (tracking()) { progressTracking(); return; }')
    assert branch
    assert_operator branch, :<, server.index('if (run == null)')
    %w[instanceCreated accepted transferred claimed damageEntering].each do |method|
      assert_includes server, "P11L1TrackingBoundaryProbe.#{method}("
    end
    assert_includes client, 'P11L1ServerHarness.tracking() ? logins == 1 && sends == 1 && casts == 1'
    assert_includes probe, 'manager.updateChunkStatus(chunk, Visibility.HIDDEN);'
    assert_includes probe, 'manager.updateChunkStatus(chunk, Visibility.TRACKED);'
    assert_includes probe, 'finally {'
    assert_includes probe, 'manager.updateChunkStatus(chunk, Visibility.TICKING);'
    assert_includes probe, 'r.projectile.tickCount == r.ageBefore'
    assert_includes probe, '!manager.isLoaded(r.projectile.getUUID())'
    assert_includes probe, 'f.put("physicalChunkUnloadedClaim", false);'
    refute_match(/\.setDeltaMovement\(|\.setPos\(|\.tick\(|addRegionTicket|Thread\.sleep/, probe)
    assert_equal ['gramarye-p11-l1-harness.mixins.json'], P11OnlineRuntime.mixin_configs('l1-tracking-retirement')
    %w[l1-tracking-retirement-extra L1-TRACKING-RETIREMENT l1-natural-unload-extra].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name) }
    end
  end

  def test_l1_open_before_reconnect_is_not_a_wrong_owner_pass
    server = File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1ServerHarness.java'))
    valid = ->(source) {
      !source.include?('OPEN_RECONNECT_OLD_OWNER_NOT_OBSERVED') &&
        source.include?('require(run.projectile.getOwner() == run.actor, "RECONNECT_RETARGETED_OLD_OWNER")') &&
        source.include?('&& run.transfers == 1 && run.oldOwnerAfterReconnect && run.selfPlaced') &&
        source.include?('facts.put("oldOwnerAfterReconnect", run.oldOwnerAfterReconnect)')
    }
    assert valid.call(server), 'OPEN hit-before-B is permitted, separate owner evidence remains explicit'
    refute valid.call(server.sub('run.projectile.getOwner() == run.actor', 'true'))
    refute valid.call(server.sub('&& run.oldOwnerAfterReconnect && run.selfPlaced', '&& run.selfPlaced'))
    refute valid.call(server + '\nOPEN_RECONNECT_OLD_OWNER_NOT_OBSERVED')
    assert_includes server, 'run.logoutComplete && run.claims == 1 && run.transfers == 1 && run.p5DamageApplied'
    assert_includes server, 'run.nativeDamageReturns == 1 && run.nativeOriginA && run.nativeProjectileExact'
  end

  def test_l1_natural_unload_has_separate_loaded_and_physical_native_proof
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    server = File.read(File.join(base, 'P11L1ServerHarness.java'))
    client = File.read(File.join(base, 'P11L1ClientHarness.java'))
    probe = File.read(File.join(base, 'P11L1NaturalUnloadProbe.java'))
    assert_includes P11OnlineRuntime::L1_CASES, 'l1-natural-unload'
    %w[l1-natural-unload.extra L1-NATURAL-UNLOAD l1-natural-unload/../l1-open].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name) }
    end
    branch = server.index('if (naturalUnload()) { progressNaturalUnload(); return; }')
    assert branch
    assert_operator branch, :<, server.index('if (run == null)')
    %w[arm tick accepted instanceCreated transferred projectileTick claimed damageEntering release].each do |name|
      assert_includes server, "P11L1NaturalUnloadProbe.#{name}("
    end
    assert_includes server, 'player == current && P11L1NaturalUnloadProbe.closeRequested()'
    assert_includes server, 'require(finalLogouts == 1 && !current.connection.getConnection().isConnected()'
    assert_includes client, 'cue("a-unload-close.ready")'
    assert_includes client, 'minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen());'
    assert_includes client, 'sends == 1 && casts == 1 && terminalCloseReturned'
    assert_includes client, 'receiptPresent(serverOutput, "natural-unload-result.json")'
    valid = ->(text) {
      text.include?('r.terminalRuntimeTick < r.permit.deadlineRuntimeTick') &&
        text.include?('ProjectileClosureReason.ENTITY_OR_LEVEL_REMOVED') &&
        text.include?('if (!r.trackingTerminal || r.unloadEvents != 1) { return; }') &&
        text.include?('r.level.getChunkSource().getChunkNow(r.chunk.x, r.chunk.z) == null')
    }
    assert valid.call(probe)
    refute valid.call(probe.gsub('r.terminalRuntimeTick < r.permit.deadlineRuntimeTick', 'true'))
    refute valid.call(probe.gsub('r.unloadEvents != 1', 'false'))
    refute valid.call(probe.gsub('ProjectileClosureReason.ENTITY_OR_LEVEL_REMOVED', 'ProjectileClosureReason.DEADLINE'))
    refute_match(/updateChunkStatus\(|addRegionTicket|setChunkForced|\.setDeltaMovement\(|Thread\.sleep/, probe)
    assert_includes probe, 'f.put("physicalUnloadWithin100Guaranteed",false)'
  end

  def test_l1_online_peer_keeps_real_damage_and_server_submission_scope
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    server = File.read(File.join(base, 'P11L1ServerHarness.java'))
    client = File.read(File.join(base, 'P11L1ClientHarness.java'))
    probe = File.read(File.join(base, 'P11L1OnlinePeerProbe.java'))
    assert_includes P11OnlineRuntime::L1_CASES, 'l1-online-peer'
    %w[l1-online-peer.extra L1-ONLINE-PEER l1-online-peer/../l1-open].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name) }
    end
    branch = server.index('if (onlinePeer()) { progressOnlinePeer(); return; }')
    assert branch
    assert_operator branch, :<, server.index('if (run == null)')
    %w[arm tick accepted instanceCreated transferred claimed hitReturned damageEntering damageReturned commitReturned release].each do |name|
      assert_includes server, "P11L1OnlinePeerProbe.#{name}("
    end
    assert_includes client, 'P11L1ServerHarness.onlinePeer() ? logins == 1 && sends == 1 && casts == 1'
    assert_includes probe, '((PlayerAccess)r.peer).p11$l1SpawnInvulnerableTime()>0'
    assert_includes probe, 'r.peer.getHealth()==r.healthBeforeHurt-4'
    assert_includes probe, 'source.getEntity()==r.actor'
    assert_includes probe, 'ProjectileClosureReason.DAMAGE_TERMINAL'
    assert_includes probe, 'f.put("clientPacketReceiptClaim",false)'
    assert_includes probe, 'f.put("clientRenderClaim",false)'
    refute_match(/\.hurt\(|\.onHit|\.setHealth\(|\.setDeltaMovement\(|setPvpAllowed\(|Thread\.sleep/, probe)
    wrapper = File.read(File.join(base, 'harnessmixin/P11L1OnlinePeerP8Mixin.java'))
    assert_equal 1, wrapper.scan('original.call(recipient,payload)').length
    assert_includes wrapper, 'primary=failure;throw failure;'
  end

  def test_l1_work_context_wait_uses_original_scope_then_fresh_manual_retry
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    server = File.read(File.join(base, 'P11L1ServerHarness.java'))
    client = File.read(File.join(base, 'P11L1ClientHarness.java'))
    probe = File.read(File.join(base, 'P11L1ContextRefusalProbe.java'))
    client_probe = File.read(File.join(base, 'P11L1ContextRefusalClientProbe.java'))
    assert_includes P11OnlineRuntime::L1_CASES, 'l1-work-context-refusal'
    %w[l1-work-context-refusal.extra L1-WORK-CONTEXT-REFUSAL l1-work-context-refusal/../l1-open].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name) }
    end
    assert_includes server, 'if (P11L1ContextRefusalProbe.selected()) { P11L1ContextRefusalProbe.normalLogoutReturned(); }'
    logout = server[/private static void closeNormally\(\).*?private static boolean observes/m]
    assert_operator logout.index('P11L1ContextRefusalProbe.normalLogoutReturned()'), :<, logout.index('P11C4aEvidence.cue(output, "a-reconnect-"')
    assert_includes probe, 'TASK_MUST_ARRIVE_BEFORE_NATURAL_HIT'
    assert_includes probe, 'held.original.call(held.task, held.sender)'
    assert_operator server.index('P11L1ContextRefusalProbe.pending()'), :<, server.index('P11L1ContextRefusalProbe.abort()')
    assert_includes server, 'P11L1ContextRefusalProbe.authenticated(exact, connection, id)'
    assert_includes client, 'case CONNECTING -> { P11L1ContextRefusalClientProbe.tick(minecraft); }'
    assert_operator server.index('P11L1WorkRewardProbe.finish()'), :<, server.index('P11L1ContextRefusalProbe.localSealed()')
    assert_includes probe, 'server.reloadResources(List.copyOf(server.getPackRepository().getSelectedIds())).join()'
    assert_includes probe, 'observed.retainedBindings() == 2'
    assert_includes probe, 'state.reason() == Reason.ACTIVE_OPERATION'
    assert_includes probe, 'r.gate.complete(null)'
    assert_includes probe, 'state.scope() == Scope.CONFIG && state.requestSeq() == 0'
    assert_includes probe, 'r.factories == 0 && r.frames == 0 && r.tries == 0'
    assert_includes probe, 'r.localSealed && r.may != null && r.retry != null'
    assert_includes probe, 'f.put("normalWorkLifetimePreservationClaim", false)'
    assert_includes probe, 'f.put("combinedFopAndQctxNotIsolatedQctx", true)'
    assert_includes client_probe, 'P11ClientTransitions.view() != state'
    assert_includes client_probe, 'P11C4aClientInputProbe.Action.RETRY'
    assert_includes client_probe, 'request.requestSeq() == 1'
    refute_match(/new Request\(|P11ClientTransitions\.retry\(|\.offer\(|Thread\.sleep/, probe + client_probe)
    ordered = ->(text) {
      seal = text.index('r.localSealed = true;')
      cue = text.index('P11C4aEvidence.cue(r.output, "a-context-retry.ready")')
      !seal.nil? && !cue.nil? && seal < cue
    }
    assert ordered.call(probe)
    refute ordered.call(probe.sub('r.localSealed = true;', 'removedSeal();'))
    # L1 selects only its own config; an observer registered solely in C4a is unreachable.
    assert_equal ['gramarye-p11-l1-harness.mixins.json'], P11OnlineRuntime.mixin_configs('l1-work-context-refusal')
    manifest = JSON.parse(File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/resources/gramarye-p11-l1-harness.mixins.json')))
    terminal_name = 'P11C4aServerTerminalMixin'
    registered = ->(value) {
      value.fetch('required') == true &&
        value.fetch('package') == 'com.yo1no.gramarye.harnessmixin' &&
        value.fetch('mixins').count(terminal_name) == 1 &&
        !value.fetch('client').include?(terminal_name)
    }
    assert registered.call(manifest)
    refute registered.call(manifest.merge('mixins' => manifest.fetch('mixins') - [terminal_name]))
    refute registered.call(manifest.merge('mixins' => manifest.fetch('mixins') + [terminal_name]))
    refute registered.call(manifest.merge('mixins' => manifest.fetch('mixins') - [terminal_name],
                                          'client' => manifest.fetch('client') + [terminal_name]))
    refute registered.call(manifest.merge('mixins' => manifest.fetch('mixins').map { |name| name == terminal_name ? name + '.extra' : name }))
    refute registered.call(manifest.merge('package' => 'foreign.harnessmixin'))
    refute registered.call(manifest.merge('required' => false))
    %w[P11L1ImpactCustodyMixin P11L1ImpactClaimMixin P11L1TerminalServerMixin].each do |name|
      assert_equal 1, manifest.fetch('mixins').count(name)
    end
    terminal = File.read(File.join(base, 'harnessmixin/P11C4aServerTerminalMixin.java'))
    assert_includes terminal, '@WrapOperation(method = "runServer()V"'
    assert_includes terminal, 'require = 4, expect = 4, allow = 4'
    assert_includes terminal, '@Inject(method = "halt(Z)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)'
    assert_equal 1, terminal.scan('original.call(logger, message, failure)').length
    assert_operator terminal.index('P11C4aServerTerminalProbe.caught('), :<, terminal.index('original.call(logger, message, failure)')
    terminal_probe = File.read(File.join(base, 'P11C4aServerTerminalProbe.java'))
    guard = terminal_probe[/private static boolean contextDiagnosticCase\(\) \{(.*?)\n    \}/m, 1]
    assert_equal 'return "l1-work-context-refusal".equals(System.getProperty("gramarye.p11.online.case", ""));', guard.strip
    refute_includes guard, 'startsWith'

  end

  def test_l1_t1_work_refusals_remain_distinct_and_use_original_inputs
    manifest = prepare(case: 'l1-work-capacity', run_id: 't1-new-work')
    expected = FIXTURE.sub('p11.retention.maxUuids = 4', 'p11.retention.maxUuids = 1')
                      .sub('p11.save.dirtyUuidAdmissionWatermark = 4', 'p11.save.dirtyUuidAdmissionWatermark = 1')
    assert_equal expected, File.binread(File.join(manifest['runtime'], 'server/defaultconfigs/gramarye-server.toml'))
    assert_equal P11OnlineRuntime::L1_JAR_SHA, manifest.fetch('jarSha256')
    assert_equal ['gramarye-p11-l1-harness.mixins.json'], P11OnlineRuntime.mixin_configs('l1-work-capacity')
    %w[l1-work-capacity.extra l1-work-capacity/../l1-open L1-WORK-CAPACITY].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name) }
    end
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    probe = File.read(File.join(base, 'P11L1CapacityWorkProbe.java'))
    server = File.read(File.join(base, 'P11L1ServerHarness.java'))
    client = File.read(File.join(base, 'P11L1ClientHarness.java'))
    assert_operator server.index('if (capacityWork()) { progressCapacity(); return; }'), :<, server.index('if (run == null)')
    refute_includes probe, 'equippedSlot0()'
    refute_match(/\.admitAuthenticatedPlayerCast\(|\.hurt\(|\.setDeltaMovement\(|Thread\.sleep/, probe)
    assert_includes probe, 'result instanceof RuntimeAdmissionResult.OwnerInstanceUnavailable'
    assert_includes probe, '++r.peerRefusals == 1 && r.managedGateReturns == 0'
    assert_includes probe, 'accountOwner == r.body.account.resource && !result'
    assert_includes probe, 'counts.retainedUuids() == 1 && counts.dirtyUuids() == 1'
    assert_includes probe, 'slot.queue.isEmpty() && slot.eventIndex.isEmpty()'
    assert_includes probe, 'slot.eventSequenceHighWater == 0 && slot.skillInstanceSequenceHighWater == 0'
    assert_includes probe, 'f.put("unmanagedRefusalIsManagedWatermarkClaim", false)'
    refute_includes probe, 'r.body.source == r.source'
    assert_includes probe, 'r.body.source.epoch() == r.epoch && r.body.source.version() >= r.lastVersion'
    assert_includes probe, 'r.lastVersion = r.body.source.version();'
    assert_includes probe, 'r.body.account.current == r.body'
    assert_includes probe, 'r.body.advancements == r.advancements && r.body.stats == r.stats'
    assert_includes probe, 'f.put("initialSourceVersion", r.initialVersion)'
    assert_includes probe, 'f.put("lastObservedSourceVersion", r.lastVersion)'
    assert_includes client, 'minecraft.getConnection().sendCommand("gramarye starter")'
    assert_includes client, 'minecraft.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND)'
    assert_includes client, 'cue(role + "-cast-" + (casts + 1) + ".ready")'
    assert_includes client, 'GLFW.glfwFocusWindow(minecraft.getWindow().getWindow())'
    assert_includes client, 'minecraft.isWindowActive() && cue(role + "-cast-"'
    assert_includes client, '&& !capacityFocusRequested'
    refute_match(/setWindowActive\(|\.active\s*=/, client)
    gate = File.read(File.join(base, 'harnessmixin/P11L1CapacityResourcesMixin.java'))
    assert_includes gate, 'require = 1, expect = 1, allow = 1'
    assert_includes gate, 'mayAdmitWork(Lcom/yo1no/gramarye/P11ControlBudgets$Resources$AccountOwner;)Z'
    refute_includes gate, 'mayAdmitWork(Lcom/yo1no/gramarye/P11ControlBudgets$AccountOwner;)Z'
    assert_equal 1, gate.scan('original.call(account)').length
    assert_operator gate.index('original.call(account)'), :<, gate.index('P11L1CapacityWorkProbe.managedGateReturned')
  end

  def test_l1_supplemental_fault_branches_do_not_enter_natural_logout_flow
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    server = File.read(File.join(base, 'P11L1ServerHarness.java'))
    supplemental = File.read(File.join(base, 'P11L1SupplementalHarness.java'))
    packet = File.read(File.join(base, 'P11L1PacketProbe.java'))
    assert_operator server.index('if (P11L1SupplementalHarness.selected())'), :<, server.index('if (run == null)')
    assert_includes server, 'if (!P11L1SupplementalHarness.ack()) { P11C4aEvidence.cue(output, "a-finish.ready"); }'
    refute_match(/closeNormally|dispatchReturned|\.hurt\(|\.onHitEntity\(|\.admitAuthenticatedPlayerCast\(|Thread\.sleep/, supplemental)
    assert_includes supplemental, 'first.work != null && !first.lease.pin.isClosed()'
    assert_includes supplemental, 'CLIENT_RELOAD_DID_NOT_OVERLAP_LIVE_OLD_WORK'
    assert_includes supplemental, 'OLD_WORK_TERMINAL_BEFORE_OWNED_TARGET_CLEANUP'
    assert_operator supplemental.index('oldRevisionTarget.discard();'), :<, supplemental.index('secondCue = true;')
    assert_includes supplemental, 'facts.put("detachedDurabilityRequiresOriginalStop", ack());'
    assert_includes supplemental, 'facts.put("writerDirtyClearedHere", !ack());'
    refute_includes supplemental, 'persistenceQualifiedHere'
    assert_includes supplemental, 'if (!ack()) {'
    assert_includes supplemental, '!P11L1PacketProbe.readyToFinish() || ack() && !ackLogoutObserved'
    assert_includes supplemental, 'require(!ackLogoutObserved, "ORIGINAL_ACK_LOGOUT_ONCE")'
    assert_includes packet, 'active.mode == Mode.ACK_SEND && active.injections == 1'
    assert_includes packet, 'run.ackSamePrimary = !normal && escaping == run.primary;'
    assert_includes packet, 'run.damageReturns <= 1'
    %w[l1-revision.extra l1-ack-fault.extra l1-p8-send-fault.extra].each do |name|
      failure('INVALID_CASE') { P11OnlineRuntime.mixin_config(name) }
    end
  end

  def test_l1_positive_fixture_preserves_original_capacity_without_changing_stress_cases
    (P11OnlineRuntime::L1_CASES - ['l1-stats-write-fault-memory', 'l1-work-capacity']).each do |name|
      actual, source_hash = P11OnlineRuntime.startup_fixture!(@fixture, @private, name, with_source_hash: true)
      assert_equal FIXTURE, actual
      assert_equal P11OnlineRuntime::FIXTURE_SHA, Digest::SHA256.hexdigest(actual)
      assert_equal P11OnlineRuntime::FIXTURE_SHA, source_hash
    end
    { 'c4a-qctx' => 2, 'c4a-capacity' => 1 }.each do |name, limit|
      actual = P11OnlineRuntime.startup_fixture!(@fixture, @private, name)
      assert_includes actual, "p11.retention.maxUuids = #{limit}\n"
      assert_includes actual, "p11.save.dirtyUuidAdmissionWatermark = #{limit}\n"
    end
    File.write(@fixture, FIXTURE.sub('p11.retention.maxUuids = 4', 'p11.retention.maxUuids = 5'))
    failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, 'l1-pre-spawn') }
    failure('INVALID_CASE') { prepare(case: 'l1-pre-spawn.extra') }
  end

  def test_l1_stats_memory_profile_is_only_two_approved_substitutions
    name = 'l1-stats-write-fault-memory'
    expected = FIXTURE.sub('p11.save.maxSealedSnapshots = 1', 'p11.save.maxSealedSnapshots = 4')
                      .sub('p11.save.maxSealedBytes = 1', 'p11.save.maxSealedBytes = 67108864')
    assert_equal expected, File.binread(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/fixtures/l1/stats-memory-startup.toml'))
    actual, input_sha = P11OnlineRuntime.startup_fixture!(@fixture, @private, name, with_source_hash: true)
    assert_equal expected, actual
    assert_equal P11OnlineRuntime::FIXTURE_SHA, input_sha
    assert_equal P11OnlineRuntime::L1_STATS_MEMORY_FIXTURE_SHA, Digest::SHA256.hexdigest(actual)
    File.binwrite(@fixture, expected)
    assert_equal expected, P11OnlineRuntime.startup_fixture!(@fixture, @private, name)
    (P11OnlineRuntime::CASES - [name]).each do |other|
      failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, other) }
    end
    File.binwrite(@fixture, expected.sub('p11.retention.maxUuids = 4', 'p11.retention.maxUuids = 5'))
    failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, name) }
  end

  def test_l1_work_reward_and_memory_routes_remain_one_natural_episode
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    server = File.read(File.join(base, 'P11L1ServerHarness.java'))
    client = File.read(File.join(base, 'P11L1ClientHarness.java'))
    %w[l1-partial-reward-function l1-work-multi-uuid-qctx l1-stats-write-fault-memory].each do |name|
      assert_includes P11OnlineRuntime::L1_CASES, name
      failure('INVALID_CASE') { P11OnlineRuntime.product_pin(name + '.extra') }
    end
    %w[accepted target damageEntering damageReturned finish expectedSourceFailures].each do |name|
      assert_includes server, "P11L1WorkRewardProbe.#{name}("
    end
    %w[arm accepted normalLogoutReturned workTerminal reconnected restoreBeforeOriginalSave originalSaveReturned restoreAfterFailure expectedSourceFailures].each do |name|
      assert_includes server, "P11L1StatsMemoryProbe.#{name}("
    end
    ordered = ->(source) do
      local = source.index('P11L1WorkRewardProbe.finish()')
      barrier = source.index('if (episode == 3 || run.damageReturns != 1 || !run.reconnected')
      !local.nil? && !barrier.nil? && local < barrier
    end
    assert ordered.call(server)
    refute ordered.call(server.sub('P11L1WorkRewardProbe.finish()', 'removedLocalProof()'))
    assert_match(/singleNatural\(\) \|\| P11L1RestartProbe.writeSelected\(\) \? "l1-pre-spawn"\s*: switch \(selected\(\)\)/, server)
    %w[pre-spawn open claimed].each do |window|
      assert_includes server, "case \"cooldown-l1-#{window}\" -> \"l1-#{window}\";"
    end
    assert_includes server, 'default -> selected();'
    assert_includes server, 'summary.failures() == expectedFailures'
    assert_includes server, 'body.stats == run.initialBody.stats && body.advancements == run.initialBody.advancements'
    assert_includes server, 'peerChicken == run.peerChickenBefore && peerMobKills == run.peerMobKillsBefore'
    refute_includes server.sub('peerMobKills == run.peerMobKillsBefore', 'true'), 'peerChicken == run.peerChickenBefore && peerMobKills == run.peerMobKillsBefore'
    assert_includes server, 'try { P11L1StatsMemoryProbe.restoreAfterFailure(null); P11L1StatsMemoryProbe.release(); }'
    assert_includes client, 'P11L1ServerHarness.singleNatural() ? logins == 2 && sends == 1 && casts == 1'
    assert_includes client, '!delivery[logins - 1].selectedReward || !delivery[logins - 1].bread'
    refute_includes server, 'p11.retention.maxUuids ='
    refute_match(/\.admitAuthenticatedPlayerCast\(|\.onHitEntity\(|Thread\.sleep/, server)
  end

  def test_l1_terminal_cases_use_original_client_leave_and_original_native_receivers
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    server = File.read(File.join(base, 'P11L1ServerHarness.java'))
    client = File.read(File.join(base, 'P11L1ClientHarness.java'))
    probe = File.read(File.join(base, 'P11L1TerminalBoundaryProbe.java'))
    %w[l1-work-deadline l1-spawn-callback-remove l1-logout-cleanup-fault].each do |name|
      assert_includes P11OnlineRuntime::L1_CASES, name
      failure('INVALID_CASE') { P11OnlineRuntime.mixin_config(name + '.extra') }
    end
    branch = server.index('if (terminalBoundary()) { progressTerminal(); return; }')
    refute_nil branch
    assert_operator branch, :<, server.index('if (run == null)')
    %w[instanceCreated accepted transferred projectileTick claimed damageEntering rootRetired].each do |name|
      assert_includes server, "P11L1TerminalBoundaryProbe.#{name}("
    end
    assert_includes client, 'cue("a-terminal-close.ready")'
    assert_includes client, 'try { minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen()); }'
    assert_includes client, 'minecraft.player != null || minecraft.level != null || minecraft.getConnection() != null'
    assert_includes client, 'P11C4aEvidence.receiptPresent(serverOutput, "terminal-boundary-result.json")'
    assert_includes probe, 'r.instance.logoutState == SkillRuntimeService.LogoutState.INVALID'
    assert_includes probe, 'slot.runtimeTick == r.permit.deadlineRuntimeTick'
    refute_match(/\.handleDisconnection\(|\.halt\(|\.tickCount\s*=(?!=)|Thread\.sleep/, probe)
    assert_includes server, 'NAMED_TERMINAL_BOUNDARY_SOURCE_OBSERVATION_NOT_HEALTHY_STOP_CLAIM'
  end

  def test_l1_new_observers_and_resources_have_exact_registration_and_catalogue
    json = JSON.parse(File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/resources/gramarye-p11-l1-harness.mixins.json')))
    catalog = File.read(File.join(P11OnlineRuntime::REPO, 'scripts/verify-p7-s4-source-contracts.sh'))
    names = %w[P11L1WorkRewardMixin P11L1WorkFunctionMixin P11L1WorkCommandMixin P11L1WorkFailureCountMixin P11L1StatsFailureCountMixin
               P11L1TerminalAddMixin P11L1TerminalRuntimeMixin P11L1TerminalLogoutMixin P11L1TerminalConnectionMixin P11L1TerminalServerMixin]
    names.each do |name|
      assert_equal 1, json.fetch('mixins').count(name)
      refute_includes json.fetch('client'), name
    end
    paths = names.map { |n| 'src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/' + n + '.java' } +
      %w[P11L1WorkRewardProbe P11L1StatsMemoryProbe P11L1TerminalBoundaryProbe].map { |n| 'src/p11OnlineHarness/java/com/yo1no/gramarye/' + n + '.java' } +
      P11OnlineRuntime::L1_FIXTURE_HASHES.keys.map { |n| 'src/p11OnlineHarness/fixtures/l1/' + n } +
      ['src/p11OnlineHarness/fixtures/l1/stats-memory-startup.toml'] +
      %w[src/main/java/com/yo1no/gramarye/P11P9TrackingCleanup.java
         src/main/java/com/yo1no/gramarye/mixin/P11P9EntitySectionMixin.java
         src/main/java/com/yo1no/gramarye/mixin/P11P9EntityMoveMixin.java
         src/test/java/com/yo1no/gramarye/P11P9TrackingCleanupTest.java]
    paths.each do |path|
      assert_equal 1, catalog.scan(Regexp.new(Regexp.escape(path + ' |'))).size
      refute_includes catalog, path + '.extra |'
    end
  end

  def test_l1_failure_diagnostic_is_fixed_bounded_and_does_not_replace_primary
    source = File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1ServerHarness.java'))
    assert_includes source, 'require(false, "NATURAL_PROJECTILE_ENDED_WITHOUT_REQUIRED_HIT")'
    refute_includes source, 'throw new IllegalStateException("NATURAL_PROJECTILE_ENDED_WITHOUT_REQUIRED_HIT")'
    failure_body = source.split('private static void fail(String code, Throwable primary)', 2).last.split('public static void rootRetired', 2).first
    %w[stage naturalObservation terminalReason projectileRemoved projectileAge workRoots nativeCreditRoots operationRoots commandContextRoots].each do |name|
      assert_includes failure_body, '"' + name + '"'
    end
    assert_includes failure_body, 'catch (IOException | RuntimeException | Error ignoredDiagnostic)'
    refute_match(/getMessage\(|getStackTrace\(|printStackTrace\(|getGameProfile\(|toString\(/, failure_body)
    refute_includes failure_body, '"PASS"'
    refute_includes failure_body, '"status", "QUALIFIED"'
  end

  def test_l1_logout_observers_do_not_move_work_or_fabricate_hit
    source = File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1ServerHarness.java'))
    assert_includes source, 'run.connection.disconnect('
    assert_includes source, 'run.connection.handleDisconnection();'
    assert_includes source, 'run.instanceState.logoutState == SkillRuntimeService.LogoutState.COMPLETE'
    assert_includes source, 'run.instanceState.lease.pin.isClosed()'
    refute_match(/\.onHitEntity\(|\.claimLoadedEntityHit\(|\.setOwner\(|\.acquireCredit\(|\.beginCredit\(|Thread\.sleep|\.tickCount\s*=/, source)
    assert_includes source, 'source.getEntity() == run.actor && workRoots() > 0 && roots("OPERATION") > 0'
    assert_includes source, 'run.firstHitNativeRoots == 0'
    assert_includes source, 'Blocks.LAVA.defaultBlockState()'
    assert_includes source, 'run.victim.getKillCredit() == run.actor && nativeRoots() > 0'
    assert_includes source, 'actualVictim == current && current != run.actor && current.getUUID().equals(run.actor.getUUID())'
    hooks = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin')
    constructor = File.read(File.join(hooks, 'P11L1InstanceMixin.java'))
    exact = '<init>(Lcom/yo1no/gramarye/magic/api/id/SkillInstanceId;Lcom/yo1no/gramarye/RuntimeSkillInstanceSequence;Lcom/yo1no/gramarye/RuntimeBudgetAttribution;Lcom/yo1no/gramarye/RuntimeRevisionLease;Lnet/minecraft/server/level/ServerPlayer;)V'
    assert_equal 1, constructor.scan(exact).size
    refute_includes constructor.sub(exact, exact.sub('/magic/api/id/', '/')), exact
    %w[P11L1RuntimeMixin.java P11L1DispatchMixin.java P11L1DamageMixin.java].each do |name|
      bytes = File.read(File.join(hooks, name))
      refute_includes bytes, '@At("RETURN")'
      assert_includes bytes, '@At("TAIL")'
    end
    projectile = File.read(File.join(hooks, 'P11L1ProjectileMixin.java'))
    assert_equal 3, projectile.scan('@WrapMethod(').size
    block = projectile[/@WrapMethod\(method = "onHitBlock\(Lnet\/minecraft\/world\/phys\/BlockHitResult;\)V".*?\n    \}/m]
    refute_nil block
    assert_equal 1, block.scan('original.call(hit)').size
    assert_includes block, 'require = 1, expect = 1, allow = 1'
    assert_match(/blockImpactEntering\(this, hit\);.*?try \{ original\.call\(hit\); normal = true; \}\s*finally \{ com\.yo1no\.gramarye\.P11CooldownL1Probe\.blockImpactReturned\(this, normal\); \}/m, block)
    assert_match(/original\.call\(hit\);\s*P11L1ServerHarness\.hitReturned/, projectile)
    tick_order = /try \{ original\.call\(\); normal = true; \}\s*finally \{ P11L1TerminalBoundaryProbe\.closeDiagnosticTickFinished\(selected, normal\); \}\s*com\.yo1no\.gramarye\.P11L1ImpactCustodyProbe\.tickReturned\(this\);\s*P11L1ServerHarness\.projectileTick/
    impact_observer = 'com.yo1no.gramarye.P11L1ImpactCustodyProbe.tickReturned(this);'
    assert_match tick_order, projectile
    assert_equal 1, projectile.scan(impact_observer).size
    refute_match tick_order, projectile.sub(impact_observer, '')
    refute_match tick_order, projectile.sub(impact_observer, impact_observer + "\n" + impact_observer)
    refute_match tick_order, projectile.sub(impact_observer, 'P11L1ServerHarness.projectileTick(this);')
    assert_equal 1, projectile.scan('original.call();').size
    refute_match(/catch\s*\(/, projectile)
    assert_includes source, 'roots.stream().allMatch(value -> value.count() == 0)'
    assert_includes source, 'summary.nativeStopNormal()'
    assert_includes source, 'summary.resources().dirtyUuids() == 0'
    assert_includes source, 'receiver == current && receiver.getScore() == before + amount'
    score = File.read(File.join(hooks, 'P11L1ScoreMixin.java'))
    assert_match(/original\.call\(amount\);\s*P11L1ServerHarness\.scoreReturned/, score)
    assert_includes score, '@Mixin(Player.class)'
  end

  def test_l1_claim_observation_is_at_original_recorded_disposition_not_pending
    source = File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1RuntimeMixin.java'))
    descriptor = 'recordP9HitClaimResult(Lcom/yo1no/gramarye/ServerSlot;Lcom/yo1no/gramarye/ServerSlot$InstanceState;Lcom/yo1no/gramarye/RuntimeProjectileContinuationPermit;Lcom/yo1no/gramarye/ProjectileHitCandidateV0;Lcom/yo1no/gramarye/RuntimePermitClaimDisposition;)V'
    valid = lambda do |bytes|
      bytes.scan(descriptor).size == 1 &&
        bytes.include?('at = @At("TAIL"), require = 1, expect = 1, allow = 1)') &&
        bytes.include?('private static void p11$l1Claim(') &&
        bytes.include?('P11L1ServerHarness.claimed(disposition);') &&
        !bytes.include?('method = "claimProjectileHit(') &&
        !bytes.include?('Optional') && !bytes.include?('submitObservedHit(')
    end
    assert valid.call(source)
    [source.sub(descriptor, descriptor.sub('ServerSlot$InstanceState', 'ServerSlot')),
     source.gsub('@At("TAIL")', '@At("HEAD")'),
     source.sub('private static void p11$l1Claim(', 'private void p11$l1Claim('),
     source.sub('P11L1ServerHarness.claimed(disposition);', 'P11L1ServerHarness.claimed(null);'),
     source + 'Optional HOLD', source + 'submitObservedHit('].each { |changed| refute valid.call(changed) }
    runtime = File.read(File.join(P11OnlineRuntime::REPO, 'src/main/java/com/yo1no/gramarye/SkillRuntimeService.java'))
    body = runtime.split('var child = new RuntimeEvent(', 2).last.split('private RuntimePermitClaimDisposition rejectClaimAndClose(', 2).first
    assert_operator body.index('permit.state = RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE;'), :<, body.index('recordP9HitClaimResult(')
    refute_includes source, 'getMessage('
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
    names = "['single', 'qctx', 'capacity', 'c4a-dedicated', 'c4a-host-lan', 'c4a-qctx', 'c4a-capacity', 'c4a-reward', 'l1-pre-spawn', 'l1-open', 'l1-claimed', 'l1-impact-close-custody', 'l1-two-work-reload', 'l1-two-work-stop', 'l1-revision', 'l1-p8-send-fault', 'l1-ack-fault', 'l1-work-death', 'l1-work-dimension', 'l1-work-config', 'l1-partial-reward-function', 'l1-work-multi-uuid-qctx', 'l1-stats-write-fault-memory', 'l1-work-deadline', 'l1-spawn-callback-remove', 'l1-logout-cleanup-fault', 'l1-tracking-retirement', 'l1-work-capacity', 'l1-natural-unload', 'l1-online-peer', 'l1-work-context-refusal', 'l1-restart-write', 'l1-restart-read', 'l1-qctx', 'l1-capacity', 'cooldown-d1', 'cooldown-d120', 'cooldown-d600', 'cooldown-restart-write', 'cooldown-restart-read', 'cooldown-prepared-reentry', 'cooldown-add-false', 'cooldown-add-remove', 'cooldown-clone', 'cooldown-before-arm-throw', 'cooldown-after-arm-throw', 'cooldown-unarmed-stop', 'cooldown-save-active', 'cooldown-save-clear', 'cooldown-dual', 'cooldown-l1-pre-spawn', 'cooldown-l1-open', 'cooldown-l1-claimed']"
    valid = lambda do |source|
      source.scan(/!p11OnlineJar\.isFile\(\) \|\| !\(p11OnlineCase in (\[[^\n]+\])\)/).flatten == [names]
    end
    assert valid.call(build)
    [names.sub(", 'c4a-capacity'", ''), names.sub('c4a-qctx', 'c4a-qctx.extra'),
     names.sub(", 'l1-impact-close-custody'", ''), names.sub('l1-impact-close-custody', 'l1-impact-close-custody.extra'),
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

  def test_companion_resource_inventory_is_exact_nine_and_rejects_near_names_or_missing_resource
    assert_equal ['gramarye-p11-online-private-console.xml', 'gramarye-p11-online-harness.mixins.json',
                  'gramarye-p11-c4a-harness.mixins.json', 'gramarye-p11-c6-observers.mixins.json',
                  'gramarye-p11-l1-harness.mixins.json',
                  'gramarye-p11-cooldown-harness.mixins.json',
                  'data/gramarye_p11_engineering/advancement/l1_first_kill.json',
                  'data/gramarye_p11_engineering/loot_table/l1_loot.json',
                  'data/gramarye_p11_engineering/function/l1_reward.mcfunction'].sort,
                 P11OnlineRuntime::COMPANION_RESOURCES
    manifest = prepare(case: 'c4a-dedicated')
    generated_fixture(manifest)
    resources = File.join(manifest['repo'], 'build/resources/p11OnlineHarness')
    %w[gramarye-p11-c6-observers.mixins.json gramarye-p11-cooldown-harness.mixins.json].each do |leaf|
      selected = File.join(resources, leaf)
      bytes = File.binread(selected)
      File.unlink(selected)
      failure('UNEXPECTED_COMPANION_RESOURCE') { export(manifest) }
      [leaf + '.extra', 'foreign.mixins.json', 'nested/' + leaf].each do |bad|
        replacement = File.join(resources, bad)
        FileUtils.mkdir_p(File.dirname(replacement))
        File.write(replacement, bytes)
        failure('UNEXPECTED_COMPANION_RESOURCE') { export(manifest) }
        File.unlink(replacement)
      end
      File.write(selected, bytes)
    end
    extra = File.join(resources, 'foreign.xml')
    File.write(extra, '<SYNTHETIC/>')
    failure('UNEXPECTED_COMPANION_RESOURCE') { export(manifest) }
    File.unlink(extra)
    frozen = export(manifest)
    assert_equal 9, frozen['bundleFiles'].keys.count { |path| path.start_with?('resources/') }
  end

  def test_l1_host_stop_is_prepare_only_exact_case_pin_fixture_and_frozen_mode
    assert_equal P11OnlineRuntime::L1_JAR_SHA, P11OnlineRuntime.product_pin('c4a-host-lan', l1_host_stop: true)
    assert_equal P11OnlineRuntime::C4A_JAR_SHA, P11OnlineRuntime.product_pin('c4a-host-lan')
    %w[single c4a-dedicated l1-pre-spawn c4a-host-lan.extra].each do |name|
      failure(P11OnlineRuntime::CASES.include?(name) ? 'INVALID_L1_HOST_SELECTION' : 'INVALID_CASE') do
        P11OnlineRuntime.product_pin(name, l1_host_stop: true)
      end
    end
    [nil, 'true', 1].each do |bad|
      failure('INVALID_L1_HOST_SELECTION') { P11OnlineRuntime.product_pin('c4a-host-lan', l1_host_stop: bad) }
    end
    manifest = prepare(case: 'c4a-host-lan', l1_host_stop: true)
    assert_equal true, manifest.fetch('l1HostStop')
    assert_equal P11OnlineRuntime::L1_JAR_SHA, manifest.fetch('jarSha256')
    assert_equal P11OnlineRuntime::FIXTURE_SHA, manifest.fetch('fixtureSha256')
    assert_equal FIXTURE, File.binread(File.join(manifest['runtime'], 'host/defaultconfigs/gramarye-server.toml'))
    refute File.exist?(File.join(manifest['runtime'], 'host/saves'))
    generated_fixture(manifest)
    source = File.join(manifest['repo'], 'src/p11OnlineHarness/java')
    mode = File.join(source, 'com/yo1no/gramarye/P11C4aScenario.java')
    original = File.binread(mode)
    assert P11OnlineRuntime.verify_host_mode!(manifest, source)
    %w[UI_HELD L1_HOST_STOP_EXTRA].each do |bad|
      File.binwrite(mode, original.sub('Mode.L1_HOST_STOP;', "Mode.#{bad};"))
      failure('L1_HOST_MODE_MISMATCH') { P11OnlineRuntime.verify_host_mode!(manifest, source) }
    end
    File.binwrite(mode, original)
    failure('L1_HOST_MODE_MISMATCH') { P11OnlineRuntime.verify_host_mode!(manifest.merge('l1HostStop' => false), source) }
    frozen = export(manifest)
    assert_equal true, frozen.fetch('l1HostStop')
    P11OnlineRuntime::L1_HOST_RESOURCES.each do |name, hash|
      assert_equal hash, Digest::SHA256.file(File.join(frozen['bundle'], 'resources', name)).hexdigest
    end
    _, error = capture_io { assert_equal 2, P11OnlineRuntime.main(['status', '--l1-host-stop']) }
    assert_equal 'PREPARE_ONLY_OPTION', JSON.parse(error).fetch('code')
  end

  def test_cooldown_host_is_exact_prepare_only_current_pin_t4_and_frozen_mode
    assert_equal P11OnlineRuntime::COOLDOWN_JAR_SHA, P11OnlineRuntime.product_pin('c4a-host-lan', cooldown_host: true)
    assert_equal P11OnlineRuntime::C4A_JAR_SHA, P11OnlineRuntime.product_pin('c4a-host-lan')
    assert_equal P11OnlineRuntime::L1_JAR_SHA, P11OnlineRuntime.product_pin('c4a-host-lan', l1_host_stop: true)
    P11OnlineRuntime::CASES.reject { |name| name == 'c4a-host-lan' }.each do |name|
      failure('INVALID_COOLDOWN_HOST_SELECTION') { P11OnlineRuntime.product_pin(name, cooldown_host: true) }
    end
    [nil, 'true', 1].each do |bad|
      failure('INVALID_COOLDOWN_HOST_SELECTION') { P11OnlineRuntime.product_pin('c4a-host-lan', cooldown_host: bad) }
    end
    failure('CONFLICTING_HOST_SELECTION') do
      P11OnlineRuntime.product_pin('c4a-host-lan', l1_host_stop: true, cooldown_host: true)
    end
    failure('INVALID_CASE') { P11OnlineRuntime.product_pin('c4a-host-lan.extra', cooldown_host: true) }
    manifest = prepare(case: 'c4a-host-lan', cooldown_host: true)
    assert_equal true, manifest.fetch('cooldownHost')
    assert_equal false, manifest.fetch('l1HostStop')
    assert_equal %w[host b], manifest.fetch('roles')
    assert_equal P11OnlineRuntime::COOLDOWN_JAR_SHA, manifest.fetch('jarSha256')
    assert_includes manifest.fetch('jar'), '/frozen-cooldown/'
    assert_equal P11OnlineRuntime::FIXTURE_SHA, manifest.fetch('fixtureSha256')
    assert_equal FIXTURE, File.binread(File.join(manifest['runtime'], 'host/defaultconfigs/gramarye-server.toml'))
    refute File.exist?(File.join(manifest['runtime'], 'host/saves'))
    generated_fixture(manifest)
    source = File.join(manifest['repo'], 'src/p11OnlineHarness/java')
    mode = File.join(source, 'com/yo1no/gramarye/P11C4aScenario.java')
    original = File.binread(mode)
    assert P11OnlineRuntime.verify_host_mode!(manifest, source)
    %w[UI_HELD COOLDOWN_HOST_EXTRA].each do |bad|
      File.binwrite(mode, original.sub('Mode.COOLDOWN_HOST;', "Mode.#{bad};"))
      failure('COOLDOWN_HOST_MODE_MISMATCH') { P11OnlineRuntime.verify_host_mode!(manifest, source) }
    end
    File.binwrite(mode, original)
    failure('COOLDOWN_HOST_MODE_MISMATCH') { P11OnlineRuntime.verify_host_mode!(manifest.merge('cooldownHost' => false), source) }
    frozen = export(manifest)
    assert frozen.fetch('cooldownHost')
    file = File.join(manifest['runtime'], 'frozen-manifest.json')
    File.chmod(0o600, file)
    [[false, 'MANIFEST_JAR_MISMATCH'], [nil, 'INVALID_COOLDOWN_HOST_SELECTION'], ['true', 'INVALID_COOLDOWN_HOST_SELECTION']].each do |flag, error|
      File.binwrite(file, JSON.generate(frozen.merge('cooldownHost' => flag)))
      failure(error) { P11OnlineRuntime.load_manifest(file, runtime_root: @options[:runtime_root], private_root: @private) }
    end
    File.binwrite(file, JSON.generate(frozen))
    assert P11OnlineRuntime.load_manifest(file, runtime_root: @options[:runtime_root], private_root: @private).fetch('cooldownHost')
    _, error = capture_io { assert_equal 2, P11OnlineRuntime.main(['status', '--cooldown-host']) }
    assert_equal 'PREPARE_ONLY_OPTION', JSON.parse(error).fetch('code')
    File.write(@fixture, FIXTURE.sub('p11.control.maxWaitingConnections = 4', 'p11.control.maxWaitingConnections = 1'))
    failure('STARTUP_FIXTURE_MISMATCH') { P11OnlineRuntime.startup_fixture!(@fixture, @private, 'c4a-host-lan', cooldown_host: true) }
  end

  def test_cooldown_host_status_is_fixed_presence_only_and_current_pin_requires_matching_mode
    manifest = prepare(case: 'c4a-host-lan', cooldown_host: true)
    leaves = %w[server/cooldown-host-formal.json server/cooldown-host-arm.json server/cooldown-host-saved.json
                server/cooldown-host-stopped.json server/cooldown-host-failure.json server/cooldown-host-diagnostic.json client-host/cooldown-host-input.json]
    (leaves + ['server/cooldown-host-arm.json.extra', 'server/latest.log', 'client-host/accounts.json']).each do |leaf|
      file = File.join(manifest['evidence'], leaf)
      FileUtils.mkdir_p(File.dirname(file))
      File.write(file, 'PRESENCE_ONLY_NOT_ACCEPTANCE')
    end
    File.stub(:binread, ->(*) { flunk 'status must not read contents' }) do
      assert_equal (leaves + %w[prepare.json launch-cues.txt]).sort, P11OnlineRuntime.status(manifest).fetch('present').sort
    end
    java = File.read(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineInputs.java'))
    assert_includes java, 'P11CooldownHostProbe.selected() || java.util.List.of'
    assert_includes java, 'P11C4aScenario.MODE != P11C4aScenario.Mode.COOLDOWN_HOST || P11CooldownHostProbe.selected()'
    assert_includes java, 'value.getName().contains("P11CooldownHostProbe")'
    assert_includes java, 'value.getName().contains("P11CooldownHostClientProbe")'
  end

  def test_l1_host_resources_reject_changed_original_bytes_before_bundle_creation
    manifest = prepare(case: 'c4a-host-lan', l1_host_stop: true)
    generated_fixture(manifest)
    P11OnlineRuntime::L1_HOST_RESOURCES.each_key do |relative|
      file = File.join(manifest['repo'], 'build/resources/p11OnlineHarness', relative)
      original = File.binread(file)
      File.binwrite(file, original + ' ')
      failure('L1_HOST_RESOURCE_MISMATCH') { export(manifest) }
      refute File.exist?(File.join(manifest['runtime'], 'launch-bundle'))
      File.binwrite(file, original)
    end
    frozen = export(manifest)
    assert frozen.fetch('l1HostStop')
    wrong = frozen.merge('l1HostStop' => false)
    file = File.join(manifest['runtime'], 'frozen-manifest.json')
    File.chmod(0o600, file)
    File.binwrite(file, JSON.generate(wrong))
    failure('MANIFEST_JAR_MISMATCH') do
      P11OnlineRuntime.load_manifest(file, runtime_root: @options[:runtime_root], private_root: @private)
    end
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
    pins = lambda do |case_name, **_options|
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
    P11OnlineRuntime.stub(:product_pin, ->(_case_name, **_options) { current_pin }) do
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
      if (p11OnlineCase in ['l1-restart-read', 'cooldown-restart-read']) {
          throw new GradleException('P11 restart read requires the frozen helper stopped-world binding')
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
    refute protected_first.call(build.sub("if (p11OnlineCase in ['l1-restart-read', 'cooldown-restart-read']) {\n                throw",
                                         "if (p11OnlineCase == 'l1-restart-read.extra') {\n                throw"))
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

  def test_owned_wait_reaps_before_any_signal_and_preserves_historical_wait
    status = Object.new
    calls = []
    waiter = lambda do |*arguments|
      calls << arguments
      [123, status]
    end
    Process.stub(:waitpid2, waiter) do
      Process.stub(:kill, ->(*) { flunk 'never signal a reaped child' }) do
        assert_equal [status, false], P11OnlineRuntime.wait_owned_process(123, 'cooldown-d1')
        assert_equal [status, false], P11OnlineRuntime.wait_owned_process(123, 'l1-open')
      end
    end
    assert_equal [[123, Process::WNOHANG], [123]], calls
    Process.stub(:waitpid2, ->(*) { raise Errno::ECHILD }) do
      Process.stub(:kill, ->(*) { flunk 'foreign PID must never be signalled' }) do
        assert_raises(Errno::ECHILD) { P11OnlineRuntime.wait_owned_process(123, 'cooldown-d1') }
      end
    end
  end

  def test_owned_wait_deadline_signals_only_its_unreaped_child_and_never_reports_pass
    status = Object.new
    [false, true].each do |term_exits|
      times = [0, 901, 901, 922]
      waits = []
      signals = []
      waiter = lambda do |*arguments|
        waits << arguments
        if waits.size == 1 || !term_exits && waits.size == 2
          nil
        else
          [123, status]
        end
      end
      Process.stub(:clock_gettime, ->(*) { times.shift || 922 }) do
        Process.stub(:waitpid2, waiter) do
          Process.stub(:kill, ->(*arguments) { signals << arguments; 1 }) do
            assert_equal [status, true], P11OnlineRuntime.wait_owned_process(123, 'cooldown-d600')
          end
        end
      end
      assert_equal(term_exits ? [['TERM', 123]] : [['TERM', 123], ['KILL', 123]], signals)
      assert_equal(term_exits ? [[123, Process::WNOHANG]] * 2 : [[123, Process::WNOHANG]] * 2 + [[123]], waits)
    end
    times = [0, 901]
    waits = 0
    Process.stub(:clock_gettime, ->(*) { times.shift || 901 }) do
      Process.stub(:waitpid2, ->(*) { waits += 1; waits == 1 ? nil : [123, status] }) do
        Process.stub(:kill, ->(*) { raise Errno::ESRCH }) do
          assert_equal [status, true], P11OnlineRuntime.wait_owned_process(123, 'cooldown-d1')
        end
      end
    end
    assert_equal 2, waits
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

  def restart_write_fixture
    manifest = prepare(case: 'l1-restart-write', accept_eula: true, run_id: 'restart-write-001')
    generated_fixture(manifest)
    frozen = export(manifest)
    server = File.join(frozen.fetch('evidence'), 'server')
    FileUtils.mkdir_p(server)
    terminal = { 'exitCode' => 0, 'signal' => nil, 'elapsedSeconds' => 1.0,
                 'endedAtUtc' => '2026-10-05T00:00:00.000000Z' }
    File.write(File.join(frozen.fetch('runtime'), 'server.exit.json'), JSON.generate(terminal.merge('status' => 'PROCESS_EXIT_NOT_ACCEPTANCE')))
    { 'a' => [2, 2], 'b' => [1, 0] }.each do |role, (logins, sends)|
      File.write(File.join(frozen.fetch('evidence'), "client-#{role}-process-exit.json"),
                 JSON.generate(terminal.merge('status' => 'OWNED_CLIENT_PROCESS_EXIT_NOT_ACCEPTANCE')))
      client = File.join(frozen.fetch('evidence'), "client-#{role}")
      FileUtils.mkdir_p(client)
      File.write(File.join(client, 'result.json'), JSON.generate(
        'status' => 'ORIGINAL_RESTART_WRITE_CLIENT_TERMINAL_NOT_RESTART_PROOF', 'logins' => logins,
        'originalP9Sends' => sends, 'originalKeyCallbackClicks' => sends,
        'closed' => true, 'playerAbsent' => true, 'levelAbsent' => true, 'currentListenerAbsent' => true))
    end
    File.write(File.join(server, 'data-terminal.json'), JSON.generate(
      'status' => 'ORIGINAL_NORMAL_STOP_AND_CLEAN_SOURCE', 'nativeStopNormal' => true,
      'sourceFailures' => 0, 'dirtyUuids' => 0,
      'allRootCounts' => %w[WORK NATIVE_CREDIT OPERATION COMMAND_CONTEXT TRANSITION].map { |kind| { 'kind' => kind, 'count' => 0 } }))
    world = File.join(frozen.fetch('runtime'), 'server/p11-online-world')
    File.write(File.join(server, 'l1-restart-expected.json'), JSON.generate(
      'status' => 'ORIGINAL_STOPPED_WORLD_READY_FOR_NEW_SERVER_NOT_RESTART_PROOF',
      'writeRunId' => frozen.fetch('runId'), 'world' => world, 'originalStopNormal' => true,
      'openWorkClosedByStop' => true, 'loadedConfiguration' => {
        'path' => File.join(world, 'serverconfig/gramarye-server.toml'), 'sha256' => P11OnlineRuntime::FIXTURE_SHA }))
    frozen
  end

  def restart_source_for(frozen)
    P11OnlineRuntime.stub(:verify_jar!, true) do
      P11OnlineRuntime.restart_source!(File.join(frozen.fetch('runtime'), 'frozen-manifest.json'), @options[:repo],
                                      @options[:runtime_root], @options[:evidence_root], @private, 'restart-read-002')
    end
  end

  def test_restart_selects_only_real_stopped_owned_world_without_copy_or_second_configuration
    old = restart_write_fixture
    source, bytes = restart_source_for(old)
    world = source.fetch('world')
    before = Dir.glob(File.join(world, '**/*')).select { |file| File.file?(file) }.to_h { |file| [file, Digest::SHA256.file(file).hexdigest] }
    read = prepare(case: 'l1-restart-read', accept_eula: true, run_id: 'restart-read-002',
                   restart_from: File.join(old.fetch('runtime'), 'frozen-manifest.json'))
    assert_equal source, read.fetch('restartSource')
    assert_equal bytes, File.binread(File.join(read.fetch('evidence'), 'l1-restart-input.json'))
    assert_equal 0o400, File.stat(File.join(read.fetch('evidence'), 'l1-restart-input.json')).mode & 0o777
    refute File.exist?(File.join(read.fetch('runtime'), 'server/p11-online-world'))
    assert_equal before, before.keys.to_h { |file| [file, Digest::SHA256.file(file).hexdigest] }
    argv = P11OnlineRuntime.gradle_argv(read, 'createP11OnlineServerLaunchScript')
    assert_includes argv, "-PgramaryeP11OnlineRestartUniverse=#{source.fetch('universe')}"
    refute P11OnlineRuntime.gradle_argv(old, 'createP11OnlineServerLaunchScript').any? { |arg| arg.include?('RestartUniverse') }
    generated_fixture(read)
    frozen = export(read)
    program = File.read(File.join(frozen.fetch('bundle'), 'originals/p11OnlineServerRunProgramArgs.txt'))
    assert_includes program, "--universe\n#{source.fetch('universe')}\n--world\np11-online-world\n"
    P11OnlineRuntime.stub(:verify_jar!, true) { assert P11OnlineRuntime.verify_frozen!(frozen) }
  end

  def test_restart_wrong_case_unfrozen_recursive_read_and_prepare_only_options_fail_closed
    failure('RESTART_SOURCE_ONLY_EXACT_READ_CASE') { prepare(case: 'l1-restart-read') }
    failure('RESTART_SOURCE_ONLY_EXACT_READ_CASE') { prepare(case: 'l1-open', restart_from: @fixture) }
    old = restart_write_fixture
    failure('RESTART_FROZEN_MANIFEST_REQUIRED') do
      P11OnlineRuntime.restart_source!(File.join(old.fetch('runtime'), 'manifest.json'), @options[:repo],
                                      @options[:runtime_root], @options[:evidence_root], @private, 'restart-read-002')
    end
    file = File.join(old.fetch('runtime'), 'frozen-manifest.json')
    original = File.binread(file)
    %w[l1-open l1-restart-read].each do |wrong|
      mutated = JSON.parse(original).merge('case' => wrong, 'restartSource' => { 'manifest' => file })
      File.chmod(0o600, file)
      File.write(file, JSON.generate(mutated))
      failure('RESTART_SOURCE_COHORT_MISMATCH') { restart_source_for(old) }
    end
    File.binwrite(file, original)
    _, error = capture_io { assert_equal 2, P11OnlineRuntime.main(['status', '--restart-from', file]) }
    assert_equal 'PREPARE_ONLY_OPTION', JSON.parse(error).fetch('code')
    %w[l1-restart-read.extra l1-restart-write.extra].each { |name| failure('INVALID_CASE') { prepare(case: name) } }
  end

  def test_restart_named_stop_proof_does_not_require_individual_logout_callbacks
    old = restart_write_fixture
    terminal_path = File.join(old.fetch('evidence'), 'server/data-terminal.json')
    original = JSON.parse(File.binread(terminal_path))
    actual = JSON.generate(original.merge('status' => 'TERMINAL_NOT_QUALIFIED'))
    File.binwrite(terminal_path, actual)
    source, = restart_source_for(old)
    assert_equal old.fetch('runId'), source.fetch('writeRunId')
    assert_equal actual, File.binread(terminal_path)
    [nil, 'STOP_AFTER_FAILURE_OR_READY_ONLY', 'UNKNOWN'].each do |wrong|
      File.binwrite(terminal_path, JSON.generate(original.merge('status' => wrong)))
      failure('RESTART_ORIGINAL_DATA_NOT_CLEAN_TERMINAL') { restart_source_for(old) }
    end
    File.binwrite(terminal_path, actual)
    named_path = File.join(old.fetch('evidence'), 'server/l1-restart-expected.json')
    named = JSON.parse(File.binread(named_path))
    [{ 'originalStopNormal' => false }, { 'openWorkClosedByStop' => false },
     { 'status' => 'MISSING_NAMED_STOP_PROOF' }].each do |wrong|
      File.binwrite(named_path, JSON.generate(named.merge(wrong)))
      failure('RESTART_EXACT_STOPPED_WORLD_MISMATCH') { restart_source_for(old) }
    end
    File.binwrite(named_path, JSON.generate(named))
    [{ 'nativeStopNormal' => false }, { 'sourceFailures' => 1 }, { 'dirtyUuids' => 1 },
     { 'allRootCounts' => [] }].each do |wrong|
      File.binwrite(terminal_path, JSON.generate(JSON.parse(actual).merge(wrong)))
      failure('RESTART_ORIGINAL_DATA_NOT_CLEAN_TERMINAL') { restart_source_for(old) }
    end
  end

  def test_restart_refuses_missing_real_terminal_native_root_and_original_client_work_facts
    old = restart_write_fixture
    paths = {
      File.join(old.fetch('runtime'), 'server.exit.json') => ['RESTART_ORIGINAL_PROCESS_NOT_NORMAL_TERMINAL', { 'exitCode' => 1 }, { 'signal' => 15 }, { 'elapsedSeconds' => -1 }],
      File.join(old.fetch('evidence'), 'client-a/result.json') => ['RESTART_ORIGINAL_CLIENT_FLOW_NOT_TERMINAL', { 'originalP9Sends' => 1 }, { 'logins' => 1 }, { 'closed' => false }],
      File.join(old.fetch('evidence'), 'client-b/result.json') => ['RESTART_ORIGINAL_CLIENT_FLOW_NOT_TERMINAL', { 'originalP9Sends' => 1 }, { 'currentListenerAbsent' => false }],
      File.join(old.fetch('evidence'), 'server/data-terminal.json') => ['RESTART_ORIGINAL_DATA_NOT_CLEAN_TERMINAL', { 'sourceFailures' => 1 }, { 'nativeStopNormal' => false }, { 'dirtyUuids' => 1 }, { 'allRootCounts' => [] }]
    }
    paths.each do |file, (code, *changes)|
      original = File.binread(file)
      changes.each do |change|
        File.write(file, JSON.generate(JSON.parse(original).merge(change)))
        failure(code) { restart_source_for(old) }
      end
      File.binwrite(file, original)
    end
    source, = restart_source_for(old)
    assert_equal File.join(old.fetch('runtime'), 'server/p11-online-world'), source.fetch('world')
  end

  def test_restart_actual_config_path_hash_world_and_fixed_input_are_immutable
    old = restart_write_fixture
    receipt = File.join(old.fetch('evidence'), 'server/l1-restart-expected.json')
    original = File.binread(receipt)
    value = JSON.parse(original)
    [{ 'world' => File.join(@root, 'foreign-world') }, { 'openWorkClosedByStop' => false }, { 'writeRunId' => 'foreign-run-001' }].each do |change|
      File.write(receipt, JSON.generate(value.merge(change)))
      failure('RESTART_EXACT_STOPPED_WORLD_MISMATCH') { restart_source_for(old) }
    end
    [{ 'path' => @fixture, 'sha256' => P11OnlineRuntime::FIXTURE_SHA }, value.fetch('loadedConfiguration').merge('sha256' => '0' * 64)].each do |config|
      File.write(receipt, JSON.generate(value.merge('loadedConfiguration' => config)))
      failure('RESTART_ACTUAL_OLD_CONFIG_MISMATCH') { restart_source_for(old) }
    end
    File.binwrite(receipt, original)
    config = value.fetch('loadedConfiguration').fetch('path')
    actual = File.binread(config)
    File.binwrite(config, actual + "\n")
    failure('RESTART_ACTUAL_OLD_CONFIG_MISMATCH') { restart_source_for(old) }
    File.binwrite(config, actual)
    read = prepare(case: 'l1-restart-read', accept_eula: true, run_id: 'restart-read-002',
                   restart_from: File.join(old.fetch('runtime'), 'frozen-manifest.json'))
    input = File.join(read.fetch('evidence'), 'l1-restart-input.json')
    File.chmod(0o600, input)
    File.binwrite(input, original + ' ')
    P11OnlineRuntime.stub(:verify_jar!, true) { failure('RESTART_FIXED_INPUT_CHANGED') { P11OnlineRuntime.verify_restart_input!(read) } }
    File.binwrite(input, original)
    File.binwrite(receipt, original + ' ')
    P11OnlineRuntime.stub(:verify_jar!, true) { failure('RESTART_SOURCE_IDENTITY_CHANGED') { P11OnlineRuntime.verify_restart_input!(read) } }
    File.binwrite(receipt, original)
    fail_file = File.join(old.fetch('evidence'), 'server/l1-restart-stop-failure.json')
    File.write(fail_file, 'SYNTHETIC_FAILURE_PRESENCE_ONLY')
    failure('RESTART_SOURCE_RECORDED_FAILURE') { restart_source_for(old) }
  end

  def test_restart_missing_and_symlink_receipts_never_fall_back_to_foreign_paths
    old = restart_write_fixture
    file = File.join(old.fetch('evidence'), 'server/l1-restart-expected.json')
    original = File.binread(file)
    File.unlink(file)
    failure('RESTART_RECEIPT_MISSING_OR_SIZE') { restart_source_for(old) }
    foreign = File.join(@root, 'foreign-receipt.json')
    File.binwrite(foreign, original)
    File.symlink(foreign, file)
    failure('SYMLINK_FORBIDDEN') { restart_source_for(old) }
    File.unlink(file)
    File.binwrite(file, original)
    config = JSON.parse(original).fetch('loadedConfiguration').fetch('path')
    bytes = File.binread(config)
    File.unlink(config)
    File.symlink(@fixture, config)
    failure('SYMLINK_FORBIDDEN') { restart_source_for(old) }
    File.unlink(config)
    File.binwrite(config, bytes)
    File.binwrite(file, '[')
    failure('RESTART_RECEIPT_SHAPE') { restart_source_for(old) }
    File.binwrite(file, ' ' * 65_537)
    failure('RESTART_RECEIPT_MISSING_OR_SIZE') { restart_source_for(old) }
  end

  def test_restart_parent_has_no_read_provision_or_work_restore_and_fixed_status_names
    base = File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/java/com/yo1no/gramarye')
    server = File.read(File.join(base, 'P11L1ServerHarness.java'))
    client = File.read(File.join(base, 'P11L1ClientHarness.java'))
    read_branch = server.split('private static void progressRestartRead()', 2).last.split('static boolean workReward()', 2).first
    assert_includes read_branch, '!armed && run == null && !restartSecond && logins == 1'
    assert_includes read_branch, 'P11L1RestartProbe.verifyRead(current)'
    refute_match(/sendCommand|starter|halt|new Run|prepareArena|accepted\(/, read_branch)
    assert_operator server.index('if (P11L1RestartProbe.readSelected()) { progressRestartRead(); return; }'), :<, server.index('if (!armed)')
    assert_includes client, 'logins == 1 && sends == 0 && casts == 0 && !starter'
    assert_includes client, 'ORIGINAL_RESTART_WRITE_CLIENT_TERMINAL_NOT_RESTART_PROOF'
    assert_includes client, '!cue(role + "-cast-1.ready")'
    config = File.read(File.join(base, 'P11C4aLoadedConfiguration.java'))
    assert_includes config, 'P11L1RestartProbe.expectedConfigurationHash(realFile)'
    %w[P11L1RestartProbe.java harnessmixin/P11L1RestartRuntimeMixin.java harnessmixin/P11L1RestartCompositionMixin.java].each do |name|
      path = 'src/p11OnlineHarness/java/com/yo1no/gramarye/' + name
      catalog = File.read(File.join(P11OnlineRuntime::REPO, 'scripts/verify-p7-s4-source-contracts.sh'))
      assert_includes catalog, path + ' |'
      refute_includes catalog, path + '.extra |'
    end
    manifest = prepare(case: 'l1-restart-write')
    FileUtils.mkdir_p(File.join(manifest.fetch('evidence'), 'server'))
    %w[l1-restart-expected.json l1-restart-expected.json.extra l1-restart-stop-failure.json latest.log].each do |leaf|
      File.write(File.join(manifest.fetch('evidence'), 'server', leaf), 'CONTENT_NOT_READ')
    end
    File.stub(:binread, ->(*) { flunk 'status must not read restart contents' }) do
      present = P11OnlineRuntime.status(manifest).fetch('present')
      assert_includes present, 'server/l1-restart-expected.json'
      assert_includes present, 'server/l1-restart-stop-failure.json'
      refute present.any? { |name| name.end_with?('.extra', '.log') }
    end
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
    selected_mode = manifest.fetch('cooldownHost', false) ? 'COOLDOWN_HOST' : manifest.fetch('l1HostStop', false) ? 'L1_HOST_STOP' : 'UI_HELD'
    File.write(File.join(source, 'java/com/yo1no/gramarye/P11C4aScenario.java'), "final class P11C4aScenario {\n static final Mode MODE = Mode.#{selected_mode};\n}\n")
    FileUtils.mkdir_p(File.join(classes, 'com/yo1no/gramarye'))
    File.write(File.join(classes, 'com/yo1no/gramarye/P11OnlineFixture.class'), 'SYNTHETIC_BYTECODE_NOT_MINECRAFT')
    File.write(File.join(classes, 'com/yo1no/gramarye/P11C4aScenario.class'), 'SYNTHETIC_MODE_BYTECODE_NOT_MINECRAFT')
    console = File.binread(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/resources', P11OnlineRuntime::CONSOLE_XML))
    [resources, File.join(source, 'resources')].each do |dir|
      File.write(File.join(dir, P11OnlineRuntime::CONSOLE_XML), console)
      File.write(File.join(dir, 'gramarye-p11-online-harness.mixins.json'), '{"required":true}')
      File.write(File.join(dir, 'gramarye-p11-c4a-harness.mixins.json'), '{"required":true}')
      File.write(File.join(dir, 'gramarye-p11-c6-observers.mixins.json'), '{"required":true}')
      File.write(File.join(dir, 'gramarye-p11-l1-harness.mixins.json'), '{"required":true}')
      File.write(File.join(dir, 'gramarye-p11-cooldown-harness.mixins.json'), '{"required":true}')
      P11OnlineRuntime::L1_HOST_RESOURCES.each_key do |relative|
        target = File.join(dir, relative)
        FileUtils.mkdir_p(File.dirname(target))
        File.binwrite(target, File.binread(File.join(P11OnlineRuntime::REPO, 'src/p11OnlineHarness/resources', relative)))
      end
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
      if role == 'server' && %w[l1-restart-read cooldown-restart-read].include?(manifest.fetch('case'))
        program += ['--universe', manifest.fetch('restartSource').fetch('universe'), '--world', 'p11-online-world']
      end
      program += P11OnlineRuntime.mixin_configs(manifest.fetch('case')).flat_map { |config| ['--mixin.config', config] }
      File.write(File.join(base, stem + 'RunProgramArgs.txt'), program.join("\n"))
      File.write(File.join(base, stem + 'Log4j2.xml'), '<Configuration><Root level="DEBUG"/></Configuration>')
    end
    base
  end

  def export(manifest)
    P11OnlineRuntime.stub(:verify_jar!, true) do
      P11OnlineRuntime.stub(:candidate_head, 'e' * 40) { P11OnlineRuntime.freeze_launchers(manifest) }
      P11OnlineRuntime.load_manifest(File.join(manifest['runtime'], 'frozen-manifest.json'),
                                    runtime_root: @options[:runtime_root], private_root: @private)
    end
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
