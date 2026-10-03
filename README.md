
# Tensa Plugin

Tensa Velocity Plugin - This one offers a variety of modules for detailed server management and monitoring. Each module can be turned on or off as needed. The plugin is designed to be as flexible as possible, allowing you to customize your server's functionality to your specific needs.

Runtime requirement: Velocity 4.2.0 or newer and Java 25. The release is also
smoke-tested against the current 4.2.1-SNAPSHOT build from PaperMC.

Tensa 3.1 removes LibreLogin Auth Bridge, its API dependency and the `tensa:auth`
protocol. Existing `auth-bridge/` files are left untouched and ignored; the retired
module flag is removed during normal root config reconciliation. If a backend
still has the old TensaProxy auth guard enabled, retire that guard before updating
the proxy. Tensa no longer supplies its signed authorization leases. Discord
linking, chat, achievements/deaths and the opt-in command bridge are independent.
See [the current audit and rollout notes](docs/PLUGIN_AUDIT.md).

## Dev Server
- Run the local Velocity dev server from the project root with `.\.run\run-velocity.cmd`.
- The script builds `target/Tensa.jar`, downloads the matching Velocity runtime from Fill v3, copies the plugin into `.run/velocity/plugins/`, and starts the proxy.
- Optional flags:
  - `.\.run\run-velocity.cmd -WithTests` to build and run tests before launch
  - `.\.run\run-velocity.cmd -SkipBuild` to restart the proxy with the already-built jar
  - `.\.run\run-velocity.cmd -VelocityVersion 4.2.0` to pin the supported release
  - `.\.run\smoke-velocity.cmd -SkipBuild -VelocityVersion 4.2.1-SNAPSHOT` to check the latest development build

For an isolated, non-interactive runtime check, run
`.\.run\smoke-velocity.cmd -WithTests`. It recreates `.run/velocity-smoke`,
starts a localhost-only Velocity process without Discord credentials or third-party
plugins, verifies the enabled local modules, performs five targeted Scheduler
and Communications reloads plus a full reload, checks for duplicate ticks and
absence of the retired auth bridge, and shuts the process down. This
profile never reads `.run/velocity` or a production server directory. Tensa is
built and tested exclusively for Velocity 4; the runner rejects Velocity 3.

## Velocity Log Cleanup
You can let the plugin clean old Velocity logs on startup through `config.yml`:

```yaml
velocity:
  log_cleanup:
    enable: true
    latest_log: false
    rotated_logs: true
    compressed_logs: true
```

- `latest_log` is best-effort only. On Windows, Velocity may still hold the file handle, so truncation can fail while rotated `.log` and `.log.gz` files are cleaned normally.

## Config Models
- Typed config classes with annotations live under `ua.co.tensa.config.model`.
- Each model maps fields to YAML keys via `@CfgKey`, writes missing defaults, and reloads safely.
- Global access is via `Tensa.config` which wraps `AppConfig` (for `config.yml`).

Quick example:
```java
// Create a config model bound to user_meta/config.yml
public class UserMetaConfig extends ConfigBase {
  @CfgKey("storage.type") public String storageType = "database";
  @CfgKey("default_persist") public boolean defaultPersist = true;
  public UserMetaConfig() {
    super("user_meta/config.yml");
    reloadCfg(); // Bind after field initializers have supplied defaults.
  }
}
```

Usage:
- Keep an instance (for example, `var userMeta = new UserMetaConfig()`) and read
  its typed fields, such as `userMeta.storageType`.
- Reload one module with `/tensa reload <module-id>` or reconcile all modules with
  `/tensa reload all`. A targeted reload re-reads the root module flag and can
  recover a configured module whose previous startup failed.
- Root config accessors: `Tensa.config.isModuleEnabled("communications")`,
  `getLang()`, database getters, etc.

## Commands
- `/tensa`: Help command to display all available commands.
- `/tensa help`: Displays command help.
- `/tensa info`: Displays the plugin name and version without module-specific arguments.
- `/tensa modules`: Displays all available modules.
- `/tensa reload <module-id>|all`: Validates and reconciles one module or all modules.
- `/tpl -v`: Display plugin list.
- `/psend <player/all> <server>`: Sends the specified player to the specified server.

