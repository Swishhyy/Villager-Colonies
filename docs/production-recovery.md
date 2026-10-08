# Production and recovery in 0.12.3

Every job block still takes one citizen, except quarries. These changes improve the output and reliability of that single worker.

## Furnaces and kitchens

Cooks accept ordinary furnaces, smokers, and lit campfires. Smelters accept furnaces and blast furnaces. Supply raw food or smeltable ore, and ordinary fuel, in the station's job barrel; couriers bring these materials from the warehouse. Kitchens can also bake bread from three real wheat.

Workers approach clear ground within hand reach and sight of the appliance or barrel. They do not need to climb onto the appliance. Keep a walkable route and two blocks of headroom beside the work area. A sealed appliance is skipped so another reachable appliance can keep working. Late supplies are retried. Furnaces shared by cook and smeltery ranges belong to the nearest compatible station, with the existing coordinate tie-breaker.

Existing furnace input is topped up with matching items and components. Actual vanilla appliances still own cooking progress, fuel use, and outputs. Blocked input slots and extinguished campfires need player attention; workers do not discard player-loaded contents.

## Yield upgrades

Open a Farm Station or Mine Station and buy **Yield** on its screen. No command is required.

| Level | Average extra output | Default price for this level |
| --- | --- | --- |
| 1 | 10% | 16 emeralds |
| 2 | 20% | 32 emeralds |
| 3 | 30% | 64 emeralds |

Each eligible harvested unit has the listed chance of one extra unit. Farm upgrades affect produce after reserving the planting item. They do not multiply wheat or beetroot seeds or poisonous potatoes. Mine upgrades affect mineral drops from endless veins, cave ore and tunnel ore, including normal Fortune results. They do not multiply stone, building blocks, Silk Touch ore blocks, or ancient debris blocks. Quarries have no yield upgrade.

Upgrades survive settlement saves and station block drops. Moving an upgraded block preserves its yield level and range; no extra worker slots are created. `yieldUpgradeCost` sets the first price, with each later price doubled.

## Pickaxes and replenishment

A miner's equipped pickaxe controls both physical digging speed and the next vein replenishment delay. Give the miner a better tool through its equipment screen. It must be suitable for that ore and have usable durability.

| Equipped pickaxe | Common vein delay at the default setting |
| --- | --- |
| Wood | 20 seconds |
| Stone | 15 seconds |
| Iron | About 12.3 seconds |
| Diamond | About 10.6 seconds |
| Netherite | 10 seconds |

Replenishment uses the square root of tool mining speed relative to stone, bounded from 0.75 to 1.5 times stone speed. Gold and unusually fast modded picks cannot exceed the cap, and weak tools cannot evade harvest requirements. Gold ore takes twice the common delay, diamond and emerald six times, and ancient debris eight times. The existing `oreVeinSeconds` setting is the stone baseline. Physical work time is additional, and each harvest still spends durability. Changing tools affects subsequent harvests, without resetting an already scheduled vein cooldown.

## Hospital recovery

Any injured citizen, including guards and medics, pauses its normal work and seeks a free, reachable bed belonging to a **Hospital Station**. Ordinary housing beds do not provide injury recovery. A hospital bed heals **1 health point every 5 loaded seconds**, without requiring supplies or a funded project. Citizens actually lie in the bed, reserve it, and stay until exactly full health. They then release the bed and resume their saved job. Alarms do not pull patients out of bed.

Patients queue when beds are occupied. If no suitable bed is loaded or reachable, they wait for hospital capacity and do not heal elsewhere. They can still obtain a meal when hungry. Breaking a patient bed releases its occupant and stops bed healing; rebuilding an accessible bed lets recovery resume. Hospital bed choice and partial healing progress are saved with the citizen.

Funding the **Field Hospital** project unlocks the hospital's single medic. While on duty and within reach and sight of a patient, the medic can spend one real meal and one paper dressing for **1 additional health point**, at most once per 5 seconds. Meal containers are preserved. An injured medic rests as a patient; the basic bed recovery still works without a healthy medic.

Ordinary meals and direct feeding satisfy hunger instead of restoring injury health. Hospital beds remain separate from recruitment capacity.
