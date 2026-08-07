import assert from 'node:assert/strict'
import crypto from 'node:crypto'
import fs from 'node:fs'
import net from 'node:net'
import path from 'node:path'
import { spawn, spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import mineflayer from 'mineflayer'
import { Vec3 } from 'vec3'

const TEST_DIR = path.dirname(fileURLToPath(import.meta.url))
const ROOT_DIR = path.resolve(TEST_DIR, '..')
const CACHE_DIR = path.join(TEST_DIR, '.cache')
const RUNS_DIR = path.join(TEST_DIR, 'runs')
const USER_AGENT = 'SneakGrow-E2E/1.0 (https://github.com/Team-Fruit/SneakGrow)'
const VIA_VERSION = '5.11.0'
const DEFAULT_VERSIONS = ['1.12.2', '1.16.5', '1.19.4', '1.20.1', '1.20.6', '1.21.11', '26.1', '26.2']

const requested = process.argv[2]
const versions = requested ? requested.split(',').filter(Boolean) : DEFAULT_VERSIONS

fs.mkdirSync(CACHE_DIR, { recursive: true })
fs.mkdirSync(RUNS_DIR, { recursive: true })

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

async function fetchJson(url) {
  const response = await fetch(url, { headers: { 'User-Agent': USER_AGENT } })
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`)
  return response.json()
}

function versionCompare(a, b) {
  return b.localeCompare(a, undefined, { numeric: true, sensitivity: 'base' })
}

async function resolvePaperVersion(requestedVersion) {
  const project = await fetchJson('https://fill.papermc.io/v3/projects/paper')
  const available = Object.values(project.versions).flat()
  if (available.includes(requestedVersion)) return requestedVersion
  const patches = available.filter(version => version.startsWith(`${requestedVersion}.`)).sort(versionCompare)
  if (patches.length) return patches[0]
  throw new Error(`Paper ${requestedVersion} (or ${requestedVersion}.x) is not available`)
}

async function downloadPaper(requestedVersion) {
  const paperVersion = await resolvePaperVersion(requestedVersion)
  const builds = await fetchJson(`https://fill.papermc.io/v3/projects/paper/versions/${paperVersion}/builds`)
  const build = builds.find(candidate => candidate.channel === 'STABLE') ?? builds[0]
  if (!build) throw new Error(`No Paper build for ${paperVersion}`)
  const download = build.downloads['server:default']
  const destination = path.join(CACHE_DIR, download.name)

  if (!fs.existsSync(destination) || sha256(destination) !== download.checksums.sha256) {
    console.log(`[${requestedVersion}] downloading ${download.name}`)
    const response = await fetch(download.url, { headers: { 'User-Agent': USER_AGENT } })
    if (!response.ok) throw new Error(`${download.url}: HTTP ${response.status}`)
    fs.writeFileSync(destination, Buffer.from(await response.arrayBuffer()))
    assert.equal(sha256(destination), download.checksums.sha256, 'Paper checksum mismatch')
  }
  return { paperVersion, jar: destination }
}

async function downloadViaPlugins() {
  const projects = ['ViaVersion', 'ViaBackwards']
  const files = []
  for (const project of projects) {
    const destination = path.join(CACHE_DIR, `${project}-${VIA_VERSION}.jar`)
    if (!fs.existsSync(destination)) {
      console.log(`[compat] downloading ${project} ${VIA_VERSION}`)
      const url = `https://hangar.papermc.io/api/v1/projects/${project}/versions/${VIA_VERSION}/PAPER/download`
      const response = await fetch(url, { headers: { 'User-Agent': USER_AGENT } })
      if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`)
      fs.writeFileSync(destination, Buffer.from(await response.arrayBuffer()))
    }
    files.push(destination)
  }
  return files
}

function sha256(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')
}

function requiredJava(version) {
  if (version.startsWith('26.')) return 25
  if (version === '1.12.2') return 11
  if (version === '1.16.5') return 16
  if (version.startsWith('1.19.')) return 17
  return 21
}

function javaExecutable(version) {
  const major = requiredJava(version)
  const direct = process.env[`E2E_JAVA_${major}`]
  const javaHome = process.env[`JAVA_HOME_${major}`]
  const executable = direct || (javaHome ? path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java')
  const check = spawnSync(executable, ['-version'], { encoding: 'utf8' })
  if (check.error) throw new Error(`Java ${major} is required for ${version}; set E2E_JAVA_${major} or JAVA_HOME_${major}`)
  return executable
}

function buildPlugin() {
  if (process.env.E2E_PLUGIN_JAR) return path.resolve(process.env.E2E_PLUGIN_JAR)
  const mvn = process.platform === 'win32' ? 'mvn.cmd' : 'mvn'
  const result = spawnSync(mvn, ['-q', '-DskipTests', 'package'], {
    cwd: ROOT_DIR,
    stdio: 'inherit',
    shell: process.platform === 'win32'
  })
  if (result.error || result.status !== 0) throw new Error(`Maven build failed${result.error ? `: ${result.error.message}` : ''}`)
  return path.join(ROOT_DIR, 'target', 'sneakgrow-1.5-SNAPSHOT.jar')
}

async function freePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer()
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port
      server.close(() => resolve(port))
    })
  })
}

function prepareServer(runDir, pluginJar, compatibilityPlugins, port) {
  fs.rmSync(runDir, { recursive: true, force: true })
  fs.mkdirSync(path.join(runDir, 'plugins', 'SneakGrow'), { recursive: true })
  fs.copyFileSync(pluginJar, path.join(runDir, 'plugins', 'SneakGrow.jar'))
  for (const compatibilityPlugin of compatibilityPlugins)
    fs.copyFileSync(compatibilityPlugin, path.join(runDir, 'plugins', path.basename(compatibilityPlugin)))
  fs.writeFileSync(path.join(runDir, 'eula.txt'), 'eula=true\n')
  fs.writeFileSync(path.join(runDir, 'server.properties'), [
    'online-mode=false',
    `server-port=${port}`,
    'server-ip=127.0.0.1',
    'level-type=FLAT',
    'generate-structures=false',
    'spawn-protection=0',
    'view-distance=4',
    'simulation-distance=4',
    'motd=SneakGrow E2E'
  ].join('\n') + '\n')
  fs.writeFileSync(path.join(runDir, 'plugins', 'SneakGrow', 'config.yml'), `Tweaks:
  cooldownTicks: 1
  growSaplings: false
  growCrops: true
  showParticles: false
  blockRadius: 5
  mobRadius: 0
  blockPercentage: 0.25
  mobPercentage: 0.0
`)
}

function waitForServer(child, log, requestedVersion) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error(`[${requestedVersion}] server startup timed out`)), 180_000)
    const onData = chunk => {
      const text = chunk.toString()
      log.write(text)
      process.stdout.write(`[${requestedVersion}] ${text}`)
      if (/Done \(.+\)! For help/.test(text)) {
        clearTimeout(timeout)
        resolve()
      }
    }
    child.stdout.on('data', onData)
    child.stderr.on('data', onData)
    child.once('exit', code => {
      clearTimeout(timeout)
      reject(new Error(`[${requestedVersion}] server exited during startup (${code})`))
    })
  })
}

function command(child, value) {
  child.stdin.write(`${value}\n`)
}

async function connectBot(version, port) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({
      host: '127.0.0.1',
      port,
      username: 'SneakGrowTest',
      version,
      auth: 'offline'
    })
    const timeout = setTimeout(() => reject(new Error('mineflayer login timed out')), 45_000)
    bot.once('spawn', () => {
      clearTimeout(timeout)
      resolve(bot)
    })
    bot.once('error', error => {
      clearTimeout(timeout)
      reject(error)
    })
  })
}

function cropAges(bot) {
  const ages = []
  for (let x = -2; x <= 2; x++) {
    for (let z = -2; z <= 2; z++) {
      if (x === 0 && z === 0) continue
      const block = bot.blockAt(new Vec3(x, 65, z))
      if (block?.name === 'wheat') ages.push(Number(block.getProperties().age ?? block.metadata ?? 0))
    }
  }
  return ages
}

async function runVersion(requestedVersion, pluginJar) {
  const { paperVersion, jar } = await downloadPaper(requestedVersion)
  const compatibilityPlugins = paperVersion.startsWith('26.') ? await downloadViaPlugins() : []
  const runDir = path.join(RUNS_DIR, requestedVersion)
  const port = await freePort()
  prepareServer(runDir, pluginJar, compatibilityPlugins, port)
  const log = fs.createWriteStream(path.join(runDir, 'e2e.log'))
  const child = spawn(javaExecutable(paperVersion), ['-Xms512M', '-Xmx1G', '-jar', jar, 'nogui'], {
    cwd: runDir,
    stdio: ['pipe', 'pipe', 'pipe']
  })
  let bot
  try {
    await waitForServer(child, log, requestedVersion)
    bot = await connectBot(paperVersion.startsWith('26.') ? '1.21.11' : paperVersion, port)
    command(child, 'gamemode spectator SneakGrowTest')
    command(child, 'tp SneakGrowTest 0.5 70 3.5')
    await sleep(1_000)
    const modernGameRules = paperVersion.startsWith('26.') || paperVersion === '1.21.11'
    command(child, modernGameRules ? 'gamerule minecraft:random_tick_speed 0' : 'gamerule randomTickSpeed 0')
    command(child, modernGameRules ? 'gamerule minecraft:advance_time false' : 'gamerule doDaylightCycle false')
    command(child, 'difficulty peaceful')
    command(child, 'fill -6 63 -6 6 63 6 stone')
    command(child, 'fill -2 64 -2 2 64 2 farmland')
    command(child, 'setblock 0 64 0 water')
    const legacy = paperVersion === '1.12.2'
    for (let x = -2; x <= 2; x++) {
      for (let z = -2; z <= 2; z++) {
        if (x === 0 && z === 0) continue
        command(child, legacy ? `setblock ${x} 65 ${z} wheat 0` : `setblock ${x} 65 ${z} wheat[age=0]`)
      }
    }
    await sleep(1_000)
    command(child, 'tp SneakGrowTest 0.5 65 3.5')
    command(child, 'gamemode survival SneakGrowTest')
    await sleep(1_500)
    const before = cropAges(bot)
    assert.equal(before.length, 24, `expected 24 wheat crops, found ${before.length}`)
    assert.ok(before.every(age => age === 0), `crops were not age 0: ${before}`)

    for (let i = 0; i < 12; i++) {
      bot.setControlState('sneak', true)
      await sleep(150)
      bot.setControlState('sneak', false)
      await sleep(150)
    }
    await sleep(1_000)
    const after = cropAges(bot)
    assert.ok(after.some(age => age > 0), `no crop grew (ages: ${after})`)
    console.log(`[${requestedVersion}] PASS (Paper ${paperVersion}; max wheat age ${Math.max(...after)})`)
  } finally {
    if (bot) bot.quit('test complete')
    if (child.exitCode === null) {
      command(child, 'stop')
      await Promise.race([
        new Promise(resolve => child.once('exit', resolve)),
        sleep(15_000).then(() => child.kill())
      ])
    }
    log.end()
  }
}

const pluginJar = buildPlugin()
const failures = []
for (const version of versions) {
  try {
    await runVersion(version, pluginJar)
  } catch (error) {
    failures.push({ version, error })
    console.error(`[${version}] FAIL: ${error.stack ?? error}`)
  }
}

if (failures.length) {
  console.error(`\n${failures.length}/${versions.length} version(s) failed: ${failures.map(failure => failure.version).join(', ')}`)
  process.exitCode = 1
} else {
  console.log(`\nAll ${versions.length} versions passed.`)
}