## Modules
### Command Scheduler:
The `scheduler` module runs named Velocity console-command schedules from
`scheduler/config.yml`. It supports fixed or random intervals and four execution
modes: `all`, `random`, `shuffle`, and `round_robin`. Reload only this module with
`/tensa reload scheduler`; the candidate file is completely validated before the
active jobs are replaced. See [`docs/COMMAND_SCHEDULER.md`](docs/COMMAND_SCHEDULER.md).
If the scheduler is enabled in root `config.yml` but its previous startup failed,
the same command retries only that module after validating the current files.

Internal reconnect/retry jobs use a separate lifecycle scheduler and do not
depend on whether the public command scheduler module is enabled.

### PlayerTime:
Tracks the total playing time of each player on the server, providing the ability to view the time played by a specific player or the entire server.
- `/tptime`: Returns the player's total playing time.
- `/tptime <player>`: Returns the specified player's total playing time.
- `/tptop:` Returns the top 10 players with the most playing time.

### RconManager:
Enables execution of RCON commands on remote servers, utilizing a configuration file for server data storage.
- `/rcon <server/all/reload> <commad>`: Sends the specified command to the specified server or all servers.
```yaml
# Rcon servers
# To allow the use of a separate server for a player, use permission:
# tensa.rcon.serve_name
# Examples: tensa.rcon.lobby, tensa.rcon.vanilla

servers:
  lobby:
    ip: 0.0.0.0
    port: 25575
    pass: "<set-in-private-config>"
  vanilla:
    ip: 0.0.0.0
    port: 25576
    pass: "<set-in-private-config>"
# List of rcon server command arguments
tab-complete-list:
  - alert
  - list
  - tps

```

### RconServer:
Establishes an RCON for Velocity server capable of receiving commands from remote clients, facilitating external server management.
```yaml
# Rcon server settings
# Rcon port
port: 25570
# Rcon password
password: "<set-in-private-config>"
# The response is colored or not
colored: true
```

### PhpModule:
Offers the ability to execute PHP scripts, extending the server's functionality with PHP's scripting capabilities. Each script can be called through a command in chat or console.
- `/php <script/reload> <args>`: Executes the specified PHP script.
```php
<?php
// index.php
$argv_line = "";

foreach ($argv as $key => $value) {
  if ($key == 0) {
    continue;
  }
  $argv_line .= " " . $value;
}
echo "&6--------------------------------------------------------------";
echo "\n&7You have run the script: &3&l$argv[0]\n&7With arguments:&3&l$argv_line";
echo "\n&6--------------------------------------------------------------\n";

// echo json_encode($argv);
```

### BashModule:
Provides a direct interface for server administrators to execute Bash scripts via in-game chat or console. Each script can be called through a command in chat or console.
- `/bash <script/reload> <args>`: Executes the specified Bash script.
```shell
#!/bin/bash
# run.sh
# Command: bash run shell <command>
str=""
for arg in "$@"; do
  str="$str $arg"
done
echo "&6--------------------------------------------------------------"
if [[ $1 == "shell" ]]; then
  find="shell"
  replace=""
  result=${str//$find/$replace}
  $result
  echo "&6--------------------------------------------------------------"
  exit
fi
echo "&7You have run the script: &3&l$0
&7With arguments:&3&l$str"
echo "&6--------------------------------------------------------------"
```

### TextReader:
Reads and outputs the contents of text files located in the "text" folder to players, providing a way to share information directly through the server.
- `/<file>`: File name to read and output its contents.
```txt
# rules.txt
[center]<gray>--------------[<green>Server Rules<gray>]--------------<reset>

[center]<green>This is a test version of text centering</green>
[center]<yellow>Text centering may not work correctly</yellow>

[center]<gold>We can use as standard formatting</gold>
[center]<gold>text and MiniMessage</gold>

<gold>To open this file in the chat, just write the name of this file</gold>

<yellow>You can create many such files and their names will be registered as commands to open the file in the chat</yellow>

<gold>To allow players to use the command grant the right: tensa.text.{filename}</gold>
```

