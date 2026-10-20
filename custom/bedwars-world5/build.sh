#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
root=../..
api="$root/libraries/org/purpurmc/purpur/purpur-api/26.1.2.build.2592-stable/purpur-api-26.1.2.build.2592-stable.jar"
bedwars="$root/plugins/BedWars-0.2.44.jar"
snakeyaml="$root/libraries/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar"
adventure="$root/libraries/net/kyori/adventure-api/4.26.1/adventure-api-4.26.1.jar"
adventure_key="$root/libraries/net/kyori/adventure-key/4.26.1/adventure-key-4.26.1.jar"
annotations=lib/annotations-26.0.2.jar
guava="$root/libraries/com/google/guava/guava/33.5.0-jre/guava-33.5.0-jre.jar"
bungee="$root/libraries/net/md-5/bungeecord-chat/1.21-R0.2-deprecated+build.21/bungeecord-chat-1.21-R0.2-deprecated+build.21.jar"
mkdir -p build/classes
javac -cp "$api:$bedwars:$snakeyaml:$adventure:$adventure_key:$annotations:$guava:$bungee" -d build/classes src/local/ronit/bedwars/*.java
cp plugin.yml config.yml build/classes/
jar cf "$root/plugins/BedwarsWorldFive.jar" -C build/classes .
