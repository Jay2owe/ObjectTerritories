// Both menu commands must be registered by the plugin jar's plugins.config.
List.setCommands;
ok = true;
single = List.get("Object Territories");
batch = List.get("Object Territories Batch...");
if (single != "territories.Object_Territories") {
    print("SMOKE FAIL commands: 'Object Territories' -> '" + single + "'");
    ok = false;
}
if (batch != "territories.Object_Territories_Batch") {
    print("SMOKE FAIL commands: 'Object Territories Batch...' -> '" + batch + "'");
    ok = false;
}
if (ok) print("SMOKE PASS commands");
