
# TENSA Plugin

TENSA Velocity Plugin - This one offers a variety of modules for detailed server management and monitoring. Each module can be turned on or off as needed. The plugin is designed to be as flexible as possible, allowing you to customize your server's functionality to your specific needs.
## Commands
- `/tensa`: Help command to display all available commands.
- `/tensareload`: Reloads the plugin configuration file.
- `/tensamodules`: Displays a list of all available modules.
- `/vpl -v`: Display plugin list.
- `/psend <player/all> <server>`: Sends the specified player to the specified server.

## Modules
### PlayerTime:
Tracks the total playing time of each player on the server, providing the ability to view the time played by a specific player or the entire server.
- `/vptime`: Returns the player's total playing time.
- `/vptime <player>`: Returns the specified player's total playing time.
- `/vptop:` Returns the top 10 players with the most playing time.

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
    pass: asdasdadsasadadsdasdasdasdasd
  vanilla:
    ip: 0.0.0.0
    port: 25576
    pass: ksdfkldkldadurjfsdjkjasdksasdasdasds
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
password: gdashgdashdfasghdfasghdfa
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

### ChatManager:
Provides the ability to manage chat messages.
```yaml
# Chat Manager 
# Placeholders: {player}, {server}, {message}

# Global chat
global:
  enabled: true
  alias: '!'
  command: g
  # If empty, everyone can use this chat and see the messages
  permission: tensa.chat.global
  see_all: true
  format: '&8[&6G&8] &a{player} &6=> &f{message}'
# Staff chat
staff:
  enabled: true
  alias: '@'
  command: s
  permission: tensa.chat.staff
  see_all: false
  format: '&8&l[&4&lS&8&l] &b&l{server} &a&l{player} &6&l=> &f&l{message}'
# Alert chat
alert:
  enabled: true
  alias: ''
  command: alert
  permission: tensa.chat.alert
  see_all: true
  format: '&8[&4ALERT&8] &f{message}'
```

