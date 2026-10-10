#!/usr/bin/env ruby
# Engineering-only preparation/control. Never collect authentication stores or native logs.
require 'digest'
require 'fileutils'
require 'json'
require 'optparse'
require 'pathname'
require 'securerandom'
require 'shellwords'
require 'socket'
require 'find'
require 'open3'
require 'time'

module P11OnlineRuntime
  REPO = File.expand_path('..', __dir__).freeze
  RUNTIME_ROOT = '/private/tmp/gramarye-p11-online-runtime-xQzfVL4W'.freeze
  EVIDENCE_ROOT = '/private/tmp/gramarye-p11-online-evidence-KdUawEUU'.freeze
  PRIVATE_ROOT = '/private/tmp/gramarye-p11-online-private-uCBSE1jU'.freeze
  JAVA_HOME = '/Users/yashen/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.8+9/Contents/Home'.freeze
  FIXTURE = '/private/tmp/gramarye-p11-native-native-reward-gKKcsevt/evidence/01-startup-fixture.toml'.freeze
  FIXTURE_SHA = '6456c061612ac52835690a75aa5c4944adc27e659a4a8672c546d276fed2bdd6'.freeze
  C4A_C6_FIXTURE_SHA = 'ac8492a10ac2376c5b2ada8a3cb6e6086a2a53a29af3cbea3547f0ae01087af4'.freeze
  C4A_C6_RATE_FIXTURE_SHA = '7fd078d2f9d61454011e378b5721b84d9e0228cf77bccd0b5ceab3b0a076431f'.freeze
  C4A_HANDOFF_FIXTURE_SHA = '7bdab7fb9c58cf8c7259eaada103671aa6881042865fd7748f91c838d58ce229'.freeze
  JAR_SHA = '4d79086f628daa137281f71312596d4dda861bafbaa9b62a65cd70e5257d248b'.freeze
  # A new cohort cannot inherit the historical online acceptance artifact.
  # Set this exact engineering pin only after the coordinated product build is accepted.
  C4A_JAR_SHA = '9f499356841f302b04d3846d85640ed273ec3104dbc0e743f8cea452243b4c97'.freeze
  C4A_CASES = %w[c4a-dedicated c4a-host-lan c4a-reward].freeze
  L1_CASES = %w[l1-pre-spawn l1-open l1-claimed l1-impact-close-custody l1-two-work-reload l1-two-work-stop l1-revision l1-p8-send-fault l1-ack-fault l1-work-death l1-work-dimension l1-work-config l1-partial-reward-function l1-work-multi-uuid-qctx l1-stats-write-fault-memory l1-work-deadline l1-spawn-callback-remove l1-logout-cleanup-fault l1-tracking-retirement l1-work-capacity l1-natural-unload l1-online-peer l1-work-context-refusal l1-restart-write l1-restart-read].freeze
  L1_CONTEXT_CASES = %w[l1-qctx l1-capacity].freeze
  L1_JAR_SHA = 'fc15bf045905285c4b8dfe661b41d34e890f628c3399c5824070194ae395aeb8'.freeze
  COOLDOWN_CASES = %w[cooldown-d1 cooldown-d120 cooldown-d600 cooldown-restart-write cooldown-restart-read cooldown-prepared-reentry cooldown-add-false cooldown-add-remove cooldown-clone cooldown-before-arm-throw cooldown-after-arm-throw cooldown-unarmed-stop cooldown-save-active cooldown-save-clear cooldown-dual].freeze
  COOLDOWN_L1_CASES = %w[cooldown-l1-pre-spawn cooldown-l1-open cooldown-l1-claimed].freeze
  # No launch is authorized by an old phase's pin. Filled only after this product build.
  COOLDOWN_JAR_SHA = '3e1960fdc37bca113f8e9d9569241e65192c5eb7ee119b64cadef8f3a97059d2'.freeze
  D3_CASES = %w[d3-replay d3-old-source d3-late-task d3-old-result d3-respawn d3-dual d3-host-lan].freeze
  D3_C4A_CASE = 'd3-c4a-baseline'.freeze
  D3_JAR_SHA = '3e1960fdc37bca113f8e9d9569241e65192c5eb7ee119b64cadef8f3a97059d2'.freeze
  L1_STATS_MEMORY_FIXTURE_SHA = '04c94e6c2913d0ad20a200e03c55787c740275c9a70fcc94af5de2286d4c52a3'.freeze
  L1_FIXTURE_HASHES = {
    'advancement/l1_first_kill.json' => '61e2889dade9569ed5f7a4b0862e0e69c209a205841d13c4b483e43a0949119b',
    'advancement/l1_late_kill.json' => '1b1c493e8b11b16409df14b0f5abe043eefe62d9a7b262060320c0b650ab390a',
    'loot_table/l1_loot.json' => 'a80d7eaf6077c9c26f8ebacbb23d5639b48ec414130eb01c11fc04931720592b',
    'function/l1_reward.mcfunction' => '287408cfc4d60c87b14ca3091e409fde1d9b47aa0afe9874c39525ee7b4a6f0b',
    'advancement/l1_partial_kill.json' => 'cc00706d444f850990c27dbc5a9e68e77c846f02aad68404fd728f74fe02eb29',
    'advancement/l1_qctx_kill.json' => 'cfa70b73ec1e66137018e3dc3e86e5c9e649b381f4af41282685d60b6b267629',
    'advancement/l1_qctx_peer.json' => '6dde4501682e2d59d88d41456dab88f72845e275e6c6f95320153620b746917f',
    'function/l1_partial_reward.mcfunction' => '30063da6b9f0b67fb056cb65d3d613f98e2d9024e4e6b43ca7aa73b17b9453eb',
    'function/l1_qctx_outer.mcfunction' => 'f2040999da0d0bec70707535c80f24db02b70737d097e82fb4d6df56dc11b1ae',
    'function/l1_qctx_peer_reward.mcfunction' => 'c56774ec01ad657ad6b6499933abb704d1251c0a76010ce1fd40d9c21ef70e51'
  }.freeze
  REWARD_FIXTURE_HASHES = {
    'advancement/native_prepared.json' => '85b51a703ea2d00354f92e80bc8a5bcca3161d06f34f734ee12e5384cc9e4eaf',
    'advancement/native_reward.json' => 'd85287408d3f18203388d733f6b80a89edc165b0370256975502ea1c7a546131',
    'advancement/delivery_recipe.json' => 'fa1b2cd39d74d0b3015b5e64b73a05d2cc21242fee356e0944b59b008ac87cef',
    'loot_table/native_reward.json' => '24cfd88a5a4241d07225ea21b9f1341fa5a516b335b44c5c64cf43d90e6d12f2',
    'function/prepared_reward.mcfunction' => '17ce9ba51d68f89bbf334a2cac48f2f334368b5b0ca22c1c9c049a8f765bf516',
    'function/reward.mcfunction' => '690ba9178a47594e593da074d50599a92e82689ff5532cc44cdbeb78f974e7d7',
    'function/delivery_tail.mcfunction' => '063b29f7caed0f928290c56bd4c67be669f80ea75a3aae821c0e84e2fe9dde3f'
  }.freeze
  CURRENT_CONTEXT_CASES = %w[c4a-qctx c4a-capacity].freeze
  CASES = (%w[single qctx capacity] + C4A_CASES + CURRENT_CONTEXT_CASES + L1_CASES + L1_CONTEXT_CASES + COOLDOWN_CASES + COOLDOWN_L1_CASES + D3_CASES + [D3_C4A_CASE]).freeze
  CLIENT_FILES = %w[bootstrap.json onboarding-continued.json inputs.json login-1.json login-2.json first-play.json
                    first-disconnected.json second-play.json capacity-respawn.json result.json].freeze
  SERVER_FILES = %w[ready.json readiness-result.json native-context-result.json result.json
                    failure.json stopped.json b-capacity-death.json b-capacity-respawn.json].freeze
  C4A_SCENE_FILES = %w[death-button.json death-negative-confirm.json death-immediate.json end.json enter-config.json].freeze
  OWN_FILES = %w[prepare.json launch-cues.txt launcher-export.json client-a-process-exit.json client-b-process-exit.json reward-fixture.json].freeze
  LAUNCH_STEMS = %w[p11OnlineServer p11OnlineClientA p11OnlineClientB].freeze
  LAUNCH_SUFFIXES = %w[RunClasspath.txt RunVmArgs.txt RunProgramArgs.txt LegacyClasspath.txt Log4j2.xml].freeze
  PLATFORM_JARS = %w[neoforge-21.1.241.jar neoforge-21.1.241-client-extra-aka-minecraft-resources.jar].freeze
  CONSOLE_XML = 'gramarye-p11-online-private-console.xml'.freeze
  C6_MIXIN_CONFIG = 'gramarye-p11-c6-observers.mixins.json'.freeze
  L1_HOST_RESOURCES = %w[advancement/l1_first_kill.json loot_table/l1_loot.json function/l1_reward.mcfunction]
                      .to_h { |leaf| ['data/gramarye_p11_engineering/' + leaf, L1_FIXTURE_HASHES.fetch(leaf)] }.freeze
  COMPANION_RESOURCES = [CONSOLE_XML, 'gramarye-p11-online-harness.mixins.json',
                         'gramarye-p11-c4a-harness.mixins.json', 'gramarye-p11-l1-harness.mixins.json',
                         'gramarye-p11-cooldown-harness.mixins.json', 'gramarye-p11-d3-harness.mixins.json', C6_MIXIN_CONFIG,
                         *L1_HOST_RESOURCES.keys].sort.freeze
  DIAGNOSTIC_CLASS = 'com/yo1no/gramarye/P11OnlineLaunchDiagnostic'.freeze
  # D3 measured source/class/resource inventory needs 1043 frozen files (no diagnostics).
  # This bounds an excluded immutable launch bundle, not any gameplay capacity.
  MAX_FROZEN_BUNDLE_FILES = 1088

  class Failure < StandardError
    attr_reader :code
    def initialize(code)
      @code = code
      super(code)
    end
  end

  module_function

  def check(condition, code)
    raise Failure, code unless condition
  end

  def path(value)
    check(value.is_a?(String) && value.start_with?('/') && !value.match?(/[\x00-\x1f\x7f]/), 'INVALID_PATH')
    Pathname.new(value).cleanpath.to_s
  end

  def within?(child, parent)
    child == parent || child.start_with?(parent + '/')
  end

  # Only caller-designated non-secret paths reach filesystem inspection. Auth is lexical only.
  def nonsecret_path!(value, private_root)
    value = path(value)
    check(!within?(value, private_root) && !within?(private_root, value), 'PRIVATE_PATH_FORBIDDEN')
    parts = value.split('/').reject(&:empty?)
    cursor = ''
    parts.each do |part|
      cursor += '/' + part
      check(!File.symlink?(cursor), 'SYMLINK_FORBIDDEN')
    end
    value
  end

  def roots!(options)
    private_root = path(options.fetch(:private_root))
    repo = nonsecret_path!(options.fetch(:repo), private_root)
    runtime = nonsecret_path!(options.fetch(:runtime_root), private_root)
    evidence = nonsecret_path!(options.fetch(:evidence_root), private_root)
    [runtime, evidence].each do |root|
      check(!within?(root, repo) && !within?(repo, root), 'ROOT_IN_REPOSITORY')
      check(File.directory?(root), 'ROOT_MISSING')
      check((File.stat(root).mode & 0o077).zero?, 'ROOT_NOT_PRIVATE')
    end
    check(!within?(runtime, evidence) && !within?(evidence, runtime), 'ROOTS_OVERLAP')
    [repo, runtime, evidence, private_root]
  end

  def write_new(file, text, mode = 0o600)
    File.open(file, File::WRONLY | File::CREAT | File::EXCL, mode) { |io| io.write(text) }
  end

  def mkdir_new(dir)
    Dir.mkdir(dir, 0o700)
  end

  def checked_port(requested)
    check(requested.is_a?(Integer) && (requested.zero? || (1024..65_535).cover?(requested)), 'INVALID_PORT')
    TCPServer.open('127.0.0.1', requested) { |socket| socket.addr[1] }
  rescue SystemCallError
    raise Failure, 'PORT_UNAVAILABLE'
  end

  def host_selection!(case_name, selected)
    check([true, false].include?(selected) && (!selected || case_name == 'c4a-host-lan'), 'INVALID_L1_HOST_SELECTION')
    selected
  end

  def cooldown_host_selection!(case_name, selected, l1_host_stop: false)
    host_selection!(case_name, l1_host_stop)
    check([true, false].include?(selected) && (!selected || case_name == 'c4a-host-lan'), 'INVALID_COOLDOWN_HOST_SELECTION')
    check(!selected || !l1_host_stop, 'CONFLICTING_HOST_SELECTION')
    selected
  end

  def product_pin(case_name, l1_host_stop: false, cooldown_host: false)
    check(CASES.include?(case_name), 'INVALID_CASE')
    cooldown_host_selection!(case_name, cooldown_host, l1_host_stop: l1_host_stop)
    if D3_CASES.include?(case_name) || case_name == D3_C4A_CASE
      check(D3_JAR_SHA.is_a?(String) && D3_JAR_SHA.match?(/\A[0-9a-f]{64}\z/), 'D3_PRODUCT_PIN_PENDING')
      return D3_JAR_SHA
    end
    if COOLDOWN_CASES.include?(case_name) || COOLDOWN_L1_CASES.include?(case_name) || cooldown_host
      check(COOLDOWN_JAR_SHA.is_a?(String) && COOLDOWN_JAR_SHA.match?(/\A[0-9a-f]{64}\z/), 'COOLDOWN_PRODUCT_PIN_PENDING')
      return COOLDOWN_JAR_SHA
    end
    if L1_CASES.include?(case_name) || L1_CONTEXT_CASES.include?(case_name) || l1_host_stop
      check(L1_JAR_SHA.is_a?(String) && L1_JAR_SHA.match?(/\A[0-9a-f]{64}\z/), 'L1_PRODUCT_PIN_PENDING')
      return L1_JAR_SHA
    end
    return JAR_SHA unless current_product_case?(case_name)
    check(C4A_JAR_SHA.is_a?(String) && C4A_JAR_SHA.match?(/\A[0-9a-f]{64}\z/), 'C4A_PRODUCT_PIN_PENDING')
    C4A_JAR_SHA
  end

  def current_product_case?(case_name)
    C4A_CASES.include?(case_name) || CURRENT_CONTEXT_CASES.include?(case_name) || L1_CASES.include?(case_name) || L1_CONTEXT_CASES.include?(case_name) || COOLDOWN_CASES.include?(case_name) || COOLDOWN_L1_CASES.include?(case_name) || D3_CASES.include?(case_name) || case_name == D3_C4A_CASE
  end

  def host_case?(case_name)
    %w[c4a-host-lan d3-host-lan].include?(case_name)
  end

  def roles_for(case_name)
    return %w[host b] if host_case?(case_name)
    case_name == 'single' ? %w[server single] : %w[server a b]
  end

  def mixin_config(case_name)
    check(CASES.include?(case_name), 'INVALID_CASE')
    return 'gramarye-p11-d3-harness.mixins.json' if D3_CASES.include?(case_name)
    return 'gramarye-p11-cooldown-harness.mixins.json' if COOLDOWN_CASES.include?(case_name)
    return 'gramarye-p11-l1-harness.mixins.json' if L1_CASES.include?(case_name) || COOLDOWN_L1_CASES.include?(case_name)
    C4A_CASES.include?(case_name) || case_name == D3_C4A_CASE ? 'gramarye-p11-c4a-harness.mixins.json' : 'gramarye-p11-online-harness.mixins.json'
  end

  def mixin_configs(case_name)
    configs = [mixin_config(case_name)]
    configs << C6_MIXIN_CONFIG if C4A_CASES.include?(case_name) || case_name == D3_C4A_CASE
    configs
  end

  def frozen_jar_path(runtime_root, case_name, l1_host_stop: false, cooldown_host: false)
    pin = product_pin(case_name, l1_host_stop: l1_host_stop, cooldown_host: cooldown_host)
    return File.join(runtime_root, 'frozen-d3', pin, 'gramarye-1.0.0.jar') if D3_CASES.include?(case_name) || case_name == D3_C4A_CASE
    return File.join(runtime_root, 'frozen-cooldown', pin, 'gramarye-1.0.0.jar') if COOLDOWN_CASES.include?(case_name) || COOLDOWN_L1_CASES.include?(case_name) || cooldown_host
    return File.join(runtime_root, 'frozen-l1', pin, 'gramarye-1.0.0.jar') if L1_CASES.include?(case_name) || L1_CONTEXT_CASES.include?(case_name) || l1_host_stop
    return File.join(runtime_root, 'frozen', 'gramarye-1.0.0.jar') unless current_product_case?(case_name)
    File.join(runtime_root, 'frozen-c4a', product_pin(case_name), 'gramarye-1.0.0.jar')
  end

  def verify_jar!(file, case_name = 'single', l1_host_stop: false, cooldown_host: false)
    expected = product_pin(case_name, l1_host_stop: l1_host_stop, cooldown_host: cooldown_host)
    check(File.file?(file) && Digest::SHA256.file(file).hexdigest == expected, 'FROZEN_JAR_MISMATCH')
  end

  def java_home!(home, private_root)
    home = nonsecret_path!(home, private_root)
    check(File.executable?(File.join(home, 'bin/java')), 'JAVA_EXECUTABLE_MISSING')
    release = nonsecret_path!(File.join(home, 'release'), private_root)
    check(File.size(release) <= 16_384 && File.binread(release).match?(/^JAVA_VERSION="21(?:[.+"-])/), 'JAVA_21_REQUIRED')
    home
  end

  def freeze_jar!(source, runtime, private_root, case_name = 'single', l1_host_stop: false, cooldown_host: false)
    source = nonsecret_path!(source, private_root)
    verify_jar!(source, case_name, l1_host_stop: l1_host_stop, cooldown_host: cooldown_host)
    target = frozen_jar_path(runtime, case_name, l1_host_stop: l1_host_stop, cooldown_host: cooldown_host)
    directory = File.dirname(target)
    directories = current_product_case?(case_name) ? [File.dirname(directory), directory] : [directory]
    directories.each do |candidate|
      nonsecret_path!(candidate, private_root)
      if File.exist?(candidate)
        check(File.directory?(candidate), 'FROZEN_DIRECTORY_INVALID')
      else
        mkdir_new(candidate)
      end
    end
    nonsecret_path!(target, private_root)
    unless File.exist?(target)
      File.open(target, File::WRONLY | File::CREAT | File::EXCL, 0o400) do |output|
        File.open(source, 'rb') { |input| IO.copy_stream(input, output) }
      end
    end
    verify_jar!(target, case_name, l1_host_stop: l1_host_stop, cooldown_host: cooldown_host)
    target
  end

  def startup_fixture!(file, private_root, case_name, with_source_hash: false, l1_host_stop: false, cooldown_host: false)
    cooldown_host_selection!(case_name, cooldown_host, l1_host_stop: l1_host_stop)
    file = nonsecret_path!(file, private_root)
    raw = File.binread(file)
    source_hash = Digest::SHA256.hexdigest(raw)
    check(!(l1_host_stop || cooldown_host) || source_hash == FIXTURE_SHA, 'STARTUP_FIXTURE_MISMATCH')
    check(source_hash == FIXTURE_SHA || case_name == 'l1-stats-write-fault-memory' && source_hash == L1_STATS_MEMORY_FIXTURE_SHA || %w[c4a-dedicated c4a-host-lan].include?(case_name) &&
          [C4A_C6_FIXTURE_SHA, C4A_C6_RATE_FIXTURE_SHA, C4A_HANDOFF_FIXTURE_SHA].include?(source_hash),
          'STARTUP_FIXTURE_MISMATCH')
    # Natural L1 positives use the original four-UUID fixture. The old two-UUID
    # context cohort is a separate stress configuration, not its admission setup.
    limit = %w[capacity c4a-capacity l1-capacity l1-work-capacity].include?(case_name) ? 1 : (L1_CASES.include?(case_name) || COOLDOWN_CASES.include?(case_name) || COOLDOWN_L1_CASES.include?(case_name) || D3_CASES.include?(case_name) || l1_host_stop || cooldown_host) ? 4 : 2
    %w[p11.retention.maxUuids p11.save.dirtyUuidAdmissionWatermark].each do |key|
      pattern = /^#{Regexp.escape(key)} = 4$/
      check(raw.scan(pattern).length == 1, 'STARTUP_FIXTURE_KEY_MISMATCH')
      raw = raw.sub(pattern, "#{key} = #{limit}")
    end
    if case_name == 'l1-stats-write-fault-memory'
      if source_hash == FIXTURE_SHA
        { 'p11.save.maxSealedSnapshots' => 4, 'p11.save.maxSealedBytes' => 67_108_864 }.each do |key, value|
          pattern = /^#{Regexp.escape(key)} = 1$/
          check(raw.scan(pattern).length == 1, 'STARTUP_FIXTURE_KEY_MISMATCH')
          raw = raw.sub(pattern, "#{key} = #{value}")
        end
      end
      check(Digest::SHA256.hexdigest(raw) == L1_STATS_MEMORY_FIXTURE_SHA, 'STARTUP_FIXTURE_MISMATCH')
    end
    with_source_hash ? [raw, source_hash] : raw
  end

  def properties(port)
    flat = { layers: [{ height: 1, block: 'minecraft:bedrock' },
                      { height: 2, block: 'minecraft:dirt' },
                      { height: 1, block: 'minecraft:grass_block' }],
             biome: 'minecraft:plains', features: true, lakes: false }
    {
      'online-mode' => 'true', 'server-ip' => '127.0.0.1', 'server-port' => port,
      'level-name' => 'p11-online-world', 'level-type' => 'minecraft:flat',
      'generator-settings' => JSON.generate(flat), 'difficulty' => 'normal',
      'gamemode' => 'survival', 'hardcore' => 'false', 'max-players' => 2,
      'enable-query' => 'false', 'enable-rcon' => 'false', 'enable-status' => 'false',
      'hide-online-players' => 'true', 'log-ips' => 'false',
      'motd' => 'Private Gramarye P11 engineering server'
    }.map { |key, value| "#{key}=#{value}\n" }.join
  end

  def delivery_root
    { display: { icon: { id: 'minecraft:stone' }, title: 'P11 delivery',
                 description: 'Excluded online delivery observation', frame: 'task',
                 show_toast: false, announce_to_chat: false, hidden: false },
      criteria: { manual: { trigger: 'minecraft:impossible' } }, rewards: { experience: 0 } }
  end

  # Closed seven-file historical fixture; no caller-chosen file list or datapack attachment API.
  def reward_fixture_sources!(repo, private_root, case_name)
    return {} unless case_name == 'c4a-reward' || L1_CASES.include?(case_name) || COOLDOWN_L1_CASES.include?(case_name)
    base = L1_CASES.include?(case_name) || COOLDOWN_L1_CASES.include?(case_name) ? 'src/p11OnlineHarness/fixtures/l1' : 'src/p9S5ClientHarness/resources/data/gramarye_p11_engineering'
    reward_fixture_hashes(case_name).to_h do |leaf, expected|
      file = nonsecret_path!(File.join(repo, base, leaf), private_root)
      check(File.file?(file) && File.size(file).between?(1, 16_384), 'REWARD_FIXTURE_SOURCE_MISSING')
      bytes = File.binread(file)
      check(Digest::SHA256.hexdigest(bytes) == expected, 'REWARD_FIXTURE_SOURCE_MISMATCH')
      [leaf, bytes]
    end
  end

  def reward_fixture_hashes(case_name)
    L1_CASES.include?(case_name) || COOLDOWN_L1_CASES.include?(case_name) ? L1_FIXTURE_HASHES : REWARD_FIXTURE_HASHES
  end

  def verify_reward_fixture!(manifest)
    unless manifest.fetch('case') == 'c4a-reward' || L1_CASES.include?(manifest.fetch('case')) || COOLDOWN_L1_CASES.include?(manifest.fetch('case'))
      check(!manifest.key?('rewardFixtureHashes'), 'UNEXPECTED_REWARD_FIXTURE')
      return
    end
    hashes = reward_fixture_hashes(manifest.fetch('case'))
    check(manifest['rewardFixtureHashes'] == hashes, 'REWARD_FIXTURE_MANIFEST_MISMATCH')
    hashes.each do |leaf, expected|
      world = manifest.fetch('case') == 'l1-restart-read' ? manifest.fetch('restartSource').fetch('world') : File.join(manifest.fetch('runtime'), 'server/p11-online-world')
      file = nonsecret_path!(File.join(world, 'datapacks/p11-online-engineering/data/gramarye_p11_engineering', leaf), manifest.fetch('privateRoot'))
      check(File.file?(file) && File.size(file).between?(1, 16_384) &&
            Digest::SHA256.file(file).hexdigest == expected, 'REWARD_FIXTURE_CONTENT_CHANGED')
    end
  end

  def gradle_argv(manifest, task)
    check(%w[runP11OnlineServer runP11OnlineClientA runP11OnlineClientB
             createP11OnlineServerLaunchScript createP11OnlineClientALaunchScript
             createP11OnlineClientBLaunchScript].include?(task), 'INVALID_LAUNCH_TASK')
    properties = {
      'Runtime' => manifest.fetch('runtime'), 'Output' => manifest.fetch('evidence'),
      'Private' => manifest.fetch('privateRoot'), 'Jar' => manifest.fetch('jar'),
      'Case' => manifest.fetch('case'), 'Port' => manifest.fetch('port').to_s,
      'RunId' => manifest.fetch('runId'), 'ReadyOnly' => manifest.fetch('readyOnly').to_s
    }
    if %w[l1-restart-read cooldown-restart-read].include?(manifest.fetch('case'))
      properties['RestartUniverse'] = manifest.fetch('restartSource').fetch('universe')
    end
    [File.join(manifest.fetch('repo'), 'gradlew'), '--no-daemon', '--console=plain'] +
      properties.map { |key, value| "-PgramaryeP11Online#{key}=#{value}" } + [task]
  end

  def client_launcher(manifest, client)
    check(%w[a b].include?(client), 'INVALID_CLIENT')
    prefix = "#!/bin/sh\n# LOCAL PRIVATE CONSOLE: no tee, redirects, screenshots or log collection.\nset -eu\numask 077\n" \
      "unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS\n" \
      "export JAVA_HOME=#{Shellwords.escape(manifest.fetch('javaHome'))}\n"
    if manifest.fetch('readyOnly')
      return prefix + "echo 'READY_ONLY_DOES_NOT_LAUNCH_AUTHENTICATION'\nexit 2\n"
    end
    if client == 'b' && manifest.fetch('case') == 'single'
      return prefix + "echo 'CLIENT_B_NOT_APPLICABLE_TO_SINGLE'\nexit 2\n"
    end
    # A later exact MDG snapshot supplies the launch command; preparation grants no launch permission.
    return prefix + "echo 'FROZEN_LAUNCHERS_REQUIRED'\nexit 2\n" unless manifest['launchMode'] == 'FROZEN_MDG'
    argv = ['/usr/bin/ruby', File.join(manifest.fetch('bundle'), 'tools/p11-online-runtime.rb'),
            'launch-client', '--manifest', File.join(manifest.fetch('runtime'), 'frozen-manifest.json'),
            '--runtime-root', manifest.fetch('runtimeRoot'), '--private-root', manifest.fetch('privateRoot'), '--client', client]
    prefix + "exec #{Shellwords.join(argv)}\n"
  end

  def launch_role(manifest, stem)
    check(LAUNCH_STEMS.include?(stem), 'INVALID_LAUNCH_ROLE')
    return 'server' if stem == 'p11OnlineServer'
    return 'b' if stem == 'p11OnlineClientB'
    return 'host' if host_case?(manifest.fetch('case'))
    manifest.fetch('case') == 'single' ? 'single' : 'a'
  end

  def launch_argv(manifest, stem, base, classes, resources)
    folders = [classes, resources, manifest.fetch('jar')].map { |root| "p11OnlineHarness%%#{root}" }.join(':')
    argv = [File.join(manifest.fetch('javaHome'), 'bin/java'), '@' + File.join(base, stem + 'RunClasspath.txt'),
            '@' + File.join(base, stem + 'RunVmArgs.txt'), '-Dfml.modFolders=' + folders]
    if manifest['launchMode'] == 'FROZEN_MDG' && manifest['diagnoseClient'] && stem != 'p11OnlineServer'
      agent = File.join(manifest.fetch('bundle'), 'diagnostic/launch-diagnostic.jar')
      output = File.join(manifest.fetch('evidence'), "client-#{launch_role(manifest, stem)}-launch-diagnostic")
      argv << "-javaagent:#{agent}=#{output}"
    end
    argv + ['net.neoforged.devlaunch.Main', '@' + File.join(base, stem + 'RunProgramArgs.txt')]
  end

  # Passive premain observer only: no transformer, authentication wrapper, or stream reader.
  def build_launch_diagnostic!(manifest, bundle, classes, class_files)
    names = class_files.select { |name| name == DIAGNOSTIC_CLASS + '.class' || name.start_with?(DIAGNOSTIC_CLASS + '$') }
    check(names.include?(DIAGNOSTIC_CLASS + '.class'), 'LAUNCH_DIAGNOSTIC_CLASS_MISSING')
    directory = File.join(bundle, 'diagnostic')
    mkdir_new(directory)
    manifest_file = File.join(directory, 'MANIFEST.MF')
    write_new(manifest_file, "Manifest-Version: 1.0\nPremain-Class: #{DIAGNOSTIC_CLASS.tr('/', '.')}\n\n", 0o400)
    target = File.join(directory, 'launch-diagnostic.jar')
    args = [File.join(manifest.fetch('javaHome'), 'bin/jar'), '--create', '--file', target,
            '--manifest', manifest_file, '--date=2026-09-29T00:00:00Z']
    args += names.flat_map { |name| ['-C', classes, name] }
    _output, _error, result = Open3.capture3({ 'JAVA_TOOL_OPTIONS' => nil, 'JDK_JAVA_OPTIONS' => nil, '_JAVA_OPTIONS' => nil }, *args)
    check(result.success?, 'LAUNCH_DIAGNOSTIC_JAR_FAILED')
    File.chmod(0o400, target)
  end

  def generated_shell!(raw, manifest, stem, base, classes, resources)
    match = /\A\(cd '([^']+)'; exec (.+)\)\s*\z/.match(raw)
    check(match && match[1] == File.join(manifest.fetch('runtime'), launch_role(manifest, stem)), 'GENERATED_SCRIPT_SHAPE')
    check(Shellwords.split(match[2]) == launch_argv(manifest, stem, base, classes, resources), 'GENERATED_SCRIPT_ARGUMENTS')
  end

  def text_tokens(raw)
    raw.lines.reject { |line| line.strip.empty? || line.lstrip.start_with?('#') }
       .flat_map { |line| Shellwords.split(line) }
  end

  def distribution_path!(file, manifest, local_roots, external)
    file = nonsecret_path!(file, manifest.fetch('privateRoot'))
    return if local_roots.include?(file)
    # Gradle renders this SHA-1 cache directory with leading zeroes trimmed on some entries.
    check(file.match?(%r{/\.gradle/caches/modules-2/files-2\.1/[^/]+/[^/]+/[^/]+/(?:[0-9a-f]{40}|[1-9a-f][0-9a-f]{0,38})/[^/]+\.jar\z}), 'UNEXPECTED_CLASSPATH')
    check(File.file?(file), 'DISTRIBUTION_MISSING')
    external[file] ||= Digest::SHA256.file(file).hexdigest
  end

  def generated_args!(files, manifest, stem, base, local_roots, external)
    cp = text_tokens(files.fetch(stem + 'RunClasspath.txt'))
    check(cp.length == 2 && cp.first == '-classpath', 'CLASSPATH_SHAPE')
    cp.last.split(':').each { |file| distribution_path!(file, manifest, local_roots, external) }
    %w[classes resources].each_with_index do |_name, index|
      check(cp.last.split(':').count(local_roots[index]) == 1, 'COMPANION_CLASSPATH_MISSING')
    end
    check(cp.last.split(':').count(manifest.fetch('jar')) == 1, 'PRODUCT_CLASSPATH_MISSING')
    files.fetch(stem + 'LegacyClasspath.txt').lines.map(&:strip).reject(&:empty?).each do |file|
      distribution_path!(file, manifest, local_roots, external)
    end
    vm = text_tokens(files.fetch(stem + 'RunVmArgs.txt'))
    check(vm[0] == '-p' && vm[1], 'MODULE_PATH_SHAPE')
    vm[1].split(':').each { |file| distribution_path!(file, manifest, [], external) }
    pairs = vm.select { |token| token.start_with?('-D') }.map { |token| token.delete_prefix('-D').split('=', 2) }
    check(pairs.all? { |pair| pair.length == 2 } && pairs.map(&:first).uniq.length == pairs.length, 'DUPLICATE_VM_PROPERTY')
    props = pairs.to_h
    expected = {
      'log4j2.configurationFile' => File.join(base, stem + 'Log4j2.xml'),
      'legacyClassPath.file' => File.join(base, stem + 'LegacyClasspath.txt'),
      'java.net.preferIPv6Addresses' => 'system',
      'ignoreList' => 'mixinextras-neoforge-0.5.3.jar,client-extra,neoforge-',
      'forge.logging.markers' => 'REGISTRIES',
      'gramarye.p11.online.readyOnly' => manifest.fetch('readyOnly').to_s,
      'gramarye.p11.online.role' => launch_role(manifest, stem),
      'gramarye.p11.online.output' => manifest.fetch('evidence'),
      'gramarye.p11.online.case' => manifest.fetch('case'),
      'gramarye.p11.online.runId' => manifest.fetch('runId'),
      'gramarye.p11.online.host' => '127.0.0.1',
      'gramarye.p11.online.jar' => manifest.fetch('jar'),
      'gramarye.p11.online.port' => manifest.fetch('port').to_s
    }
    if stem != 'p11OnlineServer'
      expected.merge!('devlogin.launch_target' => 'cpw.mods.bootstraplauncher.BootstrapLauncher',
                      'devlogin.launch_profile' => 'p11-local', 'neoforge.enableGameTest' => 'true',
                      'devlogin.storage' => File.join(manifest.fetch('privateRoot'), stem.end_with?('A') ? 'a' : 'b'))
    end
    check(props == expected, 'GENERATED_VM_PROPERTIES_MISMATCH')
    flags = vm.drop(2).reject { |token| token.start_with?('-D') }
    expected_flags = %w[--add-modules ALL-MODULE-PATH --add-opens java.base/java.util.jar=cpw.mods.securejarhandler
                        --add-opens java.base/java.lang.invoke=cpw.mods.securejarhandler --add-exports
                        java.base/sun.security.util=cpw.mods.securejarhandler --add-exports jdk.naming.dns/com.sun.jndi.dns=java.naming]
    expected_flags << '-XstartOnFirstThread' unless stem == 'p11OnlineServer'
    expected_flags += %w[-Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError]
    check(flags == expected_flags, 'GENERATED_VM_FLAGS_MISMATCH')
    program = text_tokens(files.fetch(stem + 'RunProgramArgs.txt'))
    if stem == 'p11OnlineServer'
      expected_program = %w[cpw.mods.bootstraplauncher.BootstrapLauncher --launchTarget forgeserverdev]
    else
      assets = program[8]
      check(assets && assets.match?(%r{\A/.+/\.gradle/caches/neoformruntime/assets\z}), 'ASSET_PATH_MISMATCH')
      nonsecret_path!(assets, manifest.fetch('privateRoot'))
      expected_program = %w[net.covers1624.devlogin.DevLogin --launchTarget forgeclientdev --version 21.1.241 --assetIndex 17 --assetsDir] + [assets]
    end
    expected_program += %w[--gameDir . --fml.fmlVersion 4.0.43 --fml.mcVersion 1.21.1 --fml.neoForgeVersion 21.1.241 --fml.neoFormVersion 20240808.144430]
    expected_program << '--nogui' if stem == 'p11OnlineServer'
    if stem == 'p11OnlineServer' && %w[l1-restart-read cooldown-restart-read].include?(manifest.fetch('case'))
      expected_program += ['--universe', manifest.fetch('restartSource').fetch('universe'), '--world', 'p11-online-world']
    end
    expected_program += mixin_configs(manifest.fetch('case')).flat_map { |config| ['--mixin.config', config] }
    check(program == expected_program, 'GENERATED_PROGRAM_ARGUMENTS_MISMATCH')
  end

  def tree_files!(root, private_root)
    nonsecret_path!(root, private_root)
    check(File.directory?(root), 'SNAPSHOT_DIRECTORY_MISSING')
    result = []
    Find.find(root) do |entry|
      check(!File.symlink?(entry), 'SYMLINK_FORBIDDEN')
      next if File.directory?(entry)
      check(File.file?(entry), 'SNAPSHOT_FILE_TYPE')
      result << entry.delete_prefix(root + '/')
    end
    result.sort
  end

  def candidate_head(repo)
    output, _error, status = Open3.capture3('git', '-C', repo, 'rev-parse', 'HEAD')
    check(status.success? && output.strip.match?(/\A[0-9a-f]{40}\z/), 'CANDIDATE_HEAD_UNAVAILABLE')
    output.strip
  end

  def verify_host_mode!(manifest, source_root)
    selected = host_selection!(manifest.fetch('case'), manifest.fetch('l1HostStop', false))
    cooldown = cooldown_host_selection!(manifest.fetch('case'), manifest.fetch('cooldownHost', false), l1_host_stop: selected)
    file = nonsecret_path!(File.join(source_root, 'com/yo1no/gramarye/P11C4aScenario.java'), manifest.fetch('privateRoot'))
    check(File.file?(file) && File.size(file).between?(1, 16_384), 'L1_HOST_MODE_SOURCE_MISSING')
    source = File.binread(file)
    conditional = <<~JAVA.strip
      static final Mode MODE = "d3-c4a-baseline".equals(System.getProperty("gramarye.p11.online.case", ""))
              ? Mode.BASELINE : Mode.UI_HELD;
    JAVA
    declarations = source.scan(/\bstatic\s+final\s+Mode\s+MODE\s*=[^;]*;/m)
    exact_conditional = declarations.length == 1 && declarations.first.gsub(/\s+/, ' ').strip == conditional.gsub(/\s+/, ' ').strip
    check(exact_conditional, 'D3_C4A_BASELINE_MODE_MISMATCH') if manifest.fetch('case') == D3_C4A_CASE
    matches = exact_conditional ? [manifest.fetch('case') == D3_C4A_CASE ? 'BASELINE' : 'UI_HELD'] :
      source.scan(/^\s*static final Mode MODE = Mode\.([A-Z0-9_]+);\s*$/).flatten
    check(declarations.length == 1, 'L1_HOST_MODE_MISMATCH')
    check(matches.length == 1 && (matches.first == 'L1_HOST_STOP') == selected, 'L1_HOST_MODE_MISMATCH')
    check((matches.first == 'COOLDOWN_HOST') == cooldown, 'COOLDOWN_HOST_MODE_MISMATCH')
    true
  end

  def freeze_launchers(manifest)
    check(manifest['launchMode'] == 'PREPARATION_ONLY_MDG_SNAPSHOT_REQUIRED', 'ALREADY_FROZEN')
    verify_jar!(manifest.fetch('jar'), manifest.fetch('case'), l1_host_stop: manifest.fetch('l1HostStop', false), cooldown_host: manifest.fetch('cooldownHost', false))
    java_home!(manifest.fetch('javaHome'), manifest.fetch('privateRoot'))
    repo = manifest.fetch('repo')
    base = File.join(repo, 'build/moddev')
    classes = File.join(repo, 'build/classes/java/p11OnlineHarness')
    resources = File.join(repo, 'build/resources/p11OnlineHarness')
    bundle = File.join(manifest.fetch('runtime'), 'launch-bundle')
    check(!File.exist?(bundle), 'BUNDLE_ALREADY_EXISTS')
    files = {}
    LAUNCH_STEMS.each do |stem|
      (LAUNCH_SUFFIXES.map { |suffix| stem + suffix } + ['run' + stem.sub(/^p/, 'P') + '.sh']).each do |name|
        file = nonsecret_path!(File.join(base, name), manifest.fetch('privateRoot'))
        check(File.size(file) <= 262_144, 'GENERATED_FILE_TOO_LARGE')
        files[name] = File.binread(file)
      end
    end
    local_roots = [classes, resources, manifest.fetch('jar')] + PLATFORM_JARS.map { |name| File.join(base, 'artifacts', name) }
    external = {}
    LAUNCH_STEMS.each do |stem|
      generated_shell!(files.fetch('run' + stem.sub(/^p/, 'P') + '.sh'), manifest, stem, base, classes, resources)
      generated_args!(files, manifest, stem, base, local_roots, external)
    end
    source_root = File.join(repo, 'src/p11OnlineHarness/java')
    verify_host_mode!(manifest, source_root)
    source_classes = tree_files!(source_root, manifest.fetch('privateRoot')).select { |name| name.end_with?('.java') }.map { |name| name.delete_suffix('.java') }
    class_files = tree_files!(classes, manifest.fetch('privateRoot'))
    check(!class_files.empty? && class_files.all? do |name|
      name.end_with?('.class') && source_classes.include?(name.delete_suffix('.class').split('$').first)
    end, 'UNEXPECTED_COMPANION_CLASS')
    check(class_files.map { |name| name.delete_suffix('.class').split('$').first }.uniq.sort == source_classes.sort, 'COMPANION_CLASS_MISSING')
    resource_files = tree_files!(resources, manifest.fetch('privateRoot'))
    check(resource_files == COMPANION_RESOURCES, 'UNEXPECTED_COMPANION_RESOURCE')
    L1_HOST_RESOURCES.each do |name, hash|
      check(Digest::SHA256.file(File.join(resources, name)).hexdigest == hash &&
            Digest::SHA256.file(File.join(repo, 'src/p11OnlineHarness/resources', name)).hexdigest == hash,
            'L1_HOST_RESOURCE_MISMATCH')
    end
    console = File.binread(File.join(resources, CONSOLE_XML))
    check(console == File.binread(File.join(repo, 'src/p11OnlineHarness/resources', CONSOLE_XML)), 'CONSOLE_RESOURCE_STALE')
    check(console.include?('<Console') && console.match?(/<Root\s+level="INFO"/) &&
          !console.match?(/<(?:(?:Rolling)?(?:RandomAccess)?File|Socket|JDBC|Http)\b|level="(?:DEBUG|TRACE)"/i), 'PRIVATE_CONSOLE_POLICY')
    source_inputs = {}
    %w[java resources].each do |kind|
      source = File.join(repo, 'src/p11OnlineHarness', kind)
      tree_files!(source, manifest.fetch('privateRoot')).each do |name|
        relative = File.join('src/p11OnlineHarness', kind, name)
        source_inputs[relative] = File.binread(File.join(source, name))
      end
    end
    source_inputs['build.gradle'] = File.binread(nonsecret_path!(File.join(repo, 'build.gradle'), manifest.fetch('privateRoot')))
    source_inputs['scripts/p11-online-runtime.rb'] = File.binread(__FILE__)
    head = candidate_head(repo)
    mappings = { classes => File.join(bundle, 'classes'), resources => File.join(bundle, 'resources') }
    LAUNCH_STEMS.each { |stem| LAUNCH_SUFFIXES.each { |suffix| mappings[File.join(base, stem + suffix)] = File.join(bundle, 'moddev', stem + suffix) } }
    PLATFORM_JARS.each { |name| mappings[File.join(base, 'artifacts', name)] = File.join(bundle, 'artifacts', name) }
    mkdir_new(bundle)
    %w[originals moddev classes resources artifacts tools source-inputs].each { |dir| mkdir_new(File.join(bundle, dir)) }
    originals = {}
    files.each do |name, raw|
      originals[name] = Digest::SHA256.hexdigest(raw)
      write_new(File.join(bundle, 'originals', name), raw, 0o400)
      next if name.end_with?('.sh')
      relocated = raw.gsub(Regexp.union(mappings.keys.sort_by { |key| -key.length })) { |value| mappings.fetch(value) }
      relocated = console if name.end_with?('Log4j2.xml')
      check(!relocated.include?(File.join(repo, 'build') + '/'), 'SHARED_BUILD_REFERENCE_REMAINS')
      write_new(File.join(bundle, 'moddev', name), relocated, 0o400)
    end
    { classes => class_files, resources => resource_files }.each do |origin, names|
      names.each do |name|
        target = File.join(mappings.fetch(origin), name)
        FileUtils.mkdir_p(File.dirname(target), mode: 0o700)
        write_new(target, File.binread(File.join(origin, name)), 0o400)
      end
    end
    build_launch_diagnostic!(manifest, bundle, File.join(bundle, 'classes'), class_files) if manifest['diagnoseClient']
    PLATFORM_JARS.each do |name|
      source = nonsecret_path!(File.join(base, 'artifacts', name), manifest.fetch('privateRoot'))
      target = mappings.fetch(source)
      source_hash = Digest::SHA256.file(source).hexdigest
      File.open(target, File::WRONLY | File::CREAT | File::EXCL, 0o400) do |output|
        File.open(source, 'rb') { |input| IO.copy_stream(input, output) }
      end
      check(Digest::SHA256.file(target).hexdigest == source_hash, 'SNAPSHOT_CHANGED_DURING_COPY')
    end
    write_new(File.join(bundle, 'tools/p11-online-runtime.rb'), File.binread(__FILE__), 0o400)
    source_inputs.each do |relative, raw|
      target = File.join(bundle, 'source-inputs', relative)
      FileUtils.mkdir_p(File.dirname(target), mode: 0o700)
      write_new(target, raw, 0o400)
      check(File.binread(File.join(repo, relative)) == raw, 'SOURCE_CHANGED_DURING_COPY')
    end
    check(files.all? { |name, raw| File.binread(File.join(base, name)) == raw }, 'GENERATED_INPUT_CHANGED_DURING_COPY')
    { classes => class_files, resources => resource_files }.each do |origin, names|
      check(tree_files!(origin, manifest.fetch('privateRoot')) == names && names.all? do |name|
        Digest::SHA256.file(File.join(origin, name)).hexdigest == Digest::SHA256.file(File.join(mappings.fetch(origin), name)).hexdigest
      end, 'COMPANION_CHANGED_DURING_COPY')
    end
    hashes = tree_files!(bundle, manifest.fetch('privateRoot')).to_h { |name| [name, Digest::SHA256.file(File.join(bundle, name)).hexdigest] }
    check(hashes.size.between?(25, MAX_FROZEN_BUNDLE_FILES), 'INVALID_BUNDLE_INVENTORY')
    frozen = manifest.merge('launchMode' => 'FROZEN_MDG', 'bundle' => bundle, 'bundleFiles' => hashes,
                            'externalDistributions' => external, 'generatedOriginals' => originals,
                            'exportToolSha256' => Digest::SHA256.file(__FILE__).hexdigest,
                            'candidateHead' => head,
                            'sourceInputs' => source_inputs.to_h { |relative, raw| [relative, Digest::SHA256.hexdigest(raw)] },
                            'consoleTransform' => { 'source' => CONSOLE_XML, 'sha256' => Digest::SHA256.hexdigest(console), 'roles' => LAUNCH_STEMS })
    write_new(File.join(manifest.fetch('runtime'), 'frozen-manifest.json'), JSON.pretty_generate(frozen) + "\n", 0o400)
    %w[a b].each { |client| write_new(File.join(manifest.fetch('runtime'), "run-client-#{client}.command"), client_launcher(frozen, client), 0o500) }
    directories = []
    Find.find(bundle) { |entry| directories << entry if File.directory?(entry) }
    directories.reverse_each { |entry| File.chmod(0o500, entry) }
    report = { 'status' => 'FROZEN_NOT_LAUNCHED_NOT_ACCEPTANCE', 'bundleFiles' => hashes.length,
               'companionClasses' => class_files.length, 'companionResources' => resource_files.length,
               'externalDistributionCount' => external.length, 'productSha256' => product_pin(manifest.fetch('case'), l1_host_stop: manifest.fetch('l1HostStop', false), cooldown_host: manifest.fetch('cooldownHost', false)),
               'consoleSha256' => frozen['consoleTransform']['sha256'],
               'launchDiagnostic' => manifest['diagnoseClient'] ? 'FIXED_STAGE_ONLY_NOT_AUTH_PROOF' : 'DISABLED' }
    write_new(File.join(manifest.fetch('evidence'), 'launcher-export.json'), JSON.pretty_generate(report) + "\n")
    report
  end

  def verify_frozen!(manifest)
    check(manifest['launchMode'] == 'FROZEN_MDG' && manifest['bundle'] == File.join(manifest.fetch('runtime'), 'launch-bundle'), 'FROZEN_LAUNCHERS_REQUIRED')
    bundle = manifest.fetch('bundle')
    hashes = manifest.fetch('bundleFiles')
    check(hashes.is_a?(Hash) && hashes.size.between?(25, MAX_FROZEN_BUNDLE_FILES), 'INVALID_BUNDLE_INVENTORY')
    check(tree_files!(bundle, manifest.fetch('privateRoot')) == hashes.keys.sort, 'BUNDLE_INVENTORY_CHANGED')
    hashes.each do |name, hash|
      check(Digest::SHA256.file(File.join(bundle, name)).hexdigest == hash, 'BUNDLE_CONTENT_CHANGED')
    end
    external = manifest.fetch('externalDistributions')
    check(external.is_a?(Hash) && external.size.between?(1, 256), 'INVALID_DISTRIBUTION_INVENTORY')
    external.each do |file, hash|
      actual = {}
      distribution_path!(file, manifest, [], actual)
      check(actual[file] == hash, 'DISTRIBUTION_CONTENT_CHANGED')
    end
    verify_jar!(manifest.fetch('jar'), manifest.fetch('case'), l1_host_stop: manifest.fetch('l1HostStop', false), cooldown_host: manifest.fetch('cooldownHost', false))
    java_home!(manifest.fetch('javaHome'), manifest.fetch('privateRoot'))
    verify_restart_input!(manifest)
    true
  end

  def restart_json!(file, private_root, bound = 65_536)
    file = nonsecret_path!(file, private_root)
    check(File.file?(file) && File.size(file).between?(1, bound), 'RESTART_RECEIPT_MISSING_OR_SIZE')
    raw = File.binread(file)
    value = JSON.parse(raw)
    check(value.is_a?(Hash), 'RESTART_RECEIPT_SHAPE')
    [value, raw]
  rescue JSON::ParserError
    raise Failure, 'RESTART_RECEIPT_SHAPE'
  end

  # The old process receipt is distinct from native stop/data proofs. No process/cache/log reads.
  def restart_source!(file, repo, runtime_root, evidence_root, private_root, next_run_id)
    check(File.basename(path(file)) == 'frozen-manifest.json', 'RESTART_FROZEN_MANIFEST_REQUIRED')
    old = load_manifest(file, runtime_root: runtime_root, private_root: private_root, restart_source: true)
    check(old.fetch('case') == 'l1-restart-write' && old.fetch('repo') == repo &&
          old.fetch('evidenceRoot') == evidence_root && old.fetch('runId') != next_run_id &&
          old.fetch('readyOnly') == false && old.fetch('eulaAccepted') == true &&
          !old.fetch('l1HostStop', false) && old.fetch('fixtureSha256') == FIXTURE_SHA &&
          old.fetch('sourceFixtureSha256') == FIXTURE_SHA, 'RESTART_SOURCE_COHORT_MISMATCH')
    verify_frozen!(old)
    [[File.join(old.fetch('runtime'), 'server.exit.json'), 'PROCESS_EXIT_NOT_ACCEPTANCE'],
     [File.join(old.fetch('evidence'), 'client-a-process-exit.json'), 'OWNED_CLIENT_PROCESS_EXIT_NOT_ACCEPTANCE'],
     [File.join(old.fetch('evidence'), 'client-b-process-exit.json'), 'OWNED_CLIENT_PROCESS_EXIT_NOT_ACCEPTANCE']].each do |receipt, status|
      value, = restart_json!(receipt, private_root, 4096)
      check(value['status'] == status && value['exitCode'] == 0 && value['signal'].nil? && value['ownedWallDeadlineExceeded'] != true &&
            value.key?('signal') && value['elapsedSeconds'].is_a?(Numeric) && value['elapsedSeconds'] >= 0 &&
            value['endedAtUtc'].is_a?(String), 'RESTART_ORIGINAL_PROCESS_NOT_NORMAL_TERMINAL')
    end
    { 'a' => [2, 2], 'b' => [1, 0] }.each do |role, (logins, sends)|
      value, = restart_json!(File.join(old.fetch('evidence'), "client-#{role}/result.json"), private_root)
      check(value['status'] == 'ORIGINAL_RESTART_WRITE_CLIENT_TERMINAL_NOT_RESTART_PROOF' &&
            value['logins'] == logins && value['originalP9Sends'] == sends && value['originalKeyCallbackClicks'] == sends &&
            value['closed'] == true && value['playerAbsent'] == true && value['levelAbsent'] == true &&
            value['currentListenerAbsent'] == true, 'RESTART_ORIGINAL_CLIENT_FLOW_NOT_TERMINAL')
    end
    terminal, = restart_json!(File.join(old.fetch('evidence'), 'server/data-terminal.json'), private_root)
    roots = terminal['allRootCounts']
    # Native stop can save/removeAll without each asynchronous disconnect reaching onDisconnect here.
    # Keep that generic classification unchanged; the named stopped-world receipt below is required separately.
    check(%w[ORIGINAL_NORMAL_STOP_AND_CLEAN_SOURCE TERMINAL_NOT_QUALIFIED].include?(terminal['status']) &&
          terminal['nativeStopNormal'] == true &&
          terminal['sourceFailures'] == 0 && terminal['dirtyUuids'] == 0 && roots.is_a?(Array) && roots.length == 5 &&
          roots.all? { |entry| entry.is_a?(Hash) && entry['count'] == 0 } &&
          roots.map { |entry| entry['kind'] }.sort == %w[COMMAND_CONTEXT NATIVE_CREDIT OPERATION TRANSITION WORK],
          'RESTART_ORIGINAL_DATA_NOT_CLEAN_TERMINAL')
    receipt = File.join(old.fetch('evidence'), 'server/l1-restart-expected.json')
    expected, bytes = restart_json!(receipt, private_root)
    universe = nonsecret_path!(File.join(old.fetch('runtime'), 'server'), private_root)
    world = nonsecret_path!(File.join(universe, 'p11-online-world'), private_root)
    check(File.directory?(world) && File.realpath(world) == world &&
          expected['status'] == 'ORIGINAL_STOPPED_WORLD_READY_FOR_NEW_SERVER_NOT_RESTART_PROOF' &&
          expected['writeRunId'] == old.fetch('runId') && expected['world'] == world &&
          expected['originalStopNormal'] == true && expected['openWorkClosedByStop'] == true,
          'RESTART_EXACT_STOPPED_WORLD_MISMATCH')
    config = expected['loadedConfiguration']
    config_path = nonsecret_path!(File.join(world, 'serverconfig/gramarye-server.toml'), private_root)
    check(config.is_a?(Hash) && config.keys.sort == %w[path sha256] && config['path'] == config_path &&
          config['sha256'] == FIXTURE_SHA && File.file?(config_path) && File.realpath(config_path) == config_path &&
          File.size(config_path).between?(1, 65_536) && Digest::SHA256.file(config_path).hexdigest == FIXTURE_SHA,
          'RESTART_ACTUAL_OLD_CONFIG_MISMATCH')
    %w[failure.json l1-restart-stop-failure.json].each do |leaf|
      check(!File.exist?(nonsecret_path!(File.join(old.fetch('evidence'), 'server', leaf), private_root)),
            'RESTART_SOURCE_RECORDED_FAILURE')
    end
    [{ 'manifest' => file, 'manifestSha256' => Digest::SHA256.file(file).hexdigest,
       'writeRunId' => old.fetch('runId'), 'universe' => universe, 'world' => world,
       'receipt' => receipt, 'receiptSha256' => Digest::SHA256.hexdigest(bytes),
       'loadedConfiguration' => config }, bytes]
  end

  def verify_restart_input!(manifest)
    unless %w[l1-restart-read cooldown-restart-read].include?(manifest.fetch('case'))
      check(!manifest.key?('restartSource'), 'UNEXPECTED_RESTART_SOURCE')
      return true
    end
    source = manifest.fetch('restartSource')
    check(source.is_a?(Hash), 'RESTART_SOURCE_REQUIRED')
    reader = manifest.fetch('case') == 'cooldown-restart-read' ? :cooldown_restart_source! : :restart_source!
    actual, bytes = public_send(reader, source.fetch('manifest'), manifest.fetch('repo'), manifest.fetch('runtimeRoot'),
                               manifest.fetch('evidenceRoot'), manifest.fetch('privateRoot'), manifest.fetch('runId'))
    check(actual == source, 'RESTART_SOURCE_IDENTITY_CHANGED')
    leaf = manifest.fetch('case') == 'cooldown-restart-read' ? 'cooldown-restart-input.json' : 'l1-restart-input.json'
    input = nonsecret_path!(File.join(manifest.fetch('evidence'), leaf), manifest.fetch('privateRoot'))
    check(File.file?(input) && File.size(input) == bytes.bytesize && File.binread(input) == bytes,
          'RESTART_FIXED_INPUT_CHANGED')
    true
  end

  # A distinct current-product stopped-world binding. Historical L1 acceptance is unchanged.
  # Only fixed public receipts are read; native storage is read by the new server's own probe.
  def cooldown_restart_source!(file, repo, runtime_root, evidence_root, private_root, next_run_id)
    check(File.basename(path(file)) == 'frozen-manifest.json', 'RESTART_FROZEN_MANIFEST_REQUIRED')
    old = load_manifest(file, runtime_root: runtime_root, private_root: private_root,
                        restart_source: 'cooldown-restart-write')
    check(old.fetch('case') == 'cooldown-restart-write' && old.fetch('repo') == repo &&
          old.fetch('evidenceRoot') == evidence_root && old.fetch('runId') != next_run_id &&
          old.fetch('readyOnly') == false && old.fetch('eulaAccepted') == true &&
          !old.fetch('l1HostStop', false) && old.fetch('fixtureSha256') == FIXTURE_SHA &&
          old.fetch('sourceFixtureSha256') == FIXTURE_SHA, 'RESTART_SOURCE_COHORT_MISMATCH')
    verify_frozen!(old)
    [[File.join(old.fetch('runtime'), 'server.exit.json'), 'PROCESS_EXIT_NOT_ACCEPTANCE'],
     [File.join(old.fetch('evidence'), 'client-a-process-exit.json'), 'OWNED_CLIENT_PROCESS_EXIT_NOT_ACCEPTANCE'],
     [File.join(old.fetch('evidence'), 'client-b-process-exit.json'), 'OWNED_CLIENT_PROCESS_EXIT_NOT_ACCEPTANCE']].each do |receipt, status|
      value, = restart_json!(receipt, private_root, 4096)
      check(value['status'] == status && value['exitCode'] == 0 && value['signal'].nil? && value['ownedWallDeadlineExceeded'] != true &&
            value.key?('signal') && value['elapsedSeconds'].is_a?(Numeric) && value['elapsedSeconds'] >= 0 &&
            value['endedAtUtc'].is_a?(String), 'RESTART_ORIGINAL_PROCESS_NOT_NORMAL_TERMINAL')
    end
    { 'a' => 1, 'b' => 0 }.each do |role, sends|
      value, = restart_json!(File.join(old.fetch('evidence'), "client-#{role}/result.json"), private_root)
      check(value['status'] == 'ORIGINAL_SERVER_STOP_CLIENT_TERMINAL' && value['logins'] == 1 &&
            value['originalP9Sends'] == sends && value['originalKeyCallbackClicks'] == sends &&
            value['closed'] == true && value['playerAbsent'] == true && value['levelAbsent'] == true &&
            value['currentListenerAbsent'] == true, 'RESTART_ORIGINAL_CLIENT_FLOW_NOT_TERMINAL')
    end
    terminal, = restart_json!(File.join(old.fetch('evidence'), 'server/data-terminal.json'), private_root)
    roots = terminal['allRootCounts']
    check(terminal['status'] == 'NORMAL_NATIVE_STOP_AND_ROOTS_ZERO' && terminal['nativeStopNormal'] == true &&
          terminal['sourceFailures'] == 0 && terminal['dirtyUuids'] == 0 && roots.is_a?(Array) && roots.length == 5 &&
          roots.all? { |entry| entry.is_a?(Hash) && entry['count'] == 0 } &&
          roots.map { |entry| entry['kind'] }.sort == %w[COMMAND_CONTEXT NATIVE_CREDIT OPERATION TRANSITION WORK],
          'RESTART_ORIGINAL_DATA_NOT_CLEAN_TERMINAL')
    receipt = File.join(old.fetch('evidence'), 'server/cooldown-restart-expected.json')
    expected, bytes = restart_json!(receipt, private_root)
    universe = nonsecret_path!(File.join(old.fetch('runtime'), 'server'), private_root)
    world = nonsecret_path!(File.join(universe, 'p11-online-world'), private_root)
    keys = %w[schema status case writeRunId productionJarSha256 world publicMinecraftUuid skillId revision
              cooldownTicks obligation definitionSha256 fileSha256 loadedConfiguration stoppedGameTime
              originalStopNormal openWorkClosedByStop].sort
    check(expected.keys.sort == keys && expected['schema'] == 1 &&
          expected['status'] == 'ORIGINAL_COOLDOWN_STOPPED_WORLD_NOT_RESTART_PROOF' &&
          expected['case'] == 'cooldown-restart-write' && expected['writeRunId'] == old.fetch('runId') &&
          expected['productionJarSha256'] == old.fetch('jarSha256') &&
          File.directory?(world) && File.realpath(world) == world && expected['world'] == world &&
          expected['originalStopNormal'] == true && expected['openWorkClosedByStop'] == true,
          'RESTART_EXACT_STOPPED_WORLD_MISMATCH')
    uuid = /\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z/
    sha = /\A[0-9a-f]{64}\z/
    obligation = expected['obligation']
    check(%w[publicMinecraftUuid skillId].all? { |key| expected[key].is_a?(String) && expected[key].match?(uuid) } &&
          expected['revision'].is_a?(Integer) && expected['revision'].between?(0, 2_147_483_647) &&
          expected['cooldownTicks'] == 600 && expected['definitionSha256'].is_a?(String) && expected['definitionSha256'].match?(sha) &&
          obligation.is_a?(Hash) && obligation.keys.sort == %w[acceptedAt attemptId expiresAt releaseNotAfter releasedAt] &&
          obligation['attemptId'].is_a?(String) && obligation['attemptId'].match?(uuid) &&
          %w[acceptedAt releaseNotAfter releasedAt expiresAt].all? { |key| obligation[key].is_a?(Integer) && obligation[key].between?(0, 9_223_372_036_854_775_807) } &&
          obligation['releaseNotAfter'] == obligation['acceptedAt'] + 101 &&
          obligation['releasedAt'].between?(obligation['acceptedAt'], obligation['releaseNotAfter']) &&
          obligation['expiresAt'] == obligation['releasedAt'] + 600 &&
          expected['stoppedGameTime'].is_a?(Integer) && expected['stoppedGameTime'].between?(obligation['releasedAt'], obligation['expiresAt'] - 1),
          'COOLDOWN_RESTART_OBLIGATION_MISMATCH')
    files = expected['fileSha256']
    check(files.is_a?(Hash) && files.keys.sort == ["data/gramarye_skill_definitions.dat", "playerdata/#{expected['publicMinecraftUuid']}.dat"].sort &&
          files.values.all? { |hash| hash.is_a?(String) && hash.match?(sha) }, 'COOLDOWN_RESTART_STORAGE_IDENTITY_MISMATCH')
    config = expected['loadedConfiguration']
    config_path = nonsecret_path!(File.join(world, 'serverconfig/gramarye-server.toml'), private_root)
    check(config.is_a?(Hash) && config.keys.sort == %w[path sha256] && config['path'] == config_path &&
          config['sha256'] == FIXTURE_SHA && File.file?(config_path) && File.realpath(config_path) == config_path &&
          File.size(config_path).between?(1, 65_536) && Digest::SHA256.file(config_path).hexdigest == FIXTURE_SHA,
          'RESTART_ACTUAL_OLD_CONFIG_MISMATCH')
    %w[failure.json cooldown-restart-stop-failure.json].each do |leaf|
      check(!File.exist?(nonsecret_path!(File.join(old.fetch('evidence'), 'server', leaf), private_root)),
            'RESTART_SOURCE_RECORDED_FAILURE')
    end
    [{ 'manifest' => file, 'manifestSha256' => Digest::SHA256.file(file).hexdigest,
       'writeRunId' => old.fetch('runId'), 'universe' => universe, 'world' => world,
       'receipt' => receipt, 'receiptSha256' => Digest::SHA256.hexdigest(bytes),
       'loadedConfiguration' => config }, bytes]
  end

  def prepare(options)
    repo, runtime_root, evidence_root, private_root = roots!(options)
    case_name = options.fetch(:case)
    check(CASES.include?(case_name), 'INVALID_CASE')
    host_stop = host_selection!(case_name, options.fetch(:l1_host_stop, false))
    cooldown_host = cooldown_host_selection!(case_name, options.fetch(:cooldown_host, false), l1_host_stop: host_stop)
    pin = product_pin(case_name, l1_host_stop: host_stop, cooldown_host: cooldown_host)
    run_id = options.fetch(:run_id)
    check(run_id.is_a?(String) && run_id.match?(/\A[A-Za-z0-9_-]{8,64}\z/), 'INVALID_RUN_ID')
    check(%w[l1-restart-read cooldown-restart-read].include?(case_name) == !options[:restart_from].nil?, 'RESTART_SOURCE_ONLY_EXACT_READ_CASE')
    restart, restart_bytes = if case_name == 'l1-restart-read'
                              restart_source!(options.fetch(:restart_from), repo, runtime_root, evidence_root, private_root, run_id)
                            elsif case_name == 'cooldown-restart-read'
                              cooldown_restart_source!(options.fetch(:restart_from), repo, runtime_root, evidence_root, private_root, run_id)
                            end
    check([true, false].include?(options.fetch(:ready_only)), 'INVALID_READY_ONLY')
    check([true, false].include?(options.fetch(:diagnose_client, false)), 'INVALID_DIAGNOSTIC_MODE')
    port = checked_port(options.fetch(:port))
    java_home = java_home!(options.fetch(:java_home), private_root)
    fixture, source_fixture_sha = startup_fixture!(options.fetch(:fixture), private_root, case_name, with_source_hash: true, l1_host_stop: host_stop, cooldown_host: cooldown_host)
    jar = freeze_jar!(options.fetch(:jar), runtime_root, private_root, case_name, l1_host_stop: host_stop, cooldown_host: cooldown_host)
    reward_sources = reward_fixture_sources!(repo, private_root, case_name)
    runtime = File.join(runtime_root, run_id)
    evidence = File.join(evidence_root, run_id)
    check(!File.exist?(runtime) && !File.exist?(evidence), 'RUN_ALREADY_EXISTS')
    mkdir_new(runtime)
    mkdir_new(evidence)
    roles = roles_for(case_name)
    (roles + ['server']).uniq.each { |role| mkdir_new(File.join(runtime, role)) }
    if options.fetch(:diagnose_client, false)
      roles.reject { |role| role == 'server' }.each { |role| mkdir_new(File.join(evidence, "client-#{role}-launch-diagnostic")) }
    end
    server = File.join(runtime, 'server')
    config = File.join(server, 'defaultconfigs')
    mkdir_new(config)
    write_new(File.join(config, 'gramarye-server.toml'), fixture)
    world = restart ? restart.fetch('world') : File.join(server, 'p11-online-world')
    unless restart
      mkdir_new(world)
      mkdir_new(File.join(world, 'serverconfig'))
      write_new(File.join(world, 'serverconfig', 'gramarye-server.toml'), fixture)
    end
    write_new(File.join(server, 'server.properties'), properties(port))
    write_new(File.join(server, 'eula.txt'), "eula=#{options.fetch(:accept_eula)}\n")
    pack = File.join(world, 'datapacks', 'p11-online-engineering')
    data = File.join(pack, 'data', 'gramarye_p11_engineering', 'advancement')
    unless restart
      FileUtils.mkdir_p(data, mode: 0o700)
      write_new(File.join(pack, 'pack.mcmeta'), JSON.pretty_generate(
        pack: { pack_format: 48, description: 'Excluded P11 online engineering inputs' }) + "\n")
      write_new(File.join(data, 'delivery_root.json'), JSON.pretty_generate(delivery_root) + "\n")
    end
    unless reward_sources.empty?
      reward_sources.each do |leaf, bytes|
        target = File.join(pack, 'data/gramarye_p11_engineering', leaf)
        unless restart
          FileUtils.mkdir_p(File.dirname(target), mode: 0o700)
          write_new(target, bytes)
        end
      end
      write_new(File.join(evidence, 'reward-fixture.json'), JSON.pretty_generate(
        status: 'EXACT_PUBLIC_NATIVE_REWARD_INPUTS_NOT_RUNTIME_PROOF', sourceHashes: reward_fixture_hashes(case_name),
        deliveryRootSha256: Digest::SHA256.file(File.join(data, 'delivery_root.json')).hexdigest) + "\n")
    end
    if host_case?(case_name)
      # Minecraft's original createFreshLevel owns the host world; never pre-create it.
      host_config = File.join(runtime, 'host', 'defaultconfigs')
      mkdir_new(host_config)
      write_new(File.join(host_config, 'gramarye-server.toml'), fixture)
    end
    File.mkfifo(File.join(runtime, 'server.stdin'), 0o600)
    manifest = {
      'schema' => 1, 'repo' => repo, 'runtimeRoot' => runtime_root, 'evidenceRoot' => evidence_root,
      'privateRoot' => private_root, 'runtime' => runtime, 'evidence' => evidence,
      'case' => case_name, 'runId' => run_id, 'port' => port, 'roles' => roles,
      'readyOnly' => options.fetch(:ready_only), 'eulaAccepted' => options.fetch(:accept_eula),
      'diagnoseClient' => options.fetch(:diagnose_client, false),
      'l1HostStop' => host_stop,
      'cooldownHost' => cooldown_host,
      'javaHome' => java_home, 'launchMode' => 'PREPARATION_ONLY_MDG_SNAPSHOT_REQUIRED',
      'jar' => jar, 'jarSha256' => pin, 'fixtureSha256' => Digest::SHA256.hexdigest(fixture),
      'sourceFixtureSha256' => source_fixture_sha, 'toolSha256' => Digest::SHA256.file(__FILE__).hexdigest,
      'portAvailability' => 'CHECKED_NOT_RESERVED_RECHECK_AT_LAUNCH',
      'authenticationAcceptance' => 'NOT_RUN_NOT_PROVEN',
      'evidencePolicy' => 'HARNESS_STRUCTURED_FILES_ONLY_NO_DIRECTORY_ARCHIVE'
    }
    manifest['rewardFixtureHashes'] = reward_fixture_hashes(case_name) unless reward_sources.empty?
    if restart
      manifest['restartSource'] = restart
      leaf = case_name == 'cooldown-restart-read' ? 'cooldown-restart-input.json' : 'l1-restart-input.json'
      write_new(File.join(evidence, leaf), restart_bytes, 0o400)
    end
    verify_restart_input!(manifest)
    verify_reward_fixture!(manifest)
    write_new(File.join(runtime, 'manifest.json'), JSON.pretty_generate(manifest) + "\n")
    # The manifest contains paths/tool identities, never authentication data.
    write_new(File.join(evidence, 'prepare.json'), JSON.pretty_generate(manifest) + "\n")
    %w[a b].each do |client|
      write_new(File.join(runtime, "launch-client-#{client}.command"), client_launcher(manifest, client), 0o700)
    end
    cues = "PREPARED_NOT_LAUNCHED; authentication NOT_PROVEN.\n" \
      "Topology: #{host_case?(case_name) ? 'Client A creates/publishes the original integrated host; client B waits for its ready receipt.' : 'Launch the original dedicated server before clients.'}\n" \
      "Clients: open only the local launch-client-a.command / launch-client-b.command files in Terminal.\n" \
      "Launch is disabled until generated MDG launch files are frozen for this exact cohort.\n" \
      "Only the holder enters Microsoft credentials and device codes in their browser; do not send them here.\n" \
      "Do not tee/redirect client consoles or archive runtime/private directories.\n" \
      "Native logs/crash reports and account stores are not evidence inputs.\n" \
      "Stop: ruby scripts/p11-online-runtime.rb stop --manifest #{File.join(runtime, 'manifest.json')}\n" \
      "readyOnly=#{manifest['readyOnly']}; eulaAccepted=#{manifest['eulaAccepted']}.\n"
    write_new(File.join(evidence, 'launch-cues.txt'), cues)
    verify_reward_fixture!(manifest)
    manifest
  end

  def load_manifest(file, runtime_root: RUNTIME_ROOT, private_root: PRIVATE_ROOT, restart_source: false)
    file = path(file)
    # Validate its exact non-secret location BEFORE reading even one byte of caller-selected JSON.
    private_root = path(private_root)
    runtime_root = nonsecret_path!(runtime_root, private_root)
    nonsecret_path!(file, private_root)
    run_id = File.basename(File.dirname(file))
    leaf = File.basename(file)
    check(%w[manifest.json frozen-manifest.json].include?(leaf) && run_id.match?(/\A[A-Za-z0-9_-]{8,64}\z/) &&
          file == File.join(runtime_root, run_id, leaf), 'INVALID_MANIFEST_LOCATION')
    check(File.size(file) <= 262_144, 'MANIFEST_TOO_LARGE')
    manifest = JSON.parse(File.binread(file))
    check(manifest.is_a?(Hash) && manifest['schema'] == 1, 'INVALID_MANIFEST')
    expected_write = restart_source == true ? 'l1-restart-write' : restart_source
    check(!restart_source || %w[l1-restart-write cooldown-restart-write].include?(expected_write) && manifest['case'] == expected_write,
          'RESTART_SOURCE_COHORT_MISMATCH')
    check(manifest['runtimeRoot'] == runtime_root && manifest['privateRoot'] == private_root, 'MANIFEST_ROOT_MISMATCH')
    roots!(repo: manifest.fetch('repo'), runtime_root: manifest.fetch('runtimeRoot'),
           evidence_root: manifest.fetch('evidenceRoot'), private_root: manifest.fetch('privateRoot'))
    check(CASES.include?(manifest['case']) && manifest['runId'].match?(/\A[A-Za-z0-9_-]{8,64}\z/), 'INVALID_MANIFEST')
    check(manifest['runtime'] == File.join(manifest['runtimeRoot'], manifest['runId']) &&
          manifest['evidence'] == File.join(manifest['evidenceRoot'], manifest['runId']) &&
          file == File.join(manifest['runtime'], leaf), 'MANIFEST_PATH_MISMATCH')
    check(manifest['launchMode'] == (leaf == 'manifest.json' ? 'PREPARATION_ONLY_MDG_SNAPSHOT_REQUIRED' : 'FROZEN_MDG'), 'MANIFEST_MODE_MISMATCH')
    check([true, false].include?(manifest['readyOnly']) && [true, false].include?(manifest['eulaAccepted']), 'INVALID_MANIFEST')
    check([true, false, nil].include?(manifest['diagnoseClient']), 'INVALID_DIAGNOSTIC_MODE')
    cooldown_host_selection!(manifest.fetch('case'), manifest.fetch('cooldownHost', false), l1_host_stop: manifest.fetch('l1HostStop', false))
    check(manifest['roles'] == roles_for(manifest['case']) &&
          manifest['port'].is_a?(Integer) && (1024..65_535).cover?(manifest['port']), 'INVALID_MANIFEST')
    nonsecret_path!(manifest['runtime'], manifest['privateRoot'])
    nonsecret_path!(manifest['evidence'], manifest['privateRoot'])
    check(manifest['jar'] == frozen_jar_path(manifest['runtimeRoot'], manifest['case'], l1_host_stop: manifest.fetch('l1HostStop', false), cooldown_host: manifest.fetch('cooldownHost', false)) &&
          manifest['jarSha256'] == product_pin(manifest['case'], l1_host_stop: manifest.fetch('l1HostStop', false), cooldown_host: manifest.fetch('cooldownHost', false)), 'MANIFEST_JAR_MISMATCH')
    verify_restart_input!(manifest)
    verify_reward_fixture!(manifest)
    manifest
  end

  def fifo!(manifest)
    fifo = nonsecret_path!(File.join(manifest.fetch('runtime'), 'server.stdin'), manifest.fetch('privateRoot'))
    stat = File.lstat(fifo)
    check(stat.pipe? && stat.uid == Process.uid && (stat.mode & 0o077).zero?, 'INVALID_OWNED_FIFO')
    fifo
  end

  # This launcher is the child's actual parent. Until waitpid reaps that exact child,
  # its PID cannot be reused. No process scan, credentials, or guessed external PID.
  def wait_owned_process(pid, case_name, cooldown_host: false)
    cooldown_host_selection!(case_name, cooldown_host)
    unless COOLDOWN_CASES.include?(case_name) || COOLDOWN_L1_CASES.include?(case_name) || D3_CASES.include?(case_name) || case_name == D3_C4A_CASE || cooldown_host
      return [Process.waitpid2(pid).last, false]
    end
    deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 900
    loop do
      terminal = Process.waitpid2(pid, Process::WNOHANG)
      return [terminal.last, false] if terminal
      break if Process.clock_gettime(Process::CLOCK_MONOTONIC) >= deadline
      sleep 0.25
    end
    begin
      Process.kill('TERM', pid)
    rescue Errno::ESRCH
      return [Process.waitpid2(pid).last, true]
    end
    deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 20
    loop do
      terminal = Process.waitpid2(pid, Process::WNOHANG)
      return [terminal.last, true] if terminal
      break if Process.clock_gettime(Process::CLOCK_MONOTONIC) >= deadline
      sleep 0.25
    end
    begin
      Process.kill('KILL', pid)
    rescue Errno::ESRCH
      return [Process.waitpid2(pid).last, true]
    end
    [Process.waitpid2(pid).last, true]
  end

  def launch_server(manifest)
    check(!host_case?(manifest.fetch('case')), 'INTEGRATED_HOST_REQUIRES_CLIENT_A')
    verify_frozen!(manifest)
    check(manifest.fetch('eulaAccepted'), 'EULA_ACK_REQUIRED')
    verify_jar!(nonsecret_path!(manifest.fetch('jar'), manifest.fetch('privateRoot')), manifest.fetch('case'), l1_host_stop: manifest.fetch('l1HostStop', false), cooldown_host: manifest.fetch('cooldownHost', false))
    checked_port(manifest.fetch('port'))
    runtime = manifest.fetch('runtime')
    write_new(File.join(runtime, 'server.launch-reserved'), "OWNED_SERVER_LAUNCH\n")
    fifo = File.open(fifo!(manifest), File::RDWR)
    log = File.open(File.join(runtime, 'server-console.private.log'), File::WRONLY | File::CREAT | File::EXCL, 0o600)
    environment = { 'JAVA_TOOL_OPTIONS' => nil, 'JDK_JAVA_OPTIONS' => nil, '_JAVA_OPTIONS' => nil,
                    'JAVA_HOME' => java_home!(manifest.fetch('javaHome'), manifest.fetch('privateRoot')) }
    argv = launch_argv(manifest, 'p11OnlineServer', File.join(manifest.fetch('bundle'), 'moddev'),
                       File.join(manifest.fetch('bundle'), 'classes'), File.join(manifest.fetch('bundle'), 'resources'))
    started = Time.now.utc.iso8601(6)
    clock = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    pid = Process.spawn(environment, *argv, chdir: File.join(runtime, 'server'),
                        in: fifo, out: log, err: log, umask: 0o077)
    write_new(File.join(runtime, 'server.started.json'), JSON.generate('pid' => pid, 'status' => 'PROCESS_STARTED_NOT_AUTH_PROOF',
                                                                     'startedAtUtc' => started) + "\n")
    terminal, timed_out = wait_owned_process(pid, manifest.fetch('case'), cooldown_host: manifest.fetch('cooldownHost', false))
    result = { 'status' => 'PROCESS_EXIT_NOT_ACCEPTANCE', 'exitCode' => terminal.exitstatus,
               'signal' => terminal.termsig, 'authenticationAcceptance' => 'HARNESS_EVIDENCE_REQUIRED' }
    result['ownedWallDeadlineExceeded'] = true if timed_out
    result.merge!('startedAtUtc' => started, 'endedAtUtc' => Time.now.utc.iso8601(6),
                  'elapsedSeconds' => Process.clock_gettime(Process::CLOCK_MONOTONIC) - clock)
    write_new(File.join(runtime, 'server.exit.json'), JSON.generate(result) + "\n")
    result
  ensure
    fifo.close if fifo && !fifo.closed?
    log.close if log && !log.closed?
  end

  # Exact startup receipt only: not authentication evidence and not a claim of ongoing process liveness.
  def server_ready_for_client!(manifest)
    terminal = [File.join(manifest.fetch('runtime'), 'server.exit.json'),
                File.join(manifest.fetch('evidence'), 'server/stopped.json')].any? do |candidate|
      File.exist?(nonsecret_path!(candidate, manifest.fetch('privateRoot')))
    end
    check(!terminal, 'SERVER_ALREADY_TERMINAL')
    file = nonsecret_path!(File.join(manifest.fetch('evidence'), 'server/ready.json'), manifest.fetch('privateRoot'))
    check(File.file?(file), 'SERVER_NOT_READY')
    check(File.size(file) <= 4096, 'SERVER_READY_INVALID')
    ready = JSON.parse(File.binread(file))
    expected = {
      'status' => host_case?(manifest.fetch('case')) ? 'ONLINE_INTEGRATED_PUBLISHED_NO_PARTNER_AUTH_CLAIM' : 'ONLINE_DEDICATED_READY_NO_AUTH_CLAIM', 'case' => manifest.fetch('case'),
      'runId' => manifest.fetch('runId'), 'productionJarSha256' => manifest.fetch('jarSha256'),
      'onlineMode' => true, 'integrated' => host_case?(manifest.fetch('case')),
      'expectedPlayers' => manifest.fetch('case') == 'single' ? 1 : 2,
      'configurationSha256' => manifest.fetch('fixtureSha256')
    }
    check(ready.is_a?(Hash) && ready['expectedPlayers'].is_a?(Integer) && ready == expected, 'SERVER_READY_MISMATCH')
    true
  rescue JSON::ParserError
    raise Failure, 'SERVER_READY_INVALID'
  end

  def launch_client(manifest, client)
    check(%w[a b].include?(client), 'INVALID_CLIENT')
    check(!manifest.fetch('readyOnly'), 'READY_ONLY_DOES_NOT_LAUNCH_AUTHENTICATION')
    check(client != 'b' || manifest.fetch('case') != 'single', 'CLIENT_B_NOT_APPLICABLE_TO_SINGLE')
    check(STDIN.tty? && STDOUT.tty? && STDERR.tty?, 'PRIVATE_TERMINAL_REQUIRED')
    verify_frozen!(manifest)
    # Only the exact host-A case bootstraps its own native integrated server.
    # Partner B and every dedicated client still require the matching server receipt.
    server_ready_for_client!(manifest) unless host_case?(manifest.fetch('case')) && client == 'a'
    runtime = manifest.fetch('runtime')
    write_new(File.join(runtime, "client-#{client}.launch-reserved"), "OWNED_CLIENT_LAUNCH\n")
    stem = client == 'a' ? 'p11OnlineClientA' : 'p11OnlineClientB'
    argv = launch_argv(manifest, stem, File.join(manifest.fetch('bundle'), 'moddev'),
                       File.join(manifest.fetch('bundle'), 'classes'), File.join(manifest.fetch('bundle'), 'resources'))
    environment = { 'JAVA_TOOL_OPTIONS' => nil, 'JDK_JAVA_OPTIONS' => nil, '_JAVA_OPTIONS' => nil,
                    'JAVA_HOME' => manifest.fetch('javaHome') }
    # Inherit the local terminal: no pipe, tee, redirect, output reader or process introspection.
    started = Time.now.utc.iso8601(6)
    clock = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    pid = Process.spawn(environment, *argv, chdir: File.join(runtime, launch_role(manifest, stem)), umask: 0o077)
    write_new(File.join(runtime, "client-#{client}.started.json"), JSON.generate('pid' => pid, 'status' => 'PROCESS_STARTED_NOT_AUTH_PROOF',
                                                                               'startedAtUtc' => started) + "\n")
    terminal, timed_out = wait_owned_process(pid, manifest.fetch('case'), cooldown_host: manifest.fetch('cooldownHost', false))
    result = { 'status' => 'OWNED_CLIENT_PROCESS_EXIT_NOT_ACCEPTANCE', 'exitCode' => terminal.exitstatus, 'signal' => terminal.termsig }
    result['ownedWallDeadlineExceeded'] = true if timed_out
    result.merge!('startedAtUtc' => started, 'endedAtUtc' => Time.now.utc.iso8601(6),
                  'elapsedSeconds' => Process.clock_gettime(Process::CLOCK_MONOTONIC) - clock)
    write_new(File.join(manifest.fetch('evidence'), "client-#{client}-process-exit.json"), JSON.generate(result) + "\n")
    result
  end

  def prepare_launchers(manifest)
    check(manifest['launchMode'] == 'PREPARATION_ONLY_MDG_SNAPSHOT_REQUIRED', 'ALREADY_FROZEN')
    verify_jar!(nonsecret_path!(manifest.fetch('jar'), manifest.fetch('privateRoot')), manifest.fetch('case'), l1_host_stop: manifest.fetch('l1HostStop', false), cooldown_host: manifest.fetch('cooldownHost', false))
    environment = { 'JAVA_TOOL_OPTIONS' => nil, 'JDK_JAVA_OPTIONS' => nil, '_JAVA_OPTIONS' => nil,
                    'JAVA_HOME' => java_home!(manifest.fetch('javaHome'), manifest.fetch('privateRoot')) }
    tasks = %w[createP11OnlineServerLaunchScript createP11OnlineClientALaunchScript createP11OnlineClientBLaunchScript]
    argv = gradle_argv(manifest, tasks.first) + tasks.drop(1)
    log = File.open(File.join(manifest.fetch('runtime'), 'launcher-preparation.private.log'),
                    File::WRONLY | File::CREAT | File::EXCL, 0o600)
    pid = Process.spawn(environment, *argv, chdir: manifest.fetch('repo'), out: log, err: log, umask: 0o077)
    Process.wait(pid)
    check($?.success?, 'LAUNCHER_PREPARATION_FAILED')
    { 'status' => 'MDG_GENERATED_NOT_FROZEN_NOT_LAUNCHED', 'authenticationAcceptance' => 'NOT_RUN_NOT_PROVEN' }
  ensure
    log.close if log && !log.closed?
  end

  def stop(manifest)
    check(!host_case?(manifest.fetch('case')), 'INTEGRATED_HOST_STOPS_WITH_CLIENT')
    descriptor = IO.sysopen(fifo!(manifest), File::WRONLY | File::NONBLOCK)
    IO.open(descriptor, 'w') { |io| io.write("stop\n"); io.flush }
    { 'status' => 'STOP_COMMAND_WRITTEN_NOT_TERMINAL_PROOF' }
  rescue Errno::ENXIO
    raise Failure, 'NO_OWNED_FIFO_READER'
  end

  def status(manifest)
    files = OWN_FILES + SERVER_FILES.map { |name| "server/#{name}" } +
      manifest.fetch('roles').reject { |role| role == 'server' }.flat_map do |role|
      CLIENT_FILES.map { |name| "client-#{role}/#{name}" } +
        %W[#{role}-auth-1.json #{role}-auth-2.json #{role}-first-native.json #{role}-reconnect-native.json]
          .map { |name| "server/#{name}" }
    end
    if D3_CASES.include?(manifest.fetch('case'))
      files += %w[d3-held.json d3-late-release.json d3-before-death.json d3-result.json d3-cost.json data-terminal.json
                  host-1-held.json host-generation-late-task.json].map { |name| "server/#{name}" }
      files += %w[a b].flat_map do |role|
        %w[starter.json cast-1.json cast-2.json ack-1.json ack-2.json ack-3.json ack-4.json replayed.json left.json respawn.json]
          .map { |name| "client-#{role}/#{name}" }
      end
      if manifest.fetch('case') == 'd3-host-lan'
        files += [1, 2].flat_map do |ordinal|
          %W[host-#{ordinal}-ready.json host-#{ordinal}-host-login.json host-#{ordinal}-b-login.json
             host-#{ordinal}-data-terminal.json host-#{ordinal}-stopped.json host-#{ordinal}-cost.json]
            .map { |name| "server/#{name}" } +
            %W[client-host/host-#{ordinal}-terminal.json client-b/host-#{ordinal}-terminal.json
               client-b/host-#{ordinal}-starter.json]
        end
      end
    end
    if COOLDOWN_CASES.include?(manifest.fetch('case'))
      files += %w[cooldown-result.json data-terminal.json formal-submission.json arm-1.json arm-2.json
                  saved-active.json active-refusal.json reconnect-active.json cooldown-restart-before-stop.json
                  cooldown-restart-expected.json cooldown-restart-before-login.json cooldown-restart-loaded.json
                  cooldown-restart-stop-failure.json cooldown-fault-local.json cooldown-fault-result.json cooldown-cost.json
                  cooldown-clone-before-death.json cooldown-clone-before-end.json cooldown-clone-death.json
                  cooldown-clone-end.json cooldown-clone-result.json cooldown-durability-pending.json
                  cooldown-durability-failed.json cooldown-durability-restored.json cooldown-durability-result.json
                  cooldown-durability-cleanup-failure.json]
        .map { |name| "server/#{name}" }
      files += %w[a b].flat_map { |role| %w[hud-ready.json hud-active.json hud-result.json cast-1.json cast-2.json cast-3.json
          cooldown-mirror-1.json cooldown-mirror-2.json cooldown-mirror-3.json close.json focus-request.json].map { |name| "client-#{role}/#{name}" } }
      files << 'cooldown-restart-input.json' if manifest.fetch('case') == 'cooldown-restart-read'
      if manifest.fetch('case') == 'cooldown-clone'
        files += %w[cooldown-clone-death-client.json cooldown-clone-end-client.json].map { |name| "client-a/#{name}" }
      end
      if %w[cooldown-save-active cooldown-save-clear].include?(manifest.fetch('case'))
        files += %w[cooldown-durability-mirror.json cooldown-durability-hud.json].map { |name| "client-a/#{name}" }
      end
      files += %w[client-a/cooldown-cost.json client-b/cooldown-cost.json]
      files += %w[input-focus-1.json input-focus-2.json input-focus-3.json
                  input-stall-1.json input-stall-2.json input-stall-3.json].map { |name| "client-a/#{name}" }
      if manifest.fetch('case') == 'cooldown-d600'
        files += %w[server/active-refusal-after-reconnect.json client-a/cast-4.json
                    client-a/input-focus-4.json client-a/input-stall-4.json]
      end
      if manifest.fetch('case') == 'cooldown-dual'
        files += %w[formal-submission-a.json formal-submission-b.json cooldown-dual-a.json
                    cooldown-dual-b.json cooldown-dual-result.json].map { |name| "server/#{name}" }
        files += %w[input-focus-1.json input-focus-2.json input-stall-1.json input-stall-2.json]
          .map { |name| "client-b/#{name}" }
      end
    end
    if COOLDOWN_L1_CASES.include?(manifest.fetch('case'))
      files += %w[cooldown-l1-formal-submission.json cooldown-l1-episode-1.json
                  cooldown-l1-episode-2.json cooldown-l1-episode-3.json
                  cooldown-l1-arena-restore-1.json cooldown-l1-arena-restore-2.json].map { |name| "server/#{name}" }
      files += %w[cooldown-l1-input-1.json cooldown-l1-input-2.json cooldown-l1-input-3.json
                  cooldown-l1-input-stall-1.json cooldown-l1-input-stall-2.json cooldown-l1-input-stall-3.json]
        .map { |name| "client-a/#{name}" }
    end
    if L1_CASES.include?(manifest.fetch('case')) || COOLDOWN_L1_CASES.include?(manifest.fetch('case'))
      files += %w[episode-1.json episode-2.json episode-3.json failure.json stopped.json data-terminal.json a-auth-3.json a-auth-4.json
                  impact-custody-result.json two-work-armed.json two-work-before-boundary.json two-work-result.json two-work-failure.json supplemental-result.json
                  lifecycle-armed.json lifecycle-before-native.json lifecycle-work-terminal.json lifecycle-result.json a-config-return.json
                  work-reward-local.json work-reward-physical.json stats-memory-logout.json stats-memory-work.json stats-memory-reconnected.json stats-memory-save.json stats-memory-cleanup-failure.json
                  terminal-boundary-armed.json terminal-boundary-logout.json terminal-boundary-native-error.json terminal-boundary-result.json terminal-boundary-failure.json
                  tracking-retirement-armed.json tracking-retirement-before.json tracking-retirement-result.json capacity-work-armed.json capacity-work-result.json natural-unload-armed.json natural-unload-fixture.json natural-unload-logout.json natural-unload-tracking.json natural-unload-result.json online-peer-armed.json online-peer-input-ready.json online-peer-result.json work-context-local.json work-context-result.json]
        .map { |name| "server/#{name}" }
      files += %w[login-3.json login-4.json cast-1.json cast-2.json cast-3.json].map { |name| "client-a/#{name}" }
      files += %w[client-resource-reload-started.json client-resource-reload-completed.json].map { |name| "client-a/#{name}" }
      files += %w[lifecycle-config-terminal.json lifecycle-world-ready.json].map { |name| "client-a/#{name}" }
      files << 'client-a/terminal-close.json'
      files += %w[client-b/cast-1.json client-b/capacity-swing.json]
      files += %w[client-a/capacity-focus-request.json client-b/capacity-focus-request.json client-a/natural-unload-close.json client-a/work-context-client.json]
      if %w[l1-restart-write l1-restart-read].include?(manifest.fetch('case'))
        files += %w[l1-restart-first-work.json l1-restart-before-stop.json l1-restart-expected.json
                    l1-restart-stop-failure.json l1-restart-before-login.json l1-restart-result.json].map { |name| "server/#{name}" }
        files << 'l1-restart-input.json' if manifest.fetch('case') == 'l1-restart-read'
      end
    end
    if C4A_CASES.include?(manifest.fetch('case')) || manifest.fetch('case') == D3_C4A_CASE
      files += (C4A_SCENE_FILES + ['normal-path-result.json']).map { |name| "server/#{name}" }
      files += manifest.fetch('roles').reject { |role| role == 'server' }.flat_map do |role|
        C4A_SCENE_FILES.map { |name| "client-#{role}/#{name}" }
      end
      files << 'client-host/host-published.json' if manifest.fetch('case') == 'c4a-host-lan'
      if manifest.fetch('case') == D3_C4A_CASE
        files += %w[enter-config-terminal.json metadata-h-config-terminal.json reload-fop.json reload-qctx.json
                    reload-subset-result.json first-try.json reload-h.json parking.json parking-keepalive.json]
          .map { |name| "server/#{name}" }
        files += %w[enter-config-terminal.json metadata-h-config-terminal.json reload-fop.json reload-qctx.json
                    first-try.json reload-h.json parking.json].map { |name| "client-a/#{name}" }
      end
      if manifest.fetch('l1HostStop', false)
        files += %w[host-l1-reward.json host-l1-open-before-quit.json host-l1-stopped.json host-l1-stop-failure.json]
          .map { |name| "server/#{name}" }
        files += %w[client-host/host-l1-terminal.json client-b/host-l1-terminal.json]
      end
      if manifest.fetch('cooldownHost', false)
        files += %w[cooldown-host-formal.json cooldown-host-arm.json cooldown-host-saved.json
                    cooldown-host-stopped.json cooldown-host-failure.json cooldown-host-diagnostic.json].map { |name| "server/#{name}" }
        files << 'client-host/cooldown-host-input.json'
      end
      if manifest.fetch('case') == 'c4a-reward'
        files += %w[reward-end-armed.json reward-end-complete.json reward-detached-logical.json
                    reward-continuity-server.json].map { |name| "server/#{name}" }
        files << 'client-a/reward-delivery-client.json'
      end
    end
    if manifest['diagnoseClient']
      files += manifest.fetch('roles').reject { |role| role == 'server' }.flat_map do |role|
        %w[started.json uncaught.json shutdown.json].map { |name| "client-#{role}-launch-diagnostic/#{name}" }
      end
    end
    present = files.select do |relative|
      file = nonsecret_path!(File.join(manifest.fetch('evidence'), relative), manifest.fetch('privateRoot'))
      File.file?(file)
    end
    { 'status' => 'STRUCTURED_FILE_PRESENCE_ONLY', 'present' => present,
      'authenticationAcceptance' => 'NOT_INFERRED_FROM_FILE_PRESENCE' }
  end

  def main(args)
    command = args.shift
    options = { repo: REPO, runtime_root: RUNTIME_ROOT, evidence_root: EVIDENCE_ROOT,
                private_root: PRIVATE_ROOT, fixture: FIXTURE, jar: File.join(REPO, 'build/libs/gramarye-1.0.0.jar'),
                case: 'single', port: 0, java_home: JAVA_HOME, ready_only: false, accept_eula: false, diagnose_client: false, l1_host_stop: false, cooldown_host: false }
    parser = OptionParser.new do |opt|
      opt.banner = 'Usage: p11-online-runtime.rb prepare|prepare-launchers|freeze-launchers|launch-server|launch-client|stop|status [options]'
      opt.on('--case NAME') { |v| options[:case] = v }
      opt.on('--run-id ID') { |v| options[:run_id] = v }
      opt.on('--port N', Integer) { |v| options[:port] = v }
      opt.on('--runtime-root PATH') { |v| options[:runtime_root] = v }
      opt.on('--evidence-root PATH') { |v| options[:evidence_root] = v }
      opt.on('--private-root PATH') { |v| options[:private_root] = v }
      opt.on('--jar PATH') { |v| options[:jar] = v }
      opt.on('--fixture PATH') { |v| options[:fixture] = v }
      opt.on('--java-home PATH') { |v| options[:java_home] = v }
      opt.on('--manifest PATH') { |v| options[:manifest] = v }
      opt.on('--client ROLE') { |v| options[:client] = v }
      opt.on('--ready-only') { options[:ready_only] = true }
      opt.on('--diagnose-client') { options[:diagnose_client] = true }
      opt.on('--l1-host-stop') { options[:l1_host_stop] = true }
      opt.on('--cooldown-host') { options[:cooldown_host] = true }
      opt.on('--restart-from PATH') { |value| options[:restart_from] = value }
      opt.on('--accept-eula') { options[:accept_eula] = true }
    end
    parser.parse!(args)
    check(args.empty?, 'UNEXPECTED_ARGUMENT')
    result = if command == 'prepare'
               options[:run_id] ||= "p11-#{options[:case]}-#{SecureRandom.hex(6)}"
               manifest = prepare(options)
               { 'status' => 'PREPARED_NOT_LAUNCHED', 'runtime' => manifest['runtime'],
                 'evidence' => manifest['evidence'], 'port' => manifest['port'] }
             else
               check(!options[:l1_host_stop] && !options[:cooldown_host] && options[:restart_from].nil?, 'PREPARE_ONLY_OPTION')
               check(%w[prepare-launchers freeze-launchers launch-server launch-client stop status].include?(command), 'INVALID_COMMAND')
               manifest = load_manifest(options.fetch(:manifest), runtime_root: options[:runtime_root],
                                        private_root: options[:private_root])
               if command == 'launch-client'
                 launch_client(manifest, options.fetch(:client))
               else
                 public_send(command.tr('-', '_'), manifest)
               end
             end
    puts JSON.generate(result)
    0
  rescue Failure => failure
    warn JSON.generate('status' => 'ERROR', 'code' => failure.code)
    2
  rescue StandardError => failure
    # Exception messages/argv/stack traces can contain private caller input. Never emit them.
    warn JSON.generate('status' => 'ERROR', 'code' => 'RUNTIME_HELPER_FAILURE', 'class' => failure.class.name)
    2
  end
end

exit(P11OnlineRuntime.main(ARGV)) if $PROGRAM_NAME == __FILE__
