package dev.unyielding.upsidedown.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Admin verification against the running server's actual registries and loot modifiers.
 * Uses transient containers and block entities; never places blocks or grants player items.
 */
public final class DungeonLootChecks {
    private static final String[] TABLES = {"maze_common", "maze_rare", "pagoda_rare", "pagoda_final"};
    private static final String[] EXPECTED_ITEMS = {
        "cobblemon:ability_capsule",
        "cobblemon:ability_patch",
        "cobblemon:ancient_origin_ball",
        "cobblemon:dawn_stone",
        "cobblemon:dream_ball",
        "cobblemon:dusk_ball",
        "cobblemon:exp_candy_l",
        "cobblemon:exp_candy_m",
        "cobblemon:exp_candy_s",
        "cobblemon:exp_candy_xl",
        "cobblemon:full_restore",
        "cobblemon:heal_ball",
        "cobblemon:love_ball",
        "cobblemon:luxury_ball",
        "cobblemon:master_ball",
        "cobblemon:max_potion",
        "cobblemon:max_revive",
        "cobblemon:moon_ball",
        "cobblemon:moon_stone",
        "cobblemon:rare_candy",
        "cobblemon:revive",
        "cobblemon:shiny_stone",
        "cobblemon:ultra_ball",
        "minecraft:amethyst_shard",
        "minecraft:cherry_sapling",
        "minecraft:cookie",
        "minecraft:diamond",
        "minecraft:emerald",
        "minecraft:enchanted_golden_apple",
        "minecraft:glow_berries",
        "minecraft:gold_ingot",
        "minecraft:golden_apple",
        "minecraft:golden_carrot",
        "minecraft:honey_bottle",
        "minecraft:netherite_ingot",
        "minecraft:pink_candle",
        "minecraft:pink_petals",
        "simplyswords:arcanethyst",
        "simplyswords:hearthflame",
        "simplyswords:mjolnir",
        "simplyswords:ribboncleaver",
        "simplyswords:stars_edge",
        "simplyswords:whisperwind"
    };
    private static final Set<String> FINAL_REWARDS = Set.of(
        "cobblemon:master_ball", "cobblemon:ancient_origin_ball", "simplyswords:stars_edge",
        "simplyswords:whisperwind", "simplyswords:ribboncleaver", "simplyswords:mjolnir",
        "simplyswords:hearthflame", "simplyswords:arcanethyst"
    );
    private DungeonLootChecks() {}

    private static ResourceKey<LootTable> key(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath("cuupsidedown", "chests/" + name));
    }

    /** Cheap preflight: no loot rolls. Checks the active registry, not model filenames. */
    public static List<String> problems(ServerLevel level) {
        List<String> failures = new ArrayList<>();
        for (String id : EXPECTED_ITEMS) {
            if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id))) failures.add("Missing reward item: " + id);
        }
        for (String name : TABLES) {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key(name));
            if (table == LootTable.EMPTY) failures.add("Missing or invalid loot table: " + key(name).location());
        }
        if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("lootr:lootr_chest"))) {
            failures.add("Missing native Lootr chest block: lootr:lootr_chest");
        }
        return failures;
    }

    public static int run(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<String> failures = problems(level);
        if (!failures.isEmpty()) return reportFailure(source, failures);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, source.getPosition())
            .withLuck(0).create(LootContextParamSets.CHEST);
        int fills = 0;
        Set<String> finalRewardsSeen = new HashSet<>();
        try {
            for (String name : TABLES) {
                LootTable table = level.getServer().reloadableRegistries().getLootTable(key(name));
                int samples = name.equals("pagoda_final") ? 512 : 64;
                for (long seed = 1; seed <= samples; seed++) {
                    SimpleContainer inventory = new SimpleContainer(27);
                    // Native fill includes active NeoForge global loot modifiers and slot allocation.
                    table.fill(inventory, params, seed);
                    fills++;
                    if (inventory.isEmpty()) {
                        failures.add("Empty roll in " + name + " at seed " + seed);
                        break;
                    }
                    if (name.equals("pagoda_final")) {
                        boolean guaranteedReward = false;
                        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                            ItemStack stack = inventory.getItem(slot);
                            if (!stack.isEmpty()) {
                                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                                if (FINAL_REWARDS.contains(id)) {
                                    guaranteedReward = true;
                                    finalRewardsSeen.add(id);
                                }
                            }
                        }
                        if (!guaranteedReward) {
                            failures.add("Final chest lost its guaranteed high-end reward at seed " + seed
                                + "; inspect active loot-table overrides/global loot modifiers.");
                            break;
                        }
                    }
                }
            }
            // Ensure the installed Lootr block creates native randomizable BEs with independent identities.
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("lootr:lootr_chest"));
            if (!(block instanceof EntityBlock entityBlock)) {
                failures.add("Native Lootr chest does not provide a block entity.");
            } else {
                BlockEntity first = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
                BlockEntity second = entityBlock.newBlockEntity(new BlockPos(1, 0, 0), block.defaultBlockState());
                if (!(first instanceof RandomizableContainerBlockEntity a)
                    || !(second instanceof RandomizableContainerBlockEntity b)) {
                    failures.add("Native Lootr chest BE is not a RandomizableContainerBlockEntity.");
                } else {
                    if (!first.getType().isValid(block.defaultBlockState()) || !second.getType().isValid(block.defaultBlockState()))
                        failures.add("Native Lootr block entity type does not accept its chest block.");
                    a.setLootTable(key("pagoda_final"));
                    b.setLootTable(key("pagoda_final"));
                    a.setLootTableSeed(123L);
                    b.setLootTableSeed(123L);
                    UUID aId = (UUID) first.getClass().getMethod("getInfoUUID").invoke(first);
                    UUID bId = (UUID) second.getClass().getMethod("getInfoUUID").invoke(second);
                    UUID aAgain = (UUID) first.getClass().getMethod("getInfoUUID").invoke(first);
                    if (aId.equals(bId) || !aId.equals(aAgain)) failures.add("Native Lootr container identities are not independent/stable.");
                    if (!key("pagoda_final").equals(a.getLootTable()) || a.getLootTableSeed() != 123L)
                        failures.add("Native Lootr BE rejected the assigned loot table/seed.");
                }
            }
        } catch (Exception e) {
            failures.add("Loot verification raised " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        if (!failures.isEmpty()) return reportFailure(source, failures);
        final int total = fills;
        source.sendSuccess(() -> Component.literal("Dungeon loot verified: " + EXPECTED_ITEMS.length
            + " registered reward items, 4 loaded tables, " + total + " native 27-slot chest fills; all 512 final rolls kept a high-end reward ("
            + finalRewardsSeen.size() + " reward types observed). Fresh Lootr identities are independent. "
            + "Two real players must still test per-player opening/persistence."), false);
        return total;
    }

    private static int reportFailure(CommandSourceStack source, List<String> failures) {
        for (String failure : failures) source.sendFailure(Component.literal(failure));
        return 0;
    }
}