### HttpRequest:
Performs HTTP requests to specified URLs, supporting both GET and POST requests. Configuration files are used to manage request parameters. Allows you to execute commands depending on the HTTP request response. Each individual configuration file corresponds to a single request.
```yaml
# The URL of the API to be queried.
# Available placeholders: %player_name%, %player_uuid%, %player_ip%, %server%, %arg1%, %arg2%, %arg[n]%
url: "https://api.mojang.com/users/profiles/minecraft/%player_name%"

# HTTP request method. Can be "GET" or "POST".
method: "GET"

# Query parameters. They will be sent to the API with the request. If the parameters are not used, this field can be deleted.
# You can use placeholders that will be automatically replaced with the appropriate values when the query is executed.
# Available placeholders: %player_name%, %player_uuid%, %player_ip%, %server%, %arg1%, %arg2%, %arg[n]%
parameters:
  secret: "lmksfdjlfjsffsdfjkljklgjkljsieiweiefdls"
  player: "%player_name%"
  server: "%server%"
  user_code: "%arg1%"

# Commands that cause the request to be executed. When one of these commands is entered, a request to the API will be executed.
triggers:
  - accountlink
  - linkaccount
  - sitelink

# Permission required to use the command. If you want to allow all users to use the command, clear this field.
permission: "account.link"

# Commands to be executed after receiving a response from the API. They are divided into two categories: "success" and "failure".
# "Success" is used when the HTTP response status is 200, and "failure" when the HTTP response status is not 200.
# The response from the API must be in JSON format, and you can use JSON-response keys as placeholders in these commands.
response:
  # Reply with successful status (200)
  success:
    - "alert Player %player_name% is uuid %id%"
    - "Player %player_name% has successfully linked his account to the site %json_resp_key_1%"
    - "msg %player_name% You have successfully linked your account to site. %json_resp_key_2%"
  # Response in case of failed status (not 200)
  failure:
    - "msg %player_name% It was not possible to link the account to the site."

# If this option is enabled, all response options from the api/site will be sent to the sender
debug: true
```

### EventsManager:
Allows you to execute commands when certain events occur on the server. The module supports the following events:
```yaml
# Events settings 
# Placeholders: {player}, {server}, {fromServer}
# [console] - run console command
# [delay] (seconds) - delay seconds command

events:
  on_join_commands:
    enabled: false
    commands:
      - '[delay] 3'
      - '[console] g test {player}'
  on_leave_commands:
    enabled: false
    commands:
      - '[console] alert &6Player {player} left the game'
  on_server_switch:
    enabled: false
    commands:
      - '[console] alert &6Player {player} connected to server {server} from server
        {fromServer}'
  on_server_kick:
    enabled: false
    commands:
      - '[console] alert &6Player {player} kick the server {server}'
  on_server_running:
    enabled: false
    commands:
      - '[console] limbostart first'
  on_server_stop:
    enabled: false
    commands:
      - '[console] alert &6Server {server} is stop'

```

### Communications:
Owns Minecraft chat channels, Discord linking/role reconciliation, webhook-only
chat relay, announcements and delivery as one reloadable runtime. `chats.yml` contains Minecraft channel
routes, permissions and formats; `discord.yml` contains Discord and shared relay settings, including the
logical channels forwarded to Discord. Both live in
the `communications/` module directory. Runtime transitions and failures are
secret-safe lifecycle, chat-transport, queue, scheduler, resource and URL-component snapshot. See
[`docs/COMMUNICATIONS_V2_MIGRATION.md`](docs/COMMUNICATIONS_V2_MIGRATION.md)
before upgrading an existing installation.
Console invocations of every chat route render only their trusted MiniMessage
payload. Player public/private messages keep the configured route wrappers.
Minecraft text now passes through one `TextPipeline`: contextual placeholders,
legacy colors, MiniMessage, safe template values and clickable player URLs have
one ordering and one escaping policy instead of route-specific parsers.
Minecraft-to-Discord chat always uses the configured or automatically managed
global webhook so its per-message Minecraft name and avatar are preserved.
