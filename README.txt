FROST PLUGIN v1.7 — PAPER 1.21.11 — JAVA 21

BEFORE USING: This is SOURCE, not a precompiled plugin JAR.

Build: upload contents to your EXISTING GitHub repository, preserving file paths,
then Actions -> Build plugin -> Run workflow, and download successful artifact.
No need to replace .github if your existing build.yml already works.
Place the new JAR in plugins/ (REMOVE OLD JAR), restart server.

COMMANDS:
/frost help
/frost givesnowball [player]
/frost givetnt [player]
/frost givewindcharge [player]
/frost givetrident [player]     Grapple + Loyalty trident
/frost giveriptide [player]     Riptide lightning trident
/frost givefirework [player]
/frost config                   OP-only inventory GUI for weapon settings
/frost reload                   reload config.yml from disk (OP)
/frost trust STSZinnn           protect this player from YOUR Frost effects
/frost untrust STSZinnn         stop protecting the player
/frost trusted                  list YOUR protected players

TRUST RULES:
* Trust is one-way. If Alice trusts Bob, Alice's Frost weapons won't hurt Bob.
  Bob can still damage Alice unless Bob also trusts Alice.
* Protection applies to Snowball (knockback/slowness), TNT player damage,
  Wind Charge freeze, Grapple Trident (normal melee/projectile hit + pulls),
  Riptide Trident (normal melee + lightning), and homing fireworks.
* The homing barrage excludes trusted players when selecting a target, and
  missiles stop if their owner trusts a targeted player later.
* Block destruction from TNT is not affected by trust; only player effects
  and damage are protected. No protection for unrelated non-Frost items.
* Names must refer to an online or previously joined player. UUIDs saved in
  plugins/PunchSnowball/trusted.yml; surviving name changes and restarts.
* Only the owner of a trust list can change that list through /frost trust.

IN-GAME CONFIG GUI:
* OP: /frost config -> select one of the six weapon types.
* Left-click increases a value; right-click decreases; Shift = 5x change.
* Click toggles ON/OFF for boolean switches.
* Saves to plugins/PunchSnowball/config.yml immediately, no reload needed.
* The labels/lore on previously given items don't update automatically;
  give yourself a new copy if you want the displayed tooltip refreshed.
* For manual edits: save config.yml and run /frost reload.

NOTE: Vanilla client cannot completely lock camera for wind freeze.
Actual TNT block break follows vanilla explosion physics (and protection plugins).
IMPORTANT: Compile and test on a private test server before deploying.
